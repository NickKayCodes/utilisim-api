# UtiliSim Phase 2 Backlog

**Created:** 2026-10-28
**Sources of truth:** `docs/DEFECTS.md` (17 verified defects) and
`docs/DOCUMENTATION_REVIEW.md` (30 fixed documentation issues, 6 recommendations).
**Scope rule:** every item below traces to a verified defect, a verified hardening gap, or a
documented limitation. Nothing here is speculative product work.

## Priority definitions

| Priority | Meaning | Gate |
|---|---|---|
| **P0** | Cross-user access, credential exposure, unrecoverable auth failure, or a wrong financial result in a core flow | Must be closed before any shared or networked deployment |
| **P1** | Incorrect status/identity, missing validation, or a gap that makes support and testing unreliable | Must be closed before Phase 2 feature work starts |
| **P2** | Planned quality, platform, or developer-experience work | Scheduled after Phase 1.5 exits |

**Phase 1.5 exit criteria:** all P0 items closed, every P1 item either closed or explicitly
accepted with an owner and a date, API and UI gates green, and the new regression tests passing
in CI.

---

## Phase 1.5 — Critical Security

### S1 — Scope scenarios to the authenticated owner (P0)

**Source:** API-08 (runtime).
**Problem:** `ScenarioController` calls `findAll()`, uses `findById()` without an owner check, and
persists a client-supplied `userId`. Any authenticated user can read, rename, or delete every
other user's scenarios. A live request with `"userId": "someone-else"` returned `200` and stored
that value.

**Work:**
- Derive the owner from `@AuthenticationPrincipal`; ignore or reject `userId` in request bodies.
- Add `findByUserId`, `findByIdAndUserId`, and owner-scoped delete/update queries.
- Return `404` for foreign ids (avoid id probing) and `403` only where disclosure is acceptable.
- Add a two-user integration test covering list, read, update, and delete isolation.

**Acceptance:** a second user cannot see, mutate, or delete the first user's scenarios; the
`userId` column is always server-derived; the isolation test runs in CI.

---

### S2 — Remove or replace the user-search endpoint (P0)

**Source:** API-09 (runtime + source).
**Problem:** `GET /api/sentinel/users/search/by-email` requires a GET body, which Tomcat does not
parse, so every call returns `400`; a `POST` returns `500` because the `405` is masked. The
handler returns the JPA `AppUser` entity, which would expose the BCrypt hash if it ever resolved.

**Work:**
- Delete the endpoint, or replace it with `GET /api/sentinel/users/me` plus a DTO
  (`{ id, username, email }`).
- If lookup is required for administration, put it behind an explicit admin role and a query
  parameter or path variable, never a GET body.
- Ensure no controller returns a JPA entity directly; map to response records.

**Acceptance:** no endpoint accepts a GET body; no response contains `password`; user lookup is
either gone or restricted to the current user/admin; contract tests assert the response shape.

---

### S3 — Reject invalid tokens with 401 and stop leaking stack traces (P0)

**Source:** API-02 (runtime), API-03 (runtime).
**Problem:** malformed, expired, and wrongly signed tokens throw out of
`JwtAuthenticationFilter`, producing `500` with a Java stack trace. Only a missing token returns
`401`. The UI clears the session only on `401`, so an expired token leaves the app permanently
broken until manual logout.

**Work:**
- Catch `JwtException`/`IllegalArgumentException` in the filter, clear the security context, and
  continue unauthenticated so the entry point returns `401`.
- Set `server.error.include-stacktrace=never` and stop returning `ex.getMessage()` for framework
  exceptions.
- Add tests for malformed, expired, and wrongly signed tokens.
- Update `utilisim-ui/src/lib/api.ts` to treat an auth failure consistently (clear session and
  redirect) once the server returns `401`.

**Acceptance:** all three invalid-token cases return `401` with a stable JSON body and no trace;
the UI returns to the login screen without a reload; the tests run in CI.

---

### S4 — Stop echoing client-supplied simulation identity (P0)

