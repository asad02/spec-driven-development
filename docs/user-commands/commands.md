# Command Reference — running and inspecting the stack

| | |
|---|---|
| **Applies to** | `hello-micronaut` — six Compose services, two databases |
| **Run from** | the repository root (where `docker-compose.yml` lives) |
| **Date** | 2026-09-11 |
| **See also** | [Service URLs and Port Mapping](../service-urls-and-ports.md) |

All `docker compose` commands run from the repository root, with Docker Desktop up.

---

## 1. The stack at a glance

| Service | Host port | Container port | What it is |
|---|---|---|---|
| `user-management-ui` | **8081** | 80 | Product UI (Angular) + the API proxy |
| `feature-toggle-management-ui` | **8083** | 80 | Feature toggle admin console (Angular) |
| `micronaut-user-management-service` | **8080** | 8080 | Micronaut product API |
| `springboot-user-management-service` | **8082** | 8080 | Spring Boot product API |
| `feature-toggle-management-service` | **8084** | 8080 | Feature toggle service — owns the access rules |
| `db` | **15432** | 5432 | PostgreSQL 17, two databases |

| Where | URL |
|---|---|
| Product UI | <http://localhost:8081> |
| Toggle admin console | <http://localhost:8083> |
| Swagger (Spring Boot) | <http://localhost:8082/swagger-ui/index.html> |
| Swagger (feature toggle) | <http://localhost:8084/swagger-ui/index.html> |
| Swagger (Micronaut) | <http://localhost:8080/swagger-ui/> |

**Seeded operators** (local development only):

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123!` | `ROLE_ADMIN` | Everything, including feature toggles |
| `viewer` | `viewer123!` | `ROLE_VIEWER` | Read users and toggles; change nothing |
| `noaccess` | `noaccess123!` | `ROLE_NONE` | Log in only; refused everywhere |

There are **no Compose profiles** — a plain `up` starts all six.

---

## 2. Running the stack

```bash
docker compose up -d            # start all six, health-gated
docker compose ps               # status, ports and health
docker compose stop             # stop containers, keep them
docker compose start            # start them again
docker compose down             # stop and remove containers + network — DATA IS KEPT
docker compose restart micronaut-user-management-service  # restart a single service
```

Startup is ordered by health, not by luck: the product backends wait for
`pg_isready` **and** for the feature service; nginx waits for the backends.

### After changing code

```bash
docker compose up -d --build                        # rebuild whatever changed
docker compose up -d --build feature-toggle-management-service # rebuild one service
docker compose build --no-cache springboot-user-management-service  # force a clean rebuild
```

A Java or Angular change **needs `--build`**. Without it Compose reuses the old
image and your change silently does not appear.

---

## 3. Logs and diagnostics

```bash
docker compose logs -f                            # everything, following
docker compose logs -f feature-toggle-management-service     # one service
docker compose logs --tail 50 micronaut-user-management-service  # last 50 lines
docker compose logs --since 5m springboot-user-management-service # recent only

docker compose logs micronaut-user-management-service | grep -i flyway  # did migrations run?
docker compose logs feature-toggle-management-service | grep -i audit   # who changed a toggle
docker compose logs micronaut-user-management-service | grep -i "feature service"  # gate calls and failures
```

```bash
docker compose config      # the resolved file, with .env substituted
docker compose top         # processes inside each container
docker stats --no-stream   # CPU and memory per container
```

`docker compose config` is the quickest way to confirm what `.env` resolved to —
port and secret surprises usually hide there.

---

## 4. Databases — one-shot commands

**Two databases**, each with exactly one writer.

```bash
# the product database
docker compose exec db psql -U postgres -d usersdb -c "\dt"

# the feature toggle database
docker compose exec db psql -U postgres -d feature-toggle -c "\dt"

# several queries in one call
docker compose exec db psql -U postgres -d usersdb \
  -c "SELECT count(*) FROM users;" \
  -c "SELECT username, roles FROM auth_user ORDER BY username;"
```

Add `-T` when scripting (`docker compose exec -T db psql …`) to disable TTY
allocation; without it, piped output can misbehave.

---

## 5. Database — interactive shell

```bash
docker compose exec db psql -U postgres -d usersdb
docker compose exec db psql -U postgres -d feature-toggle
```

Then use psql's own meta-commands:

| Command | Shows |
|---|---|
| `\l` | All databases |
| `\c feature-toggle` | Switch database without leaving the shell |
| `\dt` | Tables in the current database |
| `\dt+` | Tables with size and row estimates |
| `\d users` | Columns, types and indexes of one table |
| `\di` | All indexes |
| `\du` | PostgreSQL roles (not the application's) |
| `\x` | Toggle expanded output — useful for wide rows |
| `\timing` | Toggle query timing |
| `\e` | Edit the last query in `$EDITOR` |
| `\?` | Help on meta-commands |
| `\h SELECT` | SQL syntax help |
| `\q` | Quit |

---

## 6. What lives where

### `usersdb` — owned by `micronaut-user-management-service` (Micronaut)

| Table | Purpose |
|---|---|
| `users` | The domain records |
| `auth_user` | Operator logins and their roles |
| `flyway_schema_history` | Its migrations (V1–V4) |

### `feature-toggle` — owned by `feature-toggle-management-service`

| Table | Purpose |
|---|---|
| `ff4j_features` | The toggles and their on/off state |
| `ff4j_roles` | **The ACL** — which roles may use each toggle |
| `ff4j_custom_properties` | `adminRole`, `readOnlyRoles`, `deniedMessage` |
| `ff4j_properties` | Global properties (unused here) |
| `ff4j_audit` | Who changed what, when — including refused attempts |
| `flyway_schema_history` | Its own migrations — a baseline row, then V1–V2 |

**One writer per database.** That is what keeps the migration histories from
colliding, and why the toggles moved out of `usersdb`.

### Queries worth knowing

```bash
# who can log in, and with what role
docker compose exec db psql -U postgres -d usersdb \
  -c "SELECT username, roles, enabled FROM auth_user ORDER BY username;"

