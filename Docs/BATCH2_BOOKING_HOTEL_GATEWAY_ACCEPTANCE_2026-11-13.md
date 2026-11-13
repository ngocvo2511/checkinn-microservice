# Batch 2 — Booking–Hotel/Availability Gateway Acceptance

Ngày hoàn thành: 13/11/2026, Asia/Saigon.

**DONE cho acceptance Booking create/cancel chưa thanh toán trong môi trường disposable. Overall standardization vẫn PARTIAL / IN_PROGRESS.** Không phát hiện lỗi production cần sửa qua những scenario đã thực thi. Không mở rộng kết luận sang provider payment/refund, production migration hoặc multi-node recovery.

## A. Initial State

Backend `feat/refund-reconciliation-workflow`, HEAD `332f97a66c02b89abfd04720f73c9cc6d6cca47a`; frontend `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`. Backend chỉ có hai thay đổi unstaged `.idea/compiler.xml`, `.idea/encodings.xml`; staged/untracked nonignored rỗng. Frontend sạch. Không đổi nhánh, stage, commit, push, merge hoặc PR. [Initial snapshot](evidence/batch-2-booking/initial-state.json).

Đã đối chiếu source BookingController/BookingAccess, BookingService/BookingIdempotency/BookingCreationCompensation/CancellationPolicy, HotelCatalogClient/HotelAvailabilityClient, Hotel/Room entities/publication, AvailabilityService/repositories/HoldCleanupScheduler và schema guards với PLAN S06/S08/S09/S10/S21/S23, checkpoint 74 và Audit G0/G1/G2. Không lấy HTTP 503 của Refund stack thiếu Hotel làm lỗi production Booking.

## B. Workspace Organization

[Manifest đầy đủ](evidence/batch-2-booking/workspace-inventory.json): 989 file ngoài repositories; 6 project documentation, 2 plan/audit, 12 SQL migrations, 12 SQL fixtures, 729 raw logs, 27 JSON evidence, 200 unknown ownership/source snapshot/script, 1 generated artifact. Mỗi entry có original path, destination, category, size, Git state, risk, action/reason và SHA-256. Có 45 file khớp mẫu cần review; không xuất matched values. Đây là heuristic, không chứng nhận toàn workspace sạch secrets.

- Copy PLAN/AUDIT vào [Docs/standardization](standardization/PROJECT_STANDARDIZATION_PLAN.md), kiểm checksum trước khi sửa links/status. Hai file root trở thành pointer tới canonical; không duy trì hai nội dung độc lập. Không overwrite destination đã tồn tại.
- Chuyển 27 root `BATCH*`/`AUDIT*` logs và JSON vào `.local/evidence/batch-1-refund/`. Kiểm SHA-256 bản copy trước khi bỏ original; kiểm lại toàn bộ archive sau di chuyển. Không redact/overwrite raw evidence.
- `DA1/logs/` đã có nhóm log và review snapshot riêng: KEEP. Patch/source snapshot, root SQL và tài liệu khác giữ nguyên, không tự merge/di chuyển khi ownership hoặc migration authority chưa rõ. Không đưa sample hotel SQL vào Git.
- Sửa links trỏ PLAN/AUDIT, links raw evidence và hai links accommodation plan; [link review](evidence/batch-2-booking/document-link-review.json). Root pointers giữ tương thích với references ngoài hai repo chưa thể kiểm kê.
- Backend `.gitignore` thêm `.local/`, `__pycache__/`; `.env.*`, `*.log`, target, IDE đã có. Frontend ignore không đổi. Không ignore Docs/source/harness/migrations, không thay Git index.
- Reviewed JSON/manifest/guide đặt ở [evidence/batch-2-booking](evidence/batch-2-booking/README.md), là **untracked, unstaged candidates để review và đưa vào Git**, chưa được commit/tracked. Raw evidence và private env bị ignore; không được hiểu là đã đưa raw logs vào Git.

## C. Environment

Project mới `checkinn-booking-smoke`, 9 containers: PostgreSQL 18, Redis, RabbitMQ, Eureka, Gateway, Auth, User, Hotel, Booking. Không Place, Revenue, Loyalty service riêng hoặc Config Server. Loyalty endpoints cần cho cancellation đã nằm trong User; chưa dùng points trong fixture. Revenue không cần để hủy booking chưa thanh toán; không thay Refund workflow.

