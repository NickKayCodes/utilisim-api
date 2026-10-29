# UtiliSim API Contract — Backend Source of Truth

**Status:** Verified against the running Spring Boot API on 2026-10-28.
**Audience:** backend developers, SDET, and future maintainers.
**Scope:** internal implementation contract for the API. Frontend consumption details live in
[`../utilisim-ui/API_CONTRACT.md`](../utilisim-ui/API_CONTRACT.md).

> **Documentation governance:** every example in this file uses synthetic placeholders. Never
> commit real credentials, tokens, email addresses, database names, connection strings, or
> machine-specific paths. When documenting configuration, use `<DATABASE_URL>`,
> `<DATABASE_USERNAME>`, `<DATABASE_PASSWORD>`, and `<JWT_SECRET>`.

---

## 1. Runtime and transport

| Item | Value |
|---|---|
| Base URL (development) | `http://localhost:8080` |
| Auth header | `Authorization: Bearer <JWT>` |
| Token subject | User email |
| Token lifetime | ~10 days |
| Content type | `application/json`; `204` responses have no body |
| Date format | ISO-8601 with offset, e.g. `2026-01-15T12:00:00Z` |
| CORS | `http://localhost:3000` only, credentials allowed, all methods/headers |
| Persistence | PostgreSQL; connection settings are local and untracked |
| Schema management | Hibernate `ddl-auto=update`; no migration tool yet |

`POST /api/auth/login` and `POST /api/auth/register` are public. Every other endpoint requires a
bearer token. Because Spring Security runs before routing, an unknown path **without** a token
returns `401`, not `404`. With a valid token, an unknown path returns `404` from
`ApiExceptionHandler`.

---

## 2. Authentication model

### Login flow

1. `AuthController` receives `LoginRequest`.
2. `AuthServiceImpl` delegates to `AuthenticationManager`.
3. `AppUserDetailsService` loads the user by email.
4. Passwords are verified with BCrypt.
5. `JwtService` issues an HS256 token whose `sub` is the user's email.
6. The response contains `id`, `username`, `email`, and `token`.

### Request authentication

`JwtAuthenticationFilter` reads the bearer token, extracts the email, loads the user, and places
the authenticated principal in the `SecurityContext`. Controllers obtain the current user with
`@AuthenticationPrincipal AppUser user`.

### Registration

Registration hashes the password, persists the user, and returns **no token**. Clients must call
login after a successful registration.

### Authorization

There are **no roles or permissions** today. `AppUser.getAuthorities()` returns an empty list, and
every authenticated user can call every protected endpoint. Do not treat any endpoint as
role-restricted.

### Token lifecycle

- There is no refresh flow and no server-side session.
- Logout is a client-side token discard.
- `JwtService` generates a random signing key at startup. Restarting the API invalidates every
  existing token.
- **Invalid tokens are not rejected cleanly.** `JwtAuthenticationFilter` calls
  `JwtService.extractEmail` without a `try`/`catch`, so a malformed, expired, or wrong-signature
  token throws out of the filter. The request returns **`500`** with Spring Boot's default error
  body (including a `trace` stack trace) instead of `401`. Only a *missing* token reaches the
  `401` entry point. See §3 and §8.
- A production implementation must move the key to `<JWT_SECRET>`, rotate it deliberately, and
  translate token exceptions into `401`.

---

## 3. Error handling model

Handled errors return a JSON object with exactly `error` and `message`:

```json
{ "error": "Bad Request", "message": "Email already in use" }
```

| Status | `error` | Typical cause |
|---|---|---|
| 400 | `Bad Request` | Duplicate email, invalid input, malformed JSON |
| 401 | `Unauthorized` | Missing token, or wrong login credentials |
| 403 | `Forbidden` | Authenticated but denied (rare; no role rules exist) |
| 404 | `Not Found` | Unknown route with a valid token, or a missing scenario |
| 500 | `Internal Server Error` | Unhandled exception, including every invalid bearer token |

### Dispatcher behaviour

