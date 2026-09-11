# Technical Specification — User Management Service (Spring Boot)

| | |
|---|---|
| **Document** | Technical Specification — Spring Boot implementation |
| **Implements** | [Functional Specification v1.1](functional-spec.md) |
| **Parallels** | [Technical Specification — Micronaut](technical-spec.md) |
| **Depends on** | [Technical Specification — Feature Toggle Backend](technical-spec-feature-toggle-backend.md) |
| **Version** | 1.3 |
| **Date** | 2026-09-11 (v1.2: 2026-09-10, v1.0: 2026-09-09) |
| **Status** | **Implemented** — see §19 for what changed during build |

---

> **Read this first.** This is a *second implementation of the same functional
> specification*, not a new product. The Micronaut service in `backend/` is built,
> tested and running. Everything in this document is constrained by one rule: the
> Angular SPA in `frontend/` and the Postman collection in `postman/` must run
> against this service **without a single edit**. Where that rule and Spring
> idiom disagree, the rule wins, and §17 records every place it bites.

## 1. Decisions of record

These were chosen explicitly; everything below follows from them. Decisions
carried over unchanged from the Micronaut spec are marked ⟳.

| # | Decision | Choice |
|---|---|---|
| D-1 | Backend framework | **Spring Boot 4.1.x** (Spring Framework 7, Spring Security 7) |
| D-2 | Build tool | Gradle, **Groovy DSL** (`build.gradle`) ⟳ — parity with the existing build, not Spring's Maven default |
| D-3 | Language / JDK | Java **25** ⟳ |
| D-4 | Database | PostgreSQL 17 ⟳ |
| D-5 | Persistence | **Spring Data JPA** / Hibernate 7 |
| D-6 | Schema management | Flyway ⟳ — **the same `V1`–`V3` scripts, byte-identical** |
| D-7 | Authentication | JWT via **`spring-boot-starter-oauth2-resource-server`** (Nimbus), HS256 |
| D-8 | Credential storage | Separate `auth_user` table ⟳ |
| D-9 | List semantics | Server-side pagination, sorting and search ⟳ |
| D-10 | UI | **The existing Angular SPA, unchanged** — no fork, no branch |
| D-11 | Repository layout | `springboot-backend/` as a **third independent build** alongside `backend/` and `frontend/` |
| D-12 | Local infrastructure | The existing `docker-compose.yml`, extended with a profile |
| D-13 | **API compatibility** | **Byte-identical wire contract** with the Micronaut service (§6). This is the acceptance test for the whole project. |
| D-14 | Web stack | **Servlet** (embedded Tomcat), not WebFlux |

**On D-14.** WebFlux buys nothing here. There are five endpoints, one database and
a blocking JDBC driver; a reactive stack would add back-pressure semantics and a
harder debugging story in exchange for throughput this workload will never need.
Servlet + virtual threads (§8.6) covers the concurrency case without the tax.

**On D-2.** Maven is the more common Spring choice and `start.spring.io` defaults
to it. Gradle is chosen anyway so both backends are driven by the same commands
and the same CI job shape. It is a one-file swap if the team disagrees — record it
as a decision, not an accident.

> **⚠ Risk R-1 — Jackson 3.** Spring Boot 4 ships **Jackson 3.1.5** (`tools.jackson.*`),
> not Jackson 2 (`com.fasterxml.jackson.databind.*`). Annotations stay on
> `com.fasterxml.jackson.annotation.*`, so DTOs port cleanly, but any custom
> serializer, module or `ObjectMapper` wiring copied from `backend/` will not
> compile. `jackson-2-bom` 2.21.5 is still managed for compatibility, but mixing
> both is a trap. **Port the trimming deserializer (§8.5) deliberately, not by
> copy-paste.**

> **⚠ Risk R-2 — Testcontainers 2.x.** The BOM manages **Testcontainers 2.0.5**,
> a major version ahead of the 1.21.3 pinned in `backend/`. Its API differs, and
> the Docker-API-version workaround that build needs (§18.1) may be unnecessary or
> may need a different form. **Spike this before committing to the test strategy in §11.**

> **⚠ Risk R-3 — Both services cannot own the same database.** Two Flyway
> instances against one `flyway_schema_history` will fight. §9.1 resolves this
> with a separate database for side-by-side running; the decision must be
> conscious, because getting it wrong corrupts migration history rather than
> failing cleanly.

## 2. Architecture

```
┌────────────────────┐     HTTPS/JSON      ┌──────────────────────┐    JDBC    ┌────────────┐
│  Angular SPA       │ ──────────────────▶ │  user-service-spring │ ─────────▶ │ PostgreSQL │
│  (unchanged)       │   Bearer <JWT>      │  (Spring Boot 4,     │   Hikari   │            │
│  served by nginx   │ ◀────────────────── │   Tomcat, stateless) │ ◀───────── │            │
└────────────────────┘                     └──────────────────────┘            └────────────┘
        │                                            │
        │ nginx proxies /api/ → whichever            ├── /actuator/health, /actuator/prometheus
        │ backend the profile selects                └── /swagger-ui.html
```

Identical shape to the Micronaut service, deliberately. Single deployable, no
service-to-service calls, no broker, no cache tier.

**Layering** — strictly one-directional, unchanged from the Micronaut spec:

```
Controller  →  Service  →  Repository  →  Entity/DB
   DTOs         domain     Spring Data JPA    JPA
```

- **Controller** — HTTP only: binding, validation triggering, status codes.
- **Service** — business rules, transaction boundaries, DTO ↔ entity mapping.
- **Repository** — Spring Data interfaces; no business logic.
- **Entities never leave the service layer.** Controllers speak DTOs exclusively.

The one structural difference is where cross-cutting HTTP concerns live: Micronaut
uses per-exception `ExceptionHandler` beans and an `HttpServerFilter`; Spring uses
a single `@RestControllerAdvice` and a `OncePerRequestFilter` (§6.6, §14).

## 3. Repository layout

```
hello-micronaut/
├── docs/
│   ├── functional-spec.md
│   ├── technical-spec.md              # Micronaut
│   └── technical-spec-springboot.md   # this document
├── backend/                           # Micronaut — untouched by this work
├── springboot-backend/                    # independent Gradle build
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradle.properties              # incl. org.gradle.java.home pin (§18.6)
│   ├── gradlew  gradlew.bat  gradle/
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/users/
│       │   ├── Application.java
│       │   ├── config/          # SecurityConfig, JacksonConfig, OpenApiConfig, CorrelationIdFilter
│       │   ├── controller/      # UserController, AuthController
│       │   ├── dto/             # request/response records — same shapes as backend/
│       │   ├── entity/          # UserEntity, AuthUserEntity
│       │   ├── exception/       # domain exceptions + one @RestControllerAdvice
│       │   ├── repository/      # UserRepository, AuthUserRepository
│       │   └── service/         # UserService, TokenService, AuthUserDetailsService
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   ├── application-test.yml
│       │   ├── logback-spring.xml
│       │   └── (no migrations — Micronaut owns the user schema; see §5.3)
│       └── test/java/com/example/users/
├── frontend/                          # unchanged
├── postman/                           # unchanged — the parity harness (§11)
├── docker-compose.yml                 # all four services, no profile
└── .github/workflows/ci.yml           # gains a springboot-backend job
```

Three builds, three toolchains, no shared build files. Deleting any one folder
leaves the others working.

## 4. Backend technology stack

Versions are **as managed by the Spring Boot 4.1.1 BOM** unless stated. Do not
pin these individually; let the BOM own them.

| Concern | Choice | Version (BOM-managed) |
|---|---|---|
| Framework | Spring Boot | 4.1.1 |
| Core | Spring Framework | 7.0.9 |
| JDK | Java (toolchain in `build.gradle`) | 25 |
| Build | Gradle, Groovy DSL | 9.x + `org.springframework.boot` plugin |
| HTTP | `spring-boot-starter-web` (Tomcat) | Tomcat 11.0.24 |
| Persistence | `spring-boot-starter-data-jpa` | Hibernate 7.4.5.Final |
| Pool | HikariCP (transitive) | BOM |
| Driver | `org.postgresql:postgresql` | 42.7.13 |
| Migrations | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Flyway 12.4.0 |
| Validation | `spring-boot-starter-validation` | Hibernate Validator 9.1.3.Final |
| Security | `spring-boot-starter-security` + `-oauth2-resource-server` | Spring Security 7.1.1 |
| Password hashing | `BCryptPasswordEncoder` (Spring Security Crypto) | cost 12 — **must match `backend/`** |
| JSON | Jackson (see R-1) | 3.1.5 |
| API docs | `springdoc-openapi-starter-webmvc-ui` | 3.1.1 (**not** BOM-managed — pin explicitly) |
| Observability | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Micrometer 1.17.1 |
| Logging | Logback + `logstash-logback-encoder` | 1.5.38 |
| Testing | JUnit Jupiter 6.0.3, Mockito 5.23.0, AssertJ 3.27.7, Testcontainers 2.0.5 | BOM |

**Password hashing is a compatibility constraint, not a preference.** `auth_user`
already holds BCrypt hashes at cost 12 generated by jBCrypt. Spring Security's
`BCryptPasswordEncoder` reads the same `$2a$` format, so the seeded operator and
any existing rows authenticate unchanged. Verify with the existing seeded hash
before writing anything else — it is a five-minute check that de-risks §7 entirely.

## 5. Data model

**Unchanged from the Micronaut spec.** Same two tables, same columns, same
constraints, same indexes. §5.1 and §5.2 of [technical-spec.md](technical-spec.md)
are normative and are not restated here.

### 5.3 Migrations

**This service migrates nothing.** Micronaut owns `users` and `auth_user` and
their history; this service runs `ddl-auto: validate` against them with
`spring.flyway.enabled: false`.

It previously carried the ff4j schema under a second history table in the same
database. That schema moved to the
[feature service](technical-spec-feature-toggle-backend.md) and its own
`feature-toggle` database at v1.3, and the tables were dropped from `usersdb`.

Placeholder substitution needs specific care in Spring — see §18.3.

`spring.jpa.hibernate.ddl-auto=validate` in **every** environment, so
entity/schema drift fails at startup rather than at 3 a.m. ⟳

### 5.4 Entities

`UserEntity` and `AuthUserEntity` are standard JPA `@Entity` classes with
`@Column(nullable=…, length=…)` mirroring the DDL. Timestamps are set in
`@PrePersist`/`@PreUpdate` rather than relying on database defaults, so the
returned object matches what was written without a re-read. ⟳

## 6. API contract

**This section is a compatibility specification, not a design.** Every shape below
is what `backend/` already returns and what `frontend/` and `postman/` already
consume. §6.1–§6.6 of [technical-spec.md](technical-spec.md) apply verbatim; only
the Spring-specific implementation notes are recorded here.

### 6.1 What must be identical

| Element | Requirement |
|---|---|
| Base path | `/api/v1` |
| Login response | `{ access_token, token_type, expires_in, username, roles }` — snake_case on `access_token` |
| Create | `201` + `Location: /api/v1/users/{id}` |
| Page envelope | `{ content, page, size, totalElements, totalPages, sort, direction }` — `content` is **always** present, `[]` when empty |
| Timestamps | ISO-8601 UTC strings, never numeric |
| Error envelope | `{ code, message, correlationId, fieldErrors? }` |
| Error codes | `VALIDATION_FAILED`, `INVALID_PARAMETER`, `USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INTERNAL_ERROR`, `ACCESS_DENIED` (v1.1, 403) |
| `DELETE /users/{id}` | Not implemented → `405` |
| Correlation header | `X-Correlation-Id` echoed on every response, request-supplied value preserved |

Two of these are free on Spring and cost effort on Micronaut, which is worth
knowing: Spring Boot disables `WRITE_DATES_AS_TIMESTAMPS` by default, and Jackson's
default inclusion is `ALWAYS`, so ISO timestamps and a present-but-empty `content`
are the out-of-the-box behaviour rather than a configuration fix. See §18.5.

### 6.2 `POST /api/v1/auth/login` — written by hand

Micronaut supplies a built-in `LoginController`; **Spring Security has no
equivalent**, so this endpoint is an ordinary `@RestController`:

```java
@PostMapping("/api/v1/auth/login")
ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request)
```

It authenticates through `AuthenticationManager`, then mints the token with
`NimbusJwtEncoder` (§7.1) and assembles the response record to match the shape
above exactly — including `token_type: "Bearer"` and `expires_in` in seconds.

**This is an improvement, not merely parity.** Because the endpoint is a real
annotated controller, springdoc documents it. The Micronaut service's OpenAPI
document omits login entirely (it is a framework built-in, not a controller),
which is why importing that spec into Postman leaves you unable to authenticate.
Here, the generated spec is complete and importable on its own.

### 6.3 Deliberate divergence: the 401 body

The Micronaut service returns a framework-default body for unauthenticated calls:

```jsonc
{ "message": "Unauthorized", "_links": {…}, "_embedded": { "errors": [{ "message": "Unauthorized" }] } }
```

That is **not** the §6.6 envelope its own spec documents — a real drift between
document and implementation. This service returns the documented envelope instead:

```jsonc
{ "code": "UNAUTHENTICATED", "message": "Authentication required.", "correlationId": "…" }
```

Implemented via a custom `AuthenticationEntryPoint` and `AccessDeniedHandler`.
Safe to diverge: the SPA's `error.interceptor` and the Postman collection both
branch on **status code**, not body shape. Recorded here so the difference is a
decision rather than a surprise. Whether `backend/` should be corrected to match
is **TQ-7**.

## 7. Security design

### 7.1 Token issuance and validation

- Signature **HS256**, secret ≥ 256 bits from `JWT_SIGNATURE_SECRET`. ⟳
- **Issue:** `NimbusJwtEncoder` over an `ImmutableSecret<SecurityContext>`.
- **Validate:** `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`, wired
  through `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`.
- Claims: `sub` (username), `roles`, `iat`, `exp`, `iss` — same set, so a token
  minted by either service is accepted by the other when they share a secret.
  That property makes side-by-side cutover testing possible; it is not a goal in
  itself.
- Lifetime **3600 s** (OQ-2). No refresh token in v1. ⟳
- Stateless: signature + expiry only. No token store, no revocation. ⟳

Using the resource-server starter rather than a hand-written `OncePerRequestFilter`
is the substantive Spring decision here. Token parsing, error handling and the
`Authentication` population are library code that is already correct; a bespoke
filter would be more code and a larger surface for mistakes.

### 7.2 Authentication provider

A `UserDetailsService` loads the account by lower-cased username from
`auth_user`, and `DaoAuthenticationProvider` verifies the BCrypt hash and rejects
disabled accounts.

**Timing-attack mitigation (FR-6):** `DaoAuthenticationProvider` already performs
a dummy password comparison when the user is not found — Spring's built-in
mitigation. Confirm `hideUserNotFoundExceptions` remains enabled (the default) so
`UsernameNotFoundException` surfaces as `BadCredentialsException` and unknown-user
and wrong-password are indistinguishable in body, shape *and* timing.

### 7.3 Authorization

A single `SecurityFilterChain` bean, default-deny:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(POST, "/api/v1/auth/login").permitAll()
    .requestMatchers("/actuator/health/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()   // dev only, see §10
    .anyRequest().authenticated())
```

`anyRequest().authenticated()` is the guarantee: an endpoint added without
thought is unreachable, not open. ⟳

CSRF disabled (stateless bearer tokens, no cookies), sessions
`STATELESS`, HTTP Basic and form login both off.

### 7.4 CORS

Enabled only for the SPA origin via `CorsConfigurationSource`, driven by
`CORS_ALLOWED_ORIGIN`. Methods `GET,POST,PUT,OPTIONS`; headers
`Authorization,Content-Type,X-Correlation-Id`; `X-Correlation-Id` exposed. ⟳

In the deployed topology CORS is inert — nginx proxies `/api/` onto the SPA's own
origin, so requests are same-origin and no preflight occurs. It exists for
`ng serve` against a directly-addressed backend.

### 7.5 Known limitations (accepted for v1)

Unchanged from [technical-spec.md §7.5](technical-spec.md): no revocation, token
in `localStorage` (OQ-3), no login rate limiting, no password policy. Identical
implementation, identical exposure — porting frameworks does not change the
threat model.

## 8. Key implementation notes

### 8.1 Uniqueness

Two layers ⟳: a pre-check in `UserService` for a clean `409`, **and** the
`ux_users_email_lower` unique index as the real guarantee. The service catches
`DataIntegrityViolationException` on insert and translates it to
`EMAIL_ALREADY_EXISTS`, closing the check-then-insert race.

Spring's `DataIntegrityViolationException` is broader than the Micronaut
equivalent — it covers every constraint, not just this one. Inspect the cause's
constraint name before translating, or a future NOT NULL violation will be
reported to users as a duplicate email.

### 8.2 Transactions

`@Transactional` on service methods; reads `@Transactional(readOnly = true)`.
Controllers are never transactional. ⟳

`spring.jpa.open-in-view=false` — **set it explicitly.** Spring Boot's default is
`true`, which holds the persistence context open through view rendering, hides
lazy-loading bugs behind an accidentally-open session, and keeps a connection
checked out for the whole request. It also warns on every startup. This single
line is the most consequential Spring-specific default in the document.

### 8.3 Search implementation

Same portable form ⟳, expressed as a Spring Data `@Query`:

```java
@Query("""
    SELECT u FROM UserEntity u
    WHERE LOWER(u.firstName) LIKE :pattern
       OR LOWER(u.lastName)  LIKE :pattern
       OR LOWER(u.email)     LIKE :pattern
    """,
    countQuery = "SELECT COUNT(u) FROM UserEntity u WHERE …")
