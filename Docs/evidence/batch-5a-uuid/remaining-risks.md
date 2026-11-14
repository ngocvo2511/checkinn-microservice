# Remaining risks / status

**PARTIAL**, not DONE: mandatory frontend TypeScript gate fails at pre-existing protected Place showDistance mismatch. No UUID-induced Place change found; cannot repair the protected component under this batch. Resolve in separately authorized Place scope, then rerun tsc.

Source38 conversions implemented and representative native persistence verified; historical VARCHAR schemas deliberately unchanged. Canonical Flyway V1, all-five-owner fresh schema replay, JPA validate, production SQL-only constraints/indexes, grants/Data API/RLS, full application/broker/Gateway and deployed concurrency/restart acceptance remain NOT VERIFIED. No automatic Batch5B/6 start. Synthetic create-only test schemas are not V1 acceptance.

STD-021 IN_PROGRESS; STD-006 OPEN. Existing D01–D09 decisions/gates (including money/rate/time/delete policy, inventory provenance, retention/cutover/security) remain per design; U01/U03/U04 no longer pending. Five independent Supabase projects remain target. No current project connectivity assumed.

Old invalid aliases/caches/events may be rejected at typed boundaries; raw Revenue malformed evidence remains durable. Fresh-database direction avoids automatic legacy data cast/import. Do not deploy source with old VARCHAR demo DB. No new provider refunds/dual approval/evidence requirement; refund internal flag unchanged.

Safety: no existing DB/Supabase access or mutations, no canonical migration, no historic SQL/runtime credential edits, no demo import/reset/drop/truncate, no Place/frontend change, no Git stage/commit/push/merge/reset. Owner-only test PostgreSQL cluster stopped after verification; retained data/logs contain synthetic fixtures. See stop receipt.