`SecurityConfig` permits the container's `/error` dispatcher and `/error` itself. Without that,
Spring Security rewrites failures to an empty `403` and clients lose the error body.

### Exceptions that bypass `ApiExceptionHandler`

`ApiExceptionHandler` covers exceptions thrown by controllers and services. Exceptions thrown
inside the security filter chain never reach it. Today that means:

- A malformed, expired, or wrongly signed bearer token returns **`500`** with Spring Boot's
  default body: `timestamp`, `status`, `error`, and a full `trace`. It is not the
  `{error, message}` shape and it leaks a stack trace.
- A missing token returns `401` `{"error":"Unauthorized","message":"Authentication required"}`.
- An unknown route without a token returns the same `401`; with a valid token it returns `404`
  `{"error":"Not Found","message":"No endpoint matches ..."}`.

Clients must treat a `500` as a possible authentication failure. The UI only clears its session
on `401`, so a stale token can leave the client signed in while every request fails.

### Inconsistent 404/500 behaviour

- `GET/PUT/DELETE /api/archive/scenarios/{id}` return **404 with an empty body** when the id does
  not exist.
- `DELETE /api/tomes/{id}` returns **500** with
  `{"error":"Internal Server Error","message":"Not found"}` for a missing id, and **500** with
  `"Forbidden"` for another user's id. `TomeService` throws a bare `RuntimeException`; this is
  tracked as technical debt.
- The correct client behaviour is to treat a scenario `404` as "not found" without parsing the
  body, and to treat a tome-delete `5xx` as "could not delete".

---

## 4. DTO reference

DTOs are Java records unless noted. Field names are the JSON names.

### Auth

| DTO | Fields |
|---|---|
| `LoginRequest` | `email`, `password` |
| `LoginResponse` | `id`, `username`, `email`, `token` |
| `RegisterRequest` | `username`, `password`, `email` |
| `RegisterResponse` | `id`, `username`, `email` |

### Accounts ("tomes")

| DTO | Fields |
|---|---|
| `TomeRequest` | `name`, `currentBalance`, `apr` (nullable), `minimumDebtPayment`, `creditLimit`, `type` |
| `TomeResponse` | `id`, `userId`, `name`, `currentBalance`, `apr`, `estimatedApr`, `minimumDebtPayment`, `creditLimit`, `type`, `createdAt`, `updatedAt` |

### Scenarios

`ScenarioContext` (JPA entity, also used as the request/response body):
`id`, `userId`, `name`, `strategy`, `extraPayment`, `lumpSumPayment`, `createdAt`, `updatedAt`.

### Simulation

| DTO | Fields |
|---|---|
| `SimulationInput` | `id`, `userId`, `type`, `principal`, `interestRate`, `termMonths`, `minimumPayment`, `scenarioId`, `strategy`, `extraPayment`, `lumpSumPayment`, timestamps |
| `SimulationResult` | `id`, `simulationInputId`, `userId`, `status`, `startedAt`, `completedAt`, `totalInterestPaid`, `totalPrincipalPaid`, `monthsToPayoff`, `payoffDate`, `savingsComparedToBaseline`, `schedule`, `archivedAt`, `ledgerEntryId` |
| `AmortizationEntry` | `monthIndex`, `date`, `payment`, `principalPaid`, `interestPaid`, `remainingBalance` |

### Strategy

| DTO | Fields |
|---|---|
| `DebtSnapshot` | `tomeId`, `accountName`, `currentBalance`, `apr`, `estimatedApr`, `minimumPayment`, `interestRate` |
| `StrategyInput` | `strategyType`, `customDebtOrder`, `extraPayment`, `lumpSumPayment` |
| `StrategyOutput` | `strategyName`, `orderedDebts`, `description` |
| `StrategyComparison` | `strategyName`, `projectedInterest`, `debtCount`, `description` |
| `StrategyAnalysis` | `comparisons`, `recommendedStrategy`, `analysisNotes` |

Request wrappers:

- `StrategyOrderRequest`: `debts`, `input`
- `StrategyAnalysisRequest`: `debts`, `strategies`, `input`

---

## 5. Endpoint definitions

### 5.1 Auth

