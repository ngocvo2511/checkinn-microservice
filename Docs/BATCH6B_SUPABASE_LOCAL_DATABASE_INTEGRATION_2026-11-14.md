# BATCH 6B — Supabase database integration cho phát triển local

14/11/2026, Asia/Saigon. **Final status: PARTIAL. Hosted execution: BLOCKED.** Đã hoàn thành cấu hình, runner, kiểm thử local và tài liệu trong phạm vi có thể làm an toàn. Chưa kết nối/migrate/khởi động runtime hoặc tạo test data trên Supabase. Không đánh dấu hosted PASS từ kết quả local.

## A. Initial State

Backend `feat/canonical-uuid-schema` / `909e73d6021d076479bb6533badb9a029c5a9c2a`; frontend `fix/place-card-distance-visibility` / `a10f82b7d84d941e77a2c5ae44aa2a899ab0a547`. Backend có sẵn IDE edits, bốn tài liệu đang sửa và report/evidence6A untracked; frontend sạch. Giữ mọi thay đổi đó. [Snapshot](evidence/batch-6b-supabase-local-integration/initial-state.md), [manifest17 tài liệu](evidence/batch-6b-supabase-local-integration/input-manifest.json).

Đối chiếu YAML/POM/Compose/runner/SQL/entity guards thực tế: canonical profile an toàn nếu bật đúng; base defaults/Compose chưa hosted-safe; runner5B chỉ hỗ trợ loopback. Không sử dụng credentials trong .env cũ. Không sửa Place, frontend, business payment/refund, historical migrations hoặc V1; không reset/checkout/stage/commit/push.

## B. Five-Project Mapping

| Service owner | Project alias | Actual identity/connection |
|---|---|---|
| User + Loyalty | Supabase User | BLOCKED — chưa xác minh |
| Hotel | Supabase Hotel | BLOCKED — chưa xác minh |
| Booking + Payment/Refund | Supabase Booking | BLOCKED — chưa xác minh |
| Revenue | Supabase Revenue | BLOCKED — chưa xác minh |
| Regulations | Supabase Regulations | BLOCKED — chưa xác minh |

Năm existing independent projects giữ nguyên, public/history riêng; không tạo/gộp project/cross-project FK/SQL joins/shared credentials. Template metadata để trống actual refs/org/endpoint/login, SHA V1 lấy từ source. [Mapping contract](evidence/batch-6b-supabase-local-integration/supabase-project-mapping.md).

## C. Connection Strategy

Exact Connect tuple riêng cho runtime/migration, session pooler5432 khi phù hợp mạng local; hỗ trợ direct5432. Không transaction pooler/fallback. URL không credentials, allowlisted JDBC options, verify-full+CA, public/postgres, connectTimeout10. Runner kiểm CA SHA, exact host/login/SQL role/major/schema và authenticated Management API project/org/database host trên mọi connection. Actual TLS/network/pooler **NOT VERIFIED**. [Configuration](evidence/batch-6b-supabase-local-integration/connection-configuration.md).

