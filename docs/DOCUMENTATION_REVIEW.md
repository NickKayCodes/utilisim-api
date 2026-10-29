# UtiliSim Documentation Verification — Release Readiness

**Review date:** 2026-10-28
**Scope:** every Markdown document in `utilisim-api` and `utilisim-ui`
**Method:** read each document against the current controllers, DTOs, services, security
configuration, tests, and a live API on `:8080`. Every link and heading anchor was resolved
locally; every example was checked for synthetic values.

## Verdict

The documentation set is **release-ready after the fixes below**, with code defects that must be
decided by an owner. The full runtime and source inventory is in `DEFECTS.md`; this document
focuses on where the documentation disagreed with the code. The documents no longer describe
removed routes, deleted planning files, or behaviour the code does not implement.

| Metric | Result |
|---|---|
| Documents reviewed | 6 |
| Documentation issues found | 30 (18 critical + 12 recommended) |
| Documentation issues fixed | 30 |
| Nice-to-have recommendations | 6 (not applied) |
| Broken links / anchors | 0 |
| Leaked credentials or real emails | 0 |
| Documented code defects | 17 |
| API tests | 15 passed, 0 failed |
| UI typecheck | passed |
| UI lint | passed |
| UI build | passed (294.8 kB) |

---

## CRITICAL — inaccurate documentation and mismatched contracts

| ID | File | Section / was line | Problem | Fix applied |
|---|---|---|---|---|
| C1 | `utilisim-api/API_CONTRACT.md` | §5.3 `POST /api/tomes` (was L308) | Claimed a blank `apr` triggers a server-side `estimatedApr` fallback. `Tome.apr` is a primitive `float`; `TomeService.createTome` unboxes `null` and throws `NullPointerException`, returning `500` before any estimate runs. | Documented that `apr` must be numeric, that the estimate is unreachable, and that a future fix would store `0.16` percent because of a second unit bug. |
| C2 | `utilisim-ui/API_CONTRACT.md` | §3, §4, §5, §6 | Repeated the same false estimate behaviour and described `apr: null` as a supported create path. | Corrected the status table, `TomeRequest` comments, the create section, and the "Estimated APR" quirk. |
| C3 | `utilisim-ui/README.md` | §9 data shaping, §10 smoke test | Said `effectiveApr()` falls back to an estimate and told testers to confirm an "estimated" label for a blank APR. That path cannot be reached. | Marked the fallback as defensive only and rewrote the smoke step to exercise the current `500` failure. |
| C4 | `utilisim-ui/src/pages/AccountsPage.tsx` | New-account section (was L178) and APR field hint (was L219) | Product copy promised "Leave it blank to use the server estimate" and labelled APR "Optional". | Changed to "Enter a value; a blank APR is not supported yet" and "Required today". |
| C5 | `utilisim-api/API_CONTRACT.md` | §5.4 `POST /api/assemble/run` (was L348) | Claimed the engine runs until the balance is zero. It actually loops while `balance > 0` **and** `month < termMonths * 2`, so a run can end `COMPLETED` with money still owed. | Documented the 2× cap, the non-zero `remainingBalance` case, and added the cap to the roadmap and debt table. |
| C6 | `utilisim-ui/API_CONTRACT.md` | §5 simulation, §6 item 3 | Claimed "runs until the balance is zero" and described the non-terminating guard as the only limit. | Documented the 2× cap and warned that a "completed" run may still owe a balance. |
| C7 | `utilisim-api/API_CONTRACT.md` | §5.4 bullets (was L387) | Said `payoffDate` is computed separately and "may differ" from the last schedule date. `ChronosService.getPayoffDate` returns exactly the last entry's date. | Corrected the description and the example date (`2028-01-15`, matching month 24). |
| C8 | `utilisim-api/API_CONTRACT.md` | §5.4 field table (was L353) | Said `id` and `userId` are "accepted but ignored". `ForgeSimulationEngine` copies them into `simulationInputId` and `userId`. | Documented the echo and warned that they are not derived from the token. |
| C9 | `utilisim-api/API_CONTRACT.md` | §2, §3 | Said invalid tokens return `401`. A malformed, expired, or wrongly signed token throws out of `JwtAuthenticationFilter` and returns `500` with Spring's default body and a stack trace. Only a missing token reaches `401`. | Added a dedicated "exceptions that bypass `ApiExceptionHandler`" section and corrected the status table and token lifecycle. |
| C10 | `utilisim-api/README.md` | §6 Tokens, §7 error table (was L268, L299) | Same `401` claim for invalid tokens. | Corrected both and linked the new gap #17. |
| C11 | `utilisim-ui/API_CONTRACT.md` | §2 session/logout, §3 statuses | Claimed restart/expiry produces `401` and signs the user out. The actual `500` leaves the session in place because `lib/api.ts` only clears on `401`. | Documented the stuck-session failure mode and recovery. |
| C12 | `utilisim-ui/README.md` | §7 token caveats, §7 global 401, §12 gap #7 | Repeated the `401`/sign-out claim and described expiry as an "abrupt sign-out". | Corrected to the `500` + manual-logout behaviour. |
| C13 | `utilisim-api/API_CONTRACT.md` | §10 (was L616–632) | Contained a table of removed legacy routes and referenced a deleted planning file. | Replaced with a current-only "Endpoints outside this contract" policy. No historical routes remain. |
| C14 | `utilisim-api/README.md` | §11 "Non-existent endpoints", §13 related documents (was L494–507, L532) | Referenced the deleted planning file and listed removed legacy routes. | Removed the table, pointed to the contract index, and removed the dead link. |
| C15 | `utilisim-ui/API_CONTRACT.md` | §5 `recommend` (was L419–427) | The request example was `["SNOWBALL", "AVALANCHE"]`, but the endpoint expects a bare `DebtSnapshot[]`. A client copying it would send the wrong shape. | Replaced with a valid `DebtSnapshot` array example. |
| C16 | `utilisim-ui/API_CONTRACT.md` | §5 `analyze` (was L465) | The JSON example contained `/* DebtSnapshot[] */`, which is not valid JSON. | Replaced with a concrete synthetic debt object. |
| C17 | `utilisim-api/API_CONTRACT.md` | §5.2, §6 (was L211, L537) | Said scenario `userId` is "never populated". The server never sets it, but a client-supplied `userId` is persisted verbatim. | Corrected both the endpoint section and the data-model table. |
| C18 | `utilisim-api/README.md` | §1 (was L10) | Typo: "the source of truth.— update the contract" ran two sentences together. | Rewrote the sentence. |

