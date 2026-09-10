# Functional Specification — User Management Service

| | |
|---|---|
| **Document** | Functional Specification |
| **Product** | User Management Service (`hello-micronaut`) |
| **Version** | 1.1 |
| **Date** | 2026-09-10 (v1.0: 2026-09-01) |
| **Status** | Implemented — see §13 Change log |

---

## 1. Purpose

Deliver a single backend microservice that owns user records in a relational
database, and a separate web UI that lets an authenticated operator create,
update, browse and search those records.

This document describes **what** the system does and how it behaves from the
outside. It contains no technology decisions; those live in the companion
technical specifications — [Micronaut](technical-spec.md) and
[Spring Boot](technical-spec-springboot.md). Both implement this document; §5.4
describes how an operator chooses between them at runtime.

## 2. Scope

### 2.1 In scope

- One backend microservice exposing a JSON HTTP API for user records.
- Persistent storage of user records in a database.
- Token-based authentication: operators log in and receive a token that is
  required on every user-management call.
- A separate web UI (its own codebase and build) that consumes the API.
- Four capabilities on the user resource: **create**, **update**,
  **get one**, **list many** (paged, sorted, searchable).
- **Role-based authorisation** (v1.1): what an operator may read or change is
  decided by the roles held by their login account, evaluated per request.
- **Runtime backend selection** (v1.1): two interchangeable implementations of
  this API run side by side, and an administrator chooses which one serves
  traffic without a redeploy.

### 2.2 Explicitly out of scope for v1

| Item | Rationale |
|---|---|
| Deleting users (hard or soft) | Not requested; API returns 405 on `DELETE`. |
| Roles/permissions on the *user* record | User is domain data only; roles belong to the login account (§5.2). |
| Self-service signup, password reset, email verification | No user-facing registration flow in v1. |
| Refresh tokens / silent re-login | Open question OQ-2. |
| Bulk import/export, CSV upload | Not requested. |
| Audit trail of who changed what | Only `createdAt`/`updatedAt` timestamps are kept. |
| Multi-tenancy | Single tenant. |
| Internationalisation | English (en-US) only. |

## 3. Actors

| Actor | Description | Capabilities |
|---|---|---|
| **Administrator** | An operator holding `ROLE_ADMIN`. | Everything below, plus switching the active backend (FR-10). |
| **Viewer** | An operator holding `ROLE_VIEWER`. | Log in; view and search users. **Cannot** create or update. |
| **Operator without access** | A login account whose roles grant nothing. Authenticates successfully but is refused every user endpoint. | Log in only. |
| **User (record)** | A person whose details are stored. **Not** an actor — has no login and never signs in. | — |
| **API client** | Any programmatic consumer holding a valid token. | Whatever its account's roles allow — identical rules to a human operator. |

> **Important distinction:** a *login account* and a *user record* are separate
> things. Creating a user record does **not** create a login. Operator accounts
> are provisioned out-of-band (§5.2).

## 4. Glossary

| Term | Meaning |
|---|---|
| **User** | A stored person record: name, email, phone. |
| **Login account** | Credentials (username + password) that an Operator uses to authenticate. |
| **Token** | Short-lived credential issued at login and presented on every subsequent API call. |
| **Page** | A bounded slice of the user list, described by page number, page size and total count. |
| **Role** | A named capability held by a login account (e.g. `ROLE_ADMIN`). Roles are what authorisation is decided from. |
| **Feature** | A named gate in configuration that lists the roles allowed to use it, and any policy attached to it (read-only roles, who may administer it). |
| **Active backend** | Which of the two interchangeable API implementations is currently serving requests. |

## 5. Functional requirements

### 5.1 User record

**FR-1 — User attributes**

Every user record carries exactly these attributes:

| Attribute | Type | Origin | Required | Rules |
|---|---|---|---|---|
| `id` | Opaque identifier | Server-generated | — | Immutable; never supplied or changed by a client. |
| `firstName` | Text | Client | Yes | 1–50 characters after trimming. |
| `lastName` | Text | Client | Yes | 1–50 characters after trimming. |
| `email` | Text | Client | Yes | Valid email format; ≤ 254 characters; **unique across all users**; stored and compared case-insensitively. |
| `phone` | Text | Client | No | When present, 7–20 characters, digits with optional leading `+`, spaces, hyphens, parentheses. |
| `createdAt` | Timestamp (UTC) | Server-generated | — | Set once at creation; immutable. |
| `updatedAt` | Timestamp (UTC) | Server-generated | — | Refreshed on every successful update. |

**FR-2 — Create user**

- The Operator submits first name, last name, email and optionally phone.
- The system validates per FR-1, assigns an id and timestamps, persists the
  record, and returns the complete created record.
- If the email already belongs to another user, creation is rejected with a
  conflict error naming the field (§7).
