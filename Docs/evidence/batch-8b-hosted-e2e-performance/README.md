# Batch8B evidence — 17/11/2026

**PARTIAL.** See [full report](../../BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md). No performance improvement or production-readiness claim.

- `initial-git-*.json`, `final-git-state.json`: branch/HEAD/index/diff provenance; prior changes retained.
- `*-runtime-preflight.json`, `*-before.json`, `*-after.json`: fresh target/TLS/catalog/history/count checks.
- `admin-baseline.json`, `admin-authentication.json`, `final-admin-preservation.json`: protected permanent account; initial real login/authorization, final DB+credential preservation; final HTTP503 explicitly blocked.
- `fixture-manifest.json`, `cleanup-exact-id-plan.json`, `cleanup-results.json`:48 exact IDs absent,47 rows explicitly deleted; old OTP lifecycle-deleted.
- `e2e-results.json`, `concurrency-*.json`: raw attempt-level outcomes including failures; not unique scenario totals.
- `financial-side-effect-check.json`, `zero-point-side-effects.json`: scoped zero-point cancellation receipts/account, no provider/payment/refund/real ledger effects.
- `query-counts-before-small.json`, `query-count-slope.json`, `sql-plans-small.json`: populated counts and limits; multi-size slope/AFTER missing.
- `benchmark-before-small-raw.json`, `benchmark-before-small-summary.json`, `before-after.json`:29/30, one timeout, stop; AFTER blocked, no fabricated metrics.
- `network-transaction-probe.json`, Hikari snapshot, readiness/pressure files: separate diagnostics, not pure RTT/CPU attribution.
- `regression-results.json`, `local-disposable/`:843 executed final PASS/41 opt-in skips, local native tests do not replace hosted acceptance.
- `existing-row-digests-baseline.json`, `existing-row-digests-after.json`: salted opaque complete-row digests,5/5 equality after cleanup. Baseline captured after registration with exact fixture exclusion; baseline-empty tables normalized using pre-write counts. `existing-row-digests-before.json` is an earlier superseded capture and is not the comparison baseline.
- `broker-cleanup.json`, `local-side-effect-cleanup.json`: exact fixture payload cleanup, no queue purge/cache flush; owned runtime stopped.

No credentials, password hashes or JWTs belong in this directory. Private test logs/input files remain ignored. Existing permanent ADMIN must never be included in future fixture cleanup. Re-run tooling requires a new manifest/run; completed manifest writesAllowed=false.
