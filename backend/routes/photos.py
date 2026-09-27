"""Photo bytes for a space, addressed by their own sha256.

Which photos exist, their dates and captions, is part of the state document and merged by
the app. This only stores the bytes. Because a photo's name is its hash, a stored photo
can never change, so there is nothing to version and a repeated upload is harmless.

ponytail: bytes are never deleted — a tombstoned photo keeps its row so undo on either
phone still has something to show. Two people's pregnancy photos are a few hundred MB at
most; add a sweep of rows no live photo references if storage ever matters.
"""

import hashlib
import re

from fastapi import APIRouter, Header, HTTPException, Request, Response

from config import MAX_PHOTO_BYTES
from routes.spaces import _as_uuid, _authorise

router = APIRouter()

_SHA256 = re.compile(r"[0-9a-f]{64}")


def _checked_sha(sha: str) -> str:
    if not _SHA256.fullmatch(sha):
        raise HTTPException(status_code=404, detail="No such photo")
    return sha


@router.get("/spaces/{space_id}/photos")
async def list_photos(request: Request, space_id: str, authorization: str = Header(None)):
    await _authorise(request, space_id, authorization)
    rows = await request.app.state.pool.fetch(
        "select sha256 from photos where space_id = $1", _as_uuid(space_id)
    )
    return {"photos": [r["sha256"] for r in rows]}


@router.put("/spaces/{space_id}/photos/{sha}", status_code=204)
async def put_photo(
    request: Request, space_id: str, sha: str, authorization: str = Header(None)
):
    await _authorise(request, space_id, authorization)
    _checked_sha(sha)

    # Checked before reading so an oversized upload is refused without being buffered.
    declared = request.headers.get("content-length")
    if declared is not None and declared.isdigit() and int(declared) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=413, detail="Photo too large")
    body = await request.body()
    if len(body) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=413, detail="Photo too large")
    # The name is a promise about the contents; holding the client to it is what makes
    # "same name, same bytes" true on the other phone.
    if hashlib.sha256(body).hexdigest() != sha:
        raise HTTPException(status_code=400, detail="Body does not match its sha256")

    await request.app.state.pool.execute(
        """
        insert into photos (space_id, sha256, bytes) values ($1, $2, $3)
        on conflict do nothing
        """,
        _as_uuid(space_id),
        sha,
        body,
    )
    return Response(status_code=204)


@router.get("/spaces/{space_id}/photos/{sha}")
async def get_photo(
    request: Request, space_id: str, sha: str, authorization: str = Header(None)
):
    await _authorise(request, space_id, authorization)
    data = await request.app.state.pool.fetchval(
        "select bytes from photos where space_id = $1 and sha256 = $2",
        _as_uuid(space_id),
        _checked_sha(sha),
    )
    if data is None:
        raise HTTPException(status_code=404, detail="No such photo")
    return Response(content=data, media_type="image/jpeg")