- Creation is not idempotent: two valid requests with different emails create
  two users.

**FR-3 — Update user**

- The Operator identifies a user by id and submits a **complete** replacement
  of the editable fields (first name, last name, email, phone). Omitting
  `phone` clears it; omitting a required field is a validation error.
- `id` and `createdAt` cannot be changed; `updatedAt` is refreshed by the server.
- Changing email to a value held by a *different* user is rejected with a
  conflict. Re-submitting a user's own current email is allowed.
- Updating an unknown id returns not-found.
- An update that changes nothing still succeeds and refreshes `updatedAt`.

**FR-4 — Get a single user**

- Given an id, the system returns the complete user record, or not-found.

**FR-5 — List users**

- Returns a page of user records together with pagination metadata: current
  page number, page size, total number of matching users, total pages.
- **Paging:** zero-based page number (default `0`); page size default `20`,
  minimum `1`, maximum `100`. A page beyond the end returns an empty list with
  correct totals — not an error.
- **Sorting:** sortable on `firstName`, `lastName`, `email`, `createdAt`,
  `updatedAt`; ascending or descending; default `createdAt` descending
  (newest first). An unknown sort field is a validation error.
- **Searching:** a single free-text term matches if it appears anywhere within
  first name, last name, or email, case-insensitively. Blank or absent term
  means "no filter". The term is applied before paging, so totals reflect the
  filtered set.

### 5.2 Authentication

**FR-6 — Login**

- An Operator submits username and password and receives a token plus its
  expiry on success.
- Invalid username, wrong password, or a disabled account all return the same
  generic "invalid credentials" failure — the response must not reveal which
  part was wrong, nor whether a username exists.
- Login accounts are provisioned out-of-band (seeded at deployment). There is
  no UI or API to create, list or modify login accounts in v1.

**FR-7 — Protected endpoints**

- All user endpoints (FR-2 … FR-5) require a valid, unexpired token.
- A missing or malformed token returns *unauthenticated*.
- An expired token returns *unauthenticated*; the UI must send the Operator
  back to the login screen with an explanatory message rather than showing a
  raw error.
- The login endpoint and the service health check are the only unauthenticated
  endpoints.
- Authentication only establishes *who* is calling. What they may then do is
  decided separately by FR-9.

**FR-8 — Session end**

- Logout is client-side: the UI discards the token and returns to the login
  screen. The server keeps no session state and does not revoke tokens in v1.
- Signing out must **navigate** to the login screen, not merely discard the
  token. An operator left on an authenticated screen holding no token sees every
  subsequent action fail, which reads as a broken application.
- The signed-in screen must not remain reachable with the browser Back button
  after signing out.

### 5.3 Authorisation (v1.1)

**FR-9 — Role-based access to user data**

- Every user endpoint is gated by the roles on the caller's login account.
- An operator whose roles are not permitted is refused with *access denied* and
  a message explaining that their role lacks access. This is distinct from
  *unauthenticated*: the credentials were valid, the permission was not.
- Access has two tiers: roles that may **read and write**, and roles that may
  **read only**. A read-only operator is refused create and update, and the UI
  must not offer them controls that would only be refused.
- The permitted roles, the read-only roles, and the refusal message are
  **configuration, not code**. Changing who has access takes effect on the next
  request, with no rebuild, redeploy or restart of any service.
- Both backend implementations enforce identical rules from the same
  configuration, and each enforces independently — neither delegates the
  decision to the other, and neither needs the other to be running.
- Where no access policy has been configured at all, a service that does not own
  that configuration treats the absence as *unrestricted* rather than *denied*,
  so a configuration outage cannot lock every operator out of a working system.
  Refusals always come from a policy that exists and excludes the caller.

**FR-10 — Selecting the active backend**

- Two interchangeable implementations of this API run at the same time over the
  same data. Exactly one serves requests at any moment.
- An **administrator** may switch which one serves traffic. The change takes
  effect on the next request; no restart or redeploy is involved.
- Operators without the administrator role are refused the switch and are not
  shown the control.
- The UI shows which implementation actually answered the most recent request,
  taken from the response itself rather than from the requested setting, so a
  mis-routed request is visible rather than silent.
- Switching backends must not change what data is returned. Both read and write
  the same records.

## 6. User interface

The UI is a separate single-page web application. It has three screens.

### 6.1 Login screen

- Fields: username, password. Submit button disabled until both are non-empty.
- On success: navigate to the user list.
- On failure: an inline, non-dismissing error — "Invalid username or password."
  The password field is cleared; the username is kept.
- Visiting any other screen without a valid token redirects here, preserving
  the originally requested destination for post-login return.

### 6.2 User list screen (default landing screen)

- A table with columns: First name, Last name, Email, Phone, Created.
  Empty phone renders as an em dash, not blank.
