# Batch 2 evidence

- `workspace-inventory.json`: 989 files outside repositories, categories, original/new paths, SHA-256 and sensitivity review flags. Pattern matches are risk indicators, never disclosed values.
- `gateway-results.json`: reviewed synthetic HTTP/SQL assertions from the latest execution; no credentials, JWTs or contact bodies.
- `schema-bootstrap.json`: exact disposable bootstrap and index inventory. JPA update and compatibility SQL are test-only, not production migration certification.
- `regression-results.json`: fresh Maven console totals, skips separated.

Raw Batch 1 files: `../../../.local/evidence/batch-1-refund/`. Raw Batch 2 files: `../../../.local/evidence/batch-2-booking/`. Both ignored; manifest maps archived names and checksums. Old `DA1/logs/` subdirectories already group prior execution logs and review snapshots; retained in place to preserve ownership and references. Snapshot patches/source copies are REVIEW/KEEP, not source to merge or raw logs to publish. Root SQL, fixture data and unrelated documentation remain KEEP pending ownership/migration review; no historical SQL is silently promoted to a production migration.

The original Batch 2 execution left these candidates unstaged. The subsequent PR review commits the reviewed files; raw logs and private environments remain ignored. Existing snapshots and raw logs are not certified sanitized. See [PR review](../../BATCH2_PR_REVIEW_2026-11-13.md) for fresh checks and harness corrections.

Final accepted execution (2026-11-13 Asia/Saigon): main Gateway42, expiry6, final-SQL/deadline9 assertions PASS; focused service tests627 PASS/34 skipped; PostgreSQL2 PASS separately. Final raw files: gateway-final.log, expiry.log, final-sql.log, regression.log, AvailabilityRangeIntegrationTest.log, BookingEventOutboxIntegrationTest.log, postgres-runner.log. Earlier probes/failed harness runs remain local and are not acceptance results.

- `initial-state.json`: pre-write Git snapshot.
- `environment-results.json`: reviewed image IDs, mounts/health and registrations; no env/credentials.
- `expiry-results.json`, `final-sql-results.json`: actual scheduler and persisted expiry/policy SQL/readback checks.
- `postgres-integration-results.json`: two newly executed opt-in suites, broker mock explicitly identified.
- `document-link-review.json`: relocation link repairs.
- [Reproduction](REPRODUCE.md), [Vietnamese report](../../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md).

- `schema-constraints.json`: required Hotel/Booking table presence and actual PostgreSQL constraint definitions; unique command indexes checked.
- `verification-summary.json`: final archive hashes, relative links, known smoke credential/JWT scan, protected scope and unchanged HEADs.
