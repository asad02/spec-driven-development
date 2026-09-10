# Technical Specification — User Management Service

| | |
|---|---|
| **Document** | Technical Specification |
| **Implements** | [Functional Specification v1.1](functional-spec.md) |
| **Sibling** | [Technical Specification — Spring Boot](technical-spec-springboot.md) |
| **Version** | 1.1 |
| **Date** | 2026-09-10 (v1.0: 2026-09-01) |
| **Status** | **Implemented** — see §17 for what was built and what changed |

---

## 1. Decisions of record

These were chosen explicitly; everything below follows from them.

| # | Decision | Choice |
|---|---|---|
| D-1 | Backend framework | Micronaut 4.x |
| D-2 | Build tool | Gradle, **Groovy DSL** (`build.gradle`) |
| D-3 | Language / JDK | Java **25** |
| D-4 | Database | PostgreSQL |
| D-5 | Persistence | Micronaut Data **JPA / Hibernate** |
| D-6 | Schema management | **Flyway** versioned SQL migrations |
| D-7 | Authentication | **JWT** via `micronaut-security-jwt` |
| D-8 | Credential storage | **Separate credentials table**, distinct from the user table |
| D-9 | List semantics | Server-side pagination, sorting and search |
| D-10 | UI framework | Angular + **Angular Material** |
| D-11 | Repository layout | **One repository, two folders** — independent builds |
| D-12 | Local database | **Docker Compose** |
| D-13 | Also specified in depth | OpenAPI/Swagger UI, testing strategy, CI/CD + Docker, observability |

> **⚠ Risk R-1 — Java 25 toolchain.** Java 25 is the current LTS, but Micronaut's
> annotation processors, Hibernate, Mockito and Testcontainers must all be on
> versions that support class-file 69. Mitigation: pin the newest Micronaut 4.x
> line at scaffold time and run a spike (`./gradlew test`) before committing to
> Java 25. Fallback is Java 21 with no other design change. **Confirm before
> implementation starts.**

## 2. Architecture

```
┌────────────────────┐     HTTPS/JSON      ┌──────────────────────┐    JDBC    ┌────────────┐
│  Angular SPA       │ ──────────────────▶ │  user-service        │ ─────────▶ │ PostgreSQL │
│  (Angular Material)│   Bearer <JWT>      │  (Micronaut 4, JVM)  │   pool     │            │
│  static assets     │ ◀────────────────── │  stateless           │ ◀───────── │            │
└────────────────────┘                     └──────────────────────┘            └────────────┘
        │                                            │
        │ served by nginx (prod)                     ├── /health, /metrics, /info
        │ ng serve + proxy (dev)                     └── /swagger-ui
```

Single deployable backend. No service-to-service calls, no message broker, no
cache tier. The SPA is static content and never talks to the database.

**Layering inside the backend** — strictly one-directional:

```
Controller  →  Service  →  Repository  →  Entity/DB
   DTOs         domain      Micronaut Data     JPA
```

- **Controller** — HTTP concerns only: binding, validation triggering, status
  codes. Never touches entities.
- **Service** — business rules (uniqueness, trimming, timestamps), transaction
  boundaries, DTO ↔ entity mapping.
- **Repository** — Micronaut Data interfaces; no business logic.
- **Entities never leave the service layer.** Controllers speak DTOs
  exclusively, so a schema change cannot silently alter the API contract.

## 3. Repository layout

```
hello-micronaut/
├── docs/
│   ├── functional-spec.md
│   └── technical-spec.md
├── backend/                       # independent Gradle build
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradle.properties
│   ├── gradlew  gradlew.bat  gradle/
│   ├── micronaut-cli.yml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/users/
│       │   ├── Application.java
│       │   ├── config/            # CORS, security, OpenAPI, Jackson
│       │   ├── controller/        # UserController, AuthController
│       │   ├── dto/               # request/response records
│       │   ├── entity/            # UserEntity, AuthUserEntity
│       │   ├── exception/         # domain exceptions + handlers
│       │   ├── mapper/            # DTO ↔ entity
│       │   ├── repository/        # UserRepository, AuthUserRepository
│       │   └── service/           # UserService, AuthenticationProviderImpl
│       ├── main/resources/
│       │   ├── application.yml            # shared config
│       │   ├── application-dev.yml
│       │   ├── application-test.yml
│       │   ├── logback.xml
│       │   └── db/migration/              # Flyway V*.sql
│       └── test/java/com/example/users/   # unit + integration tests
├── frontend/                      # independent npm/Angular build
│   ├── package.json  angular.json  tsconfig.json
│   ├── Dockerfile  nginx.conf
│   ├── proxy.conf.json            # dev proxy → backend
│   └── src/app/…                  # see §12
├── docker-compose.yml             # postgres + pgadmin (local dev)
├── .github/workflows/ci.yml
└── README.md
```

