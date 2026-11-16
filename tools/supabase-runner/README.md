# Supabase runner 1.3.0 — PostgreSQL17 follow-up

Daily application startup is through `docker/stack.ps1 -Environment Supabase`
or `-Environment Test`; see [the current Docker entry point](../../docker/README.md).
This folder also retains guarded migration/acceptance/operator tools. Those
tools are not a daily startup checklist and must not replay completed fixtures.
Application runtime values come from `docker/.env`; private management/migrator
inputs, approvals and historical evidence stay under `.local/batch6b`.

Standalone Java21 operator tool; application POMs, Spring Boot, canonical V1 and historical migrations are unchanged. Flyway core/PostgreSQL module are both pinned10.22.0. PostgreSQL17 support was introduced in [Flyway10.20](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine). Supported-major gate14..17;18+ still blocked. Runtime pgJDBC remains42.7.4. This targeted upgrade does not claim latest-version security certification or hosted acceptance.

Build from backend root:

```powershell
mvn -B -ntp -f tools/supabase-runner/pom.xml package dependency:copy-dependencies '-DincludeScope=runtime'
java -cp 'tools/supabase-runner/target/supabase-runner-1.3.0.jar;tools/supabase-runner/target/dependency-10.22.0/*' com.checkinn.db.HostedRunner preflight user
```

Use the versioned dependency folder. The old `target/dependency/*` may contain10.10 jars; do not combine old/new wildcard classpaths. Credentials stay in the runner process environment, not arguments; no `.env` is implicitly loaded. Current private target lacks dedicated-role credentials intentionally, so hosted preflight remains blocked. Missing/incomplete input stops before network. Exact project/mapping/DDL approval remains mandatory for migrate.

Real local acceptance (new owned cluster/data, never the existing17/18 data directories):

```powershell
$env:BATCH6B_PG_BIN='C:/Users/ACER/IdeaProjects/DA1/checkinn/.local/tools/postgresql-17.11/pgsql/bin'
python tools/supabase-runner/test-local.py --canonical-roles
```

This tests the same canonical Flyway configuration, five V1 applies/reruns and generated SQL role/grant plans using real PostgreSQL17.11. All local role names, credentials and databases are synthetic. Cluster is stopped in finally; files/logs remain ignored. Actual Supabase TLS/Supavisor/managed permissions/API exposure are separate gates. A plain local PostgreSQL test cannot certify them.

Prepare **review-only** per-project SQL/env examples:

```powershell
python tools/supabase-runner/prepare-role-review.py --output .local/batch6b/role-review-2026-11-15-runner1.1.0
```

The generator refuses existing output directories, enforces ignored `.local` output, validates five distinct refs/V1/CA hashes and PostgreSQL major17; never connects or generates passwords. It does not fill active target login/runtime roles or credentials before actual role creation. Private review packages contain exact project references, hashes and separate runtime/migration env examples with empty passwords.

Review stages:

1. `00-admin-readonly.sql`: run only after verifying the actual Dashboard project; exports one JSON cell with catalog/ACL/role/trigger facts, no business rows or password columns. It does not prove project identity or Data API exposure. Save output privately.
2. Review authenticated mapping, managed objects/event triggers/functions, Data API/exposed schemas and admin capability. Current runner stops on active event triggers/unexpected public objects; exact reviewed allowlists are still pending. No automatic disable/revoke of managed Auth/Storage access.
3. **Only after explicit per-project role/security approval**, `01-provision-roles.sql` creates dedicated migrator-as-owner/app roles with NOLOGIN, narrow public usage/create rights, secure creator default ACL and no app owner membership. Proposed connection limits2/4 are caps to review, not verified Supabase budgets. Any existing proposed role/history/canonical table stops the transaction. No role reuse, managed DB CONNECT/TEMP revocation, app default DML or business tables.
4. `02-enable-logins.psql`: separate approved admin psql session, interactive `\password` prompts with SCRAM, then LOGIN. This is not SQL Editor syntax. No password literals in files/chat/arguments. Interactive prompts were not automated in local acceptance; local tests use synthetic credentials to exercise actual role logins.
5. Recheck authenticated identity/security/metadata, then obtain a **separate canonical V1 migration approval** and run the guarded migrator. Role approval is not migration approval.
6. After V1 validate/history PASS, `03-postmigration-grants.sql` runs as dedicated migrator and grants only exact canonical tables/sequences. No `ON ALL TABLES/SEQUENCES`, no schema ownership change, no TRUNCATE/REFERENCES/TRIGGER/sequence UPDATE. Append-only/read-only restrictions preserved, Flyway history denied to app/API roles.
7. `04-readonly-verification.sql`, real API negatives, required CRUD/JPA/business and broker/cache acceptance. Local NOLOGIN/default/grant tests do not prove hosted role provisioning is allowed.

Mutation scripts require operator-supplied `checkinn.approved_project_ref`, `checkinn.approved_service`, `checkinn.security_reviewed` settings and exact database/current-role/major checks. These settings are attestations, **not server project identity verification or authorization by themselves**; do not set them until independently reviewed approval exists. The generator never sets them automatically.

PUBLIC database rights can be inherited despite role NOINHERIT. In particular TEMP or managed-schema access needs effective hosted review; scripts intentionally do not revoke maintenance database privileges globally. Tests prove denial of persistent DDL in canonical public, not every possible temporary/managed-schema operation. Do not mark runtime least-privilege acceptance complete from the proposed scripts alone.


## Runner1.2.0: exact per-project event-trigger review

User-only private policy now binds owner/project/major/V1 hash and full reviewed function descriptors. Optional target properties `user.eventTriggerReviewFile` and `user.eventTriggerReviewSha256` point to the ignored JSON; no copies for other projects. Main identity check validates the policy and live event/function rows in its read-only transaction on **every physical Flyway connection**, and first-run preflight repeats the check. No policy still rejects active triggers. Exact six-name/schema/tag/invoker/empty-search-path shape, complete definition/ACL/owner/config/flags and set equality are required; unknown/missing/changed objects stop. Do not auto-generate a policy from an unreviewed database or approve solely by names.

Static policy review is NOT project identity proof, Data API security PASS, role provisioning approval or migration approval. The User V1 checksum contains no extension lifecycle DDL; extension hooks with broader privilege effects are reviewed only within that scope. PostgREST watcher bodies only notify schema reload in the supplied snapshot. The initial broad stop on active event triggers remains for unreviewed targets; for User it is replaced by exact scoped verification, never disabling/dropping provider objects.

Current16-test local suite includes synthetic six-trigger fixtures, body/owner/ACL/config/flag/extra-trigger drift rejection and V1/rerun using a nonsuperuser migrator. Actual managed callback execution/TLS/Supavisor still awaits approved hosted acceptance. See [User review report](../../Docs/BATCH6B_USER_EVENT_TRIGGER_REVIEW_2026-11-15.md). Historical1.1.0 role-review packages remain SQL drafts, with unchanged role/grant scripts.

One-project preflight still requires five distinct project mappings, but only the selected owner needs configured login/credentials. Duplicate nonblank configured logins remain blocked. Unconfigured other-owner logins are not treated as authorization or established database roles.


## Runner1.3.0 — actual client vs pooler backend transport

Use the explicit selected-owner private loader for read-only checks (no migrate option):

```powershell
python tools/supabase-runner/run-preflight.py user
python tools/supabase-runner/run-preflight.py user --runtime-check
python tools/supabase-runner/run-preflight.py user --tls-negatives .local/batch6b/tls-negative-distinct-ca.crt
```

Client TLS proof reads the actual SSLSocket/peer session through a fail-closed pgJDBC42.7.4 adapter; no SSL factory/hostname verifier is replaced. Direct plaintext is rejected. A Session Pooler read-only run may observe backend TLS=false and report the migration blocker. MIGRATE still requires backend TLS=true on each physical connection; no environment/approval flag bypass exists for it. Actual User client TLSv1.3 and wrong-CA/default-hostname negatives PASS; backend TLS=false is observed, not waived. Runtime PUBLIC TEMP inheritance and unauthenticated REST401 are explicitly not treated as full security PASS.

Latest local suite18 PASS. [Actual hosted report](../../Docs/BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md). Inspectors for remaining projects can be generated with `prepare-batch-readonly.py`; one JSON per owner includes metadata AND function details. No additional cloud settings/DDL are performed by these helpers.

## Scoped token groups and remaining role setup (16/11 follow-up)

`run-preflight.py` selects `USER_REGULATION_TOKEN` for user/regulations,
`HOTEL_BOOKING_TOKEN` for hotel/booking, and `REVENUE_TOKEN` for revenue.
A configured group token takes precedence over `BATCH6B_MANAGEMENT_TOKEN`;
an invalid configured token fails without retrying a broader token. Only the
selected token is passed to Java under the canonical environment key; group
token names inherited from the parent environment are removed.

All five authenticated project/org/database-host checks now PASS. User backend
TLS is now true after the operator changed SSL settings; the earlier false
observation above is historical. Remaining four snapshots have separate reviewed
policies; they do not establish dedicated JDBC roles or migration approval.

`prepare-remaining-role-setup.py` prepares four private interactive psql/PowerShell
files after checking identity evidence, exact review-policy and V1/CA hashes.
It never connects or executes SQL. Each manually executed file checks fresh
public objects and exact six-event descriptors, creates two restricted roles,
sets scoped defaults, prompts for passwords with native psql, and enables LOGIN
in one transaction. Existing roles stop execution. It never creates business
tables or executes V1. Local SQL-equivalent tests substitute synthetic passwords
only in copies: four flows, eight role logins and four rerun rejections PASS;
native interactive password prompts are not automated.

## Approved hosted V1 acceptance — latest16/11 checkpoint

All five targets now have successful canonical V1/validate/immediate no-op, reviewed runtime grants, catalog/privilege negatives and standalone Hibernate validation. Earlier blocked/unconfigured statements above are historical. The first-run `preflight` intentionally rejects existing Flyway history after migration; do not erase history, reset inventory or rerun migrate as a workaround. Use postmigration catalog/runtime validation for the new state. Private approvals are expired/disabled; a completed execution is not reusable authorization. [Hosted acceptance](../../Docs/BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md).

User's initial libpq grants connection hit the dedicated migrator connection cap; reviewed grants were applied via guarded JDBC after stopping/reviewing the attempt. Caps/SSL stayed unchanged. Readiness flags, pool budgets, full local infrastructure/startup/workflows remain pending. Overall batch remains PARTIAL.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../Docs/BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](../../Docs/evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json).

## Sử dụng stack hiện tại — không migrate lại

Frontend: http://localhost:3000 ; Gateway: http://localhost:8080 ; Eureka: http://localhost:8761 ; Mailpit: http://localhost:56825 . Có thể đăng ký account development và xem OTP ở Mailpit; không email ngoài. Không dùng payment redirect để thử provider thật: merchant/HMAC key hiện chỉ fake cho callback local. Storage upload chưa cấu hình/fail closed.

Từ backend root PowerShell:

```powershell
python tools/supabase-runner/run-local-stack.py check
# Dừng đúng JVM/Next processes của session, giữ dữ liệu:
python tools/supabase-runner/run-local-stack.py stop
python tools/supabase-runner/run-local-stack.py stop-deps
# Bật lại đúng container có ownership label, giữ vhost/volumes:
python tools/supabase-runner/run-local-stack.py start-deps
# Chờ Rabbit/Redis healthy; launcher kiểm health/label trước start:
python tools/supabase-runner/run-local-stack.py start eureka notification media auth regulations user hotel booking revenue gateway frontend
python tools/supabase-runner/run-local-stack.py check
```

Không xóa private runtime.private.json/.env/targets/review/CA để reset một session đang chạy. Prepare chỉ chứng nhận fresh namespace lần đầu; không tái gắn nhãn queues/cache populated. Reuse đúng session sau restart không phải replay broker cũ sang DB khác. Không chạy legacy docker stack/SQL migrations để chữa Supabase. IntelliJ nạp owner-only runtime vars và profile supabase-dev; không dùng migrator datasource cho ứng dụng.

Acceptance đã seal. Hosted acceptance scripts từ chối write trên session hoàn tất; cần một lượt development mới được phê duyệt, fixture identifiers riêng trước khi lặp lại. Không reset loyalty balances của account đã tương tác hoặc xóa test rows. Sources ở tools/supabase-runner, helper Java ở acceptance/java; compile JDK21 với standalone runner jar/dependency10.22 và runtime library jars của service. Không đưa secrets vào java arguments. OTP/JWT/fixture secrets chỉ private, không paste raw logs.

Backend regression cuối: từng owner chạy `mvn -o -B -ntp -f <owner>-service/pom.xml test '-Dtest=!*ApplicationTests'`. Không dùng default context với database demo. Opt-in local canonical tests giữ guard loopback/name/data-dir, không chuyển test tạo/migrate local DB sang hosted. Frontend: `npm run test:regression` và `npx tsc --noEmit --incremental false`.