- **Search box** — free-text, filters as described in FR-5, debounced so that
  typing does not issue a request per keystroke. Clearing it restores the
  unfiltered list.
- **Sorting** — clicking a sortable column header sorts by it; clicking again
  reverses direction. Current sort is visually indicated.
- **Paging** — page-size selector (10 / 20 / 50 / 100) and page navigation,
  showing "x – y of N".
- **Row action** — selecting a row opens that user for editing (§6.3).
- **"Create user"** button opens the create form (§6.3).
- States that must be handled visibly:
  - *loading* — progress indicator over the table region;
  - *empty (no users at all)* — "No users yet" with a call to action to create one;
  - *empty (search returned nothing)* — "No users match '<term>'" with a clear-search action;
  - *error* — an error panel with a retry action; the previous table contents
    must not be silently left on screen as if current.
- Changing search, sort or page size resets to page 0.

### 6.3 Create / edit user form

- One form serving both modes, shown as a dialog over the list.
  - *Create* — empty fields, title "Create user", submit "Create".
  - *Edit* — pre-filled from the selected user, title "Edit user", submit "Save".
- Fields: First name, Last name, Email, Phone (optional). Read-only context in
  edit mode: created and last-updated timestamps.
- Validation is enforced client-side per FR-1 **and** re-enforced by the server;
  the client is never the only line of defence. Field errors appear beneath the
  field on blur and on submit attempt.
- Submit is disabled while the form is invalid and while a request is in flight
  (prevents double submission).
- On success: the dialog closes, the list refreshes, and a transient
  confirmation appears ("User created." / "User updated.").
- On a duplicate-email conflict from the server: the dialog stays open and the
  email field shows "This email is already in use." — entered data is never lost.
- On any other failure: the dialog stays open with a form-level error and the
  submit button re-enabled.
- Cancelling with unsaved changes asks for confirmation before discarding.

### 6.4 Cross-cutting UI behaviour

- The signed-in operator's username and a logout action are visible on every
  authenticated screen.
- All server errors are rendered as human-readable messages; raw status codes
  and stack traces are never shown to the Operator.
- The list screen is usable down to 1024px width; below that the table scrolls
  horizontally rather than reflowing.

## 7. Error behaviour (API)

Every failure returns a machine-readable body with a stable error code, a
human-readable message, and — for validation failures — a per-field breakdown.

| Condition | Outcome | Notes |
|---|---|---|
| Field fails validation | **Validation error** | Lists every offending field at once, not just the first. |
| Email already in use | **Conflict** | Identifies `email` as the conflicting field. |
| Unknown user id | **Not found** | Same response whether the id never existed. |
| Missing/invalid/expired token | **Unauthenticated** | Generic; no detail about why. |
| Caller's roles do not permit the operation | **Access denied** | Names the gate that refused and carries the configured explanation. Distinct from *unauthenticated* — the credentials were valid. |
| Read-only role attempts a create or update | **Access denied** | Same shape; the message says the role is read-only. |
| Unhandled server fault | **Internal error** | Generic message to the client; full detail logged server-side with a correlation id echoed to the client. |

## 8. Business rules

- **BR-1** Email uniquely identifies a user; comparison is case-insensitive
  (`Jane@x.com` and `jane@x.com` are the same email).
- **BR-2** Leading/trailing whitespace is trimmed from all text fields before
  validation and storage.
- **BR-3** Timestamps are stored and transmitted in UTC; the UI renders them in
  the operator's local timezone.
- **BR-4** User records are never removed in v1.
- **BR-5** Server-managed fields (`id`, `createdAt`, `updatedAt`) supplied by a
  client are ignored, not rejected.
- **BR-6** Authorisation is evaluated per request from configuration, never
  compiled in. Revoking a role takes effect on the caller's next request.
- **BR-7** Both backend implementations return byte-identical responses for the
  same request and the same caller, including refusals. A client cannot tell
  which one served it except by the diagnostic header in FR-10.

## 9. Non-functional requirements

| Ref | Requirement |
|---|---|
| NFR-1 | A read of one user or one page of ≤ 100 users responds in < 300 ms at the 95th percentile, measured server-side, with 100 000 users in the database. |
| NFR-2 | The service is stateless; any instance can serve any request, enabling horizontal scaling behind a load balancer. |
| NFR-3 | Passwords are never stored in reversible form and never appear in logs or API responses. |
| NFR-4 | Tokens are transmitted only over TLS in any deployed environment. |
| NFR-5 | The service exposes a health check suitable for container orchestration readiness/liveness probes. |
| NFR-6 | Every request is traceable end-to-end via a correlation identifier present in logs. |
| NFR-7 | The API is self-describing: an interactive, always-current API reference is served by the running service. |
| NFR-8 | Backend and UI are independently buildable, testable and deployable; neither build depends on the other. |
| NFR-9 | The UI meets WCAG 2.1 AA for the three screens: keyboard-navigable, labelled form controls, visible focus, errors announced to assistive technology. |