---

## RECOMMENDED — missing explanations, unclear wording, duplication

| ID | File | Section | Problem | Fix applied |
|---|---|---|---|---|
| R1 | `utilisim-api/API_CONTRACT.md` | §11 endpoint index | `GET /api/sentinel/users/search/by-email` exists but was absent from the index; it requires a GET body and returns the `AppUser` entity, including the password hash. | Added the row with an explicit unsafe-endpoint warning. |
| R2 | `utilisim-api/API_CONTRACT.md` | §5.5 `calculate-total-interest` | The response was documented only as "bare number"; the formula and order-independence were not explained. | Documented `sum(currentBalance * apr)` and that it is not a projection. |
| R3 | `utilisim-api/API_CONTRACT.md`, `utilisim-ui/API_CONTRACT.md` | §5.5 `recommend` | The recommender compares percentage APR against `0.15`, so any account above 0.15% returns `AVALANCHE`. This unit bug was undocumented. | Documented the effective behaviour and added it to the debt tables/gaps. |
| R4 | `utilisim-api/API_CONTRACT.md`, `utilisim-api/README.md` | Tome debt entries | `calculateEstimatedApr` returns `0.16`, which would be stored as 0.16 percent, not 16 percent. | Documented the unit bug in the contract and gap #19. |
| R5 | `utilisim-api/API_CONTRACT.md` | §5.5 `analyze` | Said strategies "can" return the same projection; the calculation is order-independent, so they **always** do for the same debt set. | Corrected the wording and explained why ordering does not change the number. |
| R6 | `utilisim-ui/README.md` | §12 gaps, §13 troubleshooting | Restart and expiry were described as a sign-out; the actual failure is a persistent `500`. | Corrected gap #7, added gaps #17–#20, and added troubleshooting rows for blank APR, invalid tokens, and the term cap. |
| R7 | `utilisim-ui/src/components/SimulationResultView.tsx` | Footer note (was L66) | Said the run has "no result id". The response always has a generated `id`; the real limitation is the missing lookup endpoint. | Reworded to distinguish a generated id from persisted history. |
| R8 | `utilisim-api/README.md` | §5 units | The percentage note did not mention that a null APR cannot be persisted. | Added the blank-APR constraint. |
| R9 | `utilisim-api/README.md` | §3 Tests | Test coverage was described qualitatively; the actual suite size (15) was not stated. | Recorded 15 tests (14 strategy unit + 1 context) in gap #16. |
| R10 | `utilisim-api/README.md`, `utilisim-ui/README.md` | Governance blocks | Placeholder lists differed from the ones actually used (`<DATABASE_NAME>`, `<HOST>`, `<PORT>`, UI ids). | Made both governance blocks enumerate every placeholder in use. |
| R11 | `utilisim-ui/API_CONTRACT.md` | §5 `analyze` | Said `recommendedStrategy` may be `null`. The server always sets a string and falls back to `MINIMUM_ONLY`. | Corrected the description and noted the defensive TypeScript null. |
| R12 | `utilisim-api/API_CONTRACT.md` | §5.2 scenario create | Said `extraPayment: null` / `lumpSumPayment: null` fail deserialization with `400`. A live probe confirmed the `400`, but the wording had been changed to claim coercion; the original failure claim was correct. | Restored the `400` behaviour, documented the generic message, and advised sending `0`. |

---

## NICE TO HAVE — architecture and onboarding improvements

