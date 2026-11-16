# Migration Implementation Plan — Revised Batch 4.1

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.

> Historical status (superseded by checkpoint 16/11/2026): Current: Batch5B local canonical acceptance DONE (14/11/2026); see appended checkpoint. Older statuses are dated history. Supabase/Batch6 not executed.

> Historical status (superseded by checkpoint 16/11/2026): Current status: Batch5A U01/U03/U04 APPROVED;38 source conversions implemented, PARTIAL acceptance. Earlier pending/proposed statements below record Batch4.1 history; see Batch5A checkpoint at end. No database migration applied.


13/11/2026, Asia/Saigon. **Design plan, NOT EXECUTED.** Three separate batches; no automatic implementation/deployment permission from this document. [ID contract](ID_STANDARDIZATION_CONTRACT.md), [five-project architecture](SUPABASE_DATABASE_ARCHITECTURE.md), [canonical V1](CANONICAL_DATABASE_SCHEMA_V1.md), [per-column impact matrix](../evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md).

## Gates và ownership

Five existing Supabase projects, one owner database each: User/Loyalty, Hotel, Booking/Payment/Refund, Revenue, Regulations. Proposed public namespace per database với Data API/grant gates, histories independent. Auth no database project, Place excluded. Local Batch5B five fresh DBs, not one DB/multiple service schemas. Previous shared-project plan superseded.

U01 approval of38 UUID columns + exceptions/generation trước5A; U02 hashes/wire/pending-event compatibility; U03 fresh/no-import versus owner-reviewed legacy mapping; U04 ratePlanId semantics stays String. Existing D01 money/D02 rate/D03 time/D04 room deletion/D09 seed decisions chốt trước5B SQL freeze; D07 recovery và D08 concrete cutover trước6 live. User topology confirmed, version/network/roles/pool certification still pending. No password requested in prompt.

## Batch 5A — UUID Contract Implementation (chỉ sau user design approval)

### Proposed implementation sequence

1. Freeze approved per-field matrix and exact old/new fixtures. Characterize existing UUIDv3 deterministic booking/hold IDs, UUIDv4 random path, command/hash recipes, signed provider callback params and event payloads. No new UUIDv4-only validator.
2. Add shared semantic UUID parser/serializer boundary behavior without modifying protected Place contracts; REST/gRPC strings/tags remain stable. User JWT human UUID sub distinguished from service subjects. Keep all nullable legacy contracts and raw receipt/system actor exceptions.
3. Implement Booking/Item/Payment/Refund entity key types and repositories UUID, associations keep entity types; update scoped lock methods, native/JDBC UUID setObject/getObject, aggregate maps/equality/DTOs/services/controllers/client adapters. Four entity PKs and dependent columns in same module coherent. Compile/test offline fixtures; **never start new code against old varchar database**.
4. Update User Loyalty booking fields/repository reservation PK to UUID; retain command_key String `bookingUUID|TYPE`, exact lock order and reversal/dedup behavior. Preserve nullable points_transactions.booking_id. Client/RPC getLoyaltyBookingState must accept UUID canonical text.
5. Update Revenue accepted projections/cash/gaps typed references + UUID binds; preserve raw revenue_event_receipts.booking_id TEXT and original JSON/hash for invalid events. EventId/source versions/CREDIT/DEBIT semantics unchanged. Event producer and both Revenue/Notification consumers validate entity UUID strings consistently; legacy String transport DTOs may stay only with semantic UUID adapters. Do not recompute pending event receipts/hashes.
6. HotelReview already UUID: test Booking eligibility/review route with target UUID including deterministic v3, keep no new FK or eligibility rule. Hotel/User actor refs already UUID, Media polymorphic targets unchanged; review mixed/system actor callsites before converting human-only actor fields.
7. Frontend keeps string transport types/URLs/query keys/storage/NextAuth identity; add explicit entity-ID validation/type aliases only where approved, no Place/shared-contract edits. Preserve provider parameters and current creation-command retry behavior. No frontend refund UI expansion assumed.
8. Update all affected unit/native/integration fixtures/caller contracts, duplicate proto descriptors and generated client build artifacts through normal build in implementation batch. Use real UUID valid fixtures; retain intentional b1/payment1 etc only for malformed/legacy negative tests, never blindly replace provider refs.