Gateway `127.0.0.1:28080`, Eureka `28761`, Rabbit management `35672`; PostgreSQL không publish host port trong stack. DB mới `checkinn_smoke_user`, `checkinn_smoke_booking`, `checkinn_smoke_hotel`. Named volumes: `checkinn-booking-smoke_booking-smoke-postgres-data`, `checkinn-booking-smoke_redis-data`, `checkinn-booking-smoke_rabbitmq-data`; network riêng của project. Labels/mounts/image IDs và 9 health checks, 5 Eureka registrations UP: [environment evidence](evidence/batch-2-booking/environment-results.json). Auth login và Gateway lifecycle xác nhận readiness nghiệp vụ, không chỉ Gateway health.

Inspect Docker ownership trước startup, lưu metadata an toàn ở `.local/evidence/batch-2-booking/docker-initial-ownership.json`. Không dùng developer/shared DB, không clone dữ liệu thật, không reset volumes cũ, không global prune. Refund stack giữ nguyên containers/config/volumes, không rebuild tag của stack đó. Booking/Hotel build từ current source với tag riêng `checkinn/booking-smoke-booking:batch2`, `checkinn/booking-smoke-hotel:batch2`; image IDs đã ghi evidence. Auth/User/Gateway/Eureka tái sử dụng local images hiện có; không claim mọi image đều newly rebuilt. Private `.env.booking-smoke` sinh random riêng, không lấy VNPay/provider credentials; Refund flag false.

Bootstrap **test-only disposable**, không production migration certification, không chứng minh upgrade legacy:

1. PostgreSQL init tạo ba DB và minimal User legacy baseline; User Flyway tiếp tục V2+ rồi JPA bổ sung schema.
2. Booking/Hotel JPA `ddl-auto=update` tạo baseline trống; áp dụng SQL explicit theo [schema manifest](evidence/batch-2-booking/schema-bootstrap.json), với `ON_ERROR_STOP`.
3. Booking V3 cancellation/refund, idempotency, onsite/paid intents, loyalty, transaction time/outbox/manual/internal refund schema. Không gọi thực thi Refund.
4. Hotel V4 availability và hold-command unique index; amenity catalog; accommodation compatibility bootstrap; DRAFT approval check. V5 không replay vì JPA đã tạo `expires_at`; lần bootstrap đầu phát hiện duplicate column được giữ trong raw log.
5. Accommodation SQL lịch sử không replay trực tiếp trên JPA tables đã tồn tại; file `booking-smoke-accommodation-bootstrap.sql` chỉ dùng `IF NOT EXISTS` cho baseline disposable. Các constraints của table đã được JPA tạo không mặc nhiên được chứng nhận tương đương migration gốc. Schema guard/startup PASS không đóng STD-021. [Actual tables/constraints](evidence/batch-2-booking/schema-constraints.json) xác nhận required relations và unique idempotency indexes trong DB disposable.

RabbitMQ startup đầu có cookie permission race. Chỉ chown volume mới thuộc project Booking, overlay chạy broker bằng user `rabbitmq`; không sửa shared startup script hay Refund volume. Hotel startup đầu bị chặn đúng bởi schema guards; bổ sung prerequisites thay vì disable guard. Probe UUID key sai định dạng và probe trước discovery ready là lỗi harness/readiness, không production defect; giữ logs, không tính vào acceptance PASS.

## D. Fixtures

Hai CUSTOMER đăng ký qua Gateway, email verification flag cập nhật SQL trong User DB disposable, sau đó login thật; OWNER/ADMIN seed bcrypt + role trong cùng DB rồi login thật qua Auth/Gateway. JWT chỉ ở memory, không ký giả/token header role bypass. Test credentials không ghi vào reviewed evidence.

Hotel/City/Room **SQL-seeded**, không giả claim API-created catalog: synthetic UUID riêng, owner hợp lệ đã login, hotel APPROVED/active, accommodation HOTEL, address JSON, business license marker; room active, base price **125000.50 VND/room/night**, capacity adults=2/children=1, total_rooms=2. Scenario cạnh tranh và expiry có stock=1 explicit. Không default 10, không thay stock rules. Availability rows được service tạo lazy cho ngày stay, không seed held counters để fake success.

Booking **API-created** qua Gateway, 2 đêm 28–30/12/2026, quantity=1, adults=2/children=0, accepted policy STANDARD_24H_V1; không points/voucher/payment. Fixture mỗi lượt runner là mới; giữ các lượt thử để debug, không reset database.

## E. Booking Creation

POST `/api/bookings`, CUSTOMER JWT thật → **201/PENDING**. SQL xác nhận đúng CUSTOMER principal, canonical hotel/room names, một booking item, unit price 125000.50, nights=2, subtotal/total **250001.00**, paid_amount=0; client hotel/room name, nights=999 và extra totalAmount=1 không quyết định snapshot/total. Unit price khác giá server bị từ chối 400, không silently chấp nhận quote cũ.

