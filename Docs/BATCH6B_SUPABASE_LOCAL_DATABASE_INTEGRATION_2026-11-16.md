# BATCH 6B — Tích hợp Supabase cho local development

> **Danh mục thật đã bổ sung:** 34 tỉnh/thành hiện hành và 40 điểm đến du lịch, hotel_count=0, không thêm khách sạn/booking mẫu. [Dữ liệu và cách nạp](../config/reference-data/README.md).

> **Dữ liệu test đã dọn theo yêu cầu người dùng:** 192 dòng trong 5 database và 7 email Mailpit. Giữ 2 tài khoản ngoài fixture, 4 quy định mặc định và migration history. [Chi tiết cleanup](BATCH6B_FIXTURE_CLEANUP_2026-11-16.md). Các kết quả acceptance bên dưới là lịch sử trước cleanup.

> **Runtime hiện tại: Docker Compose.** Đã tắt host JVM/Next dev; 10 JVM container và frontend production đều healthy, đăng nhập và tìm kiếm qua giao diện đã qua. Lệnh chạy và kết quả: [Docker runtime](BATCH6B_DOCKER_RUNTIME_2026-11-16.md). Database vẫn là 5 project Supabase đã duyệt.

> **Provider follow-up hiện tại: PARTIAL.** Người dùng yêu cầu VNPay Sandbox và Storage thật. Đã dùng credentials từ docker/.env: VNPay trả71/website chưa duyệt; Storage hostname NXDOMAIN. [Kết quả thực tế và việc cần xử lý](BATCH6B_REAL_PROVIDER_ACCEPTANCE_2026-11-16.md). Phần DONE bên dưới là core local acceptance trước yêu cầu mở rộng này.


16/11/2026, Asia/Saigon. **DONE trong phạm vi development local.** Backend, Gateway, Eureka, Next.js, RabbitMQ và Redis chạy trên máy local; PostgreSQL dùng năm Supabase project độc lập. Không triển khai backend lên cloud. VNPay provider thật và Storage uploads không được chứng nhận bởi batch này.

## A. Initial State

Backend branch `feat/canonical-uuid-schema`, HEAD `909e73d6021d076479bb6533badb9a029c5a9c2a`; frontend branch `fix/place-card-distance-visibility`, HEAD `a10f82b7d84d941e77a2c5ae44aa2a899ab0a547`. Giữ nguyên thay đổi có sẵn trong hai file .idea và bốn tài liệu planning. Không reset/checkout/stage/commit/push. [Initial snapshot](evidence/batch-6b-supabase-local-integration/initial-state.json).

## B. Five-Project Mapping

| Service | Project logical alias | Business tables | Runtime role |
|---|---|---:|---|
| User + Loyalty | User | 7 | checkinn_user_app |
| Hotel | Hotel | 24 | checkinn_hotel_app |
| Booking + Payment/Refund | Booking | 7 | checkinn_booking_app |
| Revenue | Revenue | 5 | checkinn_revenue_app |
| Regulations | Regulations | 3 | checkinn_regulations_app |

Mỗi project có migrator/app credentials/history riêng. Exact refs/org/hosts đã kiểm qua authenticated Management API, lưu private. Scoped PAT chọn đúng nhóm User/Regulations, Hotel/Booking, Revenue. Runtime JVM chỉ nhận credentials của owner, không PAT/migrator password. Năm foreign-role logins bị từ chối trước foreign SQL; role vắng mặt trong target được xác minh bằng kết nối hợp lệ. [Cross-project evidence](evidence/batch-6b-supabase-local-integration/cross-project-login-negative-results.json).

## C. Connection Strategy

Session Pooler port5432 từ Connect đã duyệt; `verify-full`, CA SHA pin/hostname verification, schema public, timeout10s, UTC. Hai chặng client→pooler→PostgreSQL có TLS; wrong-CA/hostname negatives PASS. Không chuyển transaction pooler/tắt TLS. PostgreSQL17.11, pgJDBC42.7.4; standalone Flyway10.22.0 hỗ trợ major17. Không dùng service-role API key làm JDBC password.

Pool thực tế max2/min1 mỗi service, role app cap4; SQL max_connections60/reserved3; allocated project budget7 = pool2 + migrator reserve2 + operations reserve3. Đã kiểm startup/idle/active, giữ hai connection rồi kiểm pool-exhaustion timeout, release recovery, abort connection của chính process và tái tạo connection. Pooler API trả default/null cho numeric settings; không suy diễn provider-wide client ceiling. Chỉ kiểm hai client/project, không load test lớn. [Running metrics](evidence/batch-6b-supabase-local-integration/runtime-pool-metrics.json); per-owner `*-pool-acceptance.json` chứa số đo. Provider-wide Internet outage chưa mô phỏng.

