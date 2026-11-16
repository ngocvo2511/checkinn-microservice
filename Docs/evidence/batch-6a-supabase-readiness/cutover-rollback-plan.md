# Cutover/rollback — future6B only

U03 approved: fresh canonical targets; no demo import/legacy String cast/historical Booking ID regeneration/old event replay/Redis cache copy. Preserve old DB evidence. New RabbitMQ vhost/users/queues/DLQs and Redis instance or proven ACL-isolated namespace. Notification hardcodes queue names, so vhost isolation matters. Redis DB index alone not security boundary. No cutover6A.

Order: project preflight → security preflight → identity → roles/default ACL → Flyway → catalog/history/permissions → Hibernate → private startup → API/gRPC → broker/Redis isolation acceptance → E2E → traffic → monitoring → recovery decision. Isolated broker/Redis must exist **before app startup**; later acceptance does not permit old infrastructure use.

G0: five targets/network/versions/permissions/budgets/backup/restore known. G1 before DDL:6B authorization, guarded runner/overlay reviewed, clean business namespace + managed allowlist, safe exposure/defaults. G2 before app: five catalogs/history/checksums/roles PASS/no-op. G3 before traffic: Hibernate/health/connectivity/negative security/fresh isolation/workflows/recovery/monitoring PASS; incident owner/RPO/RTO agreed. G4: record first-write/traffic time and release/config/event checkpoints. Any unknown/failed gate stops dependent stage.

Fresh empty DB needs authorized fresh users/admin/owner/hotel/room/catalog/reviewed stock via supported flows; no demo SQL copy/admin elevation/default10. Java sole Regulations seed owner; one replica first, verify expected15/0.10/10000/500 once. Seed startup itself is a write, so not a no-write rollback state.

| State | Recovery | Resume gate |
|---|---|---|
| No application writes, including seeds | stop jobs/apps; preserve canonical DB; compatible app/config route rollback | prove no writes; explicitly selected safe old environment |
| Seeds/synthetic writes, no customer traffic | isolate and preserve; assess writes; compatible application rollback | reviewed fixture retention/recovery, no blind reset |
| Customer/financial writes | freeze affected writers/consumers, durably quarantine callbacks | incident reconciliation signoff |
| Compatible application regression | previous UUID/schema-compatible artifact | never pre-UUID build or legacy varchar DB |
| Data loss/corruption | approved isolated restore/reconciliation/route switch | tested backup/security/RPO/RTO and domain signoff |

Never Flyway clean/drop/default repair. Never blind retry/route flip after financial writes. Preserve original provider order/transaction/amount/currency/receipt evidence and refund executions; verify provider status securely. Revenue exactly-once accepted CREDIT/DEBIT and net cash; immutable receipts not rewritten. Loyalty compare reserve/use/earn/reverse commands/ledger/balances. Hotel reconcile holds/commit/release/expiry/reviewed stock/no oversell. Revenue compare raw receipts/quarantine/gaps/projections/source UUID+version. Regulations booking snapshots remain consistent. Replay only reviewed NEW-environment events with dedup, not old demo queues; no forced ACK or balance rewrite to hide discrepancy.

Triggers: wrong identity/schema/API access, duplicate receipts, unexplained cash/loyalty/inventory variance, stuck recovery/outbox, pool/error/latency above agreed limits, failed restore. Incident lead records freeze time/in-flight provider/event window, backups/actions and owners before resume.