Một hold HELD quantity=1, hai dated rows held=1/booked=0, available=2−1−0=1 mỗi đêm. Booking hold ID trỏ đúng Hotel hold; expiry có thật và khớp trong 1ms giữa Hotel timestamptz, Booking persisted SQL, create response và GET qua Gateway. Booking raw timestamp phải đọc theo `hibernate.jdbc.time_zone=Asia/Ho_Chi_Minh`; không giả định raw wall clock là UTC. Policy check-in 14:00 Việt Nam, free cancellation trước 24h: [SQL/readback evidence](evidence/batch-2-booking/final-sql-results.json).

## F. Idempotency & Concurrency

[42 assertions chính](evidence/batch-2-booking/gateway-results.json): retry cùng key/payload trả 201 cùng booking ID, một hold, counters không đổi; key cũ/payload khác → 409. Hai clients barrier cùng command trước first insert đều 201, cùng ID và singleton hold. Client timing intervals overlap, không claim mọi interleaving/multi-node.

Hai command khác nhau cạnh tranh room stock=1 → một 201, một 409; SQL chỉ một hold và held=1 mỗi đêm, không oversell. Mất response được mô phỏng bằng local proxy: upstream Gateway commit và SQL connection khác thấy booking trước khi socket downstream đóng; retry trả original ID. Không chỉnh production logic để fault injection.

## G. Cancellation

DELETE `/api/bookings/{id}` owner → **204/CANCELLED**; concurrent hai cancellations và replay đều 204. Non-owner GET/DELETE →403, không mutation. ADMIN POST `/{id}/cancel-reviewed` với reason →204 theo contract hiện có. Policy snapshot/check-in/free-cancellation deadline không đổi. Original hold chuyển RELEASED một lần; held/booked trở về 0, available=total; unpaid fixture không tạo refund request. Không thay cancellation policy, không replacement room, không hoàn tiền thật.

## H. Inventory Consistency

Đối chiếu SQL trước/sau create/retry/cancel, cạnh tranh first row, negative inputs, persistence/availability/release faults. Mỗi hold command/booking idempotency hash không duplicate. Global invariant `held>=0 AND booked>=0 AND held+booked<=total_rooms` không có violation. Reservation/confirm stock có cột `booked`; batch Gateway chưa thanh toán nên booked=0, không coi đó là paid/onsite reservation acceptance.

Negative matrix: anonymous create401; forged user403; invalid hotel/room, relationship mismatch, inactive/unapproved hotel, inactive room, capacity/date/price/negative price/missing stock400; stock conflict409. Mỗi negative so booking count, holds và dated counters trước/sau, kiểm không trả trace/exception/stackTrace fields trong những response đã xét. Không suy inventory từ HTTP đơn thuần.

## I. Failure & Recovery

| Scenario | Actual | Kết luận |
|---|---|---|
| Booking INSERT trigger fault sau remote hold | HTTP500; local booking rollback, original hold RELEASED, held=0 | PASS known-hold compensation |
| Hotel hold INSERT fault | HTTP503; inventory transaction rollback, không hold leak; same-command retry sau bỏ fault201 | PASS |
| Hotel hold UPDATE fault khi cancel | HTTP503; Booking PENDING, held=1; bỏ fault rồi retry204, held=0 | PASS, không false SUCCESS |
| Lost downstream response sau commit | upstream201, SQL visible, client disconnect, retry original ID | PASS |
| Stop Hotel process riêng | create503, booking count không tăng; Hotel được start lại và healthy | PASS catalog unavailable |
| Actual expiry scheduler | fixture SQL làm deadline quá hạn; poll tối đa90s, scheduler RELEASED; cancel204 không double release | PASS [6 assertions](evidence/batch-2-booking/expiry-results.json) |
| Hold response timeout/UNKNOWN after remote commit | chưa inject riêng tại boundary Booking→Hotel | NOT VERIFIED qua Gateway |
| Compensation release cũng unavailable đồng thời persistence fail | không chạy double fault | NOT VERIFIED |
| Booking crash/restart giữa remote/local commit, multi-node, broker failover | không chạy | NOT VERIFIED |
| Paid/onsite recovery, points-positive cancellation | fresh service tests, không Gateway paid/loyalty fixture | Gateway NOT VERIFIED |

Fault injection chỉ bằng temporary triggers trong disposable DB; `finally` bỏ triggers/functions. Final SQL xác nhận không còn `batch2_failure`, không negative/oversell. Không đổi UNKNOWN thành SUCCESS. Expiry thử Hotel scheduler; không claim đã chờ Booking five-minute scheduler hoặc mọi recovery worker.

## J. Regression

