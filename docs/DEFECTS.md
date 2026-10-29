# UtiliSim Phase 1 — Defect Inventory

**Review date:** 2026-10-28
**Scope:** `utilisim-api` (Spring Boot) and `utilisim-ui` (React/Rsbuild)
**Method:** source inspection plus live requests against a local API on `:8080` and the UI
dev server on `:3000`.
**Baseline:** API 15 tests / 0 failures; UI typecheck, lint, and build passed. The defects below
are behavioural and are not covered by the current suite.

## How to read this document

Severity reflects user or security impact, not implementation effort.

| Severity | Meaning |
|---|---|
| **Critical** | Cross-user data exposure, credential leakage, or an auth failure that cannot be recovered in the UI |
| **High** | A core flow returns the wrong result, crashes, or reports success when it did not succeed |
| **Medium** | Incorrect status code, misleading identity, lost data, or a defect that blocks a documented feature |
| **Low** | Cosmetic, inconsistent, or edge-case behaviour with a clear workaround |

Every entry marked **Runtime** was reproduced against the running API. Entries marked
**Source** were confirmed by reading the code only; they are called out so they are not mistaken
for live evidence.

## Summary

| ID | Severity | Area | Defect | Evidence |
|---|---|---|---|---|
| API-01 | High | Tome create | Blank or omitted `apr` returns `500` before the estimate path runs | Runtime |
| API-02 | Critical | Auth | Malformed, expired, or wrongly signed tokens return `500` with a stack trace | Runtime |
| API-03 | Medium | Error handling | Wrong HTTP method or media type returns `500` instead of `405`/`415` | Runtime |
| API-04 | High | Simulation | Schedule stops at `termMonths * 2`; result still says `COMPLETED` with a balance owed | Runtime |
| API-05 | High | Strategy math | `calculate-total-interest` multiplies balance by a percentage APR, producing 100× values | Runtime |
| API-06 | High | Recommendation | Threshold compares percentage APR against `0.15`, so 0.2% is treated as high interest | Runtime |
| API-07 | Medium | Tome estimate | `calculateEstimatedApr` returns `0.16` and is unreachable; the unit is wrong | Source |
| API-08 | Critical | Scenarios | Every authenticated user can read/delete every scenario; `userId` is client-controlled | Runtime |
| API-09 | Critical | User search | `GET .../users/search/by-email` is unreachable and is designed to return the `AppUser` entity, including the password hash | Runtime + Source |
| API-10 | Medium | Tome delete | Deleting a missing or foreign tome returns `500` instead of `404`/`403` | Runtime |
| API-11 | Medium | Identity | `AppUser.getUsername()` returns the email, so `username` is never echoed or usable | Runtime |
| API-12 | Medium | Scenario create | Explicit `null` for primitive fields returns a generic `400` | Runtime |
| API-13 | Medium | Auth | The JWT signing key is generated per boot, so every restart invalidates all tokens | Source |
| API-14 | Medium | Simulation | Result `userId` and `simulationInputId` echo untrusted request values | Runtime |
| API-15 | Medium | Persistence | A run has an `id` but is never stored and cannot be retrieved | Source |
| API-16 | Low | Auth | Any login failure, including infrastructure errors, is reported as `401` | Source |
| API-17 | Low | Analysis | `analyze` ignores its `input`, and ties are resolved by strategy order | Runtime + Source |

---

## API-01 — Blank or omitted APR crashes tome creation

**Severity:** High
**Affected:** `ledger/tome/Tome.java`, `ledger/vault/TomeService.java`,
`ledger/leaf/create/TomeRequest.java`
**Evidence:** Runtime (both `null` and omitted field)

### What happens

`POST /api/tomes` with `"apr": null`, or with `apr` omitted, returns **500**:

```json
{
  "message": "Cannot invoke \"java.lang.Float.floatValue()\" because the return value of \"com.krath.utilisim.ledger.leaf.create.TomeRequest.apr()\" is null",
  "error": "Internal Server Error"
}
```

The request is never persisted. The UI now marks APR as required, so this is reachable only by
a direct API client or an older build, but the failure mode is still a server error rather than a
validation response.

### Root cause