Two builds, two toolchains, two Dockerfiles, no shared build files. Deleting
`frontend/` leaves a working backend and vice versa.

## 4. Backend technology stack

| Concern | Choice | Notes |
|---|---|---|
| Framework | Micronaut 4.x (latest patch) | Compile-time DI; no runtime reflection scanning. |
| JDK | Java 25 (see R-1) | Gradle toolchain pinned in `build.gradle`. |
| Build | Gradle 9.x, Groovy DSL | `io.micronaut.application` + `io.micronaut.aot` plugins. |
| HTTP | `micronaut-http-server-netty` | |
| Persistence | `micronaut-data-hibernate-jpa`, `micronaut-jdbc-hikari` | |
| Driver | `org.postgresql:postgresql` | |
| Migrations | `micronaut-flyway` + `flyway-database-postgresql` | Runs at startup, before the app serves traffic. |
| Validation | `micronaut-validation` (Jakarta Bean Validation) | |
| Security | `micronaut-security-jwt` | |
| Password hashing | BCrypt (`org.mindrot:jbcrypt`) | Cost factor 12. |
| Feature flags / ACL | `org.ff4j:ff4j-core` (v1.1) | Plain Java, no Spring. Reads the shared feature store; see §17.2. |
| API docs | `micronaut-openapi` + swagger-ui | |
| Observability | `micronaut-management`, `micronaut-micrometer-registry-prometheus` | |
| Logging | Logback + `logstash-logback-encoder` | JSON in deployed envs. |
| Testing | JUnit 5, Micronaut Test, REST-assured, Testcontainers, AssertJ | |
| Serialization | Jackson (`micronaut-jackson-databind`) | Chosen over Serde for JPA/Hibernate friendliness. |

## 5. Data model

Two tables, deliberately decoupled (D-8): `users` is domain data, `auth_user`
is a credential. There is **no foreign key between them** in v1 — operators are
not users, and coupling them would force every new operator to become a user
record.

### 5.1 `users`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` |
| `first_name` | `VARCHAR(50)` | NOT NULL |
| `last_name` | `VARCHAR(50)` | NOT NULL |
| `email` | `VARCHAR(254)` | NOT NULL |
| `phone` | `VARCHAR(20)` | NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` |

Indexes:

| Index | Definition | Purpose |
|---|---|---|
| `ux_users_email_lower` | `UNIQUE (LOWER(email))` | Enforces BR-1 case-insensitive uniqueness **in the database** — the service check is a UX nicety, this is the guarantee. |
| `ix_users_created_at` | `(created_at DESC)` | Default sort. |
| `ix_users_search` | `GIN` on `LOWER(first_name || ' ' || last_name || ' ' || email)` via `pg_trgm` | Substring search (§8.3). Deferred to §8.3's decision. |

UUID primary keys (not sequential ids) so ids are non-enumerable in URLs and
generatable client-side later if needed. `gen_random_uuid()` is built into
PostgreSQL 13+; no extension required.

### 5.2 `auth_user`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | PK |
| `username` | `VARCHAR(100)` | NOT NULL, `UNIQUE (LOWER(username))` |
| `password_hash` | `VARCHAR(100)` | NOT NULL — BCrypt output |
| `roles` | `VARCHAR(255)` | NOT NULL, comma-separated (e.g. `ROLE_ADMIN`) |
| `enabled` | `BOOLEAN` | NOT NULL, default `true` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, default `now()` |

`password_hash` is annotated so it can never be serialized, and is excluded
from `toString()`. No API surface reads or returns it.

### 5.3 Migrations

| File | Contents |
|---|---|
| `V1__create_users.sql` | `users` table + indexes. |
| `V2__create_auth_user.sql` | `auth_user` table + unique index. |
| `V3__seed_admin.sql` | One seeded operator; hash supplied per environment (§9.2), **never a hardcoded production password**. |

Rules: migrations are append-only and immutable once merged; Hibernate runs
with `hbm2ddl.auto=validate` in every environment, so entity/schema drift fails
at startup rather than at 3 a.m.

### 5.4 Entities

`UserEntity` and `AuthUserEntity` are JPA `@Entity` classes with
`@Column(nullable=…, length=…)` mirroring the DDL exactly. Timestamps are set
in a `@PrePersist`/`@PreUpdate` pair rather than relying on database defaults,
so the returned object matches what was written without a re-read.

## 6. API contract

Base path `/api/v1`. All bodies JSON, UTF-8. All timestamps ISO-8601 UTC
(`2026-09-01T10:15:30Z`).

### 6.1 Endpoint summary

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | none | Exchange credentials for a JWT |
| `POST` | `/api/v1/users` | JWT | Create a user (FR-2) |
| `GET` | `/api/v1/users` | JWT | List users, paged/sorted/searched (FR-5) |
| `GET` | `/api/v1/users/{id}` | JWT | Fetch one user (FR-4) |
| `PUT` | `/api/v1/users/{id}` | JWT | Full update (FR-3) |
| `GET` | `/health` | none | Liveness/readiness |
| `GET` | `/metrics`, `/info` | JWT | Operational endpoints |
| `GET` | `/swagger-ui` | none in dev, JWT in prod | API reference |

`DELETE /api/v1/users/{id}` is deliberately unimplemented → `405`.

### 6.2 `POST /auth/login`

```jsonc
// request
{ "username": "admin", "password": "…" }