## 10. Acceptance criteria

The release is accepted when all of the following hold:

1. An operator with seeded credentials can log in and is landed on the user list.
2. A wrong password shows the generic failure and does not reveal whether the
   username exists.
3. Creating a user with valid data adds it to the list without a manual refresh.
4. Creating a user with an email that already exists keeps the dialog open and
   flags the email field; no partial record is written.
5. Submitting an empty form flags every required field at once.
6. Editing a user changes exactly the submitted fields; `id` and `createdAt`
   are unchanged and `updatedAt` advances.
7. With 100+ users seeded, paging, page-size changes and both sort directions
   return correct, non-overlapping pages with a correct total count.
8. A search term matches on partial first name, last name or email
   case-insensitively, and totals reflect the filter.
9. Calling any user endpoint without a token is rejected; the UI redirects to
   login when the token expires mid-session.
10. `DELETE` on a user returns method-not-allowed.
11. Backend and UI each build and pass their tests from a clean checkout with a
    documented command each.
12. An operator whose roles are not permitted can log in, and is then refused
    every user endpoint with *access denied* and a readable explanation — not a
    blank screen and not a generic server error.
13. A read-only operator sees the user list but is refused create and update,
    and is not shown controls for them.
14. Revoking a role in configuration refuses that operator on their next
    request, on **both** implementations, with no restart or redeploy;
    restoring it grants access again just as immediately.
15. An administrator can switch the active backend and the next request is
    served by the other implementation, returning the same data; a
    non-administrator is refused the switch.
16. Signing out returns the operator to the login screen, and the signed-in
    screen is not reachable with the Back button.

## 11. Assumptions

- **A-1** Operator accounts **and their roles** are seeded by deployment
  tooling; no account or role self-service exists in v1.
- **A-2** Trusted internal operators only — no rate limiting or CAPTCHA in v1.
- **A-3** Modern evergreen browser (last two versions of Chrome, Edge, Firefox,
  Safari). No IE/legacy support.
- **A-4** Expected scale: tens of thousands of users, single-digit concurrent
  operators.
- **A-5** No existing user data to migrate; the system starts empty.

## 12. Open questions

| Ref | Question | Blocks | Proposal if unanswered |
|---|---|---|---|
| **OQ-1** | Should the UI expose *any* management of login accounts, or is out-of-band seeding acceptable for v1? | UI scope | Seeding only. |
| **OQ-2** | Token lifetime, and do we need refresh tokens? Without them, an operator is logged out mid-work when the token expires. | Auth design, UX | 1-hour access token, no refresh; re-login required. |
| **OQ-3** | Where is the token held in the browser? `localStorage` survives refresh but is XSS-readable; in-memory is safer but loses the session on every reload. | UI security | `localStorage` for v1, flagged as a security debt. |
| **OQ-4** | Should update be a full replacement (as specced in FR-3) or support partial patches? | API contract | Full replacement only. |
| **OQ-5** | Is deletion truly out of scope, or deferred to v1.1? | Data model | Out of scope; no `deleted` column reserved. |

---

*Approve or annotate this document before the technical specification is
treated as final; §12 answers may change the technical design.*

## 13. Change log

### v1.1 — 2026-09-10

Everything in v1.0 was built and verified; this revision records behaviour that
was added afterwards and behaviour that v1.0 described imprecisely.

| Change | Sections |
|---|---|
| **Role-based authorisation.** Access to user data is decided per request from the roles on the login account, with a read-only tier. Refusals are *access denied*, distinct from *unauthenticated*. | §3, §5.3 FR-9, §7, §8 BR-6, §10.12–14 |
| **Two interchangeable backends.** A second implementation of this API runs alongside the first over the same data; an administrator selects which one serves traffic, live. | §2.1, §4, §5.3 FR-10, §8 BR-7, §10.15 |
| **Sign-out must navigate.** v1.0 said only that the UI "discards the token". Discarding it without leaving the authenticated screen strands the operator with a dead session, which is how it was first built and shipped. FR-8 now requires the redirect and forbids Back returning to the signed-in screen. | §5.2 FR-8, §10.16 |
| **Three seeded operators** rather than one, so the three access outcomes — full, read-only, refused — are all reachable in a running system. | §3, §11 A-1 |

**Open questions unchanged.** OQ-1 … OQ-5 are still open; role assignment
remains out-of-band seeding (A-1), which is the OQ-1 answer extended to roles.

**Not yet specified.** Nothing here describes *how* roles are stored or how the
backend switch is routed — both are deliberately technology decisions and live
in the technical specifications.

---
