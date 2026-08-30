from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI

from config import DATABASE_URL
from routes import spaces

SCHEMA = """
create table if not exists spaces (
    id          uuid primary key,
    token_hash  text        not null,
    state       jsonb       not null default '{}'::jsonb,
    version     bigint      not null default 0,
    updated_at  timestamptz not null default now()
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


@app.get("/health")
def health():
    return {"status": "ok"}