Page<UserEntity> search(@Param("pattern") String pattern, Pageable pageable);
```

**The alias trap.** When a `@Query` is combined with a `Pageable` carrying a
`Sort`, the framework appends `ORDER BY <alias>.<property>`. Spring Data derives
that alias from the query itself, so the declared `u` is used and this works —
whereas Micronaut Data appends its own generated alias and the same query fails at
runtime. The Micronaut service carries a workaround for exactly this (§18.4). Do
**not** port the workaround; it is unnecessary here and would be confusing.

The leading-wildcard trade-off is unchanged and still stated plainly: `LIKE
'%…%'` cannot use a B-tree index and degrades to a sequential scan. Acceptable at
NFR-1's 100 000 rows; if benchmarking fails, switch to the `pg_trgm` GIN index —
a migration-only change. **Benchmark before assuming it's fine.** ⟳

### 8.4 Sorting

`sort` is mapped through an explicit allow-list from API field name to entity
property; anything else → `INVALID_PARAMETER`. ⟳ No string reaches
`Sort.by(...)` unvalidated.

### 8.5 Trimming

BR-2 needs every incoming `String` trimmed before validation, so `"   "` fails
`@NotBlank`. Register a Jackson module with a `String` deserializer that trims —
**written against Jackson 3's `tools.jackson.databind` API** (R-1), not copied
from `backend/`.

### 8.6 Virtual threads

`spring.threads.virtual.enabled=true`. On Java 25 this puts request handling on
virtual threads, so blocking JDBC calls no longer pin a platform thread. It is the
answer to D-14's concurrency question and costs one property.

Verify Hibernate and HikariCP behave under load before treating it as settled —
`synchronized` blocks in older driver code pin carriers. Flag as **TQ-8**.

## 9. Configuration and local development

### 9.1 Running alongside the Micronaut service

Both services expose the same paths and both run Flyway. Three collisions must be
resolved deliberately (R-3):

| Collision | Resolution |
|---|---|
| **HTTP port** | Micronaut keeps `8080`; Spring publishes **`8082`** on the host. Container port stays `8080` in both. |
| **Database** | **Both share `usersdb`.** Flipping the toggle must show the same rows, or it looks like data loss rather than a backend swap. |
| **Flyway history** | **Not shared at all.** Micronaut is the only migrator of `usersdb`; this service has Flyway disabled. The feature service migrates its own separate database. R-3 is resolved by there being exactly one writer per database. |

**No profile.** `docker compose up -d` brings up all four services — database,
both backends and the UI — because the toggle is only meaningful when both
backends are running. Routing is chosen per request by nginx from the feature
flag (§19.6), not by which containers are started, so an envsubst template for
the upstream is unnecessary.

No Flyway configuration is needed here at all (§5.3). The baseline subtleties that
applied while this service carried the ff4j schema now belong to the feature
service, which owns a database nobody else writes to and therefore needs none of
them.

### 9.2 Configuration matrix

| Key | Env var | dev | test | prod |
|---|---|---|---|---|
| Datasource URL | `DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:15432/usersdb` (host) · `jdbc:postgresql://db:5432/usersdb` (container) | Testcontainers | injected |
| Datasource user/password | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres`/`postgres` | container | secret store |
| JWT secret | `JWT_SIGNATURE_SECRET` | dev-only literal | fixed test value | **required secret** |
| JWT expiry (s) | `JWT_ACCESS_TOKEN_EXPIRATION` | 3600 | 3600 | 3600 |
| CORS origin | `CORS_ALLOWED_ORIGIN` | `http://localhost:4200` | — | SPA origin |
| Seed admin hash | `SEED_ADMIN_PASSWORD_HASH` | dev literal | fixed | **required secret** |
| Seed admin username | `SEED_ADMIN_USERNAME` | `admin` | `admin` | injected |
| Hibernate DDL | `spring.jpa.hibernate.ddl-auto` | `validate` | `validate` | `validate` |
| Open-in-view | `spring.jpa.open-in-view` | `false` | `false` | `false` |
| Log format | — | pattern | pattern | JSON |

No secret is committed. The JWT secret and seed hash have **no default at all**,
so a misconfigured production start fails loudly rather than booting with a
guessable key. ⟳

Note the env var names differ from `backend/` (`SPRING_DATASOURCE_URL` vs
`DATASOURCE_URL`) because Spring Boot's relaxed binding maps them automatically.
Deployment manifests are therefore **not** interchangeable between the two
services — call this out in any runbook.

### 9.3 Developer workflow

```bash
docker compose up -d db                        # postgres only
cd springboot-backend && ./gradlew bootRun         # :8080 locally, Flyway migrates on boot
cd frontend       && npm start                 # SPA on :4200, proxy → :8080
```

## 10. OpenAPI / Swagger UI

- **springdoc-openapi 3.1.1**, pinned explicitly — it is not in the Spring Boot BOM.
- Spec at `/v3/api-docs`, UI at `/swagger-ui.html`.
- Generated **at runtime** from the mapping annotations and DTO types. This is the
  opposite of Micronaut's compile-time annotation processor: slightly slower
  startup and a small runtime cost, in exchange for reflecting the *actual*
  registered handler mappings rather than what the source appeared to declare.
