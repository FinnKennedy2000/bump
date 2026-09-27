"""Photo storage against a real Postgres, reusing test_spaces' fixtures."""

import hashlib
import os

import pytest

from tests.test_spaces import client, space  # noqa: F401  (pytest fixtures)

pytestmark = pytest.mark.skipif(
    "DATABASE_URL" not in os.environ, reason="needs a Postgres to talk to"
)

JPEG = b"\xff\xd8\xff" + b"not really a jpeg" * 10
SHA = hashlib.sha256(JPEG).hexdigest()


async def test_put_then_get_round_trips(client, space):
    space_id, auth = space
    r = await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG, headers=auth)
    assert r.status_code == 204

    r = await client.get(f"/spaces/{space_id}/photos/{SHA}", headers=auth)
    assert r.status_code == 200
    assert r.content == JPEG

    r = await client.get(f"/spaces/{space_id}/photos", headers=auth)
    assert r.json() == {"photos": [SHA]}


async def test_reupload_is_harmless(client, space):
    space_id, auth = space
    for _ in range(2):
        r = await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG, headers=auth)
        assert r.status_code == 204
    assert (await client.get(f"/spaces/{space_id}/photos", headers=auth)).json() == {"photos": [SHA]}


async def test_body_must_match_its_name(client, space):
    space_id, auth = space
    r = await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG + b"x", headers=auth)
    assert r.status_code == 400
    assert (await client.get(f"/spaces/{space_id}/photos/{SHA}", headers=auth)).status_code == 404


async def test_oversized_photo_is_refused(client, space, monkeypatch):
    from routes import photos

    monkeypatch.setattr(photos, "MAX_PHOTO_BYTES", 10)
    space_id, auth = space
    r = await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG, headers=auth)
    assert r.status_code == 413


async def test_malformed_sha_is_not_found(client, space):
    space_id, auth = space
    r = await client.get(f"/spaces/{space_id}/photos/{SHA.upper()}", headers=auth)
    assert r.status_code == 404
    r = await client.put(f"/spaces/{space_id}/photos/NOTAHASH", content=JPEG, headers=auth)
    assert r.status_code == 404


async def test_photos_need_the_token(client, space):
    space_id, _ = space
    bad = {"Authorization": "Bearer not-the-token"}
    assert (await client.get(f"/spaces/{space_id}/photos", headers=bad)).status_code == 401
    assert (await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG,
                             headers=bad)).status_code == 401
    assert (await client.get(f"/spaces/{space_id}/photos/{SHA}")).status_code == 401


async def test_spaces_do_not_see_each_others_photos(client, space):
    space_id, auth = space
    await client.put(f"/spaces/{space_id}/photos/{SHA}", content=JPEG, headers=auth)

    other = (await client.post("/spaces")).json()
    other_auth = {"Authorization": f"Bearer {other['token']}"}
    r = await client.get(f"/spaces/{other['space_id']}/photos/{SHA}", headers=other_auth)
    assert r.status_code == 404
    assert (await client.get(f"/spaces/{other['space_id']}/photos",
                             headers=other_auth)).json() == {"photos": []}