// 200 OK
{
  "access_token": "eyJhbGciOi…",
  "token_type": "Bearer",
  "expires_in": 3600,
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}

// 401 Unauthorized — identical for unknown user, bad password, disabled account
{ "code": "INVALID_CREDENTIALS", "message": "Invalid username or password." }
```

Micronaut's built-in `LoginController` is enabled and its path set to
`/api/v1/auth/login`; the response shape above is Micronaut's
`BearerAccessRefreshToken` plus a custom `RolesAwareTokenGenerator` claim set.

### 6.3 `POST /users`

```jsonc
// request
{ "firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "phone": "+1 555 0100" }

// 201 Created  (Location: /api/v1/users/{id})
{
  "id": "8f14e45f-…", "firstName": "Jane", "lastName": "Doe",
  "email": "jane@example.com", "phone": "+1 555 0100",
  "createdAt": "2026-09-01T10:15:30Z", "updatedAt": "2026-09-01T10:15:30Z"
}
```

Unknown JSON properties are ignored (`FAIL_ON_UNKNOWN_PROPERTIES=false`), which
implements BR-5: a client echoing back `id`/`createdAt` is not rejected.

### 6.4 `GET /users`

| Query param | Type | Default | Validation |
|---|---|---|---|
| `page` | int | `0` | `>= 0` |
| `size` | int | `20` | `1…100` |
| `sort` | string | `createdAt` | one of `firstName,lastName,email,createdAt,updatedAt` — allow-listed, **never** interpolated into SQL |
| `direction` | string | `desc` | `asc` \| `desc` |
| `search` | string | — | ≤ 100 chars; blank treated as absent |

```jsonc
// 200 OK
{
  "content": [ { /* user */ } ],
  "page": 0, "size": 20, "totalElements": 137, "totalPages": 7,
  "sort": "createdAt", "direction": "desc"
}
```

A custom `PageResponse<T>` DTO is returned rather than Micronaut's `Page`, so
the wire format is ours and cannot shift under a framework upgrade.

### 6.5 `PUT /users/{id}`

Full replacement (FR-3). Request body identical to create. Returns `200` with
the updated resource, `404` if unknown, `409` on email conflict, `400` on
validation failure.

### 6.6 Error model

One shape for every failure, based on the same envelope as the auth error:

```jsonc
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "correlationId": "c0ffee12-…",
  "fieldErrors": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "firstName", "message": "must not be blank" }
  ]
}
```

| HTTP | `code` | Raised by |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `ConstraintViolationException` handler — aggregates **all** violations (FR-7 / acceptance §10.5) |
| 400 | `INVALID_PARAMETER` | Bad `sort`/`direction`/`size` |
| 401 | `INVALID_CREDENTIALS` / `UNAUTHENTICATED` | Security filter |
| 403 | `ACCESS_DENIED` | `AccessDeniedForFeatureException` — the caller's roles do not satisfy the feature ACL (§17.2) |
| 404 | `USER_NOT_FOUND` | `UserNotFoundException` |
| 405 | `METHOD_NOT_ALLOWED` | Framework |
| 409 | `EMAIL_ALREADY_EXISTS` | `DuplicateEmailException` |
| 500 | `INTERNAL_ERROR` | Catch-all — logs the stack trace with `correlationId`, returns a generic message |

Implemented as `@Singleton` classes implementing
`ExceptionHandler<E, HttpResponse<?>>`, one per exception type.

## 7. Security design

### 7.1 Token issuance and validation

- Signature: **HS256** with a ≥ 256-bit secret from the environment
  (`JWT_SIGNATURE_SECRET`). Never committed; startup fails fast if unset in a
  non-dev environment.
- Claims: `sub` (username), `roles`, `iat`, `exp`, `iss`.
- Lifetime: **3600 s** (OQ-2 — confirm). No refresh token in v1.
- Transport: `Authorization: Bearer <token>`.
- Validation is stateless: signature + expiry only. No token store, no
  revocation, no logout endpoint (FR-8).

### 7.2 Authentication provider

A `@Singleton HttpRequestAuthenticationProvider` loads the account by
lower-cased username, verifies the BCrypt hash, and rejects disabled accounts.

**Timing-attack mitigation:** when the username is unknown, still run a BCrypt
verification against a dummy hash before failing, so response time does not
leak account existence (FR-6).

### 7.3 Authorization

`@Secured(SecurityRule.IS_AUTHENTICATED)` on `UserController`;
`@Secured(SecurityRule.IS_ANONYMOUS)` on login and health. Global default is
deny — an endpoint added without an annotation is unreachable, not open.

### 7.4 CORS

Enabled only for the SPA origin, configured per environment
(`http://localhost:4200` in dev). Allowed methods `GET,POST,PUT,OPTIONS`;
allowed headers `Authorization,Content-Type`; credentials not required since
the token travels in a header, not a cookie.

