# Batch 4.1 — UUID contracts / five-project topology evidence

13/11/2026. Design-only. **No source/runtime/SQL/database/Supabase mutation.** UUID conversions proposed pending approval; five projects confirmed by user, project connectivity/configuration not verified.

- [Initial branch/HEAD/status](initial-state.json), [design input SHA](design-input-manifest.json).
- [ID inventory](id-contract-inventory.md), [167 persisted contracts JSON](id-contracts.json).
- [38 exact column impacts](id-migration-impact-matrix.md), [impact JSON](id-migration-impact-matrix.json).
- [905 inspected code file hashes](source-file-hashes.json), [667 source surfaces/line symbols](source-surface-inventory.json).
- [124 exact protobuf ID/key/version field declarations](protobuf-id-contracts.json); wire types/tags unchanged, string semantic UUID adapters proposed.
- [35 repository generic declarations](repository-id-contracts.json), including3 protected Place repositories unchanged.
- [136 frontend files /227 typed ID declarations](frontend-id-surfaces.json): API/routes/params/forms/state/cache/storage/test references, lexical scan limitations explicit.
- [254 seed/test/harness source impacts](fixture-impact-inventory.json); no data/credentials copied or rewritten.
- [Static design validation](design-validation.json), [full machine-readable revised model](../batch-4-database-design/canonical-model.json).

Contracts and surfaces counts overlap and mean different things; no runtime test count inferred. Place metadata read-only, source-backed types only/no Place snapshot; no Place-dependent edit authorized. Raw scripts and original design copies/hashes remain under ignored `checkinn/.local/evidence/batch-4-1-uuid-topology/`, not production tooling. JSON literal symbol/source metadata avoids copying provider keys, project URLs or private config values.

Main documents: [ID standard](../../database-design/ID_STANDARDIZATION_CONTRACT.md), [canonical schema](../../database-design/CANONICAL_DATABASE_SCHEMA_V1.md), [ERD](../../database-design/ERD.md), [five-project architecture](../../database-design/SUPABASE_DATABASE_ARCHITECTURE.md), [5A/5B/6 plan](../../database-design/MIGRATION_IMPLEMENTATION_PLAN.md), [Vietnamese report](../../BATCH4_1_UUID_AND_SUPABASE_ARCHITECTURE_REVISION_2026-11-13.md).
