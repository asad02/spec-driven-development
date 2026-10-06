# Technical Specification — Feature Toggle Management

| | |
|---|---|
| **Document** | Technical Specification — `feature-toggle-management-ui` |
| **Implements** | [Functional Specification — Feature Toggle](functional-spec-feature-toggle.md) FT-3, FT-5 |
| **Depends on** | [Technical Specification — Feature Toggle Backend](technical-spec-feature-toggle-management-service.md) (owns the feature store) |
| **Version** | 2.0 |
| **Date** | 2026-09-11 (v1.0: 2026-09-10) |
| **Status** | **Implemented** — see §11 |

---

> **Read this first.** This is a *fourth deployable*, not a screen inside the
> existing SPA. It is a standalone administrative application on its own port,
> with its own build and image, deliberately separable from the product UI so it
> can be switched off, firewalled or withheld from an environment without
> touching the application operators use every day.

## 1. Decisions of record

| # | Decision | Choice |
|---|---|---|
| D-1 | Deployable | **Separate SPA**, own build, own image, own port — not a route in `user-management-ui/` |
| D-2 | Name / folder | `feature-toggle-management-ui/` |
| D-3 | Port | **8083** (host) → 80 (container) |
| D-4 | Backend | **None of its own.** It consumes `feature-toggle-management-service`, which owns the feature store |
| D-5 | UI stack | Angular + Angular Material, matching `user-management-ui/` |
| D-6 | Identity | The **same** operator accounts and JWTs as the product UI — no separate login, no console credential |
| D-7 | Roles | **`ROLE_ADMIN`** manages; **`ROLE_VIEWER`** reads. No new roles are introduced |
| D-8 | Scope | Create, read, update, delete feature toggles, their role lists and their policy properties |
| D-9 | Availability | **Disabled by default**; enabled per environment (FR-11) |

**On D-4 — why no backend of its own.** The feature store has exactly one writer,
`feature-toggle-management-service`. A second writing service would reintroduce the
split-ownership hazard the extraction was meant to remove. This application is a
pure SPA over that service's admin API.

**On D-7 — why no new roles.** `ROLE_ADMIN` and `ROLE_VIEWER` already exist and
already mean "may change things" and "may look". Minting
`ROLE_FEATURE_ADMIN` would double the role surface and create accounts that can
administer flags but not the data those flags govern — a distinction nobody has
asked for. **Consequence worth accepting deliberately:** a `ROLE_VIEWER` who can
read user data can also read feature configuration. If those must be separable,
D-7 is the decision to revisit, and it is cheap to change now and expensive
later.

> **⚠ Risk R-1 — this console edits its own access rules.** It can disable
> `user-data-access` or empty its role list, locking every operator out of user
> data (functional spec BR-8). The guardrails in §6.4 are not optional polish;
> without them the first mis-click is unrecoverable without database access.

> **⚠ Risk R-2 — a fourth image to keep in step.** Angular version, Material
> theme, JWT handling and error envelope all now exist in two SPAs. They will
> drift. Mitigation: this app is deliberately small, and shares no code with
> `user-management-ui/` — duplication is preferred over a shared library for two consumers.

## 2. Architecture

```
┌──────────────────────────────────────┐
│  feature-toggle-management-ui        │  :8083   admin SPA (Angular + Material)
│  (nginx + static bundle)             │
└──────────────────┬───────────────────┘
                   │  /api/v1/admin/**   Bearer <JWT>
                   ▼
┌──────────────────────────────────────┐
│  feature-toggle-management-service   │  :8084   owns the ff4j store; enforces roles
└──────────────────┬───────────────────┘
                   │  JDBC
                   ▼
    FF4J_FEATURES · FF4J_ROLES · FF4J_CUSTOM_PROPERTIES · FF4J_AUDIT
                   ▲
                   │  asked over HTTP per request — never JDBC
┌──────────────────┴───────────────────┐
│  micronaut-user-management-service   │  :8080
│  springboot-user-management-service  │  :8082
└──────────────────────────────────────┘
```

Changes made here take effect on the **next request** to either product backend,
because both evaluate the ACL per request (functional spec BR-6). Nothing is
restarted, and this application is not in the request path of the product API.

## 3. Repository layout

```
feature-toggle-management-ui/
├── package.json  angular.json  tsconfig.json
├── Dockerfile  nginx.conf
├── proxy.conf.json                  # dev: /api/v1/auth → :8082 (spring boot), /api → :8084 (feature service)
└── src/app/
    ├── core/
    │   ├── auth/      auth.service.ts · auth.guard.ts · admin.guard.ts · jwt.interceptor.ts
    │   ├── http/      error.interceptor.ts · correlation-id.interceptor.ts
    │   └── models/    feature.model.ts · audit.model.ts · api-error.model.ts
    ├── features/
    │   ├── login/     login.component.*
    │   ├── list/      feature-list.component.*     # all features, state, roles
    │   ├── edit/      feature-form-dialog.component.*
    │   ├── roles/     role-editor.component.*      # grant / revoke, explicit
    │   └── audit/     audit-log.component.*
    └── shared/        confirm-dialog · protected-badge · empty/error states
```

