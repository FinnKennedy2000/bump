import os

DATABASE_URL = os.environ["DATABASE_URL"]

# Railway hands out `postgres://`; asyncpg only accepts `postgresql://`.
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)

# A space document is the whole app state: a due date, a few names, and nine months of
# appointments, weights, notes and kick sessions. Hundreds of small rows, not thousands.
MAX_STATE_BYTES = 1024 * 1024  # 1 MB

# Space creation is the only unauthenticated write. Two people need it twice, ever, so a
# handful per hour is generous for real use and still caps what a stranger can insert.
# Overridable because the test suite legitimately creates more than a human ever would.
CREATE_RATE_LIMIT = int(os.environ.get("CREATE_RATE_LIMIT", "5"))
CREATE_RATE_WINDOW = int(os.environ.get("CREATE_RATE_WINDOW", "3600"))  # seconds
