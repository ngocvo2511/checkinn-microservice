> Historical Batch4 evidence. `canonical-model.json` was revised by Batch4.1 to proposed UUID targets/five independent projects; original Java/source provenance preserved. Earlier discrepancy/validation files are as-of Batch4, not current target acceptance. See [Batch4.1 evidence](../batch-4-1-uuid-topology/README.md).

# Batch 4 — Reviewed database design evidence

13/11/2026. Design-only; no database connection/mutation or application tests executed. Supplied dumps remain outside repository; only sanitized metadata/diffs/specification are candidates for review. Raw inspection scripts/extracts: `checkinn/.local/evidence/batch-4-database-design/`, ignored, not production tooling.

- [Snapshot inventory / SHA / ownership limitations](schema-snapshot-inventory.md)
- [Complete snapshot catalog](snapshot-catalog.json): tables/columns/defaults/PK/FK/UNIQUE/CHECK/indexes/identity sequences/TOC/special counts; DDL metadata only.
- [SQL classification + SHA](sql-source-classification.json): all68 visible workspace SQL, not all treated as snapshots.
- [Discrepancy report](schema-discrepancy-report.md), [57 field/table deltas](schema-discrepancies.json): KEEP/MODIFY/REMOVE_CANDIDATE/ADD/NEEDS_DECISION/UNKNOWN findings; no auto-cleanup.
- [Canonical model](canonical-model.json): 46 tables / 458 columns, complete type/null/default/constraint/index definitions, per-table source evidence; unresolved precision/ID/delete/time gates in main spec.
- [Initial Git state](initial-state.json): existing work preserved.
- [Source SHA inventory](source-inventory.json):443 current main source/config metadata; Place metadata read-only only, three protected entities excluded from canonical coverage.
- [Entity coverage](entity-coverage.json):32 non-Place entities mapped.
- [Repositories](repository-contract-coverage.json):32 interfaces including three native-query repositories, derived methods + hashes.
- [Query/workflow signals](query-workflow-coverage.json):56 source files covering JPA/native/JDBC/transactions/schedulers/guards. Signals are static references, not executed-query results.
- [Business/validation contracts](business-contract-coverage.json):52 files with validation, transaction, rounding, scheduler or converter markers; parser marker coverage not semantic proof of all annotations.
- [Design validation](design-validation.json): structural checks and limitations, no runtime database acceptance.

Main deliverables: [canonical specification](../../database-design/CANONICAL_DATABASE_SCHEMA_V1.md), [ERD](../../database-design/ERD.md), [Supabase architecture](../../database-design/SUPABASE_DATABASE_ARCHITECTURE.md), [implementation plan](../../database-design/MIGRATION_IMPLEMENTATION_PLAN.md), [Vietnamese report](../../BATCH4_CANONICAL_DATABASE_DESIGN_2026-11-13.md).

Source-vs-snapshot comparison does not establish who changed a database or when. Historical Batch3 test-only manifests are cross-check evidence, not pgAdmin snapshots or current production applied histories. No real data/secrets, no original dump copies, no full config values stored in these reviewed files.