Đối chiếu [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [API security](https://supabase.com/docs/guides/api/securing-your-api), [PostgreSQL privilege inquiry](https://www.postgresql.org/docs/16/functions-info.html); đây không phải chứng nhận targets thực tế.

## D. Configuration Changes

Thêm `application-supabase-dev.yml`, `application-local.yml`, EnvironmentPostProcessor guard/spring.factories và tests cho5 services. Supabase runtime lấy owner-prefixed env, validate/SQLnever/Flywayfalse/baselinefalse/cleanDisabled, UTC, SQL/bind/Hikari logs tắt, Config Server disabled. Guard kiểm effective config trước datasource; tenant ref/login/runtime role, thiếu biến, unsafe overrides đều fail fast. Chặn cả Hikari JDBC/init SQL và JPA schema-generation overrides. Local selector kế thừa defaults cũ nguyên trạng; không bật canonical V1 vào demo.

`config/supabase-dev` có `.env.example`, target/approval templates không secrets. Runtime không nhận migration/admin secrets. Hikari max/budget bắt buộc từng project, không default candidate4 khi chưa biết limits. Dedicated infra Compose chỉ RabbitMQ/Redis loopback, session/volumes/vhost/ports riêng cho native host JVM. [IntelliJ/Maven/Compose runbook](evidence/batch-6b-supabase-local-integration/reproduction-guide.md).

Runner standalone Java21/Flyway core+PG10.10.0/pgJDBC42.7.4, preflight/migrate riêng. Chỉ canonical V1/hash, guard từng physical connection, OS mutex, fresh/history/object/security gates, recheck trước DDL. Không app/seed/grants/clean/repair/baseline/fallback. Migrate cần approval bind exact manifest/service/project/V1/inventory, approver/expiry<=24h và security/exclusive-window/apps-stopped gates. Không tạo approval thay user.

## E. Hosted Preflight

**BLOCKED5/5:** chưa có approved metadata/quyền truy cập/credentials riêng cho6B. Không runner hosted SQL/Management API calls. Preflight chỉ read-only transaction/rollback. CLI thiếu manifest dừng exit2 trước network. [Preflight results](evidence/batch-6b-supabase-local-integration/hosted-preflight-results.md).

Giới hạn fail-closed: active event triggers hoặc public objects không thuộc extension membership dừng để review, kể cả có thể managed. Chưa có approved per-project managed allowlist; không bypass/delete. Conservative runner version gate14..16; actual major khác cần targeted dependency/support review. Hosted response shape/custom-role/session/DDL/locks chưa verified.

## F. Security

Local tests kiểm TLS/options/target mismatch, connection thứ hai sai identity, unsafe creator defaults/API grants, runtime CREATE, unexpected objects/history và approval sai scope/hash/expiry/security. Real SQL tests chỉ trên PostgreSQL mới loopback, stopped/preserved; không demo DB.

**NOT VERIFIED hosted:** Data API/exposure/RLS/grants/memberships/runtime permissions/HTTP negatives. Không đổi Dashboard/roles/grants. Runner hỗ trợ dedicated migrator-as-owner/runtime riêng; chưa SET ROLE owner workflow. Require creator defaults/PUBLIC/API denial trước tạo bảng. Data API attestation cần manual reviewed evidence, không tự coi SQL là HTTP security PASS. Sau migrate cần allowlisted runtime grants/immutable/history restrictions và46-table API negatives. [Security](evidence/batch-6b-supabase-local-integration/security-verification.md).

## G. Flyway

Giữ SHA canonical5 V1 và historical SQL. **Hosted V1/history/validate/rerun/catalog: BLOCKED.** Runner assert1 V1, validate, exact one SQL history row và immediate rerun0; chưa thực thi hosted. Invocation mới gặp history dừng, không tự repair/retry. [Migration](evidence/batch-6b-supabase-local-integration/flyway-migration-results.md), [catalog](evidence/batch-6b-supabase-local-integration/catalog-verification.md).

**READY FOR MIGRATION APPROVAL chỉ sau mapping/preflight/security PASS; hiện prerequisites còn BLOCKED.** Yêu cầu Batch6B không phải phê duyệt DDL trên project bất kỳ.

## H. Hibernate

Final35 guard tests PASS: YAML/effective properties, missing credentials/redaction, overrides/TLS/budget/isolation, profile conflict/local compatibility, actual Spring bootstrap fail-fast và registration. **Hosted Hibernate0/5 thực thi,5/5 BLOCKED.** Không Hibernate update/manual ALTER che mismatch. [Matrix](evidence/batch-6b-supabase-local-integration/hibernate-validation.md).

## I. Local Integration

Không khởi động business services/Gateway/Eureka/frontend/infra against Supabase do migrations/security chưa đạt. Dedicated infra Compose config PASS bằng synthetic env, chưa start containers. Guard-query cluster PostgreSQL18.0 mới được stopped/preserved; không là chứng nhận Flyway hosted version hoặc business startup. [Startup](evidence/batch-6b-supabase-local-integration/local-service-startup.md), [pool](evidence/batch-6b-supabase-local-integration/connection-pool-results.md).

## J. Business Acceptance và Regression

| Kiểm tra mới6B | Kết quả/phạm vi |
|---|---|
| Backend5 affected modules | PASS843 executed,47 skipped,0 failures/errors; không ApplicationTests/opt-in DB suites |
| Final config/startup guards | PASS35/35; không cộng trùng regression |
| Runner unit + real isolated PostgreSQL catalog | PASS7/7 final local run |
| Runner package/dependencies | PASS final artifact |
| Missing-manifest CLI | PASS refusal exit2/no network |
| Frontend regression | PASS214/214 |
| Frontend TypeScript | PASS exit0 |
| Infra Compose config | PASS syntax only/no startup |
| Hosted preflight/security/V1/catalog/Hibernate | BLOCKED |
| Hosted workflows/RabbitMQ/Redis/E2E | BLOCKED/NOT RUN |
| Actual pool/TLS/API negative/recovery tests | NOT VERIFIED |

Broad regression trước các bổ sung guard cuối; final35 tests compile/recheck5 modules sau đó. Không tuyên bố rerun broad suite trên từng guard bổ sung. Không real payment/refund/OTP/mail. Historical5B không chuyển thành hosted6B PASS. [Regression](evidence/batch-6b-supabase-local-integration/backend-regression.json), [verification](evidence/batch-6b-supabase-local-integration/verification.json), [business matrix](evidence/batch-6b-supabase-local-integration/business-workflow-results.md).

## K. Remaining Risks và Next Inputs

Cần user cấu hình trực tiếp trên máy, không gửi passwords vào chat: five project refs/org/service-owner mappings; exact Connect host/port/database/login/mode; CA; dedicated migrator/runtime credentials và Management API read access; PostgreSQL major; usable connection/pooler budgets; actual Data API/security/role/default ACL evidence. Cần phân loại managed objects/event triggers, version support và permissions trước migration.

Sau preflight/security PASS mới trình exact project/service/V1/hash/DDL để user phê duyệt từng target. Sau5 migrations mới catalog/grants/runtime/API negatives/Hibernate/startup/workflows bằng synthetic data. Xác minh fresh broker/cache/vhost/volumes; JWT/HTTP/gRPC/Media/SMTP/VNPay sandbox riêng. Backup/PITR/RPO/RTO/restore và financial recovery chưa verified trước traffic. [Risks](evidence/batch-6b-supabase-local-integration/remaining-risks.md), [runbook](evidence/batch-6b-supabase-local-integration/reproduction-guide.md).

## L. Final Status

**PARTIAL — configuration/tooling/local verification hoàn thành; hosted BLOCKED.** Chưa đạt16 DONE conditions. Không cloud deployment, project mới, demo/legacy/V1/history changes, Place/frontend/business changes, Git index/commit mutations hoặc Batch7. Bốn tài liệu architecture/migration/standardization append checkpoint; không rewrite historical5A/5B/6A reports. Đủ13 evidence documents bắt buộc.


## Follow-up15/11/2026

[PostgreSQL17 runner/role review](BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md): standalone runner1.1.0/Flyway10.22.0, local17.11 validation13 PASS và5V1+rerun/scoped role SQL PASS. Historical14/11 counts/status giữ as-of scope; overall6B vẫn PARTIAL, hosted BLOCKED. Actual roles/credentials/security/migrations chưa apply.


## Follow-up16/11/2026

[User actual read-only preflight](BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md) now verifies User identity/JDBC/client TLS/SQL gates. Backend TLS=false still blocks migration; no hosted mutations by assistant. Historical14/11 results remain as-of; overall6B PARTIAL, other4 targets not yet preflighted, no business startup.

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json).