#### `POST /api/auth/login`

Public.

```json
{ "email": "user@example.com", "password": "<PASSWORD>" }
```

**200**

```json
{
  "id": "<USER_ID>",
  "username": "user@example.com",
  "email": "user@example.com",
  "token": "<JWT>"
}
```

- `username` is the email in every response. The stored username is not exposed.
- Wrong password or unknown email → **401** `{"error":"Unauthorized","message":"Invalid email or password"}`.
- No roles or permissions are returned.

#### `POST /api/auth/register`

Public.

```json
{ "username": "example-user", "password": "<PASSWORD>", "email": "user@example.com" }
```

**200**

```json
{
  "id": "<USER_ID>",
  "username": "user@example.com",
  "email": "user@example.com"
}
```

- No token is returned.
- Duplicate email → **400** `{"error":"Bad Request","message":"Email already in use"}`.
- The request username is stored, but the response echoes the email.
- There is no password-strength validation; clients must validate before submitting.

---

### 5.2 Scenarios (archive)

Backed by `scenario_context`. Scenarios are **not user-scoped**: the server never sets `userId`
from the token, and `GET` returns every row. A client can still send `userId` and it will be
stored verbatim, but it has no effect on visibility.

#### `GET /api/archive/scenarios`

**200**

```json
[
  {
    "id": "<SCENARIO_ID>",
    "userId": null,
    "name": "Example scenario",
    "strategy": "SNOWBALL",
    "extraPayment": 25.0,
    "lumpSumPayment": 100.0,
    "createdAt": "2026-01-15T12:00:00Z",
    "updatedAt": "2026-01-15T12:00:00Z"
  }
]
```

Returns `[]` when empty.

#### `POST /api/archive/scenarios`

```json
{ "name": "Example scenario", "strategy": "SNOWBALL", "extraPayment": 25, "lumpSumPayment": 100 }
```

**200** — the created row, same shape as the list element.

- `id`, `createdAt`, and `updatedAt` are server-controlled. Omit `id`; sending an existing id
  performs an upsert and should be avoided. `userId` is **not** set by the server: send it and it
  is stored as-is, omit it and it stays `null`. Neither affects visibility.
- `extraPayment` and `lumpSumPayment` are primitives. Omitting them stores `0`; an explicit
  `null` is rejected with **400** and the generic `{"message":"Malformed JSON request body"}`
  body, because the codebase does not enable null-to-primitive coercion. Send `0` instead.

#### `GET /api/archive/scenarios/{id}`

**200** with the scenario, or **404 with an empty body**.

#### `PUT /api/archive/scenarios/{id}`

The server copies `name`, `strategy`, `extraPayment`, and `lumpSumPayment`, then refreshes
`updatedAt`. Other body fields are ignored.

```json
{ "name": "Renamed scenario", "strategy": "AVALANCHE", "extraPayment": 50, "lumpSumPayment": 0 }
```

**200** with the updated row, or **404 with an empty body**.

#### `DELETE /api/archive/scenarios/{id}`

**204** with no body, or **404 with an empty body**.

---

### 5.3 Accounts ("tomes")

Backed by `tome_ledger` and scoped to the authenticated user.

#### `GET /api/tomes`

**200** — array of `TomeResponse`; `[]` when the user has none.

#### `POST /api/tomes`

```json
{
  "name": "Example Card",
  "currentBalance": 1200.5,
  "apr": 19.99,
  "minimumDebtPayment": 35,
  "creditLimit": 5000,
  "type": "CREDIT_CARD"
}
```

**200**

```json
{
  "id": "<TOME_ID>",
  "userId": "<USER_ID>",
  "name": "Example Card",
  "currentBalance": 1200.5,
  "apr": 19.99,
  "estimatedApr": 19.99,
  "minimumDebtPayment": 35.0,
  "creditLimit": 5000.0,
  "type": "CREDIT_CARD",
  "createdAt": "2026-01-15T12:00:00Z",
  "updatedAt": "2026-01-15T12:00:00Z"
}
```