- A `bearerAuth` scheme is declared so the UI can call protected endpoints.
- Every error code from §6.1 appears as an `@ApiResponse` on the operations that
  can raise it. ⟳
- **The login endpoint is included** (§6.2) — the generated document is complete
  enough to import into Postman and authenticate from, which the Micronaut
  document is not.
- Anonymous in dev; disabled or behind auth in prod, by configuration not code (TQ-6). ⟳

## 11. Testing strategy

| Layer | Tooling | Covers |
|---|---|---|
| **Unit** | JUnit 6, Mockito, AssertJ | `UserService` rules — trimming, duplicate detection, timestamps, mapping. Repository mocked, no Spring context, milliseconds. |
| **Repository integration** | `@DataJpaTest` + Testcontainers | Unique-index behaviour, search correctness, pagination and sort ordering, Flyway applying cleanly from empty. |
| **API integration** | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` | Full request→DB→response: every status code in §6.1, JWT accepted/rejected/expired, `405` on DELETE. |
| **Security** | Same harness | Unauthenticated rejected; wrong password and unknown user indistinguishable; disabled account rejected. |
| **Contract parity** | **Newman against the existing Postman collection** | See below. |
| **Frontend** | Unchanged — the existing suite still runs | No frontend work in this project. |

**`@ServiceConnection` replaces property plumbing.** A `@Container
PostgreSQLContainer` annotated `@ServiceConnection` wires the JDBC URL,
username and password into the context automatically:

```java
@Container @ServiceConnection
static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
```

No `@DynamicPropertySource`, no property map, and — importantly — **no test
lifecycle trap**. The Micronaut equivalent silently does nothing unless the test
class is annotated `@TestInstance(PER_CLASS)`, because the provider is resolved
from the test instance during `beforeAll` (§18.2). Spring resolves the container
before the context is built, so the failure mode does not exist.

**`RANDOM_PORT` is not optional here.** With the Micronaut service published on
`8080` and possibly a Spring container on `8082`, a fixed test port fails
intermittently depending on what is running. The Micronaut suite had exactly this
failure and now allocates a free port explicitly.

**Contract parity is a test, not a review.** `postman/user-management.postman_collection.json`
already asserts 31 behaviours against the Micronaut service. Point `baseUrl` at
`:8082` and run it:

```bash
newman run postman/user-management.postman_collection.json \
  --env-var baseUrl=http://localhost:8082 --env-var opsUrl=http://localhost:8082