| Group | Reported | Executed PASS | FAIL/ERROR | SKIPPED | Build |
|---|---:|---:|---:|---:|---|
| Hotel focused module suites, exclude ApplicationTests |396|364|0/0|32|SUCCESS|
| Booking focused module suites, exclude ApplicationTests |265|263|0/0|2|SUCCESS|
| Fresh PostgreSQL AvailabilityRangeIntegrationTest |1|1|0/0|0|SUCCESS|
| Fresh PostgreSQL BookingEventOutboxIntegrationTest |1|1|0/0|0|SUCCESS|

**627 service/MVC/socket tests PASS + 34 skipped**, và **2 opt-in PostgreSQL tests PASS riêng**. Không cộng skips vào PASS. [Maven totals](evidence/batch-2-booking/regression-results.json), [PostgreSQL results](evidence/batch-2-booking/postgres-integration-results.json). Counts lấy console output lượt mới, không dựa Surefire XML cũ. Các opt-in skipped khác không được coi là đã chạy.

PostgreSQL runner tạo UUID container mới, loopback port động, DB `checkinn_hotel`/`checkinn_booking`, role `checkinn_test`; chỉ copy schema từ stack disposable mới, **không copy data**, seed synthetic minimum rồi chạy existing tests. Không có application/scheduler trên các DB integration. Runner kiểm label rồi remove đúng container/anonymous volumes do nó tạo. Range test kiểm query budget/checkout-exclusive/counters; outbox test kiểm rollback/commit fault/concurrent order/replay với PostgreSQL thật nhưng **RabbitTemplate mock**, không phải live-broker recovery proof.

Gateway: **42 main +6 expiry +9 deadline/final-SQL assertions =57 PASS**, bao gồm actor setup assertions. Đây là assertion counts, không phải 57 independent end-to-end workflows. RabbitMQ healthy được dùng bởi application stack; dedicated real-broker failure/recovery suite **NOT RUN**. Frontend tests/typecheck/build **NOT RUN**, không claim frontend build PASS; Place/showDistance blocker vẫn là backlog được bảo vệ. Full repository suite NOT RUN.

## K. Files Changed & Reproduce

Production source/Java tests không đổi. Thêm `docker/booking-gateway-smoke.py`, `booking-expiry-smoke.py`, `booking-sql-assertions.py`, `booking-postgres-integration.py`; overlay `docker-compose.booking-smoke.yml`; `booking-smoke-init.sql`, `booking-smoke-accommodation-bootstrap.sql` test-only. Docs: report này, canonical PLAN/AUDIT, reviewed evidence/manifest, references trong các báo cáo Batch1; backend `.gitignore`. Hai IDE edits có trước được giữ nguyên. [Runbook](evidence/batch-2-booking/REPRODUCE.md).

Raw Batch2: `.local/evidence/batch-2-booking/` gồm image/compose/bootstrap/probe/failed harness/final Gateway/expiry/SQL/Maven/integration logs. Lượt đầu main harness dừng vì tên module `http` bị hàm `http()` che; đã alias import và rerun toàn bộ, final42 PASS. Bootstrap replay và readiness final PASS. Không che các lượt fail; reviewed results chỉ rõ final run, raw có lịch sử.

## L. Protected Scope

`place-service` không có diff/untracked mới; toàn frontend vẫn sạch, không sửa Place/showDistance/shared contracts. Booking/Hotel/Refund production source không đổi; không triển khai VNPay Refund API, không thay Refund business workflow hoặc credentials. Refund demo closure Batch1.6 giữ nguyên; không chạy lại Refund và không claim bằng chứng Refund mới. Không stage/commit/push/merge/PR, không production/shared database mutation.

## M. Final Assessment

**Batch2 Booking–Hotel/Availability unpaid Gateway lifecycle: DONE trong phạm vi đã thực thi.** Workspace organization hoàn tất cho PLAN/AUDIT và root raw execution artifacts đã xác định; unknown ownership/root SQL/review snapshots giữ nguyên có manifest. Không phát hiện source defect cần sửa. Các scope failure/paid/provider/multi-node chưa thực thi giữ NOT VERIFIED, không nâng parent STD-006/007/021/027/028 thành FIXED_VERIFIED.

Bước tiếp theo đề nghị: **G2 — service schema/applied-history manifest, clean-vs-representative-legacy upgrade diff và dữ liệu inventory có nguồn xác nhận**, không tự động backfill tiền/tồn phòng. G1 lifecycle gap của Refund-only stack đã được giải quyết bằng stack riêng; broader non-Place deployment G4, provider G1b, Place G3 vẫn tách scope.

Giữ stack Booking, synthetic fixtures và private ignored env để review/debug. Integration UUID resources đã dọn đúng ownership. Không tự chạy down/prune hoặc xóa evidence.