## D. Configuration Changes

Năm service có profile supabase-dev/local, fail-fast EnvironmentPostProcessor và35 guard tests. Runtime Hibernate validate, SQL init never, Flyway disabled, baseline false/clean disabled, SQL/bind/pool logging tắt. Sai/thiếu owner, TLS, credentials, budget/isolation thì fail closed, không datasource fallback. Base YAML và canonical/historical SQL giữ nguyên30 hashes.

Local selector giữ cấu hình legacy hiện có; canonical local acceptance Batch5B vẫn dùng PostgreSQL local mới/được guard. Không chứng nhận code UUID tương thích schema demo cũ; không chạy local selector lên demo để thử schema expansion.

Secrets chỉ ở ignored `.local/batch6b/.env`; public template trống. `run-local-stack.py` nạp whitelist env, chạy hidden/loopback, chỉ quản lý PID/container thuộc session và giữ dữ liệu khi stop. Readiness chỉ bật sau actual security/pool/fresh-queue/Redis checks. Acceptance đã seal, không tự replay/reset balances sau DONE.

## E. Hosted Preflight

Identity/org/host, database/schema/major, dedicated roles/default ACL, public sạch/history absent trước V1 và exact sáu managed event/function descriptors PASS5/5. Không disable/drop provider hooks. Policy owner/project/major/V1/hash và descriptor drift kiểm trên mọi physical migration connection. [Migration checkpoint](BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md).

## F. Security

Scoped Management API xác minh độc lập SSL enforcement=true/applied5/5 và PostgREST db_schema rỗng5/5. Settings chỉ GET; operator đã tự thay trước đó. Catalog xác minh PUBLIC/anon/authenticated/service_role không có quyền business table/column/sequence. Không suy security từ HTTP401 không API key. [Config evidence](evidence/batch-6b-supabase-local-integration/independent-security-settings-results.json).

App không CREATE persistent objects public, không migrator membership/ownership, không đọc/sửa history. Immutable tables chỉ SELECT/INSERT hoặc Hotel feature marker SELECT-only.30 actual expected SQLSTATE42501 probes PASS, kiểm lại sau startup. Managed PUBLIC database TEMP vẫn kế thừa; không revoke maintenance privileges rộng hoặc tuyên bố chặn mọi TEMP/managed-schema operation.

## G. Flyway

Operator duyệt exact5 project V1/grants, apps stopped/exclusive DDL. Migrate tuần tự: mỗi project một successful V1 history row, validate PASS, immediate rerun no-op PASS.46 business +5 history tables. Không baseline/repair/clean, sửa SQL/history, import demo hoặc tạo project. Grants đúng hash-bound allowlist. Approval files hết hạn/migrationApproved=false. First-run preflight chặn existing history sau migration; không xóa history để chạy lại.

## H. Hibernate

Standalone validate32 JPA entities PASS5/5, sau đó full Spring startup và actual restart PASS5/5 với supabase-dev. Catalog/grants/history kiểm lại sau business/startup: exact tables/owners, không thêm schema/history row, không invalid constraints. Không Hibernate update/create/create-drop trên hosted. Non-JPA tables kiểm bằng V1/catalog và JDBC/business workflows thật.

## I. Local Integration

| Thành phần | Địa chỉ local | Kết quả |
|---|---|---|
| Next.js | http://localhost:3000 | HTTP200 + actual NextAuth session PASS |
| Gateway | http://localhost:8080 | health/routes/auth/report PASS |
| Eureka | http://localhost:8761 | health/discovery PASS |
| Auth/User/Hotel/Booking | 8081/8082/8083/8084 | healthy |
| Media | 8085 | healthy; empty-media paths, provider unconfigured |
| Revenue/Regulations | 8088/8091 | healthy |
| Notification | headless | Rabbit consumers + Mailpit delivery PASS |
| RabbitMQ/Redis | 127.0.0.1:56672/56679 | fresh isolated session/vhost/volumes |
| Mailpit | http://localhost:56825 | SMTP sink local; không email ngoài |

11 recorded processes healthy. HTTP/gRPC/frontend bind loopback. Trước startup đã kiểm fresh queues/Redis db0 trống; không replay state cũ. Restart giữ đúng session-owned data, không purge/xóa volume. [Startup evidence](evidence/batch-6b-supabase-local-integration/runtime-startup-final-results.json).

