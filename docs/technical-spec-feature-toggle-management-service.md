# Technical Specification — Feature Toggle Backend

| | |
|---|---|
| **Document** | Technical Specification — `feature-toggle-management-service` |
| **Implements** | [Functional Specification — Feature Toggle](functional-spec-feature-toggle.md) |
| **Consumed by** | [feature-toggle-management-ui](technical-spec-feature-toggle-management-ui.md) · [Micronaut](technical-spec.md) · [Spring Boot](technical-spec-springboot.md) |
| **Version** | 1.0 |
| **Date** | 2026-09-11 |
| **Status** | **Implemented** |

---

> **This is the only service in the system that depends on ff4j.** Everything else
> asks it over HTTP. That is the point of the extraction: one owner, one store, one
> set of rules.

## 1. Decisions of record

| # | Decision | Choice |
|---|---|---|
| D-1 | Framework | Spring Boot 4.1.x (Spring Framework 7, Spring Security 7) |
| D-2 | Language / JDK | Java 25 |
| D-3 | **Database** | **`feature-toggle`** — its own database, not a schema inside `usersdb` |
| D-4 | Flag engine | ff4j 3.0.0-RC1 (`ff4j-core` only) behind the OpenFeature SDK |
| D-5 | Persistence | **JDBC only.** No JPA, no Hibernate — this service owns no entities |
| D-6 | Authentication | Validates the product backends' JWTs with the shared secret. **Issues none.** |
| D-7 | Roles | `ROLE_ADMIN` manages, `ROLE_VIEWER` reads. No new roles introduced |
| D-8 | Port | **8084** (host) → 8080 (container) |
| D-9 | Consumers couple by | **HTTP contract only** — no shared library, no shared schema |

**On D-3 — why a separate database, not a separate schema.** Two Flyway instances
over one database was survivable only because the histories were split by table
name, which is a convention rather than a boundary. A separate database makes the
ownership physical: this service is the sole writer, so its Flyway needs no
baseline games and no coordination with anyone.

**On D-6 — validates but never issues.** Adding a login endpoint here would mean a
second copy of `auth_user`, or a second identity. Neither is wanted. The product
backend authenticates; this service trusts the signature and reads the `roles`
claim. **Every service must therefore agree on the claim name** — that shared
constant is the real coupling, and it is one word.

**On D-5 — no JPA.** ff4j's store is plain JDBC and nothing else here persists
anything. Dropping JPA removes Hibernate, entity/schema validation and a startup
cost, for a service whose whole job is a handful of small reads.

## 2. Architecture

```
 feature-toggle-management-ui         :8083 ─┐  admin CRUD + history
                                             │
 user-management-ui                   :8081 ─┤  routing subrequest (anonymous)
                                             ├──▶  feature-toggle-management-service :8084
 micronaut-user-management-service    :8080 ─┤         ff4j + OpenFeature                │
 springboot-user-management-service   :8082 ─┘         the only ff4j dependency          │  JDBC
                 (access decisions)                                                      ▼
                                                                              [ feature-toggle ]
                                                                                FF4J_FEATURES
                                                                                FF4J_ROLES
                                                                                FF4J_CUSTOM_PROPERTIES
                                                                                FF4J_AUDIT
```

Product backends hold **no rules**. They forward the end user's token, receive a
decision, and apply it.

## 3. API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/features/{uid}/access` | any authenticated | **The decision endpoint.** Consuming services call this with the end user's token |
| `GET` | `/api/v1/features/route` | **anonymous** | Routing for the proxy — `204` + `X-Backend-Host` |
| `GET` | `/api/v1/features` | any authenticated | Runtime summary and the caller's rights |
| `PUT` | `/api/v1/features` | `ROLE_ADMIN` | Switch the active backend |
| `GET` | `/api/v1/admin/features` | `ROLE_ADMIN`, `ROLE_VIEWER` | List |
| `POST` | `/api/v1/admin/features` | `ROLE_ADMIN` | Create |
| `PUT`/`DELETE` | `/api/v1/admin/features/{uid}` | `ROLE_ADMIN` | Update / delete |
| `POST`/`DELETE` | `/api/v1/admin/features/{uid}/roles/{role}` | `ROLE_ADMIN` | Grant / revoke |
| `PUT`/`DELETE` | `/api/v1/admin/features/{uid}/properties/{key}` | `ROLE_ADMIN` | Set / remove policy |
| `GET` | `/api/v1/admin/features/audit` | `ROLE_ADMIN`, `ROLE_VIEWER` | History, newest first |