**Source:** API-14 (runtime).
**Problem:** `ForgeSimulationEngine` copies `input.getUserId()` and `input.getId()` into the
result, and `AssembleController` never derives identity from the principal.

**Work:**
- Derive `userId` from `@AuthenticationPrincipal` in the controller.
- Remove `userId` from `SimulationInput` or ignore it on input.
- Generate `simulationInputId` server-side or delete the field until persistence exists.
- Add a test that a spoofed body value cannot appear in the response.

**Acceptance:** the response identity always matches the token owner; spoofed values are ignored.

---

### S5 — Persist and rotate the JWT signing key (P1)

**Source:** API-13 (source).
**Problem:** `JwtService` generates a random key at startup, so every restart invalidates all
tokens; combined with S3 this becomes a `500` with a stack trace.

**Work:** load `<JWT_SECRET>` from environment/secret storage, enforce a minimum key length, and
document a rotation procedure. Keep the old key available only for an explicit overlap window if
rotation without logout is required.

**Acceptance:** a token minted before a restart still validates after restart; a missing or weak
secret fails fast at startup; the secret never appears in Git or logs.

---

### S6 — Replace `localStorage` token storage with a safer session model (P1)

**Source:** Hardening gap verified in `src/lib/api.ts` and `AuthProvider.tsx`.
**Problem:** the bearer token and user object live in `localStorage`, readable by any XSS. Logout
is client-only; there is no revocation.

**Work:** prefer an `HttpOnly`, `Secure`, `SameSite` cookie issued by the API, or keep the bearer
token in memory with a short lifetime and a refresh mechanism. Add a strict Content-Security
Policy and sanitise any rendered user content.

**Acceptance:** the token is not readable from JavaScript after login; logout invalidates the
session server-side; the login/session flow still survives a reload.

---

### S7 — Add password policy, login throttling, and registration limits (P1)

**Source:** Verified hardening gap (`RegisterRequest` has no constraints; no rate limiter).
**Problem:** any string is accepted as a password, and login has no lockout or throttling.

**Work:** minimum length/entropy rules, a breached-password check if available, per-IP and
per-account throttling on login/register, and audit logging for repeated failures.

**Acceptance:** weak passwords are rejected with a field-level error; brute-force attempts are
rate-limited; tests cover the limits.

---

### S8 — Move configuration out of source and lock down CORS (P1)

**Source:** Hardening gap (ignored `application.properties` holds the local DB credentials;
CORS allowlist is hardcoded to `http://localhost:3000`).
**Problem:** the only configuration source is a local file; a shared deployment would either
reuse those credentials or fail. The CORS origin is not environment-specific.

**Work:** require `<DATABASE_URL>`, `<DATABASE_USERNAME>`, `<DATABASE_PASSWORD>`, and
`<JWT_SECRET>` from the environment; add a `.env.example`; read allowed origins from
configuration; disable `show-sql` outside development.

**Acceptance:** a clean environment starts from documented variables only; no secret is committed;
CORS rejects unknown origins.

---

## Phase 1.5 — Correctness

### C1 — Make blank APR a first-class outcome (P0)

**Source:** API-01 (runtime), API-07 (source).
**Problem:** `TomeService` unboxes `request.apr()` before the null check, so `null` or an omitted
APR returns `500`. The estimate path is unreachable and would return `0.16` (0.16%, not 16%).

**Work:** choose one:
1. Require `apr` with bean validation and return `400` + field error (fastest, matches the current
   UI copy).
2. Make `Tome.apr` nullable and implement a real estimate in the correct unit.

Either way, delete or fix `calculateEstimatedApr`, and add tests for null, omitted, zero, and
valid APR.

**Acceptance:** no blank-APR request can produce a `500`; the documented behavior and the UI copy
match the implementation.

---

### C2 — Define simulation completion honestly (P0)

**Source:** API-04 (runtime).
**Problem:** the schedule loop stops at `termMonths * 2`, and the engine still sets `COMPLETED`
even when a balance remains. An underpaying plan produced 24 rows, a final balance of `1000.0`,
and `COMPLETED`. `monthsToPayoff` is really the number of rows emitted.

