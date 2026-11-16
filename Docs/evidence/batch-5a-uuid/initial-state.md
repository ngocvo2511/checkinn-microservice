# Initial state — Batch 5A, 13/11/2026

Backend: branch `test/booking-hotel-gateway-acceptance`, HEAD `8ee230b250863164dd47f83af037711a0ea4a0ef`. Frontend: `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`, clean. Full status: [initial-state.json](initial-state.json).

Pre-existing backend changes: two .idea XML files; deleted BATCH2_PR_REVIEW; PLAN/AUDIT modifications; untracked Batch3/4/4.1 documents, database-design and evidence, eight docker schema harness files. Preserved; no stage/commit/push/merge/reset. No AGENTS.md found.

Read Batch1.6/2/3/4/4.1 and contract/impact/topology/design/standardization materials. Authority for this batch: U01 approved38, U03 fresh DB/no demo import, U04 String ratePlanId; U02 requires evidence. Earlier “pending approval” statements are historical, superseded by this request.

Dependency order: Booking entity IDs → Hotel hold/catalog adapters and Booking REST/gRPC/outbox → User loyalty commands/recovery → Revenue receipt/projection/cash/report → Notification/Auth/protobuf/frontend compatibility. Cross-owner links remain logical; no new physical FK. Regulations BIGINT and Place excluded.

Before-source ZIP and SHA-256 inventory (1266 files) retained privately in `.local/evidence/batch-5a-uuid/`. Golden test ran successfully against legacy String implementation before conversion. Broad initial compile did not finish: no full backend baseline PASS claimed. TypeScript baseline fails on existing protected Place showDistance mismatch; same failure after batch. Existing opt-in tests that could reach shared DBs were not enabled.