### 3.1 The decision

```jsonc
{
  "feature": "user-data-access",
  "allowed": true,
  "readOnly": false,
  "deniedMessage": null,
  "allowedRoles": ["ROLE_ADMIN", "ROLE_VIEWER"]
}
```

A **decision, not a feature dump**. A consuming service that received the raw
toggle would have to re-implement the rules to interpret it, which is exactly what
extracting this service was meant to stop.

### 3.2 Errors

Same envelope as the product services: `code`, `message`, `correlationId`,
optional `fieldErrors`. Codes: `ACCESS_DENIED` (403), `FEATURE_NOT_FOUND` (404),
`FEATURE_ALREADY_EXISTS` (409), `FEATURE_PROTECTED` (409), `VALIDATION_FAILED`,
`INVALID_PARAMETER`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`,
`MALFORMED_REQUEST`, `INTERNAL_ERROR`.

## 4. Data

The `feature-toggle` database holds ff4j's five tables plus its own
`flyway_schema_history`. Migrations: `V1__ff4j_schema.sql`,
`V2__widen_audit_value.sql`.

**`EVT_VALUE` had to be widened.** ff4j sizes it `VARCHAR(100)`, which is ample for
usage counters and too small for an audit detail that must say what changed and
what would have broken — a guardrail message alone runs to ~120 characters, and the
insert failed *silently* at the original width. Since FT-5 makes the record a
precondition of the change, an audit row that cannot hold its own explanation
defeats the purpose.

## 5. Guardrails (FT-4)

Enforced server-side, refused with `409 FEATURE_PROTECTED`:

| Attempt | Outcome |
|---|---|
| Delete a protected toggle | Refused |
| Remove the last role from a protected toggle | Refused |
| Remove the role granting the caller their own access, with no other | Refused |
| Disable a protected toggle | **Allowed** — while a role remains that can re-enable it |

`protected` is computed on the server from configuration. A client cannot set or
clear it; supplying it is ignored.

## 6. Configuration

| Key | Env var | Default |
|---|---|---|
| Datasource | `DATASOURCE_URL` | `jdbc:postgresql://db:5432/feature-toggle` |
| JWT secret | `JWT_SIGNATURE_SECRET` | none — must be shared with the product backends |
| Admin surface | `FEATURE_ADMIN_ENABLED` | `true` in dev; **`false` in production** |
| Protected toggles | `FEATURE_ADMIN_PROTECTED` | `user-data-access,use-springboot-backend` |
| Allowed origins | `CORS_ALLOWED_ORIGINS` | the two SPA origins |

When the admin surface is disabled the controller is **not registered**, so its
routes return 404 rather than 403 — a disabled console should not advertise itself.

## 7. Testing

20 integration tests over a Testcontainers PostgreSQL, in three suites: role matrix
(6), lockout guardrails (7), audit trail (7).

Two things worth keeping:

- **The suite mints its own JWTs** with the shared secret. This service has no login
  endpoint, so depending on a product backend being up to get a token would couple
  the suites together.
- **One container for the whole suite**, started once. Per-class containers also
  defeat Spring's context cache: the suite went from timing out past 600s to 16s.

## 8. Operational notes

**Consumers must re-resolve this service's address.** nginx resolves a literal
upstream hostname once at startup, so a restart of this container leaves the proxy
holding a stale IP and answering `502`. Both SPA proxies name it through a variable
with `resolver 127.0.0.11`, which forces re-resolution.

**The proxy's routing subrequest must not carry a browser `Origin`.** nginx's
`auth_request` forwards the original request's headers; an origin this service does
not allow makes it answer 403, which nginx then returns for the user's actual
request. The subrequest strips it.

## 9. Open technical questions

| Ref | Question | Proposal |
|---|---|---|
| **TQ-1** | Should the decision endpoint accept a batch of feature ids? | Not yet — one gate exists. Revisit at three or more per request. |
| **TQ-2** | Should consuming services get a push invalidation rather than a short TTL? | No. A 10s TTL is simpler and bounded; push adds a delivery guarantee to maintain. |
| **TQ-3** | Is a shared JWT secret the right coupling, or should this verify via JWKS? | Shared secret for now. JWKS matters once services are deployed independently enough to rotate separately. |
| **TQ-4** | ff4j is on a release candidate (3.0.0-RC1). | Acceptable while isolated in one service. It is now trivially replaceable — one provider class implements the OpenFeature SPI. |