`TomeRequest.apr` is a boxed `Float`, but `Tome.apr` is a primitive `float`. `TomeService`
unboxes the value on line 23, before the null check on line 24:

```java
tome.setApr(request.apr());                 // NPE when null
tome.setEstimatedApr(
        request.apr() == null ? (float) calculateEstimatedApr(request) : request.apr()
);
```

The `@Column(nullable = true)` annotation on `Tome.apr` does not help a primitive field.

### Suggested fix

Pick one and test it:

1. Reject the request with `400` and a field-level message (smallest change).
2. Change `Tome.apr` to `Float` and implement the estimate path properly.
3. Remove the estimate path and make `apr` required in the DTO with bean validation.

Do not leave the current combination of a nullable DTO field, a primitive entity field, and an
unreachable estimate.

---

## API-02 — Invalid tokens return 500 with a stack trace

**Severity:** Critical (security and session recovery)
**Affected:** `sentinel/warden/sec/auth/filters/JwtAuthenticationFilter.java`,
`sentinel/warden/sec/auth/service/JwtService.java`,
`utilisim-ui/src/lib/api.ts`
**Evidence:** Runtime

### What happens

```http
GET /api/archive/scenarios
Authorization: Bearer not-a-jwt
```

returns **500**, not `401`, and the body includes a full Java stack trace:

```json
{
  "timestamp": "...",
  "status": 500,
  "error": "Internal Server Error",
  "trace": "io.jsonwebtoken.MalformedJwtException: Invalid compact JWT string: ...",
  "message": "Invalid compact JWT string: ...",
  "path": "/api/archive/scenarios"
}
```

A missing token correctly returns `401`, which makes the failure easy to miss in a smoke test.

### Impact

- Expired or tampered tokens leak class names, package structure, and framework versions.
- `utilisim-ui/src/lib/api.ts` only clears the session on `401`; because the server returns
  `500`, the UI keeps a dead token and every request keeps failing until the user logs out
  manually.
- There is no way for the client to distinguish "session expired" from "server crashed".

### Root cause

`JwtService.extractEmail` throws `JwtException` on parse/verification failure. The call is made
from `JwtAuthenticationFilter.doFilterInternal` outside any try/catch, so the exception escapes
the filter chain. Spring's `/error` dispatch then returns its default error body with `trace`.

### Suggested fix

Catch `JwtException` (and `IllegalArgumentException`) in the filter, clear the security context,
and continue the chain unauthenticated so the entry point returns `401`. Disable stack traces in
error responses (`server.error.include-stacktrace=never`) and add tests for malformed, expired,
and wrongly signed tokens.

---

## API-03 — Wrong method and unsupported media type return 500

**Severity:** Medium
**Affected:** `sentinel/warden/sec/ApiExceptionHandler.java`
**Evidence:** Runtime

### What happens

| Request | Actual | Expected |
|---|---|---|
| `DELETE /api/auth/login` | `500` `{"message":"Request method 'DELETE' is not supported"}` | `405` |
| `POST /api/auth/login` with `Content-Type: text/plain` | `500` `{"message":"Content-Type 'text/plain;charset=UTF-8' is not supported"}` | `415` |
| `GET /api/archive/scenarios` with an unacceptable `Accept` | `500`, empty body | `406` |

### Root cause

`ApiExceptionHandler` has a catch-all `@ExceptionHandler(Exception.class)` that maps everything
unrecognised to `500`. Spring's `HttpRequestMethodNotSupportedException`,
`HttpMediaTypeNotSupportedException`, and `HttpMediaTypeNotAcceptableException` are never mapped
to their proper statuses.

### Suggested fix

Add explicit handlers (or extend `ResponseEntityExceptionHandler`) for the standard Spring MVC
exceptions, keep the catch-all for genuinely unexpected failures, and stop returning
`ex.getMessage()` for framework exceptions.

---

## API-04 — Simulation stops at twice the term and calls the result complete

**Severity:** High (incorrect financial result)
**Affected:** `sim/ratio/calculate/RatioService.java`,
`sim/forge/anvil/ForgeSimulationEngine.java`
**Evidence:** Runtime

### What happens