- **`apr` must be numeric today.** The request DTO allows `null`, but `Tome.apr` is a primitive
  `float`, and `TomeService.createTome` unboxes `request.apr()` while building the entity.
  Omitting `apr` or sending `null` throws `NullPointerException` before the save and returns
  **`500`**. The advertised `estimatedApr` fallback is unreachable until the entity or service is
  fixed. See §8 #16.
- `TomeResponse.apr` is a boxed `Float`, but it is always populated from the primitive entity
  field. A successful create or list never returns `null` for `apr`.
- If the null path were fixed as written, `TomeService.calculateEstimatedApr` returns `0.16`,
  which is stored as **0.16 percent**, not 16 percent. Treat the estimate as unimplemented, not as
  a usable fallback.
- When `apr` is supplied, `estimatedApr` is set to the same value. There is no separate estimate.
- `currentBalance`, `minimumDebtPayment`, and `creditLimit` are primitives. Always send numbers.
- `type` is a free-form string; there is no enum validation. Known UI values: `CREDIT_CARD`,
  `STUDENT_LOAN`, `AUTO_LOAN`, `MORTGAGE`, `PERSONAL_LOAN`, `LINE_OF_CREDIT`, `OTHER`.
- `apr` and `estimatedApr` are **percentages** (`19.99` = 19.99%). `SimulationInput.interestRate`
  is a **decimal** (`0.1999` = 19.99%). Convert at the boundary.

#### `DELETE /api/tomes/{id}`

**204** with no body on success.

- Missing id → **500** `{"error":"Internal Server Error","message":"Not found"}`.
- Foreign id → **500** `{"error":"Internal Server Error","message":"Forbidden"}`.
- There is no `PUT /api/tomes/{id}`; editing an account is not supported yet.

---

### 5.4 Simulation

#### `POST /api/assemble/run`

Requires a token.

```json
{
  "principal": 1000,
  "interestRate": 0.2,
  "termMonths": 12,
  "minimumPayment": 50,
  "extraPayment": 0,
  "lumpSumPayment": 0,
  "strategy": "SNOWBALL"
}
```

| Field | Type | Notes |
|---|---|---|
| `principal` | float | Starting balance |
| `interestRate` | float | Annual decimal; divided by 12 internally |
| `termMonths` | int | Requested term. The loop also stops at `termMonths * 2`; this is not a guarantee of payoff |
| `minimumPayment` | float | Monthly minimum |
| `extraPayment` | float | Recurring extra per month |
| `lumpSumPayment` | float | One-time injection applied before month 1 |
| `strategy` | string | Copied into the local scenario context; not returned and does not change single-balance math |
| `id`, `userId` | string | Echoed into `simulationInputId` and `userId` in the result |
| `type`, `scenarioId`, timestamps | — | Accepted but ignored |

**200**

```json
{
  "id": "<SIMULATION_ID>",
  "simulationInputId": null,
  "userId": null,
  "status": "COMPLETED",
  "startedAt": "2026-01-15T12:00:00Z",
  "completedAt": "2026-01-15T12:00:01Z",
  "totalInterestPaid": 226.17,
  "totalPrincipalPaid": 973.83,
  "monthsToPayoff": 24,
  "payoffDate": "2028-01-15T12:00:00Z",
  "savingsComparedToBaseline": 0.0,
  "schedule": [
    {
      "monthIndex": 1,
      "date": "2026-02-15T12:00:00Z",
      "payment": 50.0,
      "principalPaid": 33.33,
      "interestPaid": 16.67,
      "remainingBalance": 966.67
    }
  ],
  "archivedAt": null,
  "ledgerEntryId": null
}
```

- `status` enum: `RUNNING`, `COMPLETED`, `FAILED`. A normal run always ends `COMPLETED`, even if
  money is still owed.
- `monthsToPayoff` is exactly `schedule.length`. The loop runs while `balance > 0` **and**
  `month < termMonths * 2`, so the schedule is capped at twice the requested term.
- If the payment does not cover monthly interest, or the term is short, the schedule stops at the
  cap with `remainingBalance > 0` and no unpaid/overdue signal. The example above is intentionally
  capped: `termMonths` is 12, the result has 24 rows, and 26.17 is still outstanding.