## J. Business Acceptance

**117 hosted workflow checks PASS** qua runtime thật:

- User: Gateway register, Rabbit→Notification→Mailpit OTP, verify/login/profile; sai password/unverified/cross-user denied; logout/Redis revocation; Next.js NextAuth proxy/session.
- Hotel/Room: API create/read/update, UUIDv4, draft/public isolation, room deactivation; ADMIN Hotel logical deactivation giữ row, owner/DRAFT sai quyền/state bị chặn. Chỉ synthetic catalog/stock2 định nghĩa rõ, không import/suy đoán legacy stock.
- Booking/Loyalty: UUIDv3, idempotent retry/changed-payload conflict, hold/reserve/commit/release, compensation/cancellation, concurrent no-oversell, points use/commit/reversal đúng một lần.
- Payment/Refund: onsite receipt/duplicate confirmation, internal request/ADMIN approval, concurrent/duplicate execution, một audit execution, receipt giữ nguyên; rejected refund không execute.
- Callbacks: fake merchant/HMAC riêng, HTTP local; failed attempt→retry UUIDv4, bad signature/amount denied, successful signed callback, identical replay, conflicting receipt denied, expired hold không tạo payment. Không navigate redirect/call VNPay/refund provider. Existing conflicting-receipt HTTP404 contract giữ nguyên.
- Revenue: real Rabbit outbox CREDIT/DEBIT, cash balance theo payment, receipts/projections/reports qua Gateway; hai duplicate events không tăng receipts/cash.
- Regulations: seed lookup, source/snapshots/version history/stale409; broker outage vẫn commit source/snapshot và PENDING outbox; broker+năm JVM restart giao tiếp event, không trùng snapshot/seed.

Synthetic data được giữ lại, không destructive cleanup. Fixture chuẩn bị lỗi trước đó cũng giữ, không sửa schema để hợp thức hóa test SQL. [Auth/catalog](evidence/batch-6b-supabase-local-integration/auth-catalog-hosted-results.json), [core](evidence/batch-6b-supabase-local-integration/booking-payment-refund-revenue-hosted-results.json), [extended](evidence/batch-6b-supabase-local-integration/extended-hosted-results.json), [callbacks](evidence/batch-6b-supabase-local-integration/payment-callback-hosted-results.json), [restart](evidence/batch-6b-supabase-local-integration/restart-outbox-hosted-results.json), [event replay](evidence/batch-6b-supabase-local-integration/revenue-event-replay-hosted-results.json).

**848 backend executed PASS,47 optional skip; frontend214/214 PASS, typecheck PASS.** Final backend excludes unconfigured default `*ApplicationTests`; actual hosted startup/restart là gate riêng PASS. Bare-default context ban đầu bị local password authentication từ chối trước DDL/DML, đã dừng/ghi nhận; không đổi credentials cũ hoặc giấu thành PASS. Không sửa business/Place/frontend để pass. [Regression](evidence/batch-6b-supabase-local-integration/final-regression-results.json).

## K. Remaining Risks

Không blocker cho mục tiêu local-development hiện tại. NOT RUN: merchant VNPay Sandbox thật, real refund, Supabase Storage uploads/private previews, provider-wide outage/load limits, production backup/PITR/restore/RPO/RTO. Không cần thêm credentials để dùng stack hiện tại. Media provider fail closed tại loopback chưa cấu hình; empty-media paths đã kiểm. SMTP chỉ Mailpit.

PUBLIC TEMP/STD-006 legacy stock provenance vẫn là giới hạn được ghi nhận. Không suy production readiness, full managed-schema denial hoặc legacy-schema compatibility. Checkpoints PARTIAL/BLOCKED trước đây là lịch sử as-of, được supersede trong phạm vi hoàn tất; historical Batch5A/5B/6A không rewrite.

## L. Final Status

**DONE — đủ16 điều kiện trong phạm vi local development.** Năm identity/connection/security/V1/history/catalog/Hibernate PASS; startup/core workflows PASS; runtime không đổi schema; cross-project denied; regression gates đạt; không DDL/DML trên demo hoặc mất dữ liệu cũ; không secrets lộ; đủ13 evidence documents.30 protected hashes unchanged, frontend sạch, Git index không đổi. Không cloud deployment/Batch7.

[Final machine-readable verification](evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json). [Runbook](evidence/batch-6b-supabase-local-integration/reproduction-guide.md). Stack giữ chạy để mở trực tiếp; private BATCH_NEXT_STEPS nay là hướng dẫn sử dụng, không còn checklist setup chờ làm.