**Work:**
- Complete only when `balance == 0`; otherwise return a non-success status with
  `remainingBalance` and the reason (`TERM_CAP`, `PAYMENT_TOO_LOW`).
- Reject or flag `minimumPayment <= monthlyInterest`.
- Compute `monthsToPayoff` from actual balance closure or rename it.
- Keep a hard safety bound but report it as a bound, not a payoff.

**Acceptance:** a completed result always has a zero balance; underpayment and cap cases are
covered by tests and by the UI.

---

### C3 — Normalise APR and interest math (P0)

**Source:** API-05, API-06, API-07 (runtime + source).
**Problem:** the codebase mixes percentages and decimals:
`calculate-total-interest` returns `18000.0` for `$1,000` at `18%`; the recommender treats
`0.2%` as high interest; the estimate returns `0.16`; the amortization schedule uses a decimal
annual rate divided by 12.

**Work:**
- Choose one internal representation (recommended: decimal annual rate, `0.18`).
- Convert once at the API boundary and document the public unit in the contract.
- Replace `currentBalance * apr` with schedule-based totals or a clearly named annual estimate.
- Compare the recommendation threshold against the same unit (`0.15` decimal, `15.0` percent).
- Add boundary tests: `0`, `0.15`, `15`, `15.01`, `18`, `30`.

**Acceptance:** `$1,000` at `18%` produces a plausible interest figure for the chosen term;
`/recommend` and `/analyze` agree; the contract states the unit once.

---

### C4 — Return the right status for the right failure (P1)

**Source:** API-03, API-10, API-12 (runtime).
**Problem:** unsupported methods/media types return `500`; deleting a missing/foreign tome
returns `500`; explicit `null` for primitive scenario fields returns a generic `400`.

**Work:** map `HttpRequestMethodNotSupportedException` → `405`,
`HttpMediaTypeNotSupportedException` → `415`, `HttpMediaTypeNotAcceptableException` → `406`,
not-found → `404`, forbidden → `403`; keep the catch-all for unexpected failures only. Add a
`@RestControllerAdvice` test matrix.

**Acceptance:** each case returns its documented status and a stable JSON body; the UI can
distinguish validation, auth, missing, and server errors.

---

### C5 — Separate login identity from the profile username (P1)

**Source:** API-11 (runtime).
**Problem:** `AppUser.getUsername()` returns `email`, so register/login responses report the email
in the `username` field and the chosen username is unusable.

**Work:** keep the email as the Spring Security login identifier, expose `username` through a
response DTO, and add a round-trip test. Do not change `getUsername()` without updating the JWT
subject and the user-details lookup together.

**Acceptance:** registration echoes the chosen username; login returns it; existing tokens and
lookups still work.

---

### C6 — Validate financial inputs (P1)

**Source:** Verified runtime (term `0`, negative principal, zero payment all returned `200`).
**Problem:** there is no bean validation on `SimulationInput`, `TomeRequest`, or
`ScenarioContext`.

**Work:** require `principal > 0`, `termMonths >= 1`, `minimumPayment >= 0`, non-negative
balances/limits, a bounded APR, and a known `strategy`/`type`. Return field-level `400`s.

**Acceptance:** invalid inputs never produce a `200`; a table-driven test covers each constraint.

---

### C7 — Stop masking infrastructure failures as bad credentials (P2)

**Source:** API-16 (source).
**Problem:** `AuthServiceImpl.login` catches every exception and throws
`BadCredentialsException`, so a database outage looks like a wrong password.

**Work:** catch `AuthenticationException` only, log the original cause, and return `503` for
infrastructure failures.

**Acceptance:** a simulated repository failure returns `503` and is logged once; a wrong password
still returns `401`.

---

### C8 — Make `/analyze` consistent with `/recommend` (P2)

**Source:** API-17 (runtime + source).
**Problem:** `analyze` ignores its `input` when computing `projectedInterest`, and ties are
resolved by the order of the request array. For one debt it returned `SNOWBALL`, while
`/recommend` returned `AVALANCHE`.

