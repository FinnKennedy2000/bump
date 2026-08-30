"""End-to-end tests against a real Postgres.

    DATABASE_URL=postgresql://... python -m pytest backend/tests

There is no fake pool: the whole point of the endpoint is the atomic
compare-and-set in `update ... where version = $3`, and an in-memory stub would
test the stub rather than the guarantee.
"""

import os

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

pytestmark = pytest.mark.skipif(
    "DATABASE_URL" not in os.environ, reason="needs a Postgres to talk to"
)


@pytest_asyncio.fixture
async def client():
    from main import app

    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as c:
        async with app.router.lifespan_context(app):
            yield c


@pytest_asyncio.fixture
async def space(client):
    r = await client.post("/spaces")
    assert r.status_code == 201
    body = r.json()
    return body["space_id"], {"Authorization": f"Bearer {body['token']}"}


async def test_new_space_starts_empty_at_version_zero(client, space):
    space_id, auth = space
    r = await client.get(f"/spaces/{space_id}/state", headers=auth)
    assert r.status_code == 200
    assert r.json() == {"version": 0, "state": {}}


async def test_put_then_get_round_trips(client, space):
    space_id, auth = space
    doc = {"dueDate": "2026-03-01", "notes": [{"id": "n1", "text": "hi"}]}

    r = await client.put(f"/spaces/{space_id}/state", json=doc,
                         headers={**auth, "If-Match": "0"})
    assert r.status_code == 200
    assert r.json()["version"] == 1

    r = await client.get(f"/spaces/{space_id}/state", headers=auth)
    assert r.json() == {"version": 1, "state": doc}


async def test_stale_version_is_rejected(client, space):
    """The whole reason the version exists: the second phone must not clobber the first."""
    space_id, auth = space
    await client.put(f"/spaces/{space_id}/state", json={"a": 1},
                     headers={**auth, "If-Match": "0"})

    r = await client.put(f"/spaces/{space_id}/state", json={"b": 2},
                         headers={**auth, "If-Match": "0"})
    assert r.status_code == 409

    r = await client.get(f"/spaces/{space_id}/state", headers=auth)
    assert r.json()["state"] == {"a": 1}, "the losing write must not have landed"


async def test_missing_if_match_is_refused(client, space):
    space_id, auth = space
    r = await client.put(f"/spaces/{space_id}/state", json={}, headers=auth)
    assert r.status_code == 428


async def test_bad_token_is_rejected(client, space):
    space_id, _ = space
    bad = {"Authorization": "Bearer not-the-token"}
    assert (await client.get(f"/spaces/{space_id}/state", headers=bad)).status_code == 401
    r = await client.put(f"/spaces/{space_id}/state", json={},
                         headers={**bad, "If-Match": "0"})
    assert r.status_code == 401


async def test_missing_token_is_rejected(client, space):
    space_id, _ = space
    assert (await client.get(f"/spaces/{space_id}/state")).status_code == 401


async def test_unknown_space_is_not_found(client):
    auth = {"Authorization": "Bearer anything"}
    r = await client.get(f"/spaces/{'0' * 8}-0000-0000-0000-{'0' * 12}/state", headers=auth)
    assert r.status_code == 404


async def test_malformed_space_id_is_not_found(client):
    r = await client.get("/spaces/not-a-uuid/state",
                         headers={"Authorization": "Bearer x"})
    assert r.status_code == 404


async def test_non_object_state_is_refused(client, space):
    space_id, auth = space
    r = await client.put(f"/spaces/{space_id}/state", json=[1, 2, 3],
                         headers={**auth, "If-Match": "0"})
    assert r.status_code == 400


async def test_health_needs_no_auth(client):
    assert (await client.get("/health")).json() == {"status": "ok"}


async def test_space_creation_is_rate_limited(client, monkeypatch):
    """The only unauthenticated write must not be an open door to filling the table."""
    from routes import spaces

    monkeypatch.setattr(spaces, "CREATE_RATE_LIMIT", 2)
    monkeypatch.setattr(spaces, "_create_attempts", {})

    assert (await client.post("/spaces")).status_code == 201
    assert (await client.post("/spaces")).status_code == 201
    assert (await client.post("/spaces")).status_code == 429