### Compatibility release gate

5A delivers code and tests as a coordinated release candidate, **not deployed onto existing schema**. API JSON/proto string compatibility does not make old String JDBC bindings compatible with native uuid columns. Target application and5B schema must be staged together. Approved plan must choose quiesced fresh cutover or versioned adapters/dual compatibility, not mixed old/new producer/consumer rollout by assumption.

Required tests: generator golden vectors, same-key same-payload duplicate/lost-response retry, key conflict, uppercase and malformed UUIDs/no side effects, auth401/403/404, UUID vs String equality/maps/JDBC binds, full callback signed raw parameters, preserved original receipt, one refund/outbox/DEBIT, loyalty command/reversal/recovery, review checked-out eligibility. Compare requestHash bytes and command lock names; unchanged public text for the same accepted identifiers. Include system actors, invalid raw receipt booking ID, non-UUID provider refs, composite code keys and Regulations bigint fixtures. Do not rehash/import old audit payloads to normalize UUID case.

## Batch 5B — Canonical Flyway V1 / Five Fresh PostgreSQL Databases

Entry: 5A approved code candidate, all target types/exceptions and D01–D04/D09 resolved, clean target allowlist. Provision five **new isolated local databases** on disposable PostgreSQL (single local server permitted), each owner roles/public namespace; old demo/developer volumes/databases intact. Pin target major/dependencies, same target topology as five hosted DBs.

### Independent canonical chains

