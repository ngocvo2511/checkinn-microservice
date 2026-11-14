# Regression results — 13/11/2026

| Run | Result | Evidence |
|---|---|---|
| Legacy Booking golden before conversion | PASS | private legacy-golden.log; test source |
| Eight-module Maven unit/focused suites | 901 executed PASS,39 skipped,0 failures/errors; BUILD SUCCESS8/8 | [class-level results](backend-unit-results.json), private final-backend.log |
| New isolated PostgreSQL contract suites | 3 executed PASS,0 skip/failure/error | [native results](native-postgres-results.json), private native-postgres-final.log |
| Frontend regression | 211 PASS,0 fail/skip | private frontend-regression.log |
| Focused frontend booking lint | PASS | private frontend-focused-lint.log |
| TypeScript baseline/final | FAIL identically | private baseline-tsc.log / final-tsc.log |
| git diff --check | PASS | final check |

Commands from backend: `mvn -o -B -ntp -fae -pl user-service,auth-service,hotel-service,media-service,booking-service,revenue-service,notification-service,regulations-service "-Dtest=*,!*ApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, with existing CHECKINN_RANGE_TEST/REFUND_TEST/SESSION_TEST=false. ApplicationTests intentionally excluded to avoid existing DB/config startup.39 skips are existing opt-in tests, not claimed PASS. New native tests were added after broad run and run separately with CHECKINN_UUID_TEST=true and owner-only URLs/password; total904 executed backend tests across these two scopes, not a single full E2E run.

Native: `mvn -o -B -ntp -fae -pl booking-service,user-service,revenue-service -Dtest=UuidPostgresContractTest test`. Test harness verifies exact local URL/database and SHOW data_directory before schema creation. PostgreSQL18 SCRAM, loopback55485, three new owner databases, unique schema per attempt; Hibernate create-only (no update/create-drop), synthetic data only. First native attempt failed because bare EMF lacked Boot snake-case naming strategy; fixed in test harness, failed namespaces retained, final three PASS. Docker daemon unavailable; existing stacks untouched. Canonical V1/JPA validate/full app startup remain NOT RUN, not migration acceptance.

Frontend: `npm run test:regression`; `npm run lint -- lib/api/booking.ts lib/booking/creation-command.ts lib/booking/payment-state.ts`; `node node_modules/typescript/bin/tsc --noEmit --incremental false`. Both tsc runs: `components/NearbyPlacesSection.tsx(161,15) TS2322`, showDistance absent from PlaceCardProps. Protected files unchanged.

Coverage by existing suite: Booking creation/duplicate/retry/compensation/paid-onsite-recovery/scheduler/authorization; Hotel hold/inventory/range/session tests within opt-in limits; Payment create/retry/expiry/IPN/idempotency and refund lifecycle/internal/manual recovery; Revenue ordering/duplicate/malformed/ledger/gaps/report; User earn/use/cancel/reservation/commit/reversal/recovery. See class-level results for executed counts. Mocks/loopback/native subsets do not establish all cross-service concurrency, response-loss, restart or broker workflows. DB/Redis opt-in stress suites and Gateway E2E NOT RUN.

Failed intermediate compile/test logs retained privately, superseded by stated final runs; no skipped assertions used to make a failure pass. No real provider transactions.