The schedule loop is:

```java
while (balance > 0 && month < input.getTermMonths() * 2) {
```

so a 12-month simulation can emit up to 24 rows. When the cap is reached with money still owed,
`ForgeSimulationEngine` still sets `SimulationStatus.COMPLETED`.

Two reproduced cases:

| Request | Result |
|---|---|
| `principal 1000`, `interestRate 0.2`, `termMonths 12`, `minimumPayment 50` | 24 rows, `monthsToPayoff 24`, final balance `26.17086`, `status COMPLETED` |
| `principal 1000`, `interestRate 0.2`, `termMonths 12`, `minimumPayment 10` | 24 rows, `monthsToPayoff 24`, final balance `1000.0`, `status COMPLETED`, interest still accrued |

In the second case the payment does not cover monthly interest, so `principalPaid` is clamped to
`0` on every row, the balance never moves, and the response still reports a completed payoff with
a `payoffDate` equal to the last (unpaid) row.

### Impact

- A user can be shown a "completed" plan that still owes the full balance.
- `monthsToPayoff` is really "rows emitted", not "months until debt-free".
- Any downstream savings comparison inherits the error.

### Suggested fix

- Decide the meaning of `COMPLETED` and enforce it: only complete when `balance == 0`.
- Return `FAILED`/`NOT_REPAID` with `remainingBalance` when the cap is reached, or loop until
  payoff with a hard safety bound and report the bound separately.
- Reject `minimumPayment <= monthlyInterest` (or surface it as a non-paying plan) instead of
  generating 24 identical rows.
- Rename `monthsToPayoff` or compute it from actual balance closure.

---

## API-05 — Total-interest calculation multiplies balance by a percentage

**Severity:** High (wrong financial output)
**Affected:** `sim/oracle/StrategyService.java` (`calculateTotalInterestForOrdering`),
`sim/oracle/OracleService.java` (`analyzeStrategyEffectiveness`)
**Evidence:** Runtime

### What happens

`POST /api/oracle/strategy/calculate-total-interest` with one debt of `currentBalance: 1000` and
`apr: 18` returns:

```json
18000.0
```

`POST /api/oracle/strategy/analyze` with the same debt returns `projectedInterest: 18000.0` for
both strategies.

### Root cause

```java
.map(debt -> debt.currentBalance() * (debt.apr() > 0 ? debt.apr() : debt.estimatedApr()))
```

APR is expressed as a percentage (`18` means 18%), but it is used as a decimal multiplier without
dividing by `100` or by `12`, and without using the term. The result is not interest, not a
projection, and not comparable to the amortization schedule produced by `RatioService`, which
uses a decimal annual rate and divides by 12.

### Impact

- Strategy comparison ranks debts using a number that is off by orders of magnitude.
- The `analyze` endpoint advertises `projectedInterest`, but the value is a one-year
  balance-times-percent figure and ignores payments, term, and compounding.
- The same inconsistency affects the recommendation threshold (API-06) and the estimate
  (API-07).

### Suggested fix

Normalise APR to one representation at the boundary (recommended: decimal annual rate,
`0.18`), then use the same rate in `RatioService`, `StrategyService`, and `OracleService`. If the
public API must accept `18`, convert once on input and test the conversion. Replace the
"projected interest" calculation with the schedule-based total or rename it to a simple annual
estimate.

---

## API-06 — Recommendation threshold uses the wrong unit

**Severity:** High (wrong recommendation)
**Affected:** `sim/oracle/OracleService.java` line 91
**Evidence:** Runtime

### What happens

```java
if (maxInterestRate > 0.15f) {
    return "AVALANCHE";
}
```

APRs are supplied as percentages, so `0.15` means **0.15%**, not 15%. Reproduced:

| Debts | Result |
|---|---|
| Two debts at `apr: 0.1` | `HYBRID` |
| Two debts at `apr: 0.2` | `AVALANCHE` |

A 0.2% APR is classified as high interest. Any realistic credit-card APR (for example `18`)
also returns `AVALANCHE`, but for the wrong reason and without ever considering the other
branches.

### Suggested fix

