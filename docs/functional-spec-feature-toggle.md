# Functional Specification — Feature Toggle

| | |
|---|---|
| **Document** | Functional Specification — feature toggle capability |
| **Extracted from** | [Functional Specification — User Management](functional-spec.md) v1.2 §5.3–5.4 |
| **Version** | 1.0 |
| **Date** | 2026-09-11 |
| **Status** | Implemented |

---

## 1. Purpose

Own the rules that decide **who may do what** across the user management system,
and **which implementation serves traffic**, as a capability in its own right —
separately deployable, separately owned, and usable by more than one consumer.

This document describes what the capability does. Technology decisions live in
[Technical Specification — Feature Toggle Backend](technical-spec-feature-toggle-backend.md)
and [Technical Specification — Feature Toggle Management](technical-spec-feature-toggle-management.md).

## 2. Why it is separate

Access rules began inside the user management services, with each reading the same
tables. That worked, but it meant two implementations of the same rules, two
services coupled to one schema, and no way to change flag behaviour without
touching a product service.

As a distinct capability it has one owner, one store, one set of rules, and one
place to change them. The user management services become consumers that ask a
question and apply the answer.

## 3. Actors

| Actor | Description | Capabilities |
|---|---|---|
| **Toggle administrator** | An operator holding `ROLE_ADMIN`. | Create, read, update and delete toggles, their role lists and their policy. |
| **Toggle viewer** | An operator holding `ROLE_VIEWER`. | Read everything; change nothing. |
| **Consuming service** | A backend deciding whether to admit a request. | Ask for a decision on behalf of an end user. |
| **Proxy** | The edge router choosing an upstream. | Ask which implementation should serve traffic. |

**No other role has access.** An operator holding neither is refused, and the
refusal reveals nothing about which toggles exist.

## 4. Glossary

| Term | Meaning |
|---|---|
| **Toggle** | A named switch with an on/off state, a list of roles permitted to use it, and policy values. |
| **Decision** | The answer to "may this caller use this toggle, and how?" — allowed, read-only, and the message to show if refused. |
| **Protected toggle** | One the system depends on. It cannot be deleted, nor left with no roles. |
| **Policy** | Values attached to a toggle that the role list cannot express: who may administer it, which roles are read-only, what to say when refusing. |
| **Active backend** | Which interchangeable implementation currently serves product traffic. |

## 5. Functional requirements

**FT-1 — Decisions for consuming services**

- A consuming service asks whether a caller may use a named toggle, **on behalf of
  that caller**, and receives a decision: allowed or not, read-only or not, and the
  message to show when refusing.
- The decision is made from the caller's own roles. A service must not have to
  re-implement the rules to interpret the answer.
- Decisions reflect the current configuration. A change is effective for the next
  decision, with no restart or redeploy of anything.
- **A toggle that does not exist is allowed.** Absence of configuration is not a
  refusal; refusals come from a policy that exists and excludes the caller.

**FT-2 — Routing decisions**

- The proxy asks which implementation should serve product traffic and is told,
  without needing credentials.
- The answer changes as soon as an administrator changes it.

**FT-3 — Administration**

- An administrator can list, create, update and delete toggles; grant and revoke
  roles; and read and edit policy values.
- A viewer sees all of it and can change none of it.
- **Creating a toggle does not create behaviour.** A toggle nothing reads has no
  effect until code consults it, and the interface must say so where toggles are
  created.

**FT-4 — Lockout protection**

- A change that would leave nobody able to administer toggles, or would strip every
  role from the toggle controlling access to user data, is **refused** — not warned
  about, and not confirmable.
- Toggles the system depends on are **protected**: they cannot be deleted, and
  cannot be left with an empty role list. They are visibly marked as such.
- A protected toggle may still be switched off, but only while a role remains that
  could switch it back on.

**FT-5 — Change history**

- Every change is recorded before it takes effect: what changed, before and after,
  who did it, and when. A change that cannot be recorded does not happen.
- **Refused attempts are recorded too** — both those refused on role grounds and
  those refused by FT-4. Someone probing what they cannot do is exactly what a
  history is for.
- History is append-only. Nothing can edit or remove an entry, for any role.

**FT-6 — Identity**

- The capability issues no credentials. Operators sign in once with their existing
  account and that session works here too.
- It authenticates every caller and authorises every action; being an internal
  service is not a reason to trust a request.

## 6. Availability expectations

**FT-7 — A consuming service is not taken down by this one**

- If the capability is unreachable, consuming services **allow** the request and
  record that they did.
- Rationale: the caller is already authenticated. Refusing everything would convert
  a dependency outage into a total outage, which is the worse failure for a system
  whose operators are trusted staff (A-6).
- Consuming services may cache a decision briefly to reduce both latency and
  exposure to an outage.

> **Accepted consequence.** During an outage the read-only and no-access tiers are
> not enforced by the consuming services. Authentication still holds, so the
> exposure is to *authenticated* operators only. Revisit if untrusted operators are
> ever introduced.

## 7. Error behaviour

| Condition | Outcome |
|---|---|
| Caller's roles do not permit the operation | **Access denied**, with the configured explanation |
| Unknown toggle | **Not found** |
| Creating a toggle whose id is taken | **Conflict** — never a silent overwrite |
| A change refused by FT-4 | **Conflict**, naming what would have broken |
| Not authenticated | **Unauthenticated** — distinct from access denied |

## 8. Acceptance criteria

1. A consuming service receives a decision for an administrator, a read-only
   operator and an operator with no access, and the three answers differ correctly.
2. Both consuming implementations reach identical conclusions, because both ask the
   same question of the same service.
3. Revoking a role takes effect on the next request to either consuming service,
   with nothing restarted.
4. The proxy obtains a routing answer without credentials, and it changes as soon as
   an administrator changes it.
5. An administrator can create a toggle, grant it a role and delete it again.
6. A viewer sees every toggle and is offered no control that would change one.
7. An operator with neither role is refused, learning nothing about which toggles exist.
8. Deleting a protected toggle, or removing the last role from one, is refused with
   an explanation of what would have broken.
9. Every change and every refusal appears in the history with who, when and the
   before/after; nothing can remove an entry.
10. With the capability stopped, both consuming services continue to serve
    authenticated traffic, and say in their logs that they are not enforcing.

## 9. Assumptions

- **FA-1** Operators and their roles are provisioned by the user management system.
  This capability reads roles from the caller's session; it maintains no accounts.
- **FA-2** Administrators are trusted staff. FT-4 guards against mistakes, not
  against a malicious administrator, who already holds the rights it grants.
- **FA-3** A break-glass path exists outside the applications — direct database
  access — and is why FT-4 refuses rather than merely warning.

## 10. Open questions

| Ref | Question | Proposal |
|---|---|---|
| **FQ-1** | Should a toggle-only role exist, separate from the user-data roles? | Not for v1. Revisit if an operator should administer toggles without seeing user data. |
| **FQ-2** | Should changing an access-control toggle need a second approver? | Not for v1 — FA-2 assumes trusted administrators. |
| **FQ-3** | How long is history retained? | Indefinitely at this volume; revisit when it grows. |
| **FQ-4** | Should consuming services cache decisions for longer than a few seconds? | No — a longer cache delays a revocation taking effect, which is the property FT-1 is for. |