- `payoffDate` is exactly the `date` of the last schedule entry (or `now` for an empty schedule).
  It is not computed separately and cannot differ from the final row.
- `totalPrincipalPaid` + `totalInterestPaid` equals the sum of `payment`, not necessarily
  `principal`; an underfunded run can leave a balance.
- `savingsComparedToBaseline` is the interest on a baseline schedule (extra = 0, lump sum = 0)
  minus the interest on this schedule. It is `0` when both scenarios match.
- `userId` and `simulationInputId` are copies of client-supplied input fields. They are not
  derived from the bearer token. Do not trust them for authorization.
- The result is **not persisted**. There is no endpoint to fetch a past run.

---

### 5.5 Strategy

All strategy endpoints require a token.

#### `POST /api/oracle/strategy/order`

```json
{
  "debts": [
    {
      "tomeId": "<TOME_ID_1>",
      "accountName": "Example Card A",
      "currentBalance": 1000,
      "apr": 18.0,
      "estimatedApr": 18.0,
      "minimumPayment": 50,
      "interestRate": 18.0
    },
    {
      "tomeId": "<TOME_ID_2>",
      "accountName": "Example Card B",
      "currentBalance": 2500,
      "apr": 24.0,
      "estimatedApr": 24.0,
      "minimumPayment": 75,
      "interestRate": 24.0
    }
  ],
  "input": {
    "strategyType": "SNOWBALL",
    "customDebtOrder": [],
    "extraPayment": 0,
    "lumpSumPayment": 0
  }
}
```

**200**

```json
{
  "strategyName": "SNOWBALL",
  "orderedDebts": [
    {
      "tomeId": "<TOME_ID_1>",
      "accountName": "Example Card A",
      "currentBalance": 1000,
      "apr": 18.0,
      "estimatedApr": 18.0,
      "minimumPayment": 50,
      "interestRate": 18.0
    }
  ],
  "description": "Snowball: Pay off smallest balance first for psychological momentum"
}
```

- `input.strategyType` is required; missing it → **400** `input.strategyType is required`.
- `debts` is optional; missing or null is treated as `[]`.
- `DebtSnapshot` fields are primitives. Send the full shape. `apr` and `estimatedApr` are
  percentages here; `interestRate` is not used by the ordering logic.
- Ordering keys: snowball = smallest `currentBalance`; avalanche = highest `apr` (falling back to
  `estimatedApr` when `apr <= 0`); hybrid = avalanche bands with snowball inside a band; custom =
  `customDebtOrder` by `tomeId`, with unlisted debts appended. `MINIMUM_ONLY` preserves input
  order.

#### `POST /api/oracle/strategy/recommend`

Body is a bare array of `DebtSnapshot`.

```json
[
  {
    "tomeId": "<TOME_ID_1>",
    "accountName": "Example Card A",
    "currentBalance": 1000,
    "apr": 18.0,
    "estimatedApr": 18.0,
    "minimumPayment": 50,
    "interestRate": 18.0
  }
]
```

**200** — a bare JSON string such as `"AVALANCHE"` or `"MINIMUM_ONLY"`. An empty array returns
`"MINIMUM_ONLY"`.

- `DebtSnapshot.apr` and `estimatedApr` are percentages. The recommender compares the maximum
  against `0.15`, not `15`, so **any account above 0.15% APR returns `"AVALANCHE"`**. This is a
  unit bug, not a deliberate threshold. The remaining branches are only reachable for near-zero
  rates.

#### `POST /api/oracle/strategy/analyze`

```json
{
  "debts": [
    {
      "tomeId": "<TOME_ID_1>",
      "accountName": "Example Card A",
      "currentBalance": 1000,
      "apr": 18.0,
      "estimatedApr": 18.0,
      "minimumPayment": 50,
      "interestRate": 18.0
    }
  ],
  "strategies": ["SNOWBALL", "AVALANCHE"],
  "input": { "extraPayment": 0, "lumpSumPayment": 0 }
}
```

