# Batch6B — approved hosted migration acceptance, 16/11/2026

**Five canonical V1 migrations, scoped runtime grants and standalone Hibernate validation PASS. Overall Batch6B remains PARTIAL: local infrastructure/startup/business acceptance not run.**

The operator explicitly approved the exact five-target V1/grants review, confirmed apps stopped and no concurrent DDL. The immutable private review binds manifest, project/service mapping, V1, live inventory and grants hashes. Each target ran sequentially with scoped PAT identity checks, verify-full/CA/hostname checks, both TLS legs and exact managed event descriptors checked before writes. No baseline/repair/clean or provider settings changes. Per-target approval files are now expired and migrationApproved=false.

| Service | Business tables | V1 + validate + immediate rerun | Scoped grants / runtime checks | Standalone Hibernate |
|---|---|---|---|---|
| user | 7 | PASS / no-op | PASS | 7 entities PASS |
| hotel | 24 | PASS / no-op | PASS | 17 entities PASS |
| booking | 7 | PASS / no-op | PASS | 4 entities PASS |
| revenue | 5 | PASS / no-op | PASS | 2 entities PASS |
| regulations | 3 | PASS / no-op | PASS | 2 entities PASS |

Total: **46 business tables + five Flyway history tables**, one successful V1 history row/project; no invalid constraints. All published counts come from actual hosted catalogs, not local fixture counts.

Runtime probes read zero business rows (`SELECT ... LIMIT 0`). 30 actual permission-denied SQLSTATE42501 checks cover public persistent CREATE, history read/update and protected immutable-table UPDATE/DELETE/INSERT where applicable. Probe transactions rolled back; no business DML/seed rows. Catalog checks verify exact table ownership/set, each expected table/column privilege, sequence USAGE/SELECT without UPDATE, and no API/PUBLIC business table rights. Mutable CRUD and immutable restrictions match the approved scripts; no broad default app grants.

Hibernate built metadata/SessionFactory with the compiled service entities, Boot naming strategies, UTC/public and hbm2ddl=validate using the dedicated runtime role over a guarded read-only datasource. 32 mapped entities PASS. No Spring application context, Rabbit listeners, repositories, business workflows, Flyway calls or schema creation in this probe. Non-JPA tables are covered by applied canonical V1, exact table/owner sets and privilege checks; do not equate this with full service startup/business acceptance.

## Resolved execution issue

User V1/validate/no-op succeeded, then a separate libpq grants session was rejected by the migrator role's existing connection cap before any grant SQL executed. Execution stopped. The same reviewed grants were then applied transactionally through the guarded PGjdbc transport already used by the runner. No cap increase, TLS downgrade, V1 rerun or schema reset. Remaining targets used guarded JDBC directly. Final runtime/catalog checks PASS across all five. Supporting private executors refuse completed migration records and completed grants; original per-target attempt/recovery evidence retained.

## Remaining scope

Runtime roles still inherit managed PUBLIC database TEMP; actual persistent public CREATE is denied. No broad managed DB revoke was made. Data API disabled remains based on operator Dashboard confirmation; effective table/column/sequence API privileges are independently checked and denied. This is not blanket certification of other managed schemas or every API endpoint.

Pool budgets/readiness flags, dedicated RabbitMQ/Redis isolation, full service startup and hosted business workflows remain pending. Readiness/isolation flags remain false. No backend cloud deployment, legacy/demo data/schema changes, frontend/Place/business-rule changes, Git stage/commit/push or Batch7. Historical reports retain their original as-of results; this checkpoint supersedes earlier hosted-blocked statements for the completed scope.

[Redacted acceptance evidence](evidence/batch-6b-supabase-local-integration/five-project-migration-acceptance-2026-11-16.json). Private approval/execution/grants/catalog records are retained under ignored `.local/batch6b/migration-review-2026-11-16`; Hibernate records under `.local/batch6b/hibernate-validate`. Credentials and actual project refs are excluded from public evidence.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json).
