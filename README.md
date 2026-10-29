# UtiliSim API

Spring Boot backend for **UtiliSim**, a debt-payoff simulator. This is a **phase-1**
application: the core flows work end to end, while several capabilities are intentionally thin or
missing. This document is the maintenance guide for backend developers, SDET, and future
maintainers.

The backend wire format is defined in [`API_CONTRACT.md`](API_CONTRACT.md). The frontend
consumption contract is [`../utilisim-ui/API_CONTRACT.md`](../utilisim-ui/API_CONTRACT.md). When
this README and the code disagree, the running application is the source of truth — update the
contract in the same change.

> **Documentation governance:** use placeholders only when documenting configuration:
> `<DATABASE_URL>`, `<DATABASE_USERNAME>`, `<DATABASE_PASSWORD>`, `<DATABASE_NAME>`, `<HOST>`,
> `<PORT>`, and `<JWT_SECRET>`. Never commit real credentials, tokens, email addresses, database
> names from a private environment, connection strings, or machine-specific paths.

---

## 1. Architecture

The API is a stateless Spring Boot service.

```
HTTP client
   │  JSON + Authorization: Bearer <JWT>
   ▼
JwtAuthenticationFilter ──► SecurityContext ──► @AuthenticationPrincipal AppUser
   ▼
Controller (gateway / gate)
   ▼
Service (business rules, ownership, orchestration)
   ▼
Repository (Spring Data JPA) ──► PostgreSQL
```

Key properties:

- **Stateless auth.** No HTTP session, no server-side token store. Every request carries a JWT.
- **Layered.** Controllers translate HTTP; services own business rules; repositories own
  persistence. Entities are never returned from controllers.
- **Synchronous.** Simulation and strategy calls run in the request thread and return a complete
  result; there is no queue or background worker.
- **Schema by Hibernate.** Entity changes become DDL at boot (`ddl-auto=update`). There is no
  migration tool yet.
- **Single service.** The API, the simulation engine, and the strategy engine are one deployable
  unit. The `sim/ratio` and `sim/chronos` packages are internal libraries, not HTTP surfaces.

### Request flows

**Authentication**

1. `POST /api/auth/login` → `AuthController` → `AuthServiceImpl.login`.
2. `AuthenticationManager` uses `AppUserDetailsService` (email lookup) and BCrypt.
3. `JwtService` issues an HS256 token whose `sub` is the user's email.
4. Later requests pass through `JwtAuthenticationFilter`, which loads the user and places it in
   the `SecurityContext`.
5. Controllers receive `AppUser` via `@AuthenticationPrincipal`.

**Simulation**

1. `POST /api/assemble/run` → `AssembleController`.
2. `ForgeSimulationEngine` asks `RatioService` for the month-by-month amortization.
3. `ChronosService` stamps dates and derives the payoff date.
4. `OracleService` compares the schedule against a minimum-payment baseline.
5. The assembled `SimulationResult` is returned **without being persisted**.

**Strategy**

1. `StrategyController` maps the request into `DebtSnapshot` / `StrategyInput`.
2. `StrategyService` orders debts (snowball = smallest balance, avalanche = highest APR, hybrid =
   APR bands with snowball inside a band, custom = caller-supplied order).
3. `OracleService` recommends a strategy or compares projected interest.

---

## 2. Setup

### Prerequisites

| Tool | Requirement |
|---|---|
| JDK | 21 (build target) |
| Maven | Wrapper included (`mvnw` / `mvnw.cmd`) |
| PostgreSQL | A supported release with DDL privileges for the application user |

### 1. Create the database

Create an empty database and note its name. Do not commit the real name or credentials.

```sql
CREATE DATABASE "<DATABASE_NAME>";
```

### 2. Create local configuration

`src/main/resources/application.properties` is gitignored because it holds credentials. Create it
locally with your own values:

```properties
spring.application.name=utilisim
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.url=<DATABASE_URL>
spring.datasource.username=<DATABASE_USERNAME>
spring.datasource.password=<DATABASE_PASSWORD>
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

`<DATABASE_URL>` is a JDBC URL for your own PostgreSQL instance, for example
`jdbc:postgresql://<HOST>:<PORT>/<DATABASE_NAME>`. Never paste a real URL into documentation,
issues, or commits.