**Work:** use the supplied input in the projection or remove it; define a deterministic tie-break
shared by both endpoints.

**Acceptance:** both endpoints agree for one debt and for equal-interest debts; the tie-break is
documented and tested.

---

### C9 — Establish the Phase 1.5 regression suite (P1)

**Source:** Documentation review and the runtime probes; current suite is 15 tests and does not
cover any defect above.

**Work:**
- API integration tests: auth failures, scenario isolation, blank APR, simulation cap and
  underpayment, unit boundaries, status-code matrix, input validation.
- Frontend tests: session recovery on `401`, blank-APR guidance, simulation-result copy.
- A CI job that runs `mvnw.cmd test`, `npm run typecheck`, `npm run lint`, and `npm run build`.

**Acceptance:** every P0/P1 defect has a failing-before/passing-after test; the gates run on every
change.

---

## Phase 2 Features

### F1 — Persist simulation runs and expose history (P1)

**Source:** API-15 (source).
**Problem:** a run has a generated `id`, `startedAt`, and `completedAt`, but nothing is stored and
there is no lookup endpoint.

**Work:** add a `SimulationRun` entity, owner-scoped repository, `GET /api/simulations` and
`GET /api/simulations/{id}`, retention rules, and a decision on storing schedule rows vs
recomputing them. Wire the UI's "last run" panel to real history.

**Acceptance:** a completed run survives restart and is visible only to its owner; the id in the
response resolves.

### F2 — Add account update and archival (P1)

**Source:** Verified limitation (create/list/delete only).
**Work:** `PUT /api/tomes/{id}` with owner checks, plus soft-delete/archival if history matters.
Update the UI form and cache invalidation.

**Acceptance:** a user can edit name, balance, APR, minimum payment, limit, and type; foreign ids
return `404`.

### F3 — Add pagination and filtering (P2)

**Source:** Verified limitation (`findAll` for tomes and scenarios).
**Work:** page/size parameters with a stable sort, a capped maximum, and UI paging or infinite
scroll.

**Acceptance:** large lists do not return unbounded payloads; the contract documents defaults.

### F4 — Generate the mechanical API reference (P2)

**Source:** DOCUMENTATION_REVIEW N3.
**Work:** add `springdoc-openapi`, annotate DTOs, and publish the generated reference alongside
the behavioural contract. Keep the contract for caps, token behavior, and phase-1 limits.

**Acceptance:** the generated reference builds in CI and matches the controller set.

### F5 — Introduce schema migrations (P2)

**Source:** Hardening gap (`ddl-auto=update`).
**Work:** Flyway or Liquibase, a baseline for the current schema, and a migration test against a
disposable database.

**Acceptance:** a fresh database reaches the expected schema; no startup mutation.

### F6 — Replace `float` money with exact decimals (P2)

**Source:** Hardening gap; runtime results show values such as `26.17086`.
**Work:** `BigDecimal` (or integer cents) for balances, payments, and interest; round explicitly
at boundaries; migrate existing rows.

**Acceptance:** totals reconcile to the cent; rounding rules are documented and tested.

### F7 — Add roles and an authorization model (P2)

**Source:** Hardening gap (`getAuthorities()` returns an empty list).
**Work:** a role enum, an admin-only surface for user lookup, and method/endpoint rules. Keep the
default user role least-privileged.

**Acceptance:** a non-admin cannot access admin routes; role changes are audited.

### F8 — Add refresh and revocation (P2)

**Source:** Hardening gap (ten-day token, no revocation).
**Work:** short-lived access tokens plus a refresh flow, or server-side session revocation. Pair
with S5 (stable key) and S6 (storage).

**Acceptance:** logout invalidates the session; a revoked token is rejected with `401`.

### F9 — Add end-to-end UI coverage (P2)

**Source:** Verified gap; the UI build passes but no browser test exists.
**Work:** Playwright (or equivalent) covering register → login → create account → simulate →
compare → scenario create/delete, including the expired-token path.

**Acceptance:** the smoke path runs in CI against a disposable API/database.

