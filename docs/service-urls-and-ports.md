# Service URLs and Port Mapping

| | |
|---|---|
| **Document** | Operational reference — addresses, ports and routing |
| **Applies to** | `hello-micronaut` — six Compose services |
| **Date** | 2026-09-11 |
| **Verified** | Every URL below was requested against the running stack |

---

## 1. At a glance

| Service | Open in a browser | Host port | Container port | What it is |
|---|---|---|---|---|
| **user-management-ui** | <http://localhost:8081> | `8081` | `80` | Product UI (Angular) + the API proxy |
| **feature-toggle-management-ui** | <http://localhost:8083> | `8083` | `80` | Feature toggle admin console (Angular) |
| **micronaut-user-management-service** | — | `8080` | `8080` | Micronaut product API |
| **springboot-user-management-service** | — | `8082` | `8080` | Spring Boot product API |
| **feature-toggle-management-service** | — | `8084` | `8080` | Feature toggle service |
| **db** | — | `15432` | `5432` | PostgreSQL 17 |

**Two ports for the same thing.** The host port is what *you* use from a terminal
or browser. The container port is what *services* use to reach each other. The two
product backends both listen on `8080` inside the network and are distinguished
only by their service name.

## 2. Reaching services from your machine

```
Product UI                 http://localhost:8081
Feature toggle console     http://localhost:8083

Micronaut API              http://localhost:8080/api/v1/...
Spring Boot API            http://localhost:8082/api/v1/...
Feature toggle API         http://localhost:8084/api/v1/...

PostgreSQL                 127.0.0.1:15432
```

> **Use `127.0.0.1` for the database, not `localhost`.** Postgres.app on this
> machine binds `::1`, and `localhost` resolves `::1` first, so `localhost:15432`
> can silently reach a different server with no `usersdb`. The symptom is
> *database "usersdb" does not exist*, which reads like a migration failure and is
> not one. `15432` was chosen to sit well clear of Postgres.app's range
> (5432, 55432, 55433, …).

## 3. Reaching services from inside the network

Services address each other by **Compose service name and container port**. Host
ports are irrelevant here.

| From | To | Address |
|---|---|---|
| `micronaut-user-management-service`, `springboot-user-management-service` | feature service | `http://feature-toggle-management-service:8080` |
| `micronaut-user-management-service`, `springboot-user-management-service` | database | `jdbc:postgresql://db:5432/usersdb` |
| `feature-toggle-management-service` | database | `jdbc:postgresql://db:5432/feature-toggle` |
| `user-management-ui` (nginx) | product backends | `http://micronaut-user-management-service:8080` · `http://springboot-user-management-service:8080` |
| `user-management-ui` (nginx) | feature service | `http://feature-toggle-management-service:8080` |
| `feature-toggle-management-ui` (nginx) | feature service | `http://feature-toggle-management-service:8080` |
| `feature-toggle-management-ui` (nginx) | auth | `http://springboot-user-management-service:8080` |

## 4. How the proxies route

### 4.1 Product UI — `user-management-ui` on :8081

```
/api/v1/features…  ──────────────────────────▶  feature-toggle-management-service:8080
                                                 (pinned: the toggle API must not
                                                  be routed by the toggle itself)

/api/…             ──┬── auth_request ───────▶  feature-toggle-management-service:8080
                     │   /api/v1/features/route   answers 204 + X-Backend-Host
                     │
                     └── proxy_pass ─────────▶  micronaut-user-management-service:8080   (flag off)
                                             or  springboot-user-management-service:8080  (flag on)

/*                 ──────────────────────────▶  the Angular bundle (SPA fallback)
```

If the feature service is unreachable, the subrequest fails and an `error_page`
rule falls through to `micronaut-user-management-service:8080` rather than failing every API call.

### 4.2 Admin console — `feature-toggle-management-ui` on :8083

```
/api/v1/auth/…     ──────────────────────────▶  springboot-user-management-service:8080
                                                 (issues tokens; the feature
                                                  service validates but issues none)

/api/…             ──────────────────────────▶  feature-toggle-management-service:8080

/*                 ──────────────────────────▶  the Angular bundle
```

> Both proxies name upstreams through a **variable** with `resolver 127.0.0.11`.
> With a literal hostname nginx resolves once at startup and keeps a stale IP after
> an upstream container restarts, answering `502` while the service is healthy.

## 5. Endpoints by service

### 5.1 Product API — `micronaut-user-management-service` :8080 and `springboot-user-management-service` :8082

Both expose an identical contract; either can serve any request.

| Method | Path | Auth |
|---|---|---|
| `POST` | `/api/v1/auth/login` | none |
| `GET` | `/api/v1/users` | bearer |
| `POST` | `/api/v1/users` | bearer |
| `GET` | `/api/v1/users/{id}` | bearer |
| `PUT` | `/api/v1/users/{id}` | bearer |
| `DELETE` | `/api/v1/users/{id}` | → `405`, deliberately unimplemented |
| `GET` | `/health` | none |
| `GET` | `/prometheus` | bearer |

### 5.2 Feature toggle service — `feature-toggle-management-service` :8084

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/features/{uid}/access` | bearer | **Decision** — what the product backends call |
| `GET` | `/api/v1/features/route` | **none** | Routing for nginx — `204` + `X-Backend-Host` |
| `GET` | `/api/v1/features` | bearer | Runtime summary and caller rights |
| `PUT` | `/api/v1/features` | `ROLE_ADMIN` | Switch the active backend |
| `GET`/`POST` | `/api/v1/admin/features` | viewer / admin | List · create |
| `GET`/`PUT`/`DELETE` | `/api/v1/admin/features/{uid}` | viewer / admin | Read · update · delete |
| `POST`/`DELETE` | `/api/v1/admin/features/{uid}/roles/{role}` | `ROLE_ADMIN` | Grant · revoke |
| `PUT`/`DELETE` | `/api/v1/admin/features/{uid}/properties/{key}` | `ROLE_ADMIN` | Set · remove policy |
| `GET` | `/api/v1/admin/features/audit` | viewer / admin | Change history |
| `GET` | `/health`, `/prometheus` | none / bearer | |

## 6. API documentation

| Service | Swagger UI | Raw spec |
|---|---|---|
| Micronaut | <http://localhost:8080/swagger-ui/> | `/swagger/user-management-service-1.0.yml` |
| Spring Boot | <http://localhost:8082/swagger-ui/index.html> | `/v3/api-docs` |
| Feature toggle | <http://localhost:8084/swagger-ui/index.html> | `/v3/api-docs` |

`/swagger-ui.html` redirects to `/swagger-ui/index.html` on the Spring services.

> The Micronaut document **omits the login endpoint** — it is a framework built-in
> rather than an annotated controller, so the generator never sees it. That
> document cannot be imported into a client and used to authenticate; the Spring
> ones can.

## 7. Databases

| Database | Owner | Tables |
|---|---|---|
| `usersdb` | `micronaut-user-management-service` (Micronaut) migrates it | `users`, `auth_user`, `flyway_schema_history` |
| `feature-toggle` | `feature-toggle-management-service` migrates it | `ff4j_features`, `ff4j_roles`, `ff4j_custom_properties`, `ff4j_properties`, `ff4j_audit`, `flyway_schema_history` |

Both live in the one PostgreSQL container. **One writer per database** — that is
what keeps the migration histories from colliding.

```bash
# shadow-proof: goes through the container, never a host port
docker compose exec db psql -U postgres -d usersdb
docker compose exec db psql -U postgres -d feature-toggle
```

## 8. Changing a port

Every host port is an environment variable with a default, set in `.env`:

```
BACKEND_PORT=8080              FRONTEND_PORT=8081
SPRINGBOOT_BACKEND_PORT=8082   FEATURE_UI_PORT=8083
FEATURE_BACKEND_PORT=8084      DB_HOST_PORT=15432
```

Change one, then `docker compose up -d <service>` to recreate it. **Container
ports are not configurable this way** — they are fixed in each image, and nothing
inside the network depends on the host mapping.

## 9. Running outside Docker

| Component | Command | Serves on | Talks to |
|---|---|---|---|
| Product UI | `cd user-management-ui && npm start` | `4200` | proxies `/api` → `localhost:8080` |
| Admin console | `cd feature-toggle-management-ui && npm start` | `4300` | proxies `/api` → `localhost:8084` |
| Any backend | `./gradlew bootRun` / `run` | `8080` | needs `DATASOURCE_URL` passed explicitly |

Running a backend locally **needs the datasource URL given explicitly** — the
fallback in `application.yml` points at `localhost:5432`, which is a machine-specific
guess and not where the container database listens:

```bash
./gradlew bootRun --args='--spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/usersdb \
                          --app.jwt.secret=<256-bit-secret>'
```

## 10. Operators

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123!` | `ROLE_ADMIN` | Everything, including feature toggles |
| `viewer` | `viewer123!` | `ROLE_VIEWER` | Read users and toggles; change nothing |
| `noaccess` | `noaccess123!` | `ROLE_NONE` | Log in only; refused everywhere |

Local development values only. One token works across all three services — they
share the signing secret, and only the product backends issue tokens.

## 11. Quick health check

```bash
curl -s http://localhost:8080/health   # micronaut
curl -s http://localhost:8082/health   # spring boot
curl -s http://localhost:8084/health   # feature toggle
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/   # product UI
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8083/   # admin console
docker compose ps                                                  # all six at once
```