**200**

```json
{
  "comparisons": [
    {
      "strategyName": "SNOWBALL",
      "projectedInterest": 180.0,
      "debtCount": 1,
      "description": "Snowball: Pay off smallest balance first for psychological momentum"
    }
  ],
  "recommendedStrategy": "AVALANCHE",
  "analysisNotes": null
}
```

- `strategies` must be non-empty; otherwise **400**.
- `input` is required; only `extraPayment` and `lumpSumPayment` are read.
- Unknown strategy names fail inside `StrategyType.valueOf` → **400**.
- The projection is not strategy-sensitive. `projectedInterest` is computed as
  `sum(currentBalance * apr)` over the debt set, which is order-independent, so **every strategy
  returns the same number for the same debts**. Ordering changes only the sequence of
  `orderedDebts`. Do not present the output as guaranteed savings.

#### `GET /api/oracle/strategy/is-deterministic/{strategyType}`

**200** — bare `true` or `false`. An unknown strategy returns `false`, not an error.

#### `POST /api/oracle/strategy/calculate-total-interest`

Body: bare array of `DebtSnapshot`. **200** — bare number equal to
`sum(currentBalance * apr)` (using `estimatedApr` when `apr <= 0`). The result does not depend on
array order and is **not** an amortization projection.

---

## 6. Persistence and data model

The application uses PostgreSQL. Table creation and updates are currently handled by Hibernate
(`ddl-auto=update`); there are no migrations.

| Table | Entity | Key fields | Notes |
|---|---|---|---|
| `utilisim_user` | `AppUser` | `id`, `username`, `email`, `password` | Password is BCrypt-hashed; `getUsername()` returns email; no role column |
| `tome_ledger` | `Tome` | `id`, `user_id`, `name`, `current_balance`, `apr`, `estimated_apr`, `minimum_debt_payment`, `credit_limit`, `type`, timestamps | Scoped by `user_id`; `apr` is a primitive `float`, so the null-APR request fails before save |
| `scenario_context` | `ScenarioContext` | `id`, `user_id`, `name`, `strategy`, `extra_payment`, `lump_sum_payment`, timestamps | The server never sets `user_id`; a client-supplied value is stored. All rows are global |
| `strategy_config` | `StrategyConfig` | `id`, `user_id`, `scenario_id`, `strategy_type`, `custom_debt_order`, payments, description | Persistence layer exists but no controller calls it |
| `simulation_run` | `Simulation` | `int id`, `user_id`, `type`, `status`, `input_id`, `result_id`, timestamps | Entity exists; no write path |

Relationships are logical string references rather than JPA associations or enforced foreign
keys. `Tome.userId` points at `utilisim_user.id`; `ScenarioContext.userId` is written only when a
client supplies it; `Simulation` references input/result ids that are not persisted by the
current run endpoint.

---

## 7. Backend conventions

1. **Layering:** controllers live in `gateway`/`gate`, business logic in services, persistence in
   repositories. Do not put business rules in controllers.
2. **DTOs:** return records, never entities. `TomeMapper` is the reference pattern.
3. **Request bodies:** one `@RequestBody` per handler. Wrap multiple inputs in a record.
4. **Current user:** derive identity from `@AuthenticationPrincipal AppUser user`; never trust a
   `userId` from the request body.
5. **Ownership:** check ownership in the service layer.
6. **Errors:** throw typed exceptions and map them in `ApiExceptionHandler`. Use
   `IllegalArgumentException` for 400 and `BadCredentialsException` for 401. Add a handler when a
   new status is required.
7. **Status codes:** use `ResponseEntity` deliberately. `204` for a successful delete;
   `ResponseEntity.notFound().build()` for a missing resource.
8. **Rates:** document and normalise percent vs decimal at the boundary. `Tome` uses percentages;
   `SimulationInput.interestRate` uses a decimal.
9. **Timestamps:** use `OffsetDateTime`.
10. **Tests:** add a unit test for strategy math and ownership/validation rules. Use
    `@SpringBootTest` only when the context is required.