Propose per service `src/main/resources/db/canonical/V1__baseline.sql`, full owner baseline, then monotonic V2/V3. Names are plan only; Batch4.1 creates no SQL. Exclusive Flyway location per service, independent `public.flyway_schema_history` in correct database, external migration job role. Fresh migrate executes V1 DDL; no baseline(version1) marker before it. Future baselineOnMigrate=false, outOfOrder=false, validateOnMigrate=true, cleanDisabled=true. [Flyway baseline behavior](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

Original User V2–V5, Hotel V1–V5, Booking V2/V3/schema.sql/manual/root migrations preserved byte-for-byte and not scanned together with canonical V1. No checksum repair/history copy, no marking legacy chain as applied. Existing environment keeps old profile/location until approved retirement; canonical profile added separately. Unexpected non-empty target/history stops job; never baseline/clean to conceal wrong target. Add Flyway dependencies explicitly to services lacking activation; directory presence is not proof enabled.

| Owner | Internal object order | Runtime checks |
|---|---|---|
| User | users/loyalty_points→user_profiles; otps/points ledger→commands/reservations | UUID booking refs, reservation PK, command dedup/legacy ambiguity/recovery |
| Hotel | provinces→cities→hotels→rooms; dictionaries/content; reviews→feedback/response; availability/holds; catalog→mapping requests→codes/states; inventory requests→states; accommodation; feature receipt last | named guards, full review CHECK/FK, room deletion decision, explicit stock/no10 |
| Booking | bookings UUID→items/payments UUID→refund UUID→execution→audit; event sequence→outbox | typed local FKs, provider uniqueness, recovery intents, internal refund restrictions/outbox ordering |
| Revenue | projections UUID references→cash/receipts/gaps | accepted UUID refs, raw receipt TEXT exception, CREDIT/DEBIT/source dedup/quarantine |
| Regulations | bigint identities/current policies + snapshots→outbox UUID | key/version optimistic concurrency, TEXT serialization, atomic snapshot/outbox |

No cross-database FK, FDW/dblink/shared transaction/central history. FK only inside own DB, same underlying PK type; composite amenity code remains varchar. Outboxes and audit target scalar relationships retain current ownership/lifecycle, no cascade change to make UUID migration easy. Named Hotel guards must pass; namespace/table-OID hardening scoped future review, not disabled.

### Seeds và JPA validate transition

No production customer/booking/payment/refund/cash/points/inventory demo rows in V1. No root fix/sample execution, NULL→10, guessed mapping/receipt/policy history. Reference categories/locations need approved source. Regulations existing policy seeds15/0.10/10000/500 are source behavior, not a new policy approval; D09 choose one idempotent seeding authority preserving snapshots/outbox/version. Compatibility marker inserted only after full feature schema, no fake historical applied receipt. Test fixtures separate opt-in location/credentials.

Migration-only first, no JPA schema creation. Capture normalized metadata/constraints/indexes/roles/sequences/history/checksums. Replay migrate leaves schema/business data unchanged. Test disposable transactional failure rollback and checksum mismatch stop. Start each app under separate canonical profile with JPA validate, explicit public currentSchema/default_schema, app role no DDL, runtime Flyway disabled if external jobs selected. User currently validate; other current settings unchanged until implementation passes; no global update override.

### Acceptance gates

- All32 non-Place entities,46 app tables/458 columns, per-project namespace/history and approved38 uuid changes, exact PK/FK/reference types + exceptions.
- All repositories/JPQL/native/JDBC/guards/schedulers/recovery paths run under owner app roles; UUID-varchar operator errors absent, indexes usable without blanket column::text casts.
- App denied DDL/history writes; web roles no business direct Data API privileges; no managed/Place changes. Local roles and hosted roles are separate acceptance steps.
- Inventory first-row concurrency/expiry/hold/release/confirm/no oversell; Booking canonical pricing/ownership/compensation/idempotency; callback retry/Internal Refund immutable receipt/exactly one DEBIT; Revenue ordering/reconciliation; Loyalty reversal/recovery; Regulations atomic snapshot/outbox.
- Money/rate golden tests, time zones and JSON/native/JPA roundtrip unchanged. Batch1.6/2 evidence historical only, rerun on new DB/code hashes.
- Fresh replay/catalog and restore on isolated target, no existing DB mutation. Static Batch4.1 checks do not substitute runtime results.

After V1 applied: never edit; V2+ additive expand/deploy/backfill from authoritative evidence/validate/tighten. New enum CHECK values before writers. Concurrent index special migration handling reviewed separately. Legacy import is separate mapped/reconciled workstream, not automatic UUID cast/drop bad rows.

## Batch 6 — Deploy to Five Existing Supabase Projects

Entry: 5A/5B passing concrete artifacts, privately mapped five project identities, approved namespace/security plan and D07/D08. Projects exist per user; connectivity/configuration/permissions/plan/PG version not assumed.

1. Verify five endpoint/database identities, independent migrator/app secrets through designated secure channel; no credentials/project URLs in reports or chat. Direct/session/IPv4/IPv6/SSL verified per project, app Hikari budget per replicas/plan.
2. Verify proposed public Data API disabled/restricted/grants/default privileges/RLS behavior and managed components untouched; if blocked, revise namespace within same five-project topology before DDL.
3. Backup/restore policy per project, capture old deployment and broker/outbox/checkpoint state; allowlist rejects developer/old/shared target.
4. Run owner-specific Flyway chain to correct existing project fresh app namespace only, capture checksum/history, then restricted-role JPA validate/startup/query tests.
5. User/Hotel/Regulations RPC ready; Revenue/Notification consumers then Booking producers/Gateway; no cross-project FK/order assumption. Run full designated end-to-end auth/booking/payment/sandbox/internal/revenue/loyalty/regulation/failed-network acceptance, not provider real refund settlement.
6. Rehearse per-project and coordinated restore/reconciliation; concrete cutover review only after test results/config diff/window are reviewable. Approval is deployment authorization here, not requested in Batch4.1.

### Cutover và rollback

Quiesce old/new ingress/writers/schedulers/consumers, decide drain/isolate existing broker messages and pending commands/receipts. New UUID schema/code release atomic at system boundary; no old String worker receives new DB. Coordinate cache/storage sessions and external callback routing with original VNPay receipt retention. Fresh demo setup does not imply signed callbacks or old commands may be discarded silently; decisions recorded.

Keep old DBs intact. Before new writes, config rollback to unchanged old stack possible. After new writes/events, blind flip loses/diverges state: freeze, preserve both datasets/checkpoints/outboxes, reconcile idempotent immutable receipts, prefer forward fix/isolated restore plus owner review. Five independent backups aren't one financial-consistency snapshot; partial project restore must preserve event IDs/dedup/ledger invariants. No reset/drop/truncate/down-migration/financial or stock guessed repair.

## Completion status

Batch4.1 **design revision only**. 5A UUID implementation NOT STARTED,5B SQL/JPA acceptance NOT STARTED,6 Supabase deployment NOT STARTED. STD-021 IN_PROGRESS, STD-006 physical stock provenance OPEN. Place protected in every phase; dependency blocker recorded and compatibility proposal only, no implicit permission to edit Place.

## Batch 5A implementation checkpoint — 13/11/2026

U01 approved38 conversions implemented in source (User3 / Booking21 / Revenue14); U03 approved fresh databases without demo import; U04 approved ratePlanId String. U02 evidence: golden legacy/new PASS, eight backend builds PASS (901 tests,39 existing skips), three isolated PostgreSQL contract tests PASS, frontend regression211 PASS. JSON/protobuf strings, Booking/hold UUIDv3 recipes, hashes, provider refs, raw Revenue receipts and domain policies retained.

**Batch5A PARTIAL:** frontend tsc retains baseline protected Place showDistance error; no Place/frontend edits allowed. Full broker/Gateway/multi-node/restart and canonical migration acceptance NOT RUN. Current source mapping is UUID; actual supplied snapshot types and historical migrations remain unchanged. Never start new code against old VARCHAR schema. Native create-only synthetic tests are not canonical Flyway V1/JPA validate acceptance.

Current model preserves batch4_1_* provenance; current source/binding status supersedes earlier pending-U01 language. Historical reports/inventories remain as-of Batch4.1; this checkpoint records the approved implementation. Five independent Supabase projects unchanged. STD-021 IN_PROGRESS, STD-006 OPEN; Batch5B/6 not started. No current DB/Supabase/runtime-config/history/Place or Git index/commit mutations. [Batch5A report](../BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md), [evidence](../evidence/batch-5a-uuid/implementation-inventory.md).


## Batch 5B implementation checkpoint — 14/11/2026

**DONE for local canonical acceptance; Supabase/Batch6 NOT STARTED.** Five standalone canonical V1 chains/service db/canonical,46 tables/458 columns, five independent PostgreSQL databases/public histories. Bootstrap/rerun/catalog/Hibernate5/5 PASS;32 repositories/guards; runtime roles deny DDL/history/cross-service CONNECT. Guarded external job, opt-in runtime canonical validates/disables Flyway/SQL init; legacy defaults/migrations retained.

D01 historical unbounded Loyalty input/result/reservation total/discount and Revenue cash NUMERIC retained; bounded money38,2 unchanged. D02 exact rate; D03 Instant/timestamptz vs LocalDateTime/timestamp/date retained, JVM/JDBC/sessionUTC tested; D04 logical Room–Hotel/no cascade; D09 Java sole seed owner. Earlier proposal/model provenance stays historical design, executed local facts are recorded separately in5B evidence.

901 backend regressions executed/47 opt-in skips,8 packages PASS;5 canonical native tests PASS. Actual5 apps UP/restart5/5 with owned RabbitMQ/Redis; positive-points Booking/Hotel/User/Payment/refund/Revenue/concurrency/broker-outage/outbox/replay PASS. Place showDistance fixed minimally, tsc PASS,3 component+211 regressions PASS, lint0 errors/2 existing warnings. Minimal repository parameter correction findByRoomTypeId(UUID).

STD-021 local canonical gate complete; hosted deployment/managed security/backup/cutover and vendor PG/Flyway version certification future gates. STD-006 stock provenance OPEN/no default10/import. Full Gateway/provider/SMTP/Media/multi-node/all skipped opt-ins NOT VERIFIED. No Supabase connection/automatic Batch6. [Report](../BATCH5B_CANONICAL_FLYWAY_IMPLEMENTATION_2026-11-14.md), [evidence](../evidence/batch-5b-canonical-flyway/REPRODUCE.md).

## Batch6A checkpoint — 14/11/2026: Supabase readiness/security

**DESIGN/AUDIT DONE (static); Batch6B NOT READY for execution.** [Report](../BATCH6A_SUPABASE_READINESS_SECURITY_AUDIT_2026-11-14.md), [6B execution plan](../evidence/batch-6a-supabase-readiness/batch-6b-execution-plan.md), [risk matrix](../evidence/batch-6a-supabase-readiness/remaining-risks.md). This current checkpoint supersedes earlier “Batch6 not started” statements only for static6A; historical5A/5B results above remain unchanged.

Five existing independent projects retained; actual identity/endpoints/credentials/server versions/plans/API/RLS/grants/backups all NOT VERIFIED, no HOSTED VERIFIED items. Current V1 hashes match5B; canonical runtime validates/disables Flyway/SQL init when correctly selected. Compose currently selects docker only; base four ddl-auto=update and User baseline=true are deployment blockers until reviewed canonical overlay/resolved configuration. Existing5B runner is loopback-only, not hosted deploy tooling.

Direct TLS or accepted session pooler for persistent JDBC; dedicated guarded Flyway runner, verify-full/CA/hostname; transaction mode not selected for current session-init workload. Hikari5.1.0 default max10, each project budget replicas*max+migration+headroom; plan/replica allocation unknown, candidate lower pools not applied. Flyway10.10 major16 threshold requires actual server-version support review; no dependency upgrade performed.

Public Data API/default ACL/managed-role exposure requires pre-creation hardening and SQL/API negative tests, runtime/migrator separation, owner-specific grants; local5B permission PASS is not Supabase acceptance. Backup/PITR/RPO/RTO/restore drill and fresh broker/Redis/cutover/financial reconciliation gates documented. No demo import/cache/old event replay; STD-006 remains OPEN, no guessed stock10.

No hosted query/migration/DDL/DML/role/RLS/dashboard/backup/cutover, no Place/history/source/config/real.env changes or Git stage/commit/push.6B requires metadata/hosting/recovery inputs and explicit execution authorization after blockers close, not topology reapproval.


## Batch6B checkpoint — 14/11/2026: local backend → five Supabase databases

**PARTIAL; hosted execution BLOCKED, chưa DONE.** Đã thêm opt-in supabase-dev/local profiles, per-owner environment contract/startup guards, standalone guarded Flyway preflight/migrate runner, dedicated local RabbitMQ/Redis compose và local tests. Legacy defaults/V1/history/Place/business rules không sửa. Runtime validate/SQL never/Flyway false, fail-closed/no fallback; secrets không lưu report.

Actual five refs/org/Connect/TLS/version/budget/roles/Data API chưa cấu hình/xác minh riêng cho6B; không hosted SQL/DDL/dashboard/grants/fixtures. Phải có preflight/security PASS và user approval chính xác project/service/migration trước DDL. Active event triggers hoặc managed non-extension public objects cần review guard allowlist; không bypass. Hibernate/startup/business hosted đều BLOCKED; local regression không thay hosted acceptance. Backend/frontend/Gateway/Eureka/RabbitMQ/Redis tiếp tục chạy local, không cloud deployment.

[Báo cáo6B](../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-14.md), [hướng dẫn/evidence](../evidence/batch-6b-supabase-local-integration/reproduction-guide.md). Historical5A/5B/6A giữ nguyên as-of scope; STD-021 hosted IN_PROGRESS, STD-006 provenance OPEN.


## Batch6B PostgreSQL17 follow-up — 15/11/2026

Standalone runner1.1.0/Flyway10.22.0 đã kiểm trên new isolated PostgreSQL17.11:13 tests PASS,5 V1+validate+rerun no-op và scoped role/grant SQL local PASS. Service POMs/Boot/V1/history/business/Place/frontend không đổi. Package role/security review và admin read-only inspector đã chuẩn bị; chưa apply hosted, chưa tạo roles/passwords hoặc bật runtime. PostgreSQL17 version blocker đã đóng ở tooling/local; actual identity/TLS/Supavisor/Data API/managed permissions/event-trigger/security/approval vẫn BLOCKED. Overall6B PARTIAL, không hosted PASS từ local tests.

[Follow-up report](../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../tools/supabase-runner/README.md). Không stage/commit/push/cloud deployment/Batch7.


## Batch6B User hosted preflight —16/11/2026

Actual User-only Management API identity + migrator/runtime JDBC + client TLSv1.3/CA/hostname negatives + empty/history/default ACL/event-policy checks PASS in read-only scope. Runner1.3.0 now separates client TLS from pooler backend. Observed backend pg_stat_ssl=false, so migration remains BLOCKED; no TLS downgrade or backend-TLS exception for migrate. PUBLIC TEMP inheritance and Data API401 limitation retained.18 local tests PASS; other4 hosted preflights/roles/migrations/JPA/workflows NOT RUN. Overall6B PARTIAL, no DDL/DML/settings/app startup.

[16/11 report](../BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md), [redacted evidence](../evidence/batch-6b-supabase-local-integration/user-hosted-preflight-summary-2026-11-16.json).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](../evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json).

