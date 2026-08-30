# Bump sync backend

A versioned blob store for one shared pregnancy record. It does not know what is inside
the document it holds — merging two phones' edits is last-write-wins per item, and that
logic lives once, in the app's `data/Merge.kt`, where it is pure Kotlin and unit tested.
Putting a second copy here in Python would give two implementations of the same rules,
drifting apart.

What the server does provide is the one thing a client cannot: a version number that
makes concurrent writes safe.

    pull   GET /spaces/{id}/state       -> { version, state }
    merge  merge(local, remote)            in the app, not here
    push   PUT /spaces/{id}/state       If-Match: <version>, 409 if someone got there first

## Endpoints

| Method | Path                 | Auth   | Notes                                        |
|--------|----------------------|--------|----------------------------------------------|
| POST   | `/spaces`            | none   | creates a space; returns the token **once**  |
| GET    | `/spaces/{id}/state` | Bearer | `{version, state}`                           |
| PUT    | `/spaces/{id}/state` | Bearer | needs `If-Match: <version>`; 409 when stale  |
| GET    | `/health`            | none   | Railway healthcheck                          |

`POST /spaces` is the only unauthenticated write, so it is rate limited per IP.
The token is stored as a sha256 hash and compared with `hmac.compare_digest`.

## Running the tests

They talk to a real Postgres on purpose: the guarantee under test is the atomic
compare-and-set in `update ... where version = $3`, and a fake pool would only test the
fake. Without `DATABASE_URL` the suite skips rather than pretending to pass.

    docker run -d --rm --name bump-pg \
      -e POSTGRES_PASSWORD=test -e POSTGRES_DB=bump -p 55432:5432 postgres:16-alpine

    pip install -r requirements-dev.txt
    DATABASE_URL=postgresql://postgres:test@localhost:55432/bump \
      CREATE_RATE_LIMIT=1000 python -m pytest tests

`CREATE_RATE_LIMIT` is raised because the suite creates more spaces than a human ever
would; one test lowers it again to check the limit actually fires.

## Deploying

Railway project `bump-sync`, service `bump-api`, with its own Postgres — deliberately not
shared with any other project. Infrastructure is described in `../.railway/railway.ts`
(which is also what sets this directory as the service root).

    railway config apply     # infrastructure changes
    railway up               # deploy the code