| ID | Area | Recommendation |
|---|---|---|
| N1 | Documentation CI | Add a small script that fails on broken relative links, missing anchors, stale route strings, and non-`example.com` emails. The manual scan used here is a good starting point. |
| N2 | Contract drift | Keep one error-model section as the source of truth and link to it from the README. The two copies currently agree, but they duplicate maintenance. |
| N3 | API description | Add `springdoc-openapi` and generate the mechanical endpoint/DTO reference; keep the behavioural notes (caps, token `500`s, estimate defects) in `API_CONTRACT.md`. |
| N4 | Contract metadata | Add a short "last verified" line with the API commit/version to both contracts so a reader can tell how fresh the behavioural claims are. |
| N5 | Release checklist | Add a docs step to the endpoint/table change checklists: regenerate examples, run the link scan, and confirm the phase-1 limitation list. |
| N6 | Onboarding | Add a single root-level quickstart that links both READMEs and states the `:8080` → `:3000` proxy order. The current split is accurate but requires reading two files first. |

---

## Remaining manual decisions (code defects surfaced by this review)

These are real behaviour defects, not documentation errors. They were documented rather than
silently changed, because each needs a product/engineering decision. `DEFECTS.md` is the
authoritative inventory; the list below is the subset that directly changed the documentation.

1. **Blank APR returns `500`.** `Tome.apr` is a primitive `float`; `TomeService.createTome`
   unboxes `request.apr()`. Decide: make the entity nullable, reject with `400`, or implement a
   real estimate. The UI currently offers a blank field and now warns that it is unsupported.
2. **Invalid/expired tokens return `500` with a stack trace.** Catch `JwtException` in
   `JwtAuthenticationFilter`, return `401`, and disable `trace` in error responses. Until then,
   the UI cannot detect an expired session.
3. **Simulation stops at `termMonths * 2`.** A `COMPLETED` result can still owe a balance. Decide
   whether to loop to payoff, return an explicit "not repaid" status, or surface
   `remainingBalance` prominently.
4. **`calculateEstimatedApr` returns `0.16`, not `16`.** If the null-APR path is enabled, this
   stores a near-zero rate. Fix the unit or remove the method.
5. **Recommendation threshold uses `0.15` against percentage APRs.** Nearly every account returns
   `AVALANCHE`. Compare against `15.0` or normalise to decimals, and add tests.
6. **Scenarios are global.** The server never sets `userId` from the principal; every user sees
   and can delete every scenario. Scope reads/writes by owner.
7. **`GET /api/sentinel/users/search/by-email` is unsafe.** It needs a GET body and returns the
   `AppUser` entity, including the BCrypt hash. Remove it or replace it with a DTO-based lookup.
8. **Simulation identity echoes client input.** `SimulationResult.userId` and
   `simulationInputId` copy the request body. Derive identity from the principal and stop
   echoing untrusted ids.

Also still tracked, but not introduced by this pass: tome delete returns `500` for missing/foreign
ids, no account update endpoint, no run persistence, no roles, no migrations, `float` money, no
bean validation, no pagination, no OpenAPI, and a `localStorage` token.

---

## Files reviewed

| File | Result |
|---|---|
| `utilisim-api/README.md` | Updated: tokens, errors, gaps, troubleshooting, removed deleted references |
| `utilisim-api/API_CONTRACT.md` | Updated: APR, simulation cap/payoff, identity echo, errors, scenarios, endpoint policy |
| `utilisim-ui/README.md` | Updated: blank APR, token/restart behaviour, smoke test, gaps, troubleshooting |
| `utilisim-ui/API_CONTRACT.md` | Updated: APR, simulation cap, token `500`s, recommend/analyze examples, phase-1 limits |
| `utilisim-ui/AGENTS.md` | Reviewed; accurate for the current Rsbuild/ESLint/Prettier commands |
| `utilisim-ui/.agents/skills/rsbuild-best-practices/SKILL.md` | Reviewed; generic and current, no project-specific claims |

Cross-checked against the controllers, DTOs, security configuration, simulation/strategy services,
UI services/pages/storage/routing, `pom.xml`, `package.json`, and the test suite.

## Verification evidence

- **Stale-reference scan:** 0 hits across the deleted-file, removed-frontend, and legacy-route
  patterns used during this review. The literal patterns are intentionally not reproduced here.
- **Links and anchors:** all relative links resolve; all heading anchors resolve. 0 broken.
- **Sensitive-value scan:** no non-`example.com` email addresses, tokens, connection strings, or
  machine-specific paths in any document. Configuration examples use `<DATABASE_URL>`,
  `<DATABASE_USERNAME>`, `<DATABASE_PASSWORD>`, `<DATABASE_NAME>`, `<HOST>`, `<PORT>`,
  `<JWT_SECRET>`, `<PASSWORD>`, `<JWT>`, `<USER_ID>`, `<TOME_ID>`, `<SCENARIO_ID>`, and
  `<SIMULATION_ID>`.
- **`mvnw.cmd test`:** 15 tests, 0 failures, 0 errors.
- **`npm run typecheck`:** passed.
- **`npm run lint`:** passed.
- **`npm run build`:** passed; total bundle 294.8 kB (95.2 kB gzip).