11. **Contract sync:** update this file, the UI contract, and `utilisim-ui/src/types/api.ts` in the
    same change as any DTO or status-code change.

### Endpoint change checklist

- [ ] Entity/repository if persistence changes.
- [ ] Service method with ownership and validation.
- [ ] Request/response records; no entities on the wire.
- [ ] Controller under `/api/...`.
- [ ] Error mapping for any new exception type.
- [ ] Backend contract, UI contract, and UI types updated.
- [ ] Happy-path and failure-path tests.

---

## 8. Known technical debt

| # | Issue | Impact | Phase-2 direction |
|---|---|---|---|
| 1 | Simulation results are not persisted | No run history; no result lookup | Persist runs and add a run-detail endpoint |
| 2 | Scenarios are global | Users see each other's scenarios | Set `userId` from the principal and filter every scenario operation |
| 3 | No account update endpoint | Users delete and re-create accounts | Add `PUT /api/tomes/{id}` |
| 4 | Tome delete returns 500 for missing/foreign ids | Incorrect status codes | Replace `RuntimeException` with typed 404/403 exceptions |
| 5 | Analysis is not strategy-sensitive | Identical projections across strategies | Simulate each ordering against a real multi-debt schedule |
| 6 | No roles or permissions | Any authenticated user can do everything | Add roles, authorities, and URL/method rules |
| 7 | Random JWT key per startup | Tokens die on every restart | Load `<JWT_SECRET>` from environment/config |
| 8 | `ddl-auto=update`, no migrations | Schema drift and unsafe deploys | Add Flyway or Liquibase; use `validate` |
| 9 | No bean validation on DTOs | Bad payloads reach services; primitives default to 0 | Add `@Valid` and constraint annotations; map validation errors |
| 10 | No pagination/filtering | Unbounded list endpoints | Add paged endpoints for tomes and scenarios |
| 11 | No OpenAPI definition | Contract maintenance is manual | Add `springdoc-openapi` while keeping behavioural notes here |
| 12 | `float` money and mixed rate units | Rounding and unit bugs | Use `BigDecimal`/`NUMERIC` and one documented unit per boundary |
| 13 | `StrategyConfig` and `StrategyEngineIntegration` are unwired | Dead code obscures the real flow | Expose configuration endpoints or remove the unused layer |
| 14 | `GET /api/sentinel/users/search/by-email` is unsafe | Requires a GET body and returns a password hash | Remove it or replace it with a safe, DTO-based lookup |
| 15 | Broad `Exception` handler returns 500 for control-flow errors | Wrong statuses and noisy logs | Introduce domain exceptions and specific handlers |
| 16 | Blank/omitted `apr` on `POST /api/tomes` → `500` NPE | The UI offers a blank APR and the contract advertises an estimate that never runs | Make `Tome.apr` nullable or validate the request; wire the estimate correctly |
| 17 | Invalid/expired bearer token → `500` with a stack trace | Leaks internals; clients cannot detect an expired session; a stale token sticks | Catch `JwtException` in the filter and return `401`; disable `trace` in error responses |
| 18 | Simulation stops at `termMonths * 2` with a balance still owed | A "completed" result can be an unpaid schedule | Loop until payoff or return an explicit unpaid/insufficient-payment status |
| 19 | `SimulationResult.userId` and `simulationInputId` echo client input | Untrusted ids look like server-derived identity | Derive identity from the principal and stop echoing request ids |
| 20 | `calculateEstimatedApr` returns `0.16`, not `16` | A future null-APR fix would store a near-zero rate | Remove the estimate or return a percentage and test it |
| 21 | Recommendation compares percentage APR against `0.15` | Nearly every account returns `AVALANCHE`; the heuristic is inert | Use `15.0` or convert to decimals before comparing; add tests |

---

## 9. Phase-2 roadmap