```

**All 31 assertions passing is the definition of done for D-13.** Two will need
attention: the operations folder targets `/prometheus`, which is `/actuator/prometheus`
here (§14), and `/health` is `/actuator/health`. Resolve by mapping those paths in
Spring rather than editing the collection — see TQ-9.

Principles ⟳: one PostgreSQL container per class via the singleton pattern;
per-test isolation by truncation, not container restart; tests never reach a
shared or developer database; **80 % line coverage on `service/` and
`controller/`** enforced by JaCoCo, as a floor rather than a goal.

## 12. Frontend design

**No changes.** [technical-spec.md §12](technical-spec.md) remains normative and
the code in `frontend/` is used as-is.

The only touchpoint is `nginx.conf` becoming a template (§9.1) so the proxy target
is configurable. `environment.prod.ts` keeps `apiBaseUrl` relative (`/api/v1`),
which is precisely what makes the SPA backend-agnostic — it never learns which
implementation answered.

If any frontend change proves necessary, **D-13 has been violated** and the API
contract is wrong. Treat it as a defect in this service, not a frontend task.

## 13. Docker and deployment

| Image | Base | Notes |
|---|---|---|
| `user-service-spring` | Multi-stage: `eclipse-temurin:25-jdk` build → `eclipse-temurin:25-jre` runtime | Non-root user; `HEALTHCHECK` on `/actuator/health`; `-XX:MaxRAMPercentage=75`. |
| `user-ui` | Unchanged | Gains the envsubst nginx template. |

Build with `./gradlew bootJar` and copy the single executable jar. Spring Boot's
fat jar **is** self-contained, so the runtime stage is one `COPY`.

This is simpler than the Micronaut image, which copies `buildLayers` output
(`libs/`, `resources/`, `app/`) because its runner jar resolves dependencies
through a manifest `Class-Path` and is not self-contained. If layer caching later
matters, `bootJar` supports layered extraction — start simple.

Deployment target still unchosen (§16). Plain OCI containers, no orchestrator
assumptions. ⟳

## 14. Observability

| Concern | Implementation |
|---|---|
| **Health** | Actuator `/actuator/health`, `/health/liveness`, `/health/readiness`. The JDBC indicator reports `DOWN` when PostgreSQL is unreachable. `management.endpoint.health.show-details=when-authorized`. |
| **Metrics** | Micrometer → `/actuator/prometheus`. JVM, Hikari pool, HTTP server (rate, latency, status), plus counters per business event (`users.created`, `users.updated`, `auth.login.failed`). ⟳ |
| **Logging** | Logback via `logback-spring.xml`; JSON in deployed environments, pattern locally. `INFO` app, `WARN` frameworks, Hibernate SQL **off**. ⟳ |
| **Correlation** | A `OncePerRequestFilter` reads `X-Correlation-Id` or generates a UUID, puts it in the MDC, echoes it in the response header **and in every error body**, and clears the MDC in a `finally` block. |
| **Never logged** | Passwords, hashes, `Authorization` values, auth request bodies. ⟳ |
| **Tracing** | Not included — one service, nothing to correlate across. ⟳ |

**MDC and virtual threads.** With `spring.threads.virtual.enabled=true` (§8.6) each
request gets a fresh carrier; MDC is `ThreadLocal`-based, so the `finally`-block
clear is not optional hygiene but a correctness requirement. Assert correlation-id
propagation under concurrent load in the integration suite.

**Actuator paths differ from the Micronaut service** (`/actuator/health` vs
`/health`). This is Spring's convention and changing it fights the framework, but
it breaks the existing Postman operations folder and any deployed probe config.
See TQ-9.

## 15. CI/CD

GitHub Actions, path-filtered so a docs or frontend change does not run backend
suites. ⟳ The existing `backend` and `frontend` jobs are untouched; one job is added.

| Job | Trigger | Steps |
|---|---|---|
| `backend` | changes under `backend/` | unchanged |
| `springboot-backend` | changes under `springboot-backend/` | JDK 25 → Gradle cache → `./gradlew build` (unit + Testcontainers integration, JaCoCo threshold) → upload report |
| `frontend` | changes under `frontend/` | unchanged |
| `contract-parity` | changes under `springboot-backend/` **or** `postman/` | compose up the spring profile → wait for health → `newman run` → fail on any assertion |
| `docker` | push to `main`, after all pass | build and tag images |

`contract-parity` is the job that makes D-13 real. Without it, "the SPA still
works" is an assertion nobody re-checks after the third week.

Testcontainers needs a Docker daemon; `ubuntu-latest` provides one. ⟳

## 16. Open technical questions

| Ref | Question | Impact | Proposal |
|---|---|---|---|
| **TQ-1** | Is this a **replacement** for the Micronaut service or a permanent parallel implementation? | Everything — §9.1, §15, long-term ownership | Assume evaluation-then-replace; delete `backend/` on cutover rather than maintaining two. |
| **TQ-2** | Deployment target — Kubernetes, ECS, a compose host? ⟳ | CD pipeline, probe wiring | Publish images; decide before cutover. |
| **TQ-3** | Which secret store supplies `JWT_SIGNATURE_SECRET` and the seed hash? ⟳ | §9.2 | Platform env vars. |
| **TQ-4** | Is Gradle (D-2) or Maven the team's preference for a Spring project? | §4, CI | Gradle for parity; cheap to change now, expensive later. |
| **TQ-5** | Testcontainers 2.x API and Docker-29 behaviour (R-2) — spike result? | §11 | Spike before committing §11. |
| **TQ-6** | Should Swagger UI be reachable in production? ⟳ | §10, attack surface | Disabled in prod. |
| **TQ-7** | Should `backend/` be corrected so its 401 matches its own documented envelope (§6.3)? | Consistency between the two services | Fix Micronaut to match this spec; it is a two-line handler. |
| **TQ-8** | Virtual threads (§8.6) — verified safe under load with Hikari and Hibernate 7? | Concurrency model | Enable, then load-test before production. |
| **TQ-9** | Actuator paths: expose `/health` and `/prometheus` as aliases for parity, or update probes and the Postman collection? | §11, §14, ops runbooks | Alias them via `management.endpoints.web.base-path` so operational tooling is unchanged across both services. |

Functional open questions **OQ-1 … OQ-5** apply unchanged.

## 17. Delta from the Micronaut implementation

The honest summary of what actually differs, for anyone deciding whether this is
worth building.

| Concern | Micronaut (`backend/`) | Spring Boot (`springboot-backend/`) |
|---|---|---|
| DI | Compile-time, annotation processors | Runtime, reflection + AOT option |
| Startup | Faster; no classpath scan | Slower, though 4.x AOT narrows it |
| Login endpoint | Framework built-in — **absent from OpenAPI** | Hand-written controller — **documented** |
| Exception handling | One `ExceptionHandler` bean per exception | One `@RestControllerAdvice` |
| 401 body | Framework default (HAL-ish) | Documented `ApiError` envelope |
| Testcontainers wiring | `TestPropertyProvider` + `@TestInstance(PER_CLASS)` | `@ServiceConnection` |
| `@Query` + `Pageable` | Alias must match the generated one | Alias derived from the query |
| JSON dates | Numeric by default — needed a fix | ISO-8601 by default |
| Empty collections | Omitted by default — needed a fix | Present by default |
| Actuator paths | `/health`, `/prometheus` | `/actuator/**` (TQ-9) |
| Runtime image | Layered `buildLayers` output | Single `bootJar` |
| Ecosystem | Smaller; some gaps met by hand | Larger; more answers are library code |

**Where the effort goes.** Roughly 60 % of the work is mechanical (entities, DTOs,
repositories, migrations copy across almost unchanged), 30 % is security wiring —
the only genuinely different subsystem — and 10 % is the contract-parity tail:
error codes, header names, status codes and the page envelope, which is where the
schedule will actually slip.

## 18. Traps already paid for on this stack

These cost debugging sessions on the Micronaut build. Some carry over, some are
already solved for you. Read this section before writing code, not after.

### 18.1 Docker Engine 29 rejects old Docker API clients

Engine 29 raised its minimum API version to **1.40**. Testcontainers 1.20.x/1.21.x
negotiates 1.32, so every container start returns `400` and the failure surfaces
as the useless message `Could not find a valid Docker environment`. `backend/`
pins `systemProperty 'api.version', '1.44'` — note it is read as a **system
property**; `DOCKER_API_VERSION` in the environment is not consulted.

Testcontainers **2.0.5** (R-2) may resolve this natively. Verify by starting one
container before writing the suite; do not port the pin blindly.

### 18.2 `@TestInstance(PER_CLASS)` — not needed here

Micronaut resolves `TestPropertyProvider` from `context.getTestInstance()` during
`beforeAll`, where no instance exists under JUnit's default lifecycle, so the
container URL is discarded **silently** and tests connect to whatever the fallback
config names. `@ServiceConnection` has no equivalent failure mode (§11).

### 18.3 Flyway placeholders and relaxed binding

`V3__seed_admin.sql` contains `${seedAdminUsername}` and `${seedAdminPasswordHash}`
— **camelCase**. Micronaut's `placeholders` map kebab-cases its keys, so the names
never matched and startup failed; the fix was a different config key entirely.

Spring binds `spring.flyway.placeholders` as a `Map<String,String>`, and relaxed
binding canonicalises property keys. Use **bracket notation** to preserve case:

```yaml
spring:
  flyway:
    placeholders:
      "[seedAdminUsername]": ${SEED_ADMIN_USERNAME:admin}
      "[seedAdminPasswordHash]": ${SEED_ADMIN_PASSWORD_HASH}
```

**Verify this on day one** with a from-empty migration against a throwaway
database. It is a startup failure, so it fails loudly — but only once you try it,
and D-6 forbids editing the SQL to dodge the problem.

### 18.4 Do not port the JPQL alias workaround

`backend/`'s search query aliases the entity as `userEntity_` to match the alias
Micronaut Data appends in its generated `ORDER BY`. Spring Data derives the alias
from the query, so the natural `u` is correct here (§8.3). Copying the workaround
produces working but baffling code.

### 18.5 Two JSON defaults that only show up in the UI

The Micronaut service shipped both of these as bugs, invisible to `curl` checks
and visible immediately in the browser:

- Empty pages omitted `content` entirely, so clients saw `undefined` rather than `[]`.
- `Instant` serialized as numeric epoch seconds; Angular's date pipe reads bare
  numbers as **milliseconds**, so every row rendered as **January 1970**.

Spring Boot's defaults are correct for both. **Assert them anyway** — the Postman
collection already checks `content` is an array and `createdAt` matches
`/^\d{4}-\d{2}-\d{2}T/`, which is exactly the regression guard needed.

### 18.6 Java 25 and the Gradle daemon

The Spring Boot Gradle plugin runs on the **Gradle JVM**, not just the toolchain.
Where a developer's default `java` is older than 25, the build fails at
configuration time before compiling anything. `backend/gradle.properties` pins
`org.gradle.java.home`; do the same in `springboot-backend/gradle.properties`, and
strip that line inside the Dockerfile where the path does not exist.

### 18.7 One database, one Flyway owner

Repeated from R-3 because it is the one that corrupts rather than fails: two
services migrating one database will interleave in `flyway_schema_history`.
Separate databases while both run (§9.1).


## 19. Implementation addendum

Built and verified on 2026-09-09. Everything below is a correction to what this
document assumed, discovered by making it run.

**Read §19.1–19.4 as a record of the v1.2 build, not as current state.** The ff4j
rows there were true when written; at v1.3 the feature store left this service
entirely (§5.3, §19.5). Where they disagree with §5.3, §5.3 is normative.

### 19.1 Decisions that changed

| # | Specified | Built | Why |
|---|---|---|---|
| D-11 | folder `backend-spring/` | **`springboot-backend/`** | Requested name. |
| Switch point | UI picks the base URL | **nginx routes `/api/`** | Chosen at build time: the SPA keeps one origin and never learns which backend answered, so no CORS surface and no client-side coupling. |
| ff4j store | `ff4j-store-springjdbc` | **`ff4j-core`'s `JdbcFeatureStore`** | Its constructor takes a plain `javax.sql.DataSource`. Dropping the Spring-JDBC module removes the Spring 6.2-on-Spring-7 mismatch that R-2 warned about, with no loss of function. |
| Flag API | OpenFeature over ff4j | unchanged | Reads go through OpenFeature; writes go to ff4j directly, because OpenFeature deliberately has no mutation API. |
| Database | share `usersdb` | unchanged | Confirmed working: both services read and write the same rows, Micronaut owns `flyway_schema_history`, Spring owns `flyway_schema_history_ff4j`. |

**R-1 and R-2 both resolved.** Jackson 3 required rewriting only the trimming
deserializer (§8.5). ff4j 3.0.0-RC1 runs correctly on Spring Framework 7 — the RC
created its schema, persisted the flag and evaluated it without incident.

### 19.2 Routing

`frontend/nginx.conf` makes an `auth_request` subrequest to
`springboot-backend:8080/api/v1/features/route`, which evaluates the flag and
answers `204` with `X-Backend-Host`. nginx then proxies to that host. A flag flip
takes effect on the **next request** — no reload, no redeploy.

Two things this needed that the spec did not anticipate:

- **`resolver 127.0.0.11`** — nginx resolves a variable upstream at request time,
  so Docker's embedded DNS must be declared explicitly.
- **`location ^~ /api/v1/features`** pinned to springboot-backend. Routing the
  toggle API *through* the toggle made the flag one-way: once switched to
  Micronaut, the endpoint that switches it back no longer existed. Caught by
  testing the flip in both directions, which is the only way to see it.

If the flag service is unreachable, `error_page 500 502 503 504` falls through to
the Micronaut backend rather than failing every API call.

### 19.3 Traps found while building

| # | Trap |
|---|---|
| 1 | **Spring Boot 4 moved auto-configuration into per-technology modules.** `org.flywaydb:flyway-core` on the classpath no longer triggers Flyway; `spring-boot-starter-flyway` is required. Flyway silently did nothing — no error, no log. |
| 2 | **`baseline-on-migrate` skips `V1`.** The schema is non-empty, so Flyway baselines on first run, and at the default `baseline-version: 1` it treats `V1__ff4j_schema.sql` as already applied. Needs `baseline-version: 0`. |
| 3 | **Bean ordering.** A `@Bean` depending only on `DataSource` is created before Flyway migrates. The ff4j bean needs `@DependsOn("flywayInitializer")` or its first `exist()` call fails the context. |
| 4 | **`CorsConfigurationSource` is ambiguous.** MVC's `mvcHandlerMappingIntrospector` also implements it; inject by type and the context fails with "expected single matching bean but found 2". Use `Customizer.withDefaults()`. |
| 5 | **Spring Security 7 grants factor authorities.** `FACTOR_PASSWORD` appeared in the login response's `roles` array, which Micronaut does not return. Filtered to `ROLE_*` in `TokenService`. |
| 6 | **Actuator paths (TQ-9, now settled).** Resolved by `management.endpoints.web.base-path: /`, so `/health` and `/prometheus` match the Micronaut service and the Postman collection runs unmodified against both. |

### 19.4 Verification

| Check | Result |
|---|---|
| Postman contract suite vs `springboot-backend:8082` | **31/31 assertions, 15/15 requests** |
| Postman contract suite vs `backend:8080` (regression) | **31/31** |
| Postman contract suite through nginx `:8081` | **31/31** |
| Micronaut `./gradlew clean build` | **38/38 tests** |
| Role matrix (FR-9), both services | `admin` read+write · `viewer` read-only, 403 on write · `noaccess` 403 on both |
| Toggle gate (FR-10) | `admin` 200; `viewer` and `noaccess` 403 |
| Live ACL change | One `FF4J_ROLES` row deleted → both services refuse on the next request; restored → both allow. No restart. |
| Backend switching | Routing follows the flag in both directions; `X-Served-By` tracks it |
| UI, driven in a real browser | Three roles render their correct rights; sign-out returns to `/login`; zero console errors |
| BCrypt compatibility | The pre-existing seeded `auth_user` hash authenticates unchanged |

**D-13 is met**: `frontend/` needed no contract change, and the Postman
collection runs unedited against either backend.

### 19.5 Authorisation (v1.3 — via the feature service)

This service no longer owns the feature store, holds no ff4j dependency and
contains no feature rules. It asks
[`feature-toggle-backend`](technical-spec-feature-toggle-backend.md) and applies
the answer — the same `FeatureGateClient` shape as the Micronaut service, so the
two cannot drift.

| Concern | Implementation |
|---|---|
| Asking | `FeatureGateClient` — JDK `HttpClient`, forwards the caller's own bearer token |
| Enforcing | `FeatureAccessInterceptor` on `/api/v1/users**` |
| Caching | Per `(feature, token)`, 10s TTL |
| Unreachable | **Fails open**, with a warning (functional spec FT-7) |

**The asymmetry in v1.1 is gone.** While this service owned the schema it failed
closed on a missing feature and the Micronaut service failed open. Neither owns it
now — both are clients, both fail open, and the rule is the same on both sides.

**Removed at v1.3:** `ff4j-core`, the OpenFeature SDK, `Ff4jConfig`, the
authorizations manager, the whole `admin` package, `FeatureController`, the
`db/ff4j` migrations and the Flyway configuration that ran them. This service now
migrates nothing — Micronaut owns the user schema.

### 19.6 Backend routing (v1.1 correction)

`frontend/nginx.conf` makes an `auth_request` subrequest to
`/api/v1/features/route`, which answers `204` with `X-Backend-Host`. Two details
the original design missed:

- **`location ^~ /api/v1/features` is pinned to this service.** Routing the
  toggle API *through* the toggle made the flag one-way: once switched to
  Micronaut, the endpoint that switches it back no longer existed. Only visible
  by testing the flip in both directions.
- **`resolver 127.0.0.11`** is required, because nginx resolves a variable
  upstream at request time.

If this service is unreachable the subrequest fails and `error_page 500 502 503
504` falls through to the Micronaut backend, rather than failing every API call.

### 19.7 Two bugs the API-level checks missed

Both were found only by driving the UI in a browser, and both were introduced by
the v1.1 work:

| Bug | Cause | Fix |
|---|---|---|
| The backend toggle disappeared the moment an administrator used it | `PUT /api/v1/features` returned `{activeBackend, upstreamHost}` only; the UI read `canToggle` as `undefined ?? false` | `GET` and `PUT` now return one shared `state()` payload, so they cannot drift |
| "Served by" was permanently `springboot` | `/api/v1/features` is pinned to this service regardless of routing, and its `X-Served-By` raced the data call | The UI's interceptor ignores the feature endpoints; the indicator describes the *data* backend only |

### 19.8 Host database port

Now **15432**, not 55432 or 55433. Postgres.app allocates servers sequentially
from 5432 upward and each binds `::1`, which wins over Docker's wildcard because
`localhost` resolves `::1` first — so a neighbouring port is silently shadowed
and connections reach a server with no `usersdb`. The symptom is *database
"usersdb" does not exist*, which reads like a migration failure and is not one.
`spring.datasource.url`'s fallback of `localhost:5432` is a machine-specific
guess: pass the URL explicitly when running outside Docker, and prefer
`127.0.0.1` over `localhost`.

---

*Implemented and verified. The definition of done in §11 — 31 of 31 Postman
assertions against `springboot-backend` with `frontend/` unmodified — is met.*

## 20. Feature toggles (v1.3) — this service is a consumer

The administrative API this section once described has **moved** to
`feature-toggle-backend`, together with the ff4j store, the guardrails, the audit
trail and the 20 tests that cover them.

What remains here is a client (§19.5). Two consequences worth stating:

- **The product contract is unchanged.** `/api/v1/users`, `/api/v1/auth` and the
  error envelope are exactly as before, so D-13 still holds and the 31-assertion
  Postman suite passes untouched.
- **`/api/v1/features` is no longer served here.** The proxy pins it to the feature
  service. Anything still pointing at this service for it will 404.