Compare against `15.0` for percentage input, or normalise to decimals and compare against `0.15`.
Add table-driven tests at the boundaries (`0`, `0.15`, `15`, `15.01`, `30`) and cover the
`debtCount` and `minBalance` branches.

---

## API-07 — Estimated APR is wrong and unreachable

**Severity:** Medium
**Affected:** `ledger/vault/TomeService.java` (`calculateEstimatedApr`)
**Evidence:** Source

```java
private double calculateEstimatedApr(TomeRequest request) {
    double principal = 0.16;
    return principal;
}
```

Three problems:

1. The method returns `0.16`, which is `0.16%` in the percentage convention used by the rest of
   the API, not the intended 16%.
2. It ignores its `request` argument and is not an estimate.
3. It can never run, because the unboxing on the previous line throws first (API-01).

### Suggested fix

If the product still wants an estimate, implement it with a documented rule and the correct
unit, and only after making `Tome.apr` nullable. Otherwise delete the method and make APR
required.

---

## API-08 — Scenarios are global and trust a client-supplied `userId`

**Severity:** Critical (cross-user data access)
**Affected:** `archive/gate/ScenarioController.java`, `archive/scenario/ScenarioContext.java`
**Evidence:** Runtime

### What happens

- `POST /api/archive/scenarios` accepts a `userId` in the body. A request containing
  `"userId": "someone-else"` returned **200** and persisted that value.
- `GET /api/archive/scenarios` calls `scenarioRepository.findAll()` with no owner filter.
- `GET`, `PUT`, and `DELETE /api/archive/scenarios/{id}` operate on any id without checking the
  principal.
- The list endpoint returned scenarios created by other runs, including scenarios created by a
  different synthetic user during verification.

### Impact

Any authenticated user can read, modify, rename, or delete every other user's scenarios. The
stored `userId` is not trustworthy because the client controls it.

### Suggested fix

- Derive the owner from `@AuthenticationPrincipal`, never from the body.
- Add `findByUserId`/`findByIdAndUserId` queries and scope every read and write.
- Ignore or reject `userId` in request bodies; return `403`/`404` for foreign ids.
- Add an integration test with two users that proves isolation.

---

## API-09 — User-search endpoint is unreachable and exposes the user entity

**Severity:** Critical (security design)
**Affected:** `sentinel/gateway/UserController.java`,
`sentinel/smithy/user/UserServiceImpl.java`, `sentinel/foundation/AppUser.java`
**Evidence:** Runtime + Source

### What happens

```java
@GetMapping("/search/by-email")
public Optional<AppUser> findUserByEmail(@RequestBody AppUser user) {
    return service.findByEmail(user.getEmail());
}
```

A live GET with a JSON body returns **400** `Malformed JSON request body`; a GET without a body
returns the same. A POST returns **500** `Request method 'POST' is not supported` (API-03
masks the `405`). The endpoint therefore cannot be used successfully as written.

It is also unsafe by design:

- It requires a body on `GET`. Tomcat's default `parseBodyMethods` is `POST`, so the body is not
  available to Spring; browsers and `fetch` also do not support GET bodies reliably.
- The return type is the JPA entity `AppUser`. If the request ever succeeded, the response would
  include the BCrypt password hash and the `UserDetails` getters.
- Any authenticated user can probe whether an email is registered.

### Suggested fix

Remove the endpoint. If a lookup is required, use a DTO-based response
(`{ id, username, email }`), a path/query parameter instead of a body, and an authorization rule
that limits lookups to the current user (or to an admin role that does not exist yet).

---

## API-10 — Deleting a missing or foreign tome returns 500

**Severity:** Medium
**Affected:** `ledger/vault/TomeService.java`, `ledger/gateway/TomeController.java`
**Evidence:** Runtime

### What happens

The first `DELETE /api/tomes/{id}` returns `204`. A second delete of the same id returns **500**:

```json
{"message":"Not found","error":"Internal Server Error"}
```

A foreign id throws `RuntimeException("Forbidden")`, which the catch-all handler also turns into
`500`. `tome.getUserId().equals(userId)` would additionally throw if a legacy row had a null
owner.

### Suggested fix