# the access-control picture: every toggle and the roles allowed to use it
docker compose exec db psql -U postgres -d feature-toggle -c "
  SELECT f.feat_uid, f.enable, r.role_name
  FROM ff4j_features f
  LEFT JOIN ff4j_roles r ON r.feat_uid = f.feat_uid
  ORDER BY 1, 3;"

# the policy attached to each toggle
docker compose exec db psql -U postgres -d feature-toggle \
  -c "SELECT feat_uid, property_id, currentvalue FROM ff4j_custom_properties ORDER BY 1,2;"

# which backend is currently serving /api  (enable=1 → Spring Boot)
docker compose exec db psql -U postgres -d feature-toggle \
  -c "SELECT feat_uid, enable FROM ff4j_features WHERE feat_uid='use-springboot-backend';"

# recent toggle changes, newest first — refusals included
docker compose exec db psql -U postgres -d feature-toggle -c "
  SELECT evt_time, evt_user, evt_action, evt_name
  FROM ff4j_audit ORDER BY evt_time DESC LIMIT 10;"

# migration state of both databases
docker compose exec db psql -U postgres -d usersdb \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec db psql -U postgres -d feature-toggle \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Changing access live

Both product backends ask the feature service per request, so this takes effect on
the **next request** — no restart, no redeploy, on either backend:

```bash
# revoke read access from viewers
docker compose exec db psql -U postgres -d feature-toggle \
  -c "DELETE FROM ff4j_roles WHERE feat_uid='user-data-access' AND role_name='ROLE_VIEWER';"

# grant it back
docker compose exec db psql -U postgres -d feature-toggle \
  -c "INSERT INTO ff4j_roles VALUES ('user-data-access','ROLE_VIEWER') ON CONFLICT DO NOTHING;"
```

Prefer the admin console at <http://localhost:8083> for this — it enforces the
lockout guardrails and records who changed what. Editing the table directly
bypasses both.

---

## 7. Connecting from a GUI client (DBeaver, etc.)

| Field | Value |
|---|---|
| Host | **`127.0.0.1`** |
| Port | **`15432`** |
| Database | `usersdb` **or** `feature-toggle` |
| User / password | `postgres` / `postgres` |

**Use `127.0.0.1`, not `localhost`.** Postgres.app on this machine binds `::1`,
and `localhost` resolves `::1` first, so it can shadow Docker's binding entirely.
The symptom is the misleading `database "usersdb" does not exist` — you reached a
different PostgreSQL server that happens to have no such database. Port `15432` was
chosen to sit clear of Postgres.app, which allocates sequentially from 5432.

```bash
lsof -nP -iTCP:15432 -sTCP:LISTEN     # should show only Docker
```

The `docker compose exec db psql` route in §4–§6 bypasses host ports entirely and
can never be shadowed.

---

## 8. Destructive commands

```bash
docker compose down -v
```

**`-v` deletes the `pgdata` volume — both databases.** Every user, operator, toggle
and audit entry is lost. On the next `up`, PostgreSQL re-initialises empty,
`usersdb` is recreated by the image entrypoint, `feature-toggle` by the init script
in `db/init/`, and every Flyway history replays from scratch.

This is the only way to get a genuinely clean database, and there is no undo. Plain
`docker compose down` never touches the data.

```bash
docker compose down --rmi local    # also delete the images built here
docker system prune -a             # DANGER: affects every project on the machine
```

---

## 9. Verifying the stack works

```bash
# health of all three backends
curl -s http://localhost:8080/health   # micronaut
curl -s http://localhost:8082/health   # spring boot
curl -s http://localhost:8084/health   # feature toggle

# log in and call the API through nginx
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -s "http://localhost:8081/api/v1/users?size=2" -H "Authorization: Bearer $TOKEN"

# which backend actually answered
curl -s -o /dev/null -D - "http://localhost:8081/api/v1/users?size=1" \
  -H "Authorization: Bearer $TOKEN" | grep -i x-served-by

# switch the active backend (admin only)
curl -s -X PUT http://localhost:8081/api/v1/features \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"activeBackend":"micronaut"}'

# what the product backends ask the feature service
curl -s http://localhost:8084/api/v1/features/user-data-access/access \
  -H "Authorization: Bearer $TOKEN"

# what nginx asks (anonymous)
curl -s -o /dev/null -D - http://localhost:8084/api/v1/features/route | grep -i x-backend-host
```

### Test suites

```bash
cd micronaut-user-management-service                && ./gradlew clean build   # 38 tests
cd springboot-user-management-service     && ./gradlew clean build   # 47 tests
cd feature-toggle-management-service && ./gradlew clean build   # 20 tests
cd user-management-ui               && npm run test:ci && npm run build
cd feature-toggle-management-ui && npm run build

# the whole API surface — 61 requests, 133 assertions (needs Node 20+)
newman run postman/user-management.postman_collection.json

# and against each product entry point, which is the interchangeability guarantee
newman run postman/user-management.postman_collection.json --env-var baseUrl=http://localhost:8080
newman run postman/user-management.postman_collection.json --env-var baseUrl=http://localhost:8082
newman run postman/user-management.postman_collection.json \
  --env-var baseUrl=http://localhost:8081 --env-var opsUrl=http://localhost:8082
```
