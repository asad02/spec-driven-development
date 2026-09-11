# Postman collection — User Management System

Covers every endpoint across the system: the two interchangeable product backends,
and the feature toggle service that decides who may use them.

## Import

Postman → **Import** → both files:

- `user-management.postman_collection.json` — 61 requests, 133 assertions
- `local-nginx.postman_environment.json` — optional, routes the product API through nginx

The collection carries working defaults, so it runs with no environment selected.

## Run

**⋯ → Run collection.** Order matters:

- **Folder 1** signs in all three operators and stores their tokens. Everything after it
  depends on those variables.
- **Folder 2** stores `userId` and `takenEmail` for later folders.
- **Folder 6** creates a toggle, exercises it, and deletes it at the end.

Safe to re-run: created users and toggles carry a timestamp, and folder 6 restores
the roles it revokes.

## Three base URLs

| Variable | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | The product API. `:8080` Micronaut · `:8082` Spring Boot · `:8081` through nginx |
| `opsUrl` | `http://localhost:8080` | Health and metrics — always a product backend, never the proxy |
| `featureUrl` | `http://localhost:8084` | The feature service, which owns the toggles and the access rules |

**`opsUrl` is separate from `baseUrl` on purpose.** nginx proxies only `/api/`, so
`/health` and `/prometheus` are not reachable on `:8081`.

## What each folder is for

| Folder | Requests | Purpose |
|---|---|---|
| 1 · Authentication | 5 | Signs in admin, viewer and no-access; proves failures are indistinguishable |
| 2 · Users | 8 | CRUD, paging, search, BR-2 trimming, BR-5 ignored fields |
| 3 · Error contract | 10 | One envelope, correct status for each failure — several of these were 500s until this folder existed |
| 4 · Role-based access | 4 | The product applies the feature service's decision: viewer read-only, no-access refused |
| 5 · Feature service · runtime | 11 | Decisions for backends, routing for nginx, switching the active backend |
| 6 · Feature service · administration | 19 | Toggle CRUD, roles, policy, **the lockout guardrails**, audit trail |
| 7 · Operations | 4 | Health and metrics on both services |

## Contract parity

The product folders (1–4, 7) pass identically against all three entry points. That is
the guarantee that the two backends are interchangeable:

```bash
newman run user-management.postman_collection.json --env-var baseUrl=http://localhost:8080
newman run user-management.postman_collection.json --env-var baseUrl=http://localhost:8082
newman run user-management.postman_collection.json --env-var baseUrl=http://localhost:8081 \
                                                   --env-var opsUrl=http://localhost:8082
```

Needs Node 20+. The `newman` at `/usr/local/bin` on this machine runs under Node 12
and fails every request with `Invalid IP address: undefined` — that is the runner,
not the collection.

## Operators

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123!` | `ROLE_ADMIN` | Everything, including toggles |
| `viewer` | `viewer123!` | `ROLE_VIEWER` | Read users and toggles; change nothing |
| `noaccess` | `noaccess123!` | `ROLE_NONE` | Log in only; refused everywhere |