## Checkpoint đồng bộ và xét đóng Batch 6B — 16/11/2026

Core database/local acceptance đã đủ evidence; overall PARTIAL do phạm vi provider thực tế còn BLOCKED. Các checkpoint cũ NOT STARTED/BLOCKED/DONE phản ánh phạm vi và thời điểm trước đó, không được dùng thay current status. Cleanup fixtures và consolidation thay thế các ghi chú cũ về giữ synthetic rows, stack đang chạy và private `.local` fallback.

Giữ mở PUBLIC TEMP, STD-006 stock provenance, legacy cutover/recovery, real provider payment/refund/Storage, production backup/PITR/restore/RPO/RTO, broad outage/load và backend cloud deployment. Không tự đóng toàn bộ standardization hoặc mở Batch 7.

### Checklist và dependencies hiện hành

- [x] Xác minh năm project identities và owner-specific JDBC/TLS.
- [x] Apply canonical V1 5/5, history một successful V1/project, Flyway validate/checksum và immediate no-op 5/5.
- [x] Hosted catalog 46 business tables +5 history; Hibernate 32 entities và full startup/restart 5/5.
- [x] Runtime/migrator separation, scoped grants, independent security settings, 30 denials/5 cross-project pairs.
- [x] Isolated broker/cache, bounded pools, 117 core workflow checks và scoped regression acceptance.
- [x] Fixture cleanup và daily configuration consolidation theo evidence; không replay acceptance.
- [ ] Real VNPay Sandbox payment: merchant approval code71 cần owner/provider xử lý trước acceptance.
- [ ] Supabase Storage bucket/visibility/upload/private-preview: cần endpoint hoạt động, hiện NXDOMAIN.
- [ ] Production backup/restore/PITR/RPO/RTO, legacy-data cutover/reconciliation, broader outage/load và backend cloud deployment.

Dependencies: Batch5B local canonical → Batch6A static readiness → Batch6B hosted database/core local acceptance đã xác minh. Provider follow-up độc lập còn BLOCKED; deployment tương lai phải có acceptance riêng. STD-021 local/hosted integration subgates được giải quyết, parent migration/operational readiness vẫn IN_PROGRESS; STD-006 OPEN. Không đổi các checkbox lịch sử thành DONE ngoài phạm vi.