### 7.5 Known limitations (accepted for v1)

| Limitation | Consequence | Revisit |
|---|---|---|
| No token revocation | A stolen token is valid until expiry | v1.1 |
| Token in `localStorage` (OQ-3) | XSS can exfiltrate it | Harden with in-memory + refresh cookie |
| No rate limiting on login | Brute-force possible | Add after A-2 stops holding |
| No password policy / rotation | Weak seeded passwords possible | Deployment discipline |

## 8. Key implementation notes

### 8.1 Uniqueness

Two layers: a pre-check in `UserService` for a clean `409`, **and** the unique
index as the real guarantee. The service catches
`DataAccessException`/constraint violation on insert and translates it to
`EMAIL_ALREADY_EXISTS` — this closes the race between two concurrent creates
with the same email, which a check-then-insert alone cannot.

### 8.2 Transactions

`@Transactional` on service methods. Reads use
`@Transactional(readOnly = true)`. Controllers are never transactional, so a
transaction never spans response serialization.

### 8.3 Search implementation

Start with the portable form:

```sql
WHERE (:search IS NULL
   OR LOWER(first_name) LIKE :pattern
   OR LOWER(last_name)  LIKE :pattern
   OR LOWER(email)      LIKE :pattern)
```

with `:pattern = '%' || lower(search) || '%'`, expressed as a
`@Query`-annotated repository method (JPQL) plus a matching `countBy` query.

**Trade-off, stated plainly:** a leading-wildcard `LIKE` cannot use a B-tree
index and degrades to a sequential scan. At NFR-1's 100 000 rows this is
typically still within 300 ms, but it will not scale further. If benchmarking
fails NFR-1, switch to the `pg_trgm` GIN index in §5.1 — a migration-only
change, no API impact. **Benchmark before assuming it's fine.**

### 8.4 Sorting

`sort` is mapped through an explicit allow-list `Map<String, String>` from API
field name to entity property. Anything not in the map → `INVALID_PARAMETER`.
No string reaches the query builder unvalidated.

### 8.5 Trimming

A Jackson deserializer trims every incoming `String` before validation, so BR-2
holds without a `.trim()` in every service method and `"   "` correctly fails
`@NotBlank`.

## 9. Configuration and local development

### 9.1 `docker-compose.yml`

| Service | Image | Port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:17-alpine` | `5432` | Database; named volume for persistence |
| `pgadmin` | `dpage/pgadmin4` | `5050` | Optional inspection UI |

Includes a `pg_isready` healthcheck so dependent containers wait for a database
that actually accepts connections, not merely a started process.

### 9.2 Configuration matrix

| Key | Env var | dev | test | prod |
|---|---|---|---|---|
| Datasource URL | `DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/usersdb` | Testcontainers-assigned | injected |
| Datasource user/password | `DATASOURCE_USERNAME` / `_PASSWORD` | `postgres`/`postgres` | container | secret store |
| JWT secret | `JWT_SIGNATURE_SECRET` | dev-only literal | fixed test value | **required secret** |
| JWT expiry (s) | `JWT_ACCESS_TOKEN_EXPIRATION` | 3600 | 3600 | 3600 |
| CORS origin | `CORS_ALLOWED_ORIGIN` | `http://localhost:4200` | — | SPA origin |
| Seed admin password hash | `SEED_ADMIN_PASSWORD_HASH` | dev literal | fixed | **required secret** |
| Hibernate DDL | — | `validate` | `validate` | `validate` |
| Log format | — | pattern | pattern | JSON |