A fourth independent build. Deleting this folder leaves the other three working.

## 4. Admin API (hosted on `feature-toggle-management-service`)

Base path `/api/v1/admin`. Same JWT, same error envelope, same correlation header
as the product API.

| Method | Path | Role | Purpose |
|---|---|---|---|
| `GET` | `/features` | `ROLE_VIEWER`, `ROLE_ADMIN` | List every feature with state, roles and properties |
| `GET` | `/features/{uid}` | `ROLE_VIEWER`, `ROLE_ADMIN` | One feature |
| `POST` | `/features` | `ROLE_ADMIN` | **Create** a feature |
| `PUT` | `/features/{uid}` | `ROLE_ADMIN` | Update description and enabled state |
| `DELETE` | `/features/{uid}` | `ROLE_ADMIN` | Delete — refused for protected features |
| `POST` | `/features/{uid}/roles/{role}` | `ROLE_ADMIN` | Grant a role |
| `DELETE` | `/features/{uid}/roles/{role}` | `ROLE_ADMIN` | Revoke a role |
| `PUT` | `/features/{uid}/properties/{key}` | `ROLE_ADMIN` | Set a custom property |
| `DELETE` | `/features/{uid}/properties/{key}` | `ROLE_ADMIN` | Remove a custom property |
| `GET` | `/features/audit` | `ROLE_VIEWER`, `ROLE_ADMIN` | Change history, newest first |

### 4.1 Feature representation

```jsonc
{
  "uid": "user-data-access",
  "enabled": true,
  "description": "Access to /api/v1/users…",
  "roles": ["ROLE_ADMIN", "ROLE_VIEWER"],
  "properties": { "readOnlyRoles": "ROLE_VIEWER", "deniedMessage": "…" },
  "protected": true,          // server-computed, never client-supplied
  "protectedReason": "Gates access to user data (BR-9)"
}
```

`protected` is **derived on the server** from a configured list of feature UIDs
the system depends on. A client cannot set or clear it; sending it is ignored
(consistent with BR-5).

### 4.2 Errors

The existing envelope, with two additions:

| HTTP | `code` | Raised when |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bad feature uid, unknown role name, malformed property |
| 403 | `ACCESS_DENIED` | Caller lacks the role for this operation |
| 404 | `FEATURE_NOT_FOUND` | No such feature |
| 409 | `FEATURE_ALREADY_EXISTS` | Create with a uid that is taken |
| 409 | `FEATURE_PROTECTED` | Delete, or role-removal, refused by §6.4 |

## 5. Security

- **Authentication** — the existing `POST /api/v1/auth/login`. Same accounts,
  same tokens. A token minted for the product UI works here and vice versa; that
  is a consequence of D-6 and is intended.
- **Authorisation** — enforced **server-side** on every admin endpoint. The SPA
  hides controls a `ROLE_VIEWER` cannot use, but hiding is presentation, not
  protection; each endpoint checks the role itself.
- **Availability (D-9)** — a single property gates the whole `/api/v1/admin`
  surface. When disabled the routes are not registered at all, so they return
  404 rather than 403: a disabled console should not advertise its existence.
- **Read-only means read-only** — `ROLE_VIEWER` receives 403 on every mutating
  verb. There is no "soft" read-only that relies on the UI.
- **CORS** — the admin SPA is a different origin (`:8083`) from
  `springboot-user-management-service` (`:8082`), so unlike the product UI this application
  genuinely needs CORS. Either add `:8083` to the allowed origins, or have its
  own nginx proxy `/api` the way `user-management-ui/` does. **Prefer the proxy** — it
  keeps the browser same-origin and avoids widening CORS on a service that
  handles authentication.

## 6. Behaviour

### 6.1 Feature list (landing screen)

Every feature: uid, description, enabled state, roles, property count, and a
**protected** badge where applicable. Toggling is a direct control for
`ROLE_ADMIN`; for `ROLE_VIEWER` the state is shown but not operable.

### 6.2 Create

A form taking uid, description, initial state and optional initial roles. Uid is
validated against the same character rules ff4j accepts and checked for
collision, returning `FEATURE_ALREADY_EXISTS` rather than silently overwriting.

> **A created flag does nothing until code reads it.** The UI says so plainly on
> the create form. This was open question OQ-7 in the functional spec, answered
> in favour of allowing creation — but a flag with no reader is a common source
> of "the toggle doesn't work" confusion, and the interface should pre-empt it.

### 6.3 Roles and properties

Granting and revoking a role are **separate explicit actions**, not a free-text
list that is saved wholesale — a wholesale save makes accidental mass-revocation
one careless edit away. Property editing is key-by-key for the same reason.

### 6.4 Guardrails (functional spec BR-8, BR-9)

Enforced **server-side**, refused with `409 FEATURE_PROTECTED`:

| Attempt | Outcome |
|---|---|
| Delete a protected feature | Refused |
| Remove the last role from a protected feature's ACL | Refused |
| Remove the role that grants the caller admin rights, where no other role holds it | Refused |
| Disable a protected feature | **Allowed** — but only while a role remains that can re-enable it |

The refusal explains *what would have broken*, not merely that it was refused.

### 6.5 Audit (FR-12, BR-10)

Every mutation writes to `FF4J_AUDIT` **before** it is applied; if the write
fails, the change is not applied. Refused attempts are recorded too. The audit
screen is read-only for both roles — there is no delete, for anyone.

## 7. Configuration

| Key | Env var | dev | prod |
|---|---|---|---|
| Admin API enabled | `FEATURE_ADMIN_ENABLED` | `true` | **`false`** |
| Protected feature uids | `FEATURE_ADMIN_PROTECTED` | `user-data-access,use-springboot-backend` | same |
| API base (SPA) | — | `/api/v1/admin` (proxied) | `/api/v1/admin` |
| Port | `FEATURE_UI_PORT` | `8083` | per environment |

## 8. Docker and Compose

| Image | Base | Notes |
|---|---|---|
| `feature-toggle-management-ui` | `node:22-alpine` build → `nginx:1.29-alpine` | Same two-stage shape as `user-management-ui/`; nginx proxies `/api/v1/auth/` to `springboot-user-management-service:8080` (it issues tokens) and the rest of `/api/` to `feature-toggle-management-service:8080` |

Added to `docker-compose.yml` as a fourth service on `8083`, depending on
`springboot-user-management-service` being healthy. It is **not** in the product request path:
if it is down, the product UI and both backends are unaffected.

## 9. Testing

| Layer | Covers |
|---|---|
| Unit (SPA) | Services with `HttpTestingController`, the admin guard, form validation |
| Component (SPA) | List renders protected badges; viewer sees no mutating controls; refusals surface readably |
| API integration (Spring) | Every endpoint × every role — **the 403 matrix is the point of this suite** |
| Guardrail tests | Each row of §6.4, asserted server-side with the UI bypassed entirely |
| Audit tests | A change writes history before applying; a failed audit write blocks the change; refusals are recorded |

The guardrail and audit suites matter more than the screens. A UI bug is
visible; a missing guardrail is invisible until someone is locked out.

## 10. Open technical questions

| Ref | Question | Proposal |
|---|---|---|
| **TQ-1** | Should `ROLE_VIEWER` on user data really imply read access to feature configuration (D-7)? | Yes for v1; split into a dedicated role only if an operator should see data but not configuration. |
| **TQ-2** | Own nginx proxy, or CORS from `:8083` to `:8082`? | Proxy (§5). Decide before the first build — it shapes `nginx.conf`. |
| **TQ-3** | Does deleting a feature need a typed-uid confirmation, as destructive database actions usually do? | Yes for protected-adjacent features; plain confirm otherwise. |
| **TQ-4** | Should the audit screen paginate from day one? | Yes — it is append-only and grows without bound. |
| **TQ-5** | Is ff4j's own bundled console a viable shortcut instead of building this? | **No.** It is JSP-based, which Spring discourages in an executable jar, it has its own security model rather than reusing these accounts, and — decisively — it cannot enforce the §6.4 guardrails. Build the SPA. |

## 11. Implementation addendum (v2.0)

Built, then re-pointed when the feature capability was extracted into its own
service and database.

### 11.1 What changed at extraction

| Was | Now |
|---|---|
| Admin API hosted on `springboot-user-management-service` | Hosted on **`feature-toggle-management-service`** (:8084) |
| Store in `usersdb` alongside user tables | Its own **`feature-toggle`** database |
| One nginx upstream | **Two** — see §11.2 |

### 11.2 Authentication goes to a different upstream than the rest

`feature-toggle-management-service` validates tokens but issues none, so `/api/v1/auth/` must
reach a service that owns `auth_user`. nginx therefore proxies:

```
location ^~ /api/v1/auth/   →  springboot-user-management-service:8080   (issues the token)
location    /api/           →  feature-toggle-management-service:8080 (validates it)
```

Same operator, same token, two upstreams. Sending `/auth` to the feature service
returns 404, which presents as a login that silently fails — worth knowing before
changing this file.

### 11.3 Upstreams are named through variables

Both `proxy_pass` targets go through a `set` variable with
`resolver 127.0.0.11`. With a literal hostname nginx resolves once at startup and
keeps a stale IP after the upstream container restarts, answering **502** on every
admin call while the service itself is healthy.

### 11.4 Verification

| Check | Result |
|---|---|
| Three roles in a real browser | admin full · viewer read-only, no mutating controls · no-access refused at login |
| Create → grant role → delete | passes |
| Protected delete | control disabled; server refuses independently |
| Last-role revoke | refused with the full explanation surfaced in the UI |
| Audit | 37 entries, 9 refusals marked |
| Console errors | none |

---

*Implemented and verified. TQ-2 resolved in favour of the nginx proxy (§11.2).*