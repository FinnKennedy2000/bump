# Two-device sync — implementation plan

Goal: Finn's and wife's phones share one pregnancy record. Offline-first: the app
keeps working with no network; sync reconciles when it can.

## Shape

Client owns the merge. Server is a dumb versioned blob store with optimistic
concurrency. This keeps the only non-trivial logic (last-write-wins reconciliation)
in one place — pure Kotlin in `domain`, JVM-tested, no Python duplicate to drift.

    pull  GET  /spaces/{id}/state   -> { version, state }
    merge merge(local, remote)      -- pure Kotlin
    push  PUT  /spaces/{id}/state   -- If-Match: version -> 409 means re-pull and retry

## Backend — `backend/`, copied from pdf-merger

Same FastAPI + Dockerfile + `railway.json` layout. **New Railway project, new Postgres
instance** so bump data never shares a database with pdf-merger.

    backend/
      Dockerfile          # python:3.12-slim, no libreoffice
      railway.json        # DOCKERFILE builder, /health healthcheck  (verbatim from pdf-merger)
      requirements.txt    # fastapi, uvicorn[standard], asyncpg
      config.py           # DATABASE_URL from env
      main.py             # lifespan: pool + schema; /health
      routes/spaces.py
      tests/

One table, created on startup (no Alembic — one table, one shape):

    create table if not exists spaces (
      id          uuid primary key,
      token_hash  text   not null,          -- sha256, never store the token
      state       jsonb  not null default '{}'::jsonb,
      version     bigint not null default 0,
      updated_at  timestamptz not null default now()
    );

Endpoints:

| Method | Path                  | Auth   | Notes |
|--------|-----------------------|--------|-------|
| POST   | `/spaces`             | none   | creates space, returns `{space_id, token}` **once** |
| GET    | `/spaces/{id}/state`  | Bearer | `{version, state}` |
| PUT    | `/spaces/{id}/state`  | Bearer | `If-Match: <version>`; 409 on mismatch; bumps version |
| GET    | `/health`             | none   | Railway healthcheck |

Auth: `Authorization: Bearer <token>`, compared with `hmac.compare_digest` against the
stored sha256. Space creation is the only unauthenticated write — rate-limited to a
handful per hour per IP so the table can't be flooded.

CORS: not needed (no web client). Omit the middleware pdf-merger has.

## Android

### Schema changes (`data/Models.kt`)

1. `Logged` gains `val deleted: Boolean` — tombstones. A hard delete on one phone would
   otherwise be resurrected by the other phone's stale copy.
2. `AppState` gains `detailsUpdatedAt: Long` — due date, name, nickname and sex have no
   per-field stamp, so they merge as one group, last writer wins.

Both default-valued, so existing saved state still parses (`ignoreUnknownKeys` already on).

### Merge (`domain/Sync.kt`, pure Kotlin, no Android imports)

    fun merge(local: AppState, remote: AppState): AppState

- lists: union by `id`, keep the greater `updatedAt` (tombstones included, so a delete wins
  over an older edit and an edit wins over an older delete)
- details group: whichever side has the greater `detailsUpdatedAt`
- ties: prefer `local` (deterministic, and the local write is the one the user just made)

### Repository (`data/BumpRepository.kt`)

- `state` now strips tombstones → **no UI file changes anywhere**
- `rawState` (new) is the unfiltered document, used only by sync
- `delete(item)` sets `deleted = true, updatedAt = now` instead of removing
- `restore(item)` sets `deleted = false, updatedAt = now` — undo keeps working

### Sync plumbing (`data/sync/`)

- `SyncSettings.kt` — separate Preferences DataStore for `baseUrl`, `spaceId`, `token`.
  Deliberately **not** in `AppState`: the token must never be pushed to the server.
- `SyncClient.kt` — `java.net.HttpURLConnection` + kotlinx.serialization. Two calls and a
  create. No OkHttp, no Ktor, no Retrofit: zero new dependencies.
- `Syncer.kt` — pull → merge → write local → push; on 409 re-pull once and retry, then give up
  until the next trigger.

Triggers: on `MainActivity` resume, and debounced (~2s) after any repository write.
No WorkManager — the app doesn't need background sync when it isn't open.

### UI

- **Pairing sheet** (Settings, reachable from Now): "Create shared space" shows a code to
  copy; "Join with code" takes a pasted one. Code = base64 of `spaceId:token`, one string
  to send over WhatsApp.
- **Sync status line** on Now: "Synced 2 min ago" / "Offline — will retry" / "Sync failed:
  <reason>". Sync failures are visible, never swallowed — silently losing a partner's
  entries is the worst failure this app has.

### Manifest

`<uses-permission android:name="android.permission.INTERNET"/>` — with a comment stating
sync is the only network use and it is opt-in (no space paired = no requests ever made).

## Tests

- `domain/SyncTest.kt` (JVM, no Robolectric): concurrent adds, edit-vs-delete both ways,
  delete-vs-edit, details-group tie-break, idempotent re-merge.
- `AppStateTest.kt`: old JSON without `deleted`/`detailsUpdatedAt` still parses.
- `backend/tests/`: create → get → put → 409 on stale version → 401 on bad token.

## CLAUDE.md updates

The "No `INTERNET` permission" non-negotiable becomes: network is used *only* for
opt-in sync to the user's own Railway backend; no analytics, no third parties, and the
app is fully functional with sync never enabled.

## Deploy — done

Railway project **bump-sync**, its own Postgres, deployed at

    https://bump-api-production-3acf.up.railway.app

Infrastructure is in `.railway/railway.ts` (Railway's Config-as-Code `railway.json` is
deprecated from 2026-12-01, and it cannot set a service root directory, which this repo
needs because the repo root is an Android app). `railway config apply` for infrastructure,
`railway up` for code.

The URL is the pre-filled default in the pairing sheet and stays editable, so a redeploy
to a different host is not a rebuild.

## Order of work

1. Backend (`backend/`) + tests, runnable locally against a local Postgres
2. Schema changes + `merge` + tests — app still works, sync not wired
3. Repository tombstones + `rawState`
4. `SyncClient` / `Syncer` / `SyncSettings`
5. Pairing sheet + status line + manifest permission
6. Deploy to Railway, pair both phones, verify end to end
7. Docs: CLAUDE.md, README

## Status

Steps 1–5 and 7 are done. Step 6 — pairing both handsets and watching an entry cross —
is the remaining check, and it needs the two physical phones.