1. Fix status-code correctness: tome deletion, scenario misses, and invalid-token `500`s.
2. Fix the blank-APR `500` and either implement or remove `estimatedApr`.
3. Remove or secure the user-search endpoint.
4. Make scenarios user-scoped and add account updates.
5. Persist simulation runs and expose history/detail endpoints.
6. Make projections strategy-sensitive before presenting savings, and stop hiding the term cap.
7. Move secrets and datasource settings to environment variables.
8. Add Flyway/Liquibase, bean validation, pagination, OpenAPI, and error-response scrubbing.
9. Expand unit and integration coverage, including Testcontainers.

---

## 10. Endpoints outside this contract

Only the paths in §11 exist. Any other `/api/...` path is **not implemented**, even if an old
design note, issue, or external document mentions it.

- With a valid token, an unknown path returns `404`
  `{"error":"Not Found","message":"No endpoint matches ..."}`.
- Without a token, `401` wins because Spring Security runs before routing.

Do not add a path to a client until it appears in §11 and is backed by a controller. This
document intentionally does not keep a table of historical or rejected paths; describing them
invites clients to build against them.

---

## 11. Endpoint index and enums

| Method | Path | Auth | Body | Success |
|---|---|---|---|---|
| POST | `/api/auth/login` | no | `LoginRequest` | 200 `LoginResponse` |
| POST | `/api/auth/register` | no | `RegisterRequest` | 200 `RegisterResponse` |
| GET | `/api/archive/scenarios` | yes | — | 200 `ScenarioContext[]` |
| POST | `/api/archive/scenarios` | yes | `ScenarioContext` | 200 `ScenarioContext` |
| GET | `/api/archive/scenarios/{id}` | yes | — | 200 / 404 empty |
| PUT | `/api/archive/scenarios/{id}` | yes | `ScenarioContext` | 200 / 404 empty |
| DELETE | `/api/archive/scenarios/{id}` | yes | — | 204 / 404 empty |
| GET | `/api/tomes` | yes | — | 200 `TomeResponse[]` |
| POST | `/api/tomes` | yes | `TomeRequest` | 200 `TomeResponse` |
| DELETE | `/api/tomes/{id}` | yes | — | 204 / 500 JSON |
| POST | `/api/assemble/run` | yes | `SimulationInput` | 200 `SimulationResult` |
| POST | `/api/oracle/strategy/order` | yes | `StrategyOrderRequest` | 200 `StrategyOutput` |
| POST | `/api/oracle/strategy/recommend` | yes | `DebtSnapshot[]` | 200 string |
| POST | `/api/oracle/strategy/analyze` | yes | `StrategyAnalysisRequest` | 200 `StrategyAnalysis` |
| POST | `/api/oracle/strategy/calculate-total-interest` | yes | `DebtSnapshot[]` | 200 number |
| GET | `/api/oracle/strategy/is-deterministic/{strategyType}` | yes | — | 200 boolean |
| GET | `/api/sentinel/users/search/by-email` | yes | `AppUser` (GET body) | 200 `AppUser?` |

**Unsafe endpoint:** `GET /api/sentinel/users/search/by-email` is a legacy controller that reads a
request body on a GET, returns the `AppUser` entity (including the BCrypt password hash), and is
not used by the UI. Do not build on it; it is scheduled for removal (§8 #14).

Enums:

- `StrategyType`: `SNOWBALL`, `AVALANCHE`, `HYBRID`, `CUSTOM`, `MINIMUM_ONLY`
- `SimulationType`: `DEBT_PAYOFF`, `CASH_FLOW`, `SAVINGS_GROWTH`
- `SimulationStatus`: `RUNNING`, `COMPLETED`, `FAILED`

---

## 12. Contract governance

- This backend contract is owned by the API team and is the internal source of truth.
- The UI contract ([`../utilisim-ui/API_CONTRACT.md`](../utilisim-ui/API_CONTRACT.md)) covers only
  the subset the frontend consumes; it must not duplicate persistence or internal architecture.
- Any change to a controller, DTO, status code, or auth rule requires updating this file in the
  same change.
- Document current behaviour only. Do not keep tables of removed or hypothetical routes; §10 and
  §11 are the complete surface.
- Every example must use synthetic placeholders. Audit documentation changes for credentials,
  tokens, real emails, database names, and machine-specific paths before merge.