Throw a dedicated not-found exception mapped to `404`, and an access-denied exception mapped to
`403` (or return `404` for foreign ids to avoid id probing). Use `Objects.equals` for the owner
comparison.

---

## API-11 — `username` is never returned or usable

**Severity:** Medium
**Affected:** `sentinel/foundation/AppUser.java`,
`sentinel/smithy/auth/AuthServiceImpl.java`
**Evidence:** Runtime

### What happens

A registration request with `"username": "rc-review-..."` and a distinct email returned:

```json
{"id":"...","username":"rc-review-...@example.com","email":"rc-review-...@example.com"}
```

The supplied username is stored but never used. `AppUser` overrides the Lombok getter:

```java
@Override
public String getUsername() {
    return email;
}
```

`AuthServiceImpl` builds `RegisterResponse` and `LoginResponse` from `getUsername()`, so both
responses report the email in the `username` field. The JWT subject is also the email, which is
why `loadUserByUsername(email)` works by accident.

### Impact

The public `username` field is misleading; clients cannot display a chosen name. Any future
change to make `getUsername()` return the real username would break authentication, because the
JWT subject and the user-details lookup both assume email.

### Suggested fix

Separate the `UserDetails` login identifier from the profile field: implement
`getUsername()` as the email for Spring Security (or introduce a dedicated adapter), expose
`username` via a DTO, and add a test that registration round-trips the chosen username.

---

## API-12 — Explicit `null` for primitive scenario fields returns a generic 400

**Severity:** Medium (API ergonomics)
**Affected:** `archive/scenario/ScenarioContext.java`,
`archive/gate/ScenarioController.java`
**Evidence:** Runtime

`POST /api/archive/scenarios` with `"extraPayment": null` or `"lumpSumPayment": null` returns
**400**:

```json
{"message":"Malformed JSON request body","error":"Bad Request"}
```

Omitting the field stores `0`; sending `0` works. The codebase does not enable
`FAIL_ON_NULL_FOR_PRIMITIVES`, but the primitives still reject explicit nulls in this path, and
the handler collapses the cause into the same message used for unparseable JSON.

### Suggested fix

Accept boxed types and normalise null to `0`, or reject with a field-level error. In either case,
stop reporting every deserialization failure as "Malformed JSON request body"; include the
offending field where it is safe to do so.

---

## API-13 — JWT signing key is regenerated on every boot

**Severity:** Medium
**Affected:** `sentinel/warden/sec/auth/service/JwtService.java`
**Evidence:** Source

```java
private final SecretKey key = Jwts.SIG.HS256.key().build();
```

The HS256 key is random per JVM start. Every restart invalidates all outstanding tokens. Because
of API-02, the resulting failure is a `500` with a stack trace rather than a `401`, and the UI
does not clear the session.

### Suggested fix

Load a stable secret from configuration/environment (`<JWT_SECRET>`), require a minimum length,
and rotate deliberately. Add a test that a token minted before a context restart is rejected
with `401`, not `500`.

---

## API-14 — Simulation identity echoes client input

**Severity:** Medium (integrity)
**Affected:** `sim/forge/anvil/ForgeSimulationEngine.java`,
`sim/forge/hammer/SimulationInput.java`, `sim/forge/hammer/SimulationResult.java`
**Evidence:** Runtime

`ForgeSimulationEngine.run` sets:

```java
result.setSimulationInputId(input.getId());
result.setUserId(input.getUserId());
```

`AssembleController` does not populate either field from the principal. A caller can send any
`userId` or `simulationInputId` and have it echoed in the result. In the live probes both fields
were `null` because they were omitted, confirming that they are copied rather than derived.

### Impact

Any future persistence or audit feature would record attacker-controlled identity. Combined
with API-15, there is currently no ownership check on results because results are not stored.

### Suggested fix

Derive `userId` from `@AuthenticationPrincipal` in the controller and stop accepting it in
`SimulationInput`. Generate `simulationInputId` server-side or remove it until persistence
exists.

---

## API-15 — Runs are not persisted

**Severity:** Medium (product limitation)
**Affected:** `sim/forge/anvil/ForgeSimulationEngine.java`,
`sim/forge/controller/AssembleController.java`
**Evidence:** Source

