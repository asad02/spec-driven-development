# Postman collection — User Management Service

## Import

Postman → **Import** → drop in both files:

- `user-management.postman_collection.json` — the requests
- `local-nginx.postman_environment.json` — optional, only if you want to run through nginx

The collection carries working defaults, so it runs with no environment selected.

## Run

Open **1 · Auth → Log in** and Send. It stores the bearer token in the `accessToken`
collection variable; every other request inherits it through collection-level auth.

Or run the whole thing: **⋯ → Run collection**. Order matters — `Log in` captures the
token, and `Create user` stores the `userId` and `newEmail` that later requests reuse.

## Variables

| Variable | Default | Notes |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Backend directly. Set to `http://localhost:8081` for the nginx origin the SPA uses. |
| `opsUrl` | `http://localhost:8080` | Always the backend: nginx proxies only `/api/`, so `/health` and `/prometheus` are not reachable on 8081. |
| `username` / `password` | `admin` / `admin123!` | Seeded by Flyway `V3`. |
| `accessToken`, `userId`, `newEmail` | *(empty)* | Filled in at run time. |

## Notes

Re-running is safe: `Create user` generates a timestamped email each run, so it never
trips the uniqueness rule. That does mean each full run leaves one more user behind.

Command line, if you'd rather:

    newman run user-management.postman_collection.json

Needs Node 20+. The `newman` on this machine's `/usr/local/bin` runs under Node 12 and
fails every request with `Invalid IP address: undefined` — that's the runner, not the
collection.

## Why not import the OpenAPI spec?

The service does publish one at `http://localhost:8080/swagger/user-management-service-1.0.yml`,
and Postman can import it. But it documents only the `/api/v1/users` routes — the login
endpoint is a Micronaut security built-in rather than an annotated controller, so it is
absent from the spec, and an import alone leaves you with no way to authenticate.