No secret is committed. `application.yml` references `${ENV_VAR}` with a
dev-only default where safe, and **no default at all** for the JWT secret and
seed hash in prod, so a misconfigured production start fails loudly.

### 9.3 Developer workflow

```bash
docker compose up -d                     # start postgres
cd backend  && ./gradlew run             # backend on :8080, Flyway migrates on boot
cd frontend && npm install && npm start  # SPA on :4200, proxying /api → :8080
```

## 10. OpenAPI / Swagger UI

- `micronaut-openapi` annotation processor generates the spec at compile time
  from `@Operation`/`@ApiResponse`/`@Schema` annotations plus the DTO types —
  no runtime cost, and it cannot drift from the code because it *is* generated
  from the code.
- Served at `/swagger-ui`, spec at `/swagger/user-service-1.0.yml`.
- A `bearerAuth` security scheme is declared so Swagger UI can call protected
  endpoints with a pasted token.
- Every documented error code from §6.6 appears as an `@ApiResponse` on the
  operations that can raise it.
- Swagger UI is anonymous in dev; behind auth (or disabled) in prod — decided
  per environment via config, not code.

## 11. Testing strategy

| Layer | Tooling | Covers |
|---|---|---|
| **Unit** | JUnit 5, Mockito, AssertJ | `UserService` rules — trimming, duplicate detection, timestamp handling, mapping. Repository mocked. No Micronaut context → milliseconds. |
| **Repository integration** | `@MicronautTest` + Testcontainers PostgreSQL | Real SQL against a real PostgreSQL: unique index behaviour, search query correctness, pagination and sort ordering, Flyway migrations applying cleanly from empty. |
| **API integration** | `@MicronautTest` + declarative `@Client` / REST-assured | Full request→DB→response through the real stack: every status code in §6.6, JWT accepted/rejected/expired, `405` on DELETE. |
| **Security** | Same harness | Unauthenticated call rejected; wrong password and unknown user are indistinguishable in body *and* shape; disabled account rejected. |
| **Frontend unit** | Jasmine + Karma (or Jest) | Services with `HttpTestingController`, the JWT interceptor, the auth guard, form validators, component logic. |
| **Frontend component** | Angular Testing Library / TestBed | List renders rows; empty, loading and error states; dialog validation; conflict handling keeps entered data. |
| **E2E** *(optional, see OQ)* | Playwright against compose stack | Login → create → search → edit → verify. |

Principles:

- **One PostgreSQL container per test class**, reused via Testcontainers
  singleton pattern; per-test data isolation by truncation, not by container
  restart.
- **Tests never hit a shared/dev database.** `application-test.yml` points only
  at Testcontainers.
- Coverage target **80 % line coverage on `service/` and `controller/`**,
  enforced by JaCoCo in CI. Coverage is a floor, not a goal — the acceptance
  criteria in the functional spec §10 are the real target, and each one maps to
  at least one integration test.

## 12. Frontend design

### 12.1 Stack

Angular (latest stable — pin at scaffold time), TypeScript strict mode,
standalone components, Angular Material + CDK, RxJS. State is held in
component-level signals; **no NgRx** — three screens do not justify a store.

### 12.2 Structure

```
src/app/
├── core/
│   ├── auth/        auth.service.ts · auth.guard.ts · jwt.interceptor.ts · token.storage.ts
│   ├── http/        error.interceptor.ts · correlation-id.interceptor.ts
│   └── models/      user.model.ts · page.model.ts · api-error.model.ts
├── features/
│   ├── login/       login.component.{ts,html,scss}
│   └── users/
│       ├── user-list.component.*      # table, search, paginator, sort
│       ├── user-form-dialog.component.*
│       └── user.service.ts            # typed HTTP calls
├── shared/          confirm-dialog.component.* · loading/empty/error state components
├── app.routes.ts    app.config.ts
└── environments/    environment.ts · environment.prod.ts   # apiBaseUrl only
```

### 12.3 Behaviour

- **Routing:** `/login` (public), `/users` (guarded), `**` → `/users`. The
  guard redirects unauthenticated users to `/login?returnUrl=…`.
- **`jwt.interceptor`** attaches `Authorization: Bearer …` to every `/api` call.
- **`error.interceptor`** maps the §6.6 envelope to user-facing text; a `401`
  clears the token and routes to `/login` with an "session expired" message
  (FR-7).
- **List screen** binds `mat-table` + `MatPaginator` + `MatSort` to the server
  page. Search uses `debounceTime(300)` + `distinctUntilChanged()` +
  `switchMap`, so in-flight requests for stale terms are cancelled and results
  cannot arrive out of order.
- **Form dialog** uses typed reactive forms mirroring FR-1 validation exactly.
  A `409` is surfaced as a field error on `email` via `setErrors`, leaving the
  dialog and its data intact.
- **Unsaved-changes confirmation** uses the form's `dirty` state.
- Accessibility (NFR-9) comes largely from Material components used correctly:
  `mat-form-field` + `mat-error` for labelled, announced errors; visible focus
  retained; the table gets `aria-label`; the dialog traps focus and restores it
  on close.

### 12.4 Build

`npm start` (dev server + `proxy.conf.json` → `localhost:8080`, avoiding CORS
in dev entirely), `npm run build` (production bundle), `npm test`, `npm run lint`.

## 13. Docker and deployment

| Image | Base | Notes |
|---|---|---|
| `user-service` | Multi-stage: `gradle:jdk25` build → `eclipse-temurin:25-jre-alpine` runtime | Non-root user; `HEALTHCHECK` on `/health`; JVM container-aware memory flags. |
| `user-ui` | Multi-stage: `node:lts` build → `nginx:alpine` | `nginx.conf` serves the SPA with an SPA fallback (`try_files … /index.html`) so deep links work, plus cache headers: hashed assets immutable, `index.html` no-cache. |

Backend image builds via the Micronaut Gradle plugin's `dockerBuild` task
(or a hand-written Dockerfile — decide at implementation; the plugin route is
less code to maintain).

Deployment target is not yet chosen (§16). Both images are plain OCI containers
with no orchestrator-specific assumptions, so Kubernetes, ECS or a compose host
all remain open.

## 14. Observability

| Concern | Implementation |
|---|---|
| **Health** | `micronaut-management` `/health` with the JDBC health indicator — reports `DOWN` when PostgreSQL is unreachable, so orchestrators stop routing traffic to a broken instance. `/health/liveness` and `/health/readiness` for separate probes. |
| **Metrics** | Micrometer → `/prometheus`. JVM, HikariCP pool, HTTP server (rate, latency, status), plus a counter per business event (`users.created`, `users.updated`, `auth.login.failed`). |
| **Logging** | Logback; JSON via `logstash-logback-encoder` in deployed environments, human-readable pattern locally. `INFO` for the app, `WARN` for frameworks. Hibernate SQL logging **off** by default. |
| **Correlation** | A server filter reads `X-Correlation-Id` or generates a UUID, puts it in the SLF4J MDC for the request's lifetime, echoes it in the response header **and in every error body** (§6.6) — so a user reporting an error hands you the exact log key (NFR-6). The Angular `correlation-id.interceptor` generates one per request so the trace starts in the browser. |
| **Never logged** | Passwords, password hashes, `Authorization` header values, full request bodies of auth calls. |
| **Tracing** | Distributed tracing (OpenTelemetry) is **not** included — there is one service, so there is nothing to correlate across. Revisit when a second service appears. |

## 15. CI/CD

GitHub Actions, `.github/workflows/ci.yml`, with **path filters** so a docs or
frontend change does not run the backend suite.

| Job | Trigger | Steps |
|---|---|---|
| `backend` | changes under `backend/` | Set up JDK 25 → Gradle cache → `./gradlew build` (compile, unit + Testcontainers integration tests, JaCoCo threshold) → upload test report |
| `frontend` | changes under `frontend/` | Node LTS + npm cache → `npm ci` → `npm run lint` → `npm test -- --watch=false --browsers=ChromeHeadless` → `npm run build` |
| `docker` | push to `main`, after both pass | Build both images, tag with git SHA + `latest`, push to registry |

Branch protection: both build jobs must pass before merge. Testcontainers needs
a Docker daemon — GitHub-hosted `ubuntu-latest` runners provide one; a
self-hosted runner would need it verified.

Deployment is **not** automated in v1 — images are published, promotion is
manual until a target environment is chosen (§16).

## 16. Open technical questions

| Ref | Question | Impact | Proposal |
|---|---|---|---|
| **TQ-1** | Confirm Java 25 (risk R-1) or fall back to Java 21? | Toolchain viability | Spike on 25 first; fall back to 21 if the processor chain isn't ready. |
| **TQ-2** | Where does this deploy — Kubernetes, ECS, a VM with compose? | CD pipeline, config delivery, health probe wiring | Publish images only; decide before v1.1. |
| **TQ-3** | Which secret store supplies `JWT_SIGNATURE_SECRET` and the seed hash in production? | §9.2 | Environment variables from the platform's secret mechanism. |
| **TQ-4** | Is an E2E (Playwright) suite in scope, or are integration + component tests enough for v1? | §11 effort | Skip E2E in v1; the acceptance criteria are covered by integration tests. |
| **TQ-5** | Registry for the built images? | §15 `docker` job | GitHub Container Registry unless told otherwise. |
| **TQ-6** | Should `/swagger-ui` be reachable in production at all? | §10, attack surface | Disabled in prod; enabled in dev and staging. |

Functional open questions **OQ-1 … OQ-5** in the functional spec also affect
this design — in particular OQ-2 (refresh tokens) would add a token endpoint,
a refresh-token store and an Angular silent-refresh flow, and OQ-4 (PATCH)
would change §6.5.

## 17. Implementation addendum

Built and verified. Everything below is either a correction to what this document
assumed, or behaviour added in v1.1.

### 17.1 What changed from the v1.0 design

| # | Specified | Built | Why |
|---|---|---|---|
| R-1 | Java 25 flagged as a toolchain risk; fall back to 21 if the processor chain isn't ready | **Java 25, no fallback needed** | Micronaut 4.10's processors, Hibernate 7, Mockito and Testcontainers all handle class-file 69. Risk closed. |
| §5.3 | Three migrations | **Four** — `V4__seed_role_test_operators.sql` | v1.1 needs operators at each access tier (FR-9): `viewer` (`ROLE_VIEWER`) and `noaccess` (`ROLE_NONE`) alongside `admin`. |
| §6.6 | Six error codes | **Seven** — `ACCESS_DENIED` (403) added | FR-9 requires a refusal distinct from `UNAUTHENTICATED`. |
| §6.1 | Login response documented in the OpenAPI output | **Login is absent from the generated spec** | It is a Micronaut security built-in, not an annotated controller, so `micronaut-openapi` never sees it. The document cannot be imported into a client and used to authenticate. Not fixed; recorded as **TQ-7** in the sibling spec. |
| §7.5 | 401 returns the §6.6 envelope | **401 returns a framework-default HAL-ish body** | Real drift between this document and the implementation, found while writing the Spring service. See §17.5. |
| §13 | Image built via the plugin's `dockerBuild` or a hand-written Dockerfile | **Hand-written, copying `buildLayers` output** | The runner jar is not self-contained: its manifest `Class-Path` points at sibling `libs/` and `resources/` directories. |
| §9.1 | `postgres` + `pgadmin` | **`postgres` only**, plus the sibling service and the UI | pgadmin was never needed; see §17.4 for the host port. |

### 17.2 Authorisation (v1.1)

This service does **not** own the feature store — the Spring Boot service creates
and migrates those tables — but it enforces against them independently. Neither
service consults the other, and neither needs the other running to decide access.

| Concern | Implementation |
|---|---|
| Roles of the caller | `MicronautAuthorizationsManager` implements ff4j's `AuthorizationsManager`, reading `SecurityService`. Only `ROLE_*` authorities count. |
| ACL | `FF4J_ROLES` rows on the `user-data-access` feature, evaluated by `FF4j.check()`. |
| Read-only tier, refusal message | `FF4J_CUSTOM_PROPERTIES` — `readOnlyRoles`, `deniedMessage`. |
| Enforcement point | `FeatureAccessFilter`, a `@ServerFilter` on `/api/v1/users` and `/api/v1/users/**`. |
| Refusal | `AccessDeniedForFeatureException` → `403 ACCESS_DENIED` in the standard envelope. |

**Unconfigured means unrestricted, deliberately.** If the feature row or the
whole table is absent, this service allows and logs a warning rather than
denying. It is a reader, not the owner; a feature-store outage must not lock
every operator out of a service whose authentication still works. The owning
service fails the other way — see the sibling spec §19. Denials always come from
a policy that exists and excludes the caller, never from a failed lookup.

