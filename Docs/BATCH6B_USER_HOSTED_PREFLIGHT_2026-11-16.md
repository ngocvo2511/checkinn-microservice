# Batch6B — User hosted read-only preflight

16/11/2026, Asia/Saigon. **User authenticated connectivity/SQL preflight PASS trong phạm vi dưới; overall6B PARTIAL; migration BLOCKED.** Lần này có actual Management API/JDBC requests, phân biệt với các snapshots/local tests trước. Không migrate, baseline/repair/clean, DDL/DML, app startup hoặc Dashboard setting changes bởi assistant. Không secrets/actual refs được đưa vào report.

## Kết quả thực tế

| Check | Result |
|---|---|
| Management API project/org/database-host matches User manifest | PASS authenticated scoped token |
| Dedicated migrator login/current_user/session_user/database/public/major17 | PASS |
| Dedicated runtime login/flags/public nonownership/no schema CREATE/no migrator membership | PASS |
| Client -> Session Pooler actual SSLSocket | PASS TLSv1.3, standard verify-full/CA/hostname retained |
| Distinct valid CA negative handshake | PASS certificate trust-chain rejected |
| Default pgJDBC hostname verifier against actual peer certificate | PASS correct host accepted, deliberately wrong name rejected; no alternate remote host contacted |
| User public namespace/history first-run guard | PASS empty/no conflicting history |
| Actual six event/function descriptors match reviewed policy | PASS |
| Migrator creator defaults/API-role rights and runtime privilege checks | PASS SQL checks in runner |
| Pooler -> PostgreSQL pg_stat_ssl | FALSE; migration remains blocked |
| Runtime inherited database TEMP | TRUE; scoped managed least-privilege review still pending |
| Unauthenticated Data API GET /rest/v1/ | HTTP401; NOT proof that Data API is disabled |
| User-reported Data API disabled | Recorded, independent settings/HTTP certification NOT VERIFIED |
| Flyway V1/history/catalog/JPA/startup/business hosted | NOT RUN / BLOCKED |
| Other four project actual authenticated preflights | NOT RUN |

All database statements ran in read-only transactions/rollback. TLS negatives issue no application SQL; correct-name/wrong-name verification uses the same pinned driver's DEFAULT verifier against a live approved-endpoint SSL session. It does not install a permissive/custom verifier or contact an unapproved hostname.

## Runner1.3.0 correction and diagnostics

Initial runtime guard rejected `SQL_TLS`: pg_stat_ssl reflects the backend connection to PostgreSQL. Behind Supavisor it does not prove the client's transport. Runner now separately inspects the real client SSLSocket/peer session using a fail-closed adapter pinned to pgJDBC42.7.4; URI allowlist, verify-full, CA SHA and the standard driver hostname/trust verification remain unchanged. No TLS factory replacement or verification downgrade.

Read-only Session Pooler preflight can observe/report backend TLS=false, but **migrate always requires backend TLS=true**. Direct-mode plaintext remains rejected even during preflight. Tests explicitly cover this distinction, so a read-only success is not permission to create Flyway metadata or business tables. The adapter uses pinned driver internals; changed driver layout fails closed instead of assuming encryption.

Guard diagnostics now preserve only approved constant error codes/SQLSTATE and never forward driver messages/credentials. Wrapper `tools/supabase-runner/run-preflight.py` explicitly loads only the selected owner's required private env keys, excludes approval files, and exposes read-only entry points only. `--runtime-check` uses a dedicated runtime connection; `--tls-negatives` performs bounded TLS probes. No wrapper migration option.

**Local regression:18 tests PASS,0 failure/error/skip** on fresh isolated PostgreSQL17.11; five canonical V1/rerun/scoped-role cases, managed event drift cases, client-plaintext rejection, mode-dependent backend-TLS guard and diagnostic redaction. Local clusters stopped/preserved. No frontend/backend business code changes; broad historical regression not counted again. V1/historical SQL/base YAML hashes unchanged.

Private evidence: `user.hosted-preflight-2026-11-16.json`, `user.hosted-runtime-readonly-2026-11-16.json`, `user.hosted-tls-negatives-2026-11-16.json`, `user.data-api-negative-2026-11-16.json` under ignored `.local/batch6b`. [Redacted summary](evidence/batch-6b-supabase-local-integration/user-hosted-preflight-summary-2026-11-16.json).

## Concrete next step

For User, propose enabling **Enforce SSL on incoming connections** under Database Settings -> SSL Configuration, then rerun preflight. [Supabase documents](https://supabase.com/docs/guides/platform/ssl-enforcement) that this setting governs both shared-pooler legs and can briefly restart the database. Assistant has not changed it; operator approval/action remains required. No bypass of backend TLS has been enabled for migration.

To reduce repeated manual SQL rounds for Hotel/Booking/Revenue/Regulations, prepared four combined read-only files in `.local/batch6b/remaining-projects-readonly`: one JSON cell includes catalogs, grants/roles and full event-function details. Source `prepare-batch-readonly.py` performs only file generation. Private `.local/batch6b/BATCH_NEXT_STEPS.md` consolidates Dashboard steps and output filenames. Notes report those projects have no data/dependencies; if that is inaccurate, do not change their API/SSL settings before review. No role/V1 operations approved for those targets by these files.

Token currently sufficient for User Project Settings Read only; no unexpected cross-project or broader write API access attempted. Other projects require exact owner mapping/security/authentication before migration; original approval gates remain. Hikari budgets, managed TEMP/other-schema access, Data API effective protection, actual backend TLS and all business acceptance remain separately tracked. Do not mark batch DONE or start the local business stack.

## Follow-up after operator completed Dashboard checklist

User migrator/runtime read-only checks rerun successfully: authenticated project identity and client TLSv1.3 PASS; backend TLS now true. The earlier backend-TLS blocker above is resolved. No migration has been approved/run.

All four remaining combined snapshots received/reviewed: PostgreSQL17, empty public business namespace, no Flyway history, all six event/function descriptors exactly match reviewed definitions and metadata. Separate target-bound review policies generated and their manifest hashes verified. Snapshot expected-project labels are operator context, not independent server identity evidence.

Authenticated GET project checks: User PASS; Hotel, Booking, Revenue and Regulations HTTP403 with current token. Independent identity verification is blocked by token access; role provisioning and migrations for those four remain NOT RUN. Operator reports Data API disabled and SSL enforced across remaining projects; settings are not independently certified. Private next-step checklist now requests one scoped-token update with Project Settings Read for exactly five targets, and no repeated SQL/settings work.

All30 protected SQL/base configuration hashes unchanged. [Current redacted checkpoint](evidence/batch-6b-supabase-local-integration/five-project-checkpoint-2026-11-16.json). Overall batch remains PARTIAL; no hosted writes by assistant.

## Scoped-token follow-up

Operator supplied three scoped PATs by project group. All five authenticated GET project checks now PASS for exact project, organization and database host; previous HTTP403 blocker resolved. Wrapper selects only the owner-group token, preserves canonical Java token interface, removes inherited group token keys, and does not retry a different token after access failure. Actual User preflight rerun PASS using the group token.

Four remaining interactive role/password setup packages prepared privately; assistant has not executed hosted SQL. Each action is project-specific, checks fresh public objects/exact event descriptors, and creates restricted migrator/app roles with scoped default ACL and native password prompts. No V1 approval. [Local verification](evidence/batch-6b-supabase-local-integration/remaining-role-setup-local-validation-2026-11-16.json): four SQL-equivalent flows, eight role logins and four existing-role rerun rejections PASS under non-superuser PostgreSQL17.11 fixture with exact six callbacks. Interactive password prompts require operator input and are not automated. Next action: operator reviews/runs prepared scripts and saves new dedicated role passwords; actual remaining JDBC checks still pending.

## All-five dedicated-role hosted read-only acceptance

Operator completed four manual role/password setups. Validated no-secret role results; filled owner-specific JDBC URLs/logins and manifest runtime/migrator metadata without modifying passwords. Actual four migrator and four runtime probes PASS, with project identity, client TLSv1.3, backend TLS and reviewed event/default ACL guards. Four additional wrong-CA/default-hostname negatives PASS; User runtime refreshed PASS. All ten dedicated roles now have successful authenticated evidence. No business tables/history exist.

Manifest inventory hashes pinned to successful per-project read-only observations. Prepared exact five-target canonical V1 plus reviewed scoped runtime-grants proposal privately, bound to manifest/V1/inventory/grants hashes; no active approval file or hosted SQL writes by assistant. Await explicit migration/grants approval, apps-stopped and exclusive-DDL-window confirmation required by original Batch6B request. Runtime inherited TEMP and operator-only Data API configuration evidence remain documented limits. Startup, pool budgets and postmigration actual catalog/DML/API acceptance remain pending.

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.