`JwtService` currently generates a signing key at startup and does not read `<JWT_SECRET>`; see
[Known gaps](#11-known-gaps).

### 3. Verify the configuration

Start the application (see [Run](#4-run)) and confirm that Hibernate creates the tables. A
datasource error means the URL, user, password, or database is wrong; a DDL error usually means
the user lacks create/alter rights.

---

## 3. Build

From `utilisim-api/`:

```bash
# macOS / Linux / Git Bash
./mvnw clean package

# Windows
mvnw.cmd clean package
```

The artifact is `target/utilisim-0.0.1-SNAPSHOT.jar`.

### Tests

```bash
./mvnw test                              # full suite
./mvnw -Dtest=StrategyServiceTest test   # unit tests only, no Spring context or DB
```

- `StrategyServiceTest` is a pure unit test for ordering rules.
- `UtilisimApplicationTests` boots the full Spring context and therefore needs a reachable
  database and valid configuration.

Add a unit test for new strategy math and ownership/validation rules. Use `@SpringBootTest` only
when the context is genuinely required.

---

## 4. Run

```bash
# macOS / Linux / Git Bash
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The API listens on **`http://localhost:8080`**.

```bash
curl -i http://localhost:8080/api/archive/scenarios
# HTTP/1.1 401
# {"error":"Unauthorized","message":"Authentication required"}
```

Register a synthetic account, then log in:

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"example-user","email":"user@example.com","password":"<PASSWORD>"}'

curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"<PASSWORD>"}'
```

Registration returns **no token**; call login afterwards. Use the returned token as
`Authorization: Bearer <JWT>`.

To run the packaged jar:

```bash
java -jar target/utilisim-0.0.1-SNAPSHOT.jar
```

### Development notes

- Spring Boot DevTools is on the classpath; restarts are automatic during development.
- Restarting the API invalidates every issued token because the signing key is regenerated.
- The UI dev server proxies `/api` from `:3000` to `:8080`, so CORS is normally not involved.

---

## 5. Database requirements

| Requirement | Detail |
|---|---|
| Engine | PostgreSQL |
| Database | One empty database owned by the application user |
| Credentials | Supplied through local configuration; never committed |
| DDL rights | Required while `ddl-auto=update` is in use |
| Extensions | None |
| Migrations | None yet; Hibernate creates and alters tables |
| Connection pooling | Spring Boot's default HikariCP |

### Tables

| Table | Entity | Purpose |
|---|---|---|
| `utilisim_user` | `AppUser` | Accounts; BCrypt password, email as the login name |
| `tome_ledger` | `Tome` | Per-user debt accounts |
| `scenario_context` | `ScenarioContext` | Saved strategy presets; currently global |
| `strategy_config` | `StrategyConfig` | Persistence layer for strategy configuration; not wired to HTTP |
| `simulation_run` | `Simulation` | Simulation metadata entity; no write path today |

Relationships are logical id references, not JPA associations or enforced foreign keys.
`Tome.userId` points at `utilisim_user.id`; `ScenarioContext.userId` is never populated;
`Simulation` references input/result ids that the current run endpoint does not persist.

### Units and types

- Money and rates are `float`, not `BigDecimal`. Acceptable for phase 1; migrate before this
  becomes a ledger of record.
- `Tome.apr` and `Tome.estimatedApr` are **percentages** (`19.99` = 19.99%). `apr` is a
  primitive, so the API cannot persist a null APR; a blank APR currently fails with `500`.
- `SimulationInput.interestRate` is an **annual decimal** (`0.1999` = 19.99%).
- `DebtSnapshot.apr` and `DebtSnapshot.interestRate` are percentages as sent by the current UI.
  The strategy service compares rates only, so ordering is correct, but projections are
  unit-sensitive. Normalise at one boundary before adding real interest math.
- Timestamps are `OffsetDateTime`, serialised as ISO-8601 with offset.

---

## 6. Authentication model

### Registration

`POST /api/auth/register` hashes the password with BCrypt, persists the user, and returns
`{ id, username, email }`. It returns **no token**. Duplicate email → `400`
`{"error":"Bad Request","message":"Email already in use"}`. There is no password-strength
validation on the server; the UI enforces a minimum length.

### Login

`POST /api/auth/login` verifies the password through `AuthenticationManager` and returns
`{ id, username, email, token }`. `username` is the email in every response. The stored username
is not exposed.

### Tokens

| Property | Value |
|---|---|
| Algorithm | HS256 |
| Subject | User email |
| Lifetime | ~10 days |
| Refresh | None |
| Revocation | None; restarting the API invalidates all tokens |
| Storage | Client-side only |

`JwtAuthenticationFilter` reads `Authorization: Bearer <JWT>`, resolves the email, loads the user,
and populates the `SecurityContext`. A **missing** token produces `401`. A **malformed, expired, or
wrongly signed** token throws out of the filter and produces **`500`** with Spring Boot's default
error body and a stack trace, because `JwtService.extractEmail` is not wrapped in a `try`/`catch`.
Restarting the API therefore yields `500`s, not `401`s, for existing clients. See gap #17.

### Authorization

There are **no roles or permissions**. `AppUser.getAuthorities()` returns an empty list, and every
authenticated user can call every protected endpoint. Do not treat any endpoint as role-restricted.
Account ("tome") operations are scoped by `user_id`; scenarios are not.

### Public endpoints

Only `POST /api/auth/login` and `POST /api/auth/register`. Everything else requires a token.
Because Spring Security runs before routing, an unknown path **without** a token returns `401`,
not `404`.

### Logout

Client-side token discard only. There is no logout endpoint and no server-side session.

---

## 7. Error handling model

Handled errors return a JSON object with exactly `error` and `message`:

```json
{ "error": "Bad Request", "message": "Email already in use" }
```

| Status | `error` | Typical cause | Source |
|---|---|---|---|
| 400 | `Bad Request` | Duplicate email, invalid input, malformed JSON, bad strategy name | `IllegalArgumentException`, message converters |
| 401 | `Unauthorized` | Missing token, or wrong login credentials | Security entry point, `BadCredentialsException` |
| 403 | `Forbidden` | Authenticated but denied (rare; no role rules exist) | `SecurityConfig` access-denied handler |
| 404 | `Not Found` | Unknown route with a valid token, or a missing scenario | `NoResourceFoundException`, `ResponseEntity.notFound()` |
| 500 | `Internal Server Error` | Unhandled exception, including any invalid bearer token | `ApiExceptionHandler` or Spring's default error page |

### Dispatcher requirement

`SecurityConfig` permits the container's `/error` dispatcher and `/error` itself. Without that,
Spring Security rewrites failures to an empty `403` and clients lose the error body. If every
failure suddenly becomes a blank `403`, check this first.

### Inconsistent misses

- `GET`/`PUT`/`DELETE /api/archive/scenarios/{id}` return **404 with an empty body** when the id
  does not exist.
- `DELETE /api/tomes/{id}` returns **500** with `{"error":"Internal Server Error","message":"Not
  found"}` for a missing id, and **500** with `"Forbidden"` for another user's id. It throws a bare
  `RuntimeException`; this is tracked as technical debt.
- `POST /api/tomes` with a blank or omitted `apr` returns **`500`** with a `NullPointerException`
  message. `Tome.apr` is a primitive `float`, so the promised `estimatedApr` fallback never runs.
- An invalid bearer token returns **`500`** with `timestamp`, `status`, `error`, and a `trace`
  stack trace. It bypasses `ApiExceptionHandler` because it is thrown in the security filter
  chain.
- A broad `Exception` handler maps control-flow errors to `500` instead of their correct status.

### Adding a new status

Throw a typed exception and map it in `ApiExceptionHandler`. Use `IllegalArgumentException` for
400 and `BadCredentialsException` for 401. Do not add ad-hoc `ResponseEntity.status(...)` calls in
services.

---

## 8. Package structure

Package names are thematic ("sentinel", "ledger", "forge"). Use this map rather than guessing from
names:

```
src/main/java/com/krath/utilisim/
  UtilisimApplication.java        Spring Boot entry point

  sentinel/                       Identity and authentication
    foundation/                   AppUser entity
      scrolls/login/              LoginRequest, LoginResponse
      scrolls/register/           RegisterRequest, RegisterResponse
    warden/                       AuthController
      sec/                        SecurityConfig, CORSConfig, ApiExceptionHandler
        auth/filters/             JwtAuthenticationFilter
        auth/service/             JwtService, AppUserDetailsService
        auth/user/                UserPrincipal
    smithy/                       AuthService/AuthServiceImpl, UserService/UserServiceImpl
      auth/
      user/
    vault/                        UserRepository
    gateway/                      UserController (legacy; do not copy)

  ledger/                         Debt accounts ("tomes")
    gateway/                      TomeController
    leaf/create/                  TomeRequest, TomeResponse
    tome/                         Tome entity
    scribe/                       Scribe (JpaRepository for Tome)
    vault/                        TomeService, TomeMapper

  archive/                        Saved scenarios
    gate/                         ScenarioController
    scenario/                     ScenarioContext entity
    datastate/                    ScenarioRepository

  sim/                            Simulation domain
    forge/
      controller/                 AssembleController (POST /api/assemble/run)
      anvil/                      ForgeSimulationEngine
      hammer/                     SimulationInput, SimulationResult
      materials/                  Simulation, SimulationType, SimulationStatus, StrategyType
    ratio/                        Amortization math; internal only
      calculate/
    chronos/                      Timeline and payoff dates; internal only
      projection/
    oracle/                       Strategy ordering, recommendation, comparison
      gateway/                    StrategyController + request wrappers
      vault/                      StrategyConfig entity/repository/service (not wired to HTTP)
      integration/                StrategyEngineIntegration (not exposed)
```

Only the `gateway`/`gate`/`controller` packages are HTTP surfaces. Everything else is internal and
may be refactored without a contract change — unless a DTO on the wire is affected.

---

## 9. Development conventions

1. **Layering.** Controllers translate HTTP; services own business rules; repositories own
   persistence. Do not put business rules in controllers.
2. **DTOs on the wire.** Return records, never entities. `TomeMapper` is the reference pattern.
   `UserController` violates this — do not copy it.
3. **One `@RequestBody` per handler.** Spring does not support two. Wrap multiple inputs in a
   record, as `StrategyOrderRequest` and `StrategyAnalysisRequest` do.
4. **Current user from the principal.** Use `@AuthenticationPrincipal AppUser user` and
   `user.getId()`. Never trust a `userId` from the request body.
5. **Ownership in the service.** Check ownership there, and throw a typed exception.
6. **Status control.** Use `ResponseEntity` deliberately: `204` for a successful delete,
   `ResponseEntity.notFound().build()` for a missing resource.
7. **Rates.** Decide whether an endpoint accepts a percentage or a decimal, document it in
   `API_CONTRACT.md`, and convert once at the boundary. `Tome` uses percentages;
   `SimulationInput.interestRate` uses a decimal.
8. **Timestamps.** Use `OffsetDateTime`; keep serialisation ISO-8601 with offset.
9. **Validation.** Bean validation is on the classpath but not applied to DTOs. When adding it, use
   `@Valid` plus constraint annotations and map `MethodArgumentNotValidException`.
10. **Tests.** Add a unit test for strategy math and for ownership/validation rules. Use
    `@SpringBootTest` only when the context is required.
11. **Contract sync.** Any change to a controller, DTO, status code, or auth rule requires updating
    `API_CONTRACT.md`, the UI contract, and `utilisim-ui/src/types/api.ts` in the same change.
12. **Secrets.** Never commit `application.properties`, a real `<JWT_SECRET>`, or connection
    details. Configuration belongs in local files or environment variables.

### Endpoint change checklist

- [ ] Entity/repository if persistence changes.
- [ ] Service method with ownership and validation.
- [ ] Request/response records; no entities on the wire.
- [ ] Controller under `/api/...`.
- [ ] Error mapping for any new exception type.
- [ ] Backend contract, UI contract, and UI types updated.
- [ ] Happy-path and failure-path tests.
- [ ] Documentation uses placeholders only.

---

## 10. Expansion guidance

Phase 2 should grow the API without breaking the phase-1 client. Recommended order:

1. **Correctness first.** Return `404`/`403` from tome deletion, and use specific exceptions
   instead of a broad `Exception` handler.
2. **Secure identity.** Remove or rewrite
   `GET /api/sentinel/users/search/by-email`; it requires a GET body and returns a password hash.
   Move the JWT key to `<JWT_SECRET>`.
3. **Scope data.** Populate `ScenarioContext.userId` from the principal and filter every scenario
   operation by owner. Add `PUT /api/tomes/{id}` with an update DTO and ownership check.
4. **Persist runs.** Save `SimulationResult` and expose a run-detail endpoint plus a list
   endpoint. Add pagination from the start.
5. **Make analysis meaningful.** Project each strategy against a real multi-debt schedule before
   presenting savings.
6. **Schema discipline.** Add Flyway or Liquibase, baseline the current schema, then switch
   `ddl-auto` to `validate`.
7. **Input contracts.** Add bean validation, consistent rate units, and `BigDecimal`/`NUMERIC` for
   money.
8. **Observability and docs.** Add `springdoc-openapi` while keeping behavioural notes in
   `API_CONTRACT.md`; add structured logs and health checks.
9. **Test depth.** Add service unit tests and Testcontainers integration tests for the database
   paths.

### Adding an endpoint

1. Create request/response records next to the controller.
2. Add a service method that derives the user from the principal and checks ownership.
3. Add the controller method under `/api/...` and return a `ResponseEntity`.
4. Map any new exception in `ApiExceptionHandler`.
5. Add tests for the happy path and the main failure path.
6. Update both contracts and the UI types.
7. Document the endpoint with synthetic examples only.

### Adding a table or column

1. Update the entity.
2. Decide whether the change is safe under `ddl-auto=update` (phase 1) or needs a migration
   (phase 2+).
3. Update the data-model section of the contract.
4. Check whether the UI needs a new DTO field; update `src/types/api.ts` if so.

---

## 11. Known gaps

These are real constraints, not documentation errors.

| # | Gap | Impact | Phase-2 direction |
|---|---|---|---|
| 1 | Simulation results are never saved | No run history; the UI keeps one run in `sessionStorage` | Persist results and add lookup/history endpoints |
| 2 | Scenarios are global (`user_id` always null) | Every user sees and can delete every scenario | Set `userId` from the principal; filter all operations |
| 3 | No `PUT /api/tomes/{id}` | Users delete and re-create accounts to change data | Add an update DTO, service, and controller |
| 4 | `DELETE /api/tomes/{id}` returns 500 for missing/foreign ids | Wrong status codes; clients cannot distinguish cases | Throw typed 404/403 exceptions |
| 5 | Projections are not strategy-sensitive | Analysis shows identical interest per strategy | Simulate each ordering against a real multi-debt schedule |
| 6 | No roles or permissions | Any authenticated user can do everything | Add roles, authorities, and URL/method rules |
| 7 | Random JWT key per boot | All tokens die on restart | Read `<JWT_SECRET>` from environment/config and rotate deliberately |
| 8 | `ddl-auto=update`, no migrations | Schema drift and risky deploys | Add Flyway/Liquibase; use `validate` |
| 9 | No bean validation on DTOs | Bad payloads reach services; primitives default to 0 | Add `@Valid` and constraints; map validation errors |
| 10 | No pagination or filtering | Lists grow unbounded | Add paged endpoints for tomes and scenarios |
| 11 | No OpenAPI/Swagger | The contract is hand-maintained | Add `springdoc-openapi`; keep behaviour notes here |
| 12 | `float` money and mixed rate units | Rounding and unit bugs | `BigDecimal`/`NUMERIC`; one unit per boundary |
| 13 | `StrategyConfig` and `StrategyEngineIntegration` are unwired | Dead code obscures the real flow | Expose configuration endpoints or remove the layer |
| 14 | `GET /api/sentinel/users/search/by-email` is unsafe | Requires a GET body and returns a password hash | Remove it or replace it with a DTO-based lookup |
| 15 | Broad `Exception` handler returns 500 for control-flow errors | Wrong statuses and noisy logs | Introduce domain exceptions and specific handlers |
| 16 | Thin test coverage (15 tests: 14 strategy unit tests + 1 context test) | Regressions rely on manual smoke tests | Add service unit tests and Testcontainers integration tests |
| 17 | Invalid/expired token → `500` with a stack trace | Leaks internals; clients cannot detect an expired session and never clear it | Catch `JwtException` in `JwtAuthenticationFilter` and return `401`; disable `trace` |
| 18 | Blank/omitted `apr` → `500` NPE | The UI offers a blank APR and the contract advertises an estimate that never runs | Make `Tome.apr` nullable or validate the request; fix or remove `estimatedApr` |
| 19 | `calculateEstimatedApr` returns `0.16`, not `16` | A future null-APR fix would store a near-zero rate | Return a percentage and cover it with a test |
| 20 | Simulation caps at `termMonths * 2` | A `COMPLETED` run can still owe money | Loop until payoff or return an explicit unpaid status |
| 21 | Recommendation compares percentage APR against `0.15` | Almost every account returns `AVALANCHE` | Compare against `15.0` or normalise to decimals; add tests |

Suggested order: fix status codes and remove the unsafe user-search endpoint; scope scenarios and
add account updates; persist runs; make projections meaningful; harden secrets and schema; then
expand validation, pagination, OpenAPI, and test coverage.

### Endpoints outside the contract

The authoritative endpoint list is [`API_CONTRACT.md` §11](API_CONTRACT.md#11-endpoint-index-and-enums).
Only those paths exist. Any other `/api/...` path returns `404` with a valid token and `401`
without one. Old design notes and external documents are **not** a source of routes; see
[`API_CONTRACT.md` §10](API_CONTRACT.md#10-endpoints-outside-this-contract).

---

## 12. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Every request returns `401` with JSON | Missing token, or wrong credentials | Log in again; check the `Authorization` header |
| Requests suddenly return `500` with a `trace` | Expired/invalid token, e.g. after an API restart; the filter throws | Log in again for now; gap #17 tracks the proper `401` |
| Every failure returns an empty `403` | `/error` dispatch blocked in `SecurityConfig` | Restore `dispatcherTypeMatchers(ERROR).permitAll()` and `/error` permitAll |
| `500` on `POST /api/tomes` with a blank APR | `Tome.apr` is a primitive; the null unboxes before the estimate runs | Send a numeric APR; gap #18 tracks the fix |
| `500` on `DELETE /api/tomes/{id}` | Missing id or another user's id | Expected today; see gap #4 |
| Startup fails with a datasource error | PostgreSQL down, database missing, or wrong credentials | Start PostgreSQL; check `<DATABASE_URL>`, `<DATABASE_USERNAME>`, `<DATABASE_PASSWORD>` |
| Tables are missing | `ddl-auto` not set to `update`, or the user lacks DDL rights | Fix the property/permissions; restart |
| CORS error from a non-`:3000` origin | `CORSConfig` allows only the documented dev origin | Add the origin or use the UI dev proxy |
| UI gets `404` for `/api/...` | UI started without the proxy, or wrong base URL | Run the UI dev server; check `rsbuild.config.ts` / `PUBLIC_API_BASE` |
| `username` looks like an email | By design | `AppUser.getUsername()` returns the email |
| Tokens stop working after a restart | Random signing key per boot; the resulting request is a `500` | Log in again (expected until gaps #7 and #17 are fixed) |

---

## 13. Related documents

- [`API_CONTRACT.md`](API_CONTRACT.md) — backend source of truth: endpoints, DTOs, errors, data
  model, conventions, technical debt, roadmap.
- [`../utilisim-ui/API_CONTRACT.md`](../utilisim-ui/API_CONTRACT.md) — the subset the UI consumes.
- [`../utilisim-ui/README.md`](../utilisim-ui/README.md) — frontend setup and architecture.