### F10 — Make scenario ownership visible in the UI (P2)

**Source:** Depends on S1.
**Work:** show only the user's scenarios, explain that scenarios are private, and disable delete
for anything not owned (the API should already refuse it).

**Acceptance:** the UI never offers an action the API will reject; empty/foreign states are clear.

---

## Future Enhancements

### E1 — Add documentation CI (P2)

**Source:** DOCUMENTATION_REVIEW N1.
**Work:** a script that fails on broken relative links, missing anchors, stale route strings,
deleted-file references, and non-`example.com` emails. The manual scan from this review is the
starting point.

**Acceptance:** the check runs on every docs change and blocks regressions.

### E2 — Keep one error-model source (P2)

**Source:** DOCUMENTATION_REVIEW N2.
**Work:** define the error envelope once and link to it from both READMEs; avoid duplicating the
table.

**Acceptance:** a contract change updates one section.

### E3 — Add contract metadata (P2)

**Source:** DOCUMENTATION_REVIEW N4.
**Work:** a "last verified" line with the API commit/version in both contracts.

**Acceptance:** a reader can tell how fresh the behavioural claims are.

### E4 — Add a documentation step to the release checklist (P2)

**Source:** DOCUMENTATION_REVIEW N5.
**Work:** regenerate examples, run the link scan, and confirm the phase-1 limitation list before
tagging.

**Acceptance:** the checklist is part of the release PR template.

### E5 — Add a root quickstart (P2)

**Source:** DOCUMENTATION_REVIEW N6.
**Work:** one page that links both READMEs and states the `:8080` → `:3000` proxy order.

**Acceptance:** a new contributor can start both apps from one page.

### E6 — Generate a typed client from the contract (P2)

**Source:** DOCUMENTATION_REVIEW N3 extension; the UI currently hand-maintains
`src/types/api.ts`.
**Work:** generate types from OpenAPI and diff them in CI.

**Acceptance:** UI types cannot drift silently from the API.

### E7 — Add audit logging and observability (P2)

**Source:** Verified absence; security items above need an audit trail.
**Work:** structured logs for auth failures, scenario/tome mutations, and simulation runs; request
ids; metrics for error rate and latency. Never log tokens, passwords, or full request bodies.

**Acceptance:** security-relevant events are queryable; secrets are redacted.

### E8 — Production deployment hardening (P2)

**Source:** S5, S8, E7, and the lack of environment configuration.
**Work:** HTTPS, secret manager, environment-specific CORS, health/readiness probes, backups, and
a documented restore test.

**Acceptance:** a staging deployment runs from environment variables only and passes a restore
drill.

### E9 — Abuse protection for public endpoints (P2)

**Source:** S7; registration/login are open.
**Work:** rate limits, a bot challenge if registration is public, and alerting on repeated
failures.

**Acceptance:** automated attempts are limited without blocking normal use.

### E10 — Accessibility and responsive polish (P2)

**Source:** UI rebuild added new pages without an accessibility audit.
**Work:** keyboard navigation, labels/announcements, focus management, contrast, and small-screen
checks on the new pages.

**Acceptance:** an automated axe pass plus a manual keyboard walkthrough of the primary flow.

---

## Traceability

| Backlog item | Verified source |
|---|---|
| S1 | API-08 |
| S2 | API-09 |
| S3 | API-02, API-03 |
| S4 | API-14 |
| S5 | API-13 |
| S6, S7, S8 | Verified hardening gaps (DEFECTS.md) |
| C1 | API-01, API-07 |
| C2 | API-04 |
| C3 | API-05, API-06, API-07 |
| C4 | API-03, API-10, API-12 |
| C5 | API-11 |
| C6 | Runtime probes (term 0, negative principal, zero payment) |
| C7 | API-16 |
| C8 | API-17 |
| C9 | Current suite and review method |
| F1 | API-15 |
| F4–F6, E1–E6 | DOCUMENTATION_REVIEW N1–N6 and verified gaps |
| F2, F3, F7–F10, E7–E10 | Verified limitations and dependencies above |