**ff4j gets its own connection pool, built inline and never registered as a
bean.** Micronaut Data wraps *every* `DataSource` bean with contextual-connection
advice whose `close()` throws outside a `@Connectable`/`@Transactional` scope,
and ff4j closes every connection it opens. `DataSourceResolver` does not unwrap
far enough. A two-connection read-only Hikari pool constructed inside the factory
method is the only arrangement that no bean-created listener can intercept.

### 17.3 Diagnostics added in v1.1

`ServedByFilter` stamps `X-Served-By: micronaut` on every response. With two
interchangeable implementations behind one proxy, the UI must be able to report
which one *actually* answered rather than which one was selected — otherwise a
mis-routed request is indistinguishable from a correct one (FR-10).

### 17.4 Ports and the host database port

| Service | Host | Container |
|---|---|---|
| This service | `8080` | `8080` |
| Spring Boot sibling | `8082` | `8080` |
| UI (nginx) | `8081` | `80` |
| PostgreSQL | **`15432`** | `5432` |

The database's host port is **not** 5432, and not 55432/55433 either. Postgres.app
on this machine allocates servers sequentially from 5432 upward and each binds
`::1`, which wins over Docker's wildcard because `localhost` resolves `::1`
first. A neighbouring port is therefore silently shadowed: connections reach
Postgres.app, which has no `usersdb`, and the failure surfaces as *database
"usersdb" does not exist*. This cost three separate debugging sessions — the
integration tests, DBeaver, and a local `bootRun` — before the port was moved
clear. Container-to-container traffic uses `db:5432` and was never affected.

### 17.5 Traps paid for

| # | Trap |
|---|---|
| 1 | **The Gradle JVM itself must be 25.** The Micronaut plugin 5.x requires it to *run Gradle*, not merely as a toolchain target; otherwise the build fails at configuration time before compiling anything. Pinned via `org.gradle.java.home`, stripped inside the Dockerfile. |
| 2 | **Docker Engine 29 rejects the bundled Docker client.** Engine 29 raised its minimum API version to 1.40; the shaded docker-java in Testcontainers negotiates 1.32, and every container start returns `400` behind the useless message `Could not find a valid Docker environment`. Fixed with `systemProperty 'api.version'` — a **system property**; `DOCKER_API_VERSION` in the environment is not consulted. |
| 3 | **`TestPropertyProvider` is a silent no-op without `@TestInstance(PER_CLASS)`.** micronaut-test resolves it from `context.getTestInstance()` during `beforeAll`, where no instance exists under JUnit's default lifecycle. The container URL was discarded with no error and the suite connected to whatever the fallback config named. |
| 4 | **Flyway placeholders must not use the `placeholders` key.** It binds through `@ConfigurationBuilder`, which kebab-cases map keys, so `${seedAdminUsername}` never matched. The `properties` map preserves camelCase and is handed straight to Flyway. |
| 5 | **Two Jackson defaults shipped as user-visible bugs.** Empty pages omitted `content` entirely (clients saw `null`, not `[]`), and `Instant` serialised as numeric epoch seconds, which Angular's date pipe reads as milliseconds — every row rendered as **January 1970**. Fixed with `@JsonInclude(ALWAYS)` and `write-dates-as-timestamps: false`. Neither was visible to `curl`; both needed the UI rendered. |
| 6 | **`@Query` + `Pageable` alias mismatch.** Micronaut Data appends `ORDER BY` using its own generated alias, so the hand-written JPQL must declare `userEntity_`, not `u`. Since a sort is always applied (§8.4), *every* search failed with `INTERNAL_ERROR` until this was found. |
| 7 | **A fixed test port is a flake.** `${SERVER_PORT:8080}` made the suite fail whenever anything held 8080 — including this project's own container. Tests now allocate one free port and use it for both the server and the management endpoints; `-1` does not work, because the two would each get a *different* random port and `/health` would move off the app port. |

### 17.6 Verification

| Check | Result |
|---|---|
| `./gradlew clean build` | **38/38** — UserServiceTest 13, AuthIT 7, UserApiIT 13, UserRepositoryIT 5 |
| Postman contract suite, direct on `:8080` | **31/31 assertions** |
| Postman contract suite, through the proxy | **31/31** |
| Role matrix (FR-9) | `admin` read+write, `viewer` read-only (403 on write), `noaccess` 403 on both — identical to the sibling service |
| Live ACL change | Deleting one `FF4J_ROLES` row refuses the operator on the next request; restoring it grants access again. No restart. |

---

*Implemented and verified against Functional Specification v1.1. The open
questions in §16 that remain open are TQ-2, TQ-3 and TQ-5; TQ-1 (Java 25) is
closed by §17.1.*