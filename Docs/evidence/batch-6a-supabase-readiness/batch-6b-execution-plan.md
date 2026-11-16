# Batch6B execution plan — NOT EXECUTED / NOT READY

14/11/2026. This is a reviewable future runbook, not authorization. No command below was executed against hosted resources. Five-project topology/U03 fresh-no-import already approved, do not ask again. New6B authorization and missing deployment metadata remain required. No extra Supabase projects, no Place edits, no historical migration changes.

## 0. Inputs and artifacts before mutations

Obtain privately for each P_USER/P_HOTEL/P_BOOKING/P_REVENUE/P_REGULATIONS: existing project mapping/region, exact direct/session Connect tuple and login format, server version, plan/compute/server/pool limits, exposed schemas/API settings, backup/PITR/retention/restore destination. User supplies metadata/secret-manager references or configures approved access, never passwords in chat. Select runtime/infra hosts, egress IPv4/IPv6, replica/surge/concurrency, DNS/TLS, storage mapping, alert owner, RPO/RTO/window. Freeze old writers/queues only at separately approved cutover; preserve demo DBs.

Pin backend source/release SHA and all V1 SHA from source-inventory.json; rebuild release and record dependency tree. Confirm actual PostgreSQL support decision using dependency-compatibility.md. Fresh acceptance and minimal runner version adjustment if required; no framework-major upgrade. Review canonical overlay: per-service CANONICAL_* secret scope, no migration secrets in runtime, explicit profile canonical/Flywayfalse/Hibernatevalidate/SQLnever/UTC/no SQL logs. Reject CLI/configserver/SPRING_DATASOURCE overrides, local fallback, shared owner credential refs. Keep front-end build/runtime API variables distinct.

### Missing implementation to complete within authorized6B before DDL

Current repository has no hosted runner/overlay. Implement a dedicated standalone Java21 runner (proposed artifact `.local/batch6b-tools/hosted-migrator.jar`, **not present** in6A), containing selected pinned Flyway core+PG module+pgJDBC, no app startup/tests/seeds. Do not adapt5B tests by relaxing loopback guard. Review artifact/code before using commands below:

1. Read private OWNER_TARGET_MANIFEST and role secrets via environment/secret mounts, never command line values. Manifest holds alias→approved project/control-plane identity, exact JDBC tuple/tenant login, database, current_user/session_user under selected role mode, server major/schema, CA reference, source SHA/V1 SHA, expected preflight inventory hash and default ACL plan. Reject missing fields/unknown alias/duplicate project mappings/non-SSL/local targets/unapproved Flyway options.
2. Wrap DataSource.getConnection: before returning **every connection to Flyway**, verify approved immutable URL/login/TLS config, current_database/current_user/session_user/server major/public schema. NO Flyway metadata writes before guard; close on mismatch. Owner SET ROLE, if chosen, applied/verified on every connection and creator matches default ACL plan. No SHOW data_directory or privileged settings role.
3. Acquire external deployment mutex scoped alias/environment (no unintended PostgreSQL preflight writes). Independent read-only control-plane identity verification must match approved mapping; SQL database name/IP alone insufficient.
4. First-run preflight catalog snapshot from preflight-readonly.sql + full types/defaults/identity/sequences/views/extension ownership/functions/triggers/policies/ACL. Require no business/history and only exact reviewed managed allowlist. Recheck immediately on guarded migration connection before migrate; prevent other deploy jobs/admin DDL during window. Unexpected object/history → fail; no baseline/drop/repair.
5. Configure Flyway only approved filesystem location for one owner, schemas/defaultSchema public, createSchemas=false, baselineOnMigrate=false, cleanDisabled=true, outOfOrder=false, validateOnMigrate=true. No auto callbacks/extra locations/ignore patterns; role setup only reviewed. Modes preflight/info/validate/migrate/rerun must propagate nonzero errors and sanitize all exceptions.
6. `migrate` first-run requires exactly1 applied SQL V1; `rerun` requires prior successful exact V1/catalog/hash and returns0 migrations after validate. Never use initial empty guard for rerun or bypass failure by deleting history. Dry negative runner tests on owned disposable local targets: wrong host/db/alias/login/version/CA/hash/location/unexpected table/history and simulated second connection mismatch must stop before any mutation.

These requirements plus commands below specify implementation; artifact availability/testing is a hard blocker, not a claim of completed tooling.

## 1. Read-only project/security preflight (G0)

Confirm authenticated project identity and exact Connect metadata for all five; no credentials discovered in .env may be used. Dedicated direct migration endpoint or tested session alternative. TLS wrong CA/hostname negative test without SQL. Run catalog preflight using approved read-only login; default_transaction_read_only where supported, explicit READ ONLY transaction. Read Data API/exposure/security/backup metadata. Publish aliases/results only, full catalogs restricted. Inventory existing functions/security-definer paths/triggers/policies and managed extensions; no literal empty-public assumption. If any project nonclean/unexpected, stop entire migration stage for review. No auto baseline/clean/repair.

Compute each pool budget (replicas including surge × max + migration2 provisional + operational headroom <= app-usable budget) plus pooler client/backend constraints. Confirm supported custom role authentication on chosen mode, admin ability to provision owner/migrator/app and creator defaults. Verify actual backup/recovery capability and isolated drill destination. G0 NO-GO if unknown.

## 2. Security provisioning and identity recheck (G1)

Required permissions: approved project admin for scoped roles/memberships/ownership/schema/default grants and approved exposure settings; migrator USAGE/CREATE public + intended owner membership/history; app CONNECT/USAGE/explicit DML/sequence only; backup read role separately. Never assume SUPERUSER or unrestricted managed postgres.

Review roles-and-grants-plan.md and supabase-security-model.md. Provision unique logins through supported admin workflow, secret manager and secure password handoff. Choose supported NOLOGIN owner/SET ROLE or reviewed migrator-owner alternative. Deny API business access/creator defaults before V1; preserve managed Auth/Storage. Verify global+schema default ACLs and effective memberships. If Data API disable/removal needed, act only in authorized6B after Media mapping review;6A made no changes. No blind local REVOKE CONNECT/TEMP template use.

Reverify each target identity and preflight hash after provisioning; scoped role/default ACL changes are expected, unexplained objects not. G1 requires security plan effective before tables can appear, version decision and tested runner/overlay. No apps started.

## 3. Exact migration order / command contract

Independent FKs mean no SQL inter-project dependency; serialize for evidence and bounded recovery: **User → Hotel → Booking → Revenue → Regulations**. Stop at first failure. Recheck identity/clean catalog before EACH owner, not just first.

Private env per invocation: OWNER_TARGET_MANIFEST, FLYWAY_URL/USER/PASSWORD, EXPECTED_OWNER_ALIAS, MOUNTED_CA, approved major/schema/V1 SHA supplied via manifest. Per-service runtime variables detailed in secrets-configuration-audit.md. Do not put secrets/URLs/ref values in flags or logs.

Future PowerShell from backend, only after runner implementation/tests and G1:

```powershell
# Private environment already injected for this ONE owner by deployment system.
# Commands deliberately never load docker/.env or local5B credentials.
$owner = 'user' # then hotel, booking, revenue, regulations, each new isolated job
$location = (Resolve-Path "$owner-service/src/main/resources/db/canonical").Path
java -jar .local/batch6b-tools/hosted-migrator.jar --owner $owner --location $location --mode preflight
if ($LASTEXITCODE -ne 0) { throw 'Preflight failed' }
java -jar .local/batch6b-tools/hosted-migrator.jar --owner $owner --location $location --mode info
if ($LASTEXITCODE -ne 0) { throw 'Info failed' }
java -jar .local/batch6b-tools/hosted-migrator.jar --owner $owner --location $location --mode migrate
if ($LASTEXITCODE -ne 0) { throw 'Migration failed; preserve evidence' }
java -jar .local/batch6b-tools/hosted-migrator.jar --owner $owner --location $location --mode validate
if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
java -jar .local/batch6b-tools/hosted-migrator.jar --owner $owner --location $location --mode rerun
if ($LASTEXITCODE -ne 0) { throw 'No-op rerun failed' }
```

Flyway API inside future runner: `Flyway.configure().dataSource(guardedDataSource).locations("filesystem:"+location).defaultSchema("public").schemas("public").createSchemas(false).baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load()`; mode routes to info/validate/migrate with distinct first-run/rerun gates. This is dedicated runner execution, not app startup, and not an existing downloadable artifact. Standard CLI `flyway migrate` alone is insufficient for same-connection guard, so not offered as an unguarded workaround.

## 4. Catalog/grants verification (G2)

Per owner exact table/column counts: User7/59 Hotel24/188 Booking7/118 Revenue5/66 Regulations3/27. Compare every column/type/typmod/nullability/default/identity, FK/check/unique/index expression/predicate/validated state, sequence definition/ownership and schema against canonical spec/current V1 and5B catalog manifests; account for PG-version catalog representation differences explicitly. No extra business objects. History exactly1 successful SQL V1 with expected script/version/checksum (Flyway checksum separate from SHA256), no baseline/failure/extra. Validate + deliberate rerun no-op. Managed objects unchanged.

Apply explicit canonical table grants and append-only denials, sequence rights, history denial; verify actual owner/default ACL matches creator and no API grants. Snapshot catalog+ACL checksum/timestamp. A failed migration may roll back transactional DDL but must be inspected, never assume; no repair/clean/fix-up SQL without review. G2 all five PASS before runtime.

## 5. Infrastructure isolation and runtime order

Provision NEW broker vhost/credentials/queues/DLQ and Redis boundary before any app can connect; deny old vhost/cache endpoints. Verify empty new business queues, no legacy pending messages; no deleting old queues/data. Host infrastructure decision is outside PostgreSQL migration. Configure TLS/private networks and secret scopes. Deploy Config Server only if chosen and pinned; otherwise disable imports. Bring Eureka/monitoring, Redis/RabbitMQ, approved Media/Notification dependencies up privately. Initial one replica each, no autoscale/surge beyond budget.

Runtime startup order: **Regulations → User → Hotel → Revenue → Booking → Auth → Gateway → frontend**. Media available before Hotel media calls; Notification before OTP smoke. Circular User recovery→Booking/Hotel review→Booking means startup health may precede RPC readiness; gate actual RPC acceptance after all owners start. Regulations publishes policy seeds before consumers may subscribe; explicitly verify late consumer fetch/cache hydration, do not rely on lost seed events.

Each owner uses freshly built release JAR/container, explicit canonical profile and JVMUTC, runtime login only. For direct JAR example (secrets preinjected; choose one known artifact):

```powershell
java -Duser.timezone=UTC -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=canonical --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never --spring.jpa.show-sql=false
```

Equivalent container manifest must enforce same settings, budget, no Config Server/CLI override, no default profile fallback. Other four analogous owner JAR names. Long-running startup managed by chosen host, not unbounded local shell. Record no Hibernate schema DDL/no Flyway migration during startup. Health UP alone insufficient: logs show Hibernate validation, actual expected profile/DB alias/role and no startup guard violation. Never expose config values. Cron jobs currently inherit JVM zone; UTC deployment may shift schedules, so review business intended Asia/Ho_Chi_Minh schedule behavior before traffic rather than silently changing business policy.

## 6. Connectivity/workflow/security acceptance (G3)

Verify REST/Gateway actor matrix, Auth→User HTTP/gRPC, Booking→Hotel hold/catalog HTTP, Booking→User Loyalty, Hotel→Booking review eligibility, User→Booking recovery gRPC, Revenue→Hotel gRPC, Media/Notification, Regulations fetch/event propagation. Test DNS/discovery/private transport/service JWT/revocation/timeout and late subscriptions. No direct JDBC cross-project access.

Fresh fixtures via approved supported flows, never import demo SQL or guess stock/admin privileges. Check UUID v3 deterministic retry/v4 refs/string wire and immutable provider refs. Synthetic fresh booking with reviewed stock and loyalty: retry same key→same booking/hold; no oversell concurrent holds; payment/receipt duplicate→one CREDIT; cancellation/refund approval/internal execution (only if sandbox flag separately authorized)→one DEBIT; loyalty reserve/use/earn/reverse; release/expiry; Regulation snapshot consistency; Revenue raw invalid event quarantine. VNPay sandbox test only with merchant authorization, no real refund/provider call inferred from prior mocks. Include positive Review→Booking gRPC and Gateway/browser path or explicitly block those traffic surfaces. Place changes remain excluded.

Controlled restart/outage in approved staging window: broker pending outbox survives/replays exactly once; Redis auth fails closed; DB outage recovery does not duplicate financial write; one-node seed restart no duplicate policies. Multi-replica contention needs separate test before scaling. Never repoint5B harness at hosted targets; implement identity-guarded new fixtures/tests.

Security negative matrix every owner: runtime CREATE/ALTER/DROP/schema/history/escalation denied; immutable UPDATE/DELETE denied; required CRUD succeeds in rollback fixture transaction. Effective API-role table/column/sequence/function privileges false; real Data API guest/authenticated/service-role requests denied (empty200 not proof). Wrong-owner credentials fail against other approved targets. TLS wrong CA/hostname fails. Valid constraint-satisfying negative DML always forced rollback if unexpectedly allowed. No live destructive DROP probe of business tables: use privilege checks and disposable test objects under guarded staging transaction. No raw keys/PII/callback signature in evidence.

Confirm fresh broker/cache separation again; actual alert delivery, Hikari/DB/outbox/DLQ/quarantine/payment/loyalty/holds metrics visible. Run isolated restore drill or separately approved drill batch with evidence before traffic. G3 all required gates PASS; any unknown/unsafe exposure/financial mismatch NO-GO.

## 7. Traffic, observation, rollback and evidence

Explicit traffic approval checkpoint with domain/platform incident owner, agreed observation duration/SLO/RPO/RTO and validated rollback procedure. Record first application write including seeds/fixtures and first customer financial write separately. Retain old demo stack isolated, no cache/event replay or automatic DNS flip-back. Gradual controlled traffic; alert on variance, unresolved intents, pool timeouts/dependency failures. Post-write incident freeze/quarantine/reconcile as cutover-rollback-plan.md, never blind rollback/clean/drop/history repair.

Evidence6B separate directory with release/config hash, alias mapping attestations, timestamped preflight inventories, TLS tests, version support decision, pool budgets, role/ACL/API negative results, runner version/commands/exit/log redaction,5 migrations/history/catalog/no-op, Hibernate/startup, connectivity/workflow/outage/restore/monitoring/traffic decisions and financial reconciliation. Keep private identity/credentials/full dumps/raw sensitive logs outside public report. Do not stage/commit/push unless separately requested. Stop after agreed6B scope.
