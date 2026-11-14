# Controlled migration roadmap

1. Freeze a reviewed schema-only inventory per service; keep original SHA/checksum history. Do not rewrite V1–V5/manual scripts.
2. Obtain authorized sanitized applied history and release boundary; distinguish Flyway baseline marker from actual V1 schema.
3. User: reconstruct sanctioned V1 and compare to real deployed schemas before adopting baseline or switching runtime policy. Keep current validate unchanged.
4. Hotel: resolve V1 absent hotels prerequisite, V2 syntax, V3 repeated parent_id and V5 expiry overlap using versioned future migration strategy. Test-only accommodation is not a production replacement. No current migration edits.
5. Booking: retire schema.sql as a candidate baseline only through reviewed replacement; its cross-service users FK/UUID types conflict with current scalar String contracts. Confirm receipts for manual SQL/outbox/refund before future additive migrations.
6. Revenue/Regulations: capture full JPA core baseline plus SQL-owned objects, CHECKs, indexes and ordering. Do not derive cash/event history from projections.
7. Per service: clean PostgreSQL + representative upgrade + replay/failure + JPA validate/runtime gates, then controlled deployment. Gradually remove update only after evidence. No all-service Flyway conversion in this batch.
8. Inventory/cash/encoding historical repair stays a manual evidence/reconciliation workstream, separate from schema deployment.

No new production migration is justified safely without confirmed historical state. Confirmed defects have negative regression probes and a review path; changing potentially applied SQL would violate immutable-history restrictions. Place excluded throughout.