`ForgeSimulationEngine` assigns a random `id`, `startedAt`, and `completedAt`, and returns the
result directly. No repository or entity stores it, and there is no `GET` endpoint for a run id.
The UI correctly describes the id as generated but not retrievable.

### Suggested fix

Either remove the id/status fields from the response until persistence exists, or add a
`SimulationRun` entity, a repository, owner-scoped reads, and a retention policy. Decide whether
the schedule rows are stored or recomputed.

---

## API-16 — All login failures are reported as 401

**Severity:** Low
**Affected:** `sentinel/smithy/auth/AuthServiceImpl.java`
**Evidence:** Source

```java
try {
    authenticationManager.authenticate(...);
} catch (Exception e) {
    throw new BadCredentialsException("Invalid email or password");
}
```

The catch covers every exception, including database outages and misconfiguration. Operators see
a credential failure while the real cause is hidden, and the UI cannot distinguish a transient
outage from a wrong password.

### Suggested fix

Catch `AuthenticationException` only (or rethrow unexpected exceptions), log the original cause
server-side, and return `503` for infrastructure failures.

---

## API-17 — `analyze` ignores its input and resolves ties by order

**Severity:** Low
**Affected:** `sim/oracle/OracleService.java` (`analyzeStrategyEffectiveness`)
**Evidence:** Runtime + Source

`POST /api/oracle/strategy/analyze` accepts an `input` object, but the method does not use it
when computing `projectedInterest`. For a single debt at 18%, both strategies returned
`18000.0`, and `recommendedStrategy` was `SNOWBALL` because it is the first entry in the request
array. `/recommend` returns `AVALANCHE` for the same debt, so the two endpoints disagree.

### Suggested fix

Use the supplied input in the projection (or remove it from the DTO), and define a deterministic
tie-break that matches `/recommend`. Add a test that both endpoints agree for one debt and for
equal-interest debts.

---

## Verified hardening gaps (not counted as runtime defects)

These were confirmed by source inspection and are tracked for Phase 2 rather than as defects in
this release:

- **No bean validation.** `TomeRequest`, `RegisterRequest`, `ScenarioContext`, and
  `SimulationInput` have no `@Valid`/constraint annotations. `termMonths: 0`, negative principal,
  and `minimumPayment: 0` all return **200** (runtime-verified).
- **No password policy or rate limiting.** Registration accepts any non-null string; login has no
  lockout or throttling.
- **No roles or authorization model.** Every authenticated user is authorized for every
  endpoint; scenarios and results have no ownership checks.
- **No schema migrations.** `spring.jpa.hibernate.ddl-auto=update` mutates the schema at startup,
  and SQL logging is enabled.
- **Money is stored as `float`.** Rounding is visible in results (`26.17086`) and will not be
  acceptable for ledger-grade values.
- **No account update endpoint.** A tome can be created, listed, and deleted, but not edited.
- **No pagination.** Tome and scenario lists return everything.
- **No OpenAPI description.** The contract is maintained by hand.
- **Token stored in `localStorage`.** Any XSS can read it; there is no refresh token or
  server-side revocation, so logout is client-only.
- **CORS is hardcoded to `http://localhost:3000`.** There is no environment-specific origin
  configuration.
- **Local database credentials are stored in `src/main/resources/application.properties`.** The
  file is ignored by Git and was not committed, but it is the only configuration source; use
  environment variables or an external secret store before any shared deployment.
- **JWT lifetime is ten days** (`864000000` ms) with no refresh/rotation story.

## Test artifacts

The runtime probes used a synthetic account on `example.com` with a generated password that is
not recorded here. Scenarios created during the probes were deleted. The synthetic user remains
in the local development database; remove it before any shared environment is used.

## Reproduction environment

- API: local Spring Boot instance on `http://localhost:8080`
- UI: local Rsbuild dev server on `http://localhost:3000`
- Database: local PostgreSQL instance configured through the ignored
  `application.properties`
- All requests used a bearer token from `POST /api/auth/login`; placeholders such as `<JWT>`,
  `<TOME_ID>`, `<SCENARIO_ID>`, and `<SIMULATION_ID>` are used in examples.
