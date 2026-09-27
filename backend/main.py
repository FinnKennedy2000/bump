from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI

from config import DATABASE_URL
from routes import photos, spaces

SCHEMA = """
create table if not exists spaces (
    id          uuid primary key,
    token_hash  text        not null,
    state       jsonb       not null default '{}'::jsonb,
    version     bigint      not null default 0,
    updated_at  timestamptz not null default now()
);

-- Content-addressed and immutable: a row is written once and never updated, so photos
-- need no version and no merge. The metadata that says which photos exist lives in the
-- state document, merged by the app like everything else.
create table if not exists photos (
    space_id    uuid  not null references spaces (id) on delete cascade,
    sha256      text  not null,
    bytes       bytea not null,
    primary key (space_id, sha256)
);
"""


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.pool = await asyncpg.create_pool(DATABASE_URL, min_size=1, max_size=5)
    async with app.state.pool.acquire() as conn:
        await conn.execute(SCHEMA)
    yield
    await app.state.pool.close()


# No CORS middleware: the only client is the Android app, and a browser origin has no
# business here.
app = FastAPI(title="Bump Sync", lifespan=lifespan)

app.include_router(spaces.router)
app.include_router(photos.router)


@app.get("/health")
def health():
    return {"status": "ok"}
