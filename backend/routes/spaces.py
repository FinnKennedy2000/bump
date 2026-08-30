"""One shared pregnancy record, stored whole.

The server deliberately knows nothing about the shape of the document it holds. Merging
two phones' edits is last-write-wins per item, and that logic lives once, in the Android
app's `domain` package, where it is pure Kotlin and unit tested. Duplicating it here in
Python would mean two implementations of the same rules, drifting apart.

What the server does provide is the one thing a client cannot: a version number that
makes concurrent writes safe. A client pulls, merges locally, then pushes with the
version it pulled. If the other phone got there first the push is rejected with 409 and
the client pulls again.
"""

import hashlib
import hmac
import json
import secrets
import time
import uuid

from fastapi import APIRouter, Header, HTTPException, Request

from config import CREATE_RATE_LIMIT, CREATE_RATE_WINDOW, MAX_STATE_BYTES

router = APIRouter()


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


# ponytail: rate limiting is an in-process dict, so it resets on deploy and is per
# instance. That is the right size for an endpoint two people call twice — move it to
# Postgres or Redis only if this ever runs more than one replica.
_create_attempts: dict[str, list[float]] = {}


def _rate_limit(ip: str) -> None:
    now = time.time()
    hits = [t for t in _create_attempts.get(ip, []) if now - t < CREATE_RATE_WINDOW]
    if len(hits) >= CREATE_RATE_LIMIT:
        raise HTTPException(status_code=429, detail="Too many spaces created; try later")
    hits.append(now)
    _create_attempts[ip] = hits


async def _authorise(request: Request, space_id: str, authorization: str | None) -> None:
    """404 for a space that does not exist, 401 for a bad token, and nothing else.

    A wrong token must not be distinguishable from a missing space by timing, hence
    `compare_digest` rather than `==`.
    """
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Bearer token required")

    row = await request.app.state.pool.fetchrow(
        "select token_hash from spaces where id = $1", _as_uuid(space_id)
    )
    if row is None:
        raise HTTPException(status_code=404, detail="No such space")

    if not hmac.compare_digest(row["token_hash"], _hash(authorization[7:])):
        raise HTTPException(status_code=401, detail="Bad token")


def _as_uuid(value: str) -> uuid.UUID:
    try:
        return uuid.UUID(value)
    except ValueError:
        raise HTTPException(status_code=404, detail="No such space")


@router.post("/spaces", status_code=201)
async def create_space(request: Request):
    """Create a shared space. The token is returned once and never stored in the clear."""
    _rate_limit(request.client.host if request.client else "unknown")

    space_id = uuid.uuid4()
    token = secrets.token_urlsafe(32)

    await request.app.state.pool.execute(
        "insert into spaces (id, token_hash) values ($1, $2)", space_id, _hash(token)
    )
    return {"space_id": str(space_id), "token": token}


@router.get("/spaces/{space_id}/state")
async def get_state(request: Request, space_id: str, authorization: str = Header(None)):
    await _authorise(request, space_id, authorization)

    row = await request.app.state.pool.fetchrow(
        "select state, version from spaces where id = $1", _as_uuid(space_id)
    )
    return {"version": row["version"], "state": json.loads(row["state"])}


@router.put("/spaces/{space_id}/state")
async def put_state(
    request: Request,
    space_id: str,
    authorization: str = Header(None),
    if_match: str = Header(None),
):
    await _authorise(request, space_id, authorization)

    if if_match is None:
        raise HTTPException(status_code=428, detail="If-Match header required")
    try:
        expected = int(if_match)
    except ValueError:
        raise HTTPException(status_code=400, detail="If-Match must be a version number")

    body = await request.body()
    if len(body) > MAX_STATE_BYTES:
        raise HTTPException(status_code=413, detail="State document too large")
    try:
        state = json.loads(body)
    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Body must be JSON")
    if not isinstance(state, dict):
        raise HTTPException(status_code=400, detail="State must be a JSON object")

    # The version check and the write are one statement, so two phones pushing at the
    # same instant cannot both succeed.
    row = await request.app.state.pool.fetchrow(
        """
        update spaces
           set state = $2::jsonb, version = version + 1, updated_at = now()
         where id = $1 and version = $3
        returning version
        """,
        _as_uuid(space_id),
        json.dumps(state),
        expected,
    )
    if row is None:
        current = await request.app.state.pool.fetchval(
            "select version from spaces where id = $1", _as_uuid(space_id)
        )
        raise HTTPException(
            status_code=409,
            detail=f"Stale version {expected}; server is at {current}",
        )

    return {"version": row["version"]}
