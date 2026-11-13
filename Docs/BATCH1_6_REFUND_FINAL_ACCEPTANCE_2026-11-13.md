# Batch 1.6 — Refund Final Gateway Acceptance & Closure

Ngày 13/11/2026, Asia/Saigon.

**Batch 2 archive note:** root logs/JSON named below now reside under `checkinn/.local/evidence/batch-1-refund/` (ignored). Original paths describe execution history; original/new path and SHA-256 are in [workspace manifest](evidence/batch-2-booking/workspace-inventory.json). No raw evidence was rewritten.

**DONE — Refund Backend/API Demo Accepted**

## A. Initial State

Backend `master`, HEAD `27f1d9817c7e50182c1102c5f8b3c5294d4851fd`. Working tree đã dirty: thay đổi Refund Batch 1–1.5, hai file `.idea`, Docs/SQL/tests/harness chưa commit; `docker/refund-final-smoke.py` đã tồn tại trước batch này. Frontend clean. Không reset/stage/commit/push/merge/PR.

Đã đọc plan/audit, Batch 1–1.5 và source Refund/Gateway/Revenue/Booking. Baseline Batch 1.5 happy path, outbox/Revenue và disabled đã PASS; gap chính là concurrent execute từ APPROVED, OWNER/invalid states, lost response, net report và lifecycle Booking. Không suy diễn concurrency replay của Batch 1.5 thành true concurrency.

## B. Environment

Tái sử dụng project `checkinn-refund-smoke` sau khi inspect labels `com.docker.compose.project`, config files, working directory và mounts. PostgreSQL container mount named volume `checkinn-refund-smoke_refund-smoke-postgres-data` vào `/var/lib/postgresql`; volume labels xác nhận project và logical volume `refund-smoke-postgres-data`. Hai volume `checkinn-refund-smoke_rabbitmq-data`, `checkinn-refund-smoke_redis-data` cũng có đúng project label. Không dùng database developer/shared, không clone/reset/xóa volume.

Containers project: postgres-smoke (PostgreSQL 18), rabbitmq, redis, eureka-server, api-gateway, auth-service, user-service, booking-service, revenue-service. DB `checkinn_smoke_user`, `checkinn_smoke_booking`, `checkinn_smoke_revenue`. Host ports localhost: Gateway 18080, Eureka 18761, Rabbit management 25672; PostgreSQL không publish host port. Gateway/Auth login thật hoạt động.

Ban đầu flag false trên Booking. Bật `REFUND_SMOKE_INTERNAL_ENABLED=true` bằng process environment cho Booking/Revenue smoke, không sửa VNPay hoặc file secrets. Sau fix rebuild image riêng `checkinn/refund-acceptance-booking:batch1.6`, overlay `docker-compose.refund-acceptance.yml` chỉ thay image Booking, không đổi tag image của stack khác. Recreate chỉ hai service thuộc smoke project. Sau smoke enabled, chuyển cả hai về false để chạy disabled regression và giữ fail closed.

Integration runner tạo PostgreSQL/Rabbit riêng tên UUID `checkinn-refund-it-*`, localhost ports động; không nối DB smoke/shared. Runner đã xóa đúng containers và anonymous volumes do nó tạo; không còn container prefix này sau test.

## C. Test Fixtures

CUSTOMER/ADMIN hợp lệ đã provision Batch 1.5, login lại qua Auth/Gateway. OWNER mới được seed test-only trong DB User smoke với role mapping OWNER và bcrypt; login Gateway trả role OWNER thật. Không giả JWT, không role header, không public ADMIN endpoint. Không ghi password/token vào evidence hoặc báo cáo.

Mỗi scenario tạo UUID booking/payment/hotel riêng bằng SQL trong DB disposable. Clone một row baseline rồi override identity/user/status/amount/idempotency key; clone booking item riêng. Booking CANCELLED có policy/check-in/deadline từ fixture baseline, paid_amount 1.000.000 VND; payment COMPLETED, amount 1.000.000, original receipt và VNPay transaction number/order riêng. Original CREDIT được seed theo payment và hotel riêng; refund DEBIT phải được consumer tạo. Không gọi VNPay API/callback. Đây là SEEDED_COMPLETED_PAYMENT, không phải checkout hoặc booking lifecycle E2E.

Refund request CUSTOMER qua Gateway 201; approve cùng ADMIN qua Gateway 200. Invalid-state fixtures chỉnh SQL có chủ đích sau request/approve: PENDING payment, binding sai, booking CONFIRMED, paid_amount thiếu. REJECTED được tạo bằng endpoint reject thật. Orphan payment booking_id không tồn tại được seed riêng; schema payments hiện không có FK booking. Không thay constraints/policy để tạo PASS.

## D. True Concurrency Results

Chạy lại trên image sau sửa lỗi. DB xác nhận request `APPROVED_PENDING_EXECUTION` trước khi release `threading.Barrier(2)`; hai urllib HTTP clients ADMIN JWT thật, cùng Gateway endpoint POST `/api/payments/{id}/refund`.

| Client | Start monotonic ns | End monotonic ns | HTTP |
|---|---:|---:|---:|
| 0 | 92915522229200 | 92915823408600 | 200 |
| 1 | 92915521996700 | 92915809686900 | 200 |

Start lệch 0.232500 ms, request intervals overlap. Không timeout/500/deadlock/stuck; không tuyên bố bao phủ mọi server interleaving.

Payment `0bbd5727-4e15-488c-ae4d-ff0c41315cfb`, booking `dae226c6-5006-4101-9db1-228420364cf7`: Payment/RefundRequest REFUNDED; Booking CANCELLED, paid_amount 0.00; một execution state REFUNDED, một audit action SANDBOX_INTERNAL_REFUNDED, một logical payment.refunded outbox DELIVERED, một CREDIT và một DEBIT. Original receipt/transaction number giữ nguyên. **PASS**.

## E. Authorization & Invalid States

Tất cả request business qua Gateway 18080. So sánh SQL state trước/sau: payment/booking/request, paid_amount, receipt, completed execution/completion audit/refund outbox counts. Invalid fixtures không có Revenue DEBIT.

| Scenario | Expected | Actual | Financial result |
|---|---:|---:|---|
| Anonymous execute | 401 | 401 | unchanged |
| CUSTOMER execute | 403 | 403 | unchanged |
| OWNER execute | 403 | 403 | unchanged |
| ADMIN approved execute | success | 200 | singleton completion |
| Payment ID không tồn tại | 404 | 404 | không tạo execution |
| Request chưa approve | 409 | 409 | unchanged, no DEBIT |
| Request REJECTED | 409 | 409 | unchanged, no DEBIT |
| Payment PENDING, request đã approve | 409 | 409 | unchanged, no DEBIT |
| Refund request booking binding sai | 409 | 409 | unchanged, no DEBIT |
| Payment booking không tồn tại | 404 | 404 sau fix | COMPLETED, execution=0, outbox=0 |
| Booking CONFIRMED, request approved | 409 | 409 | unchanged, no DEBIT |
| paid_amount không đủ | 409 | 409 | unchanged, no DEBIT |
| REFUNDED replay | 200 | 200 | singleton, paid_amount không giảm thêm |
| CUSTOMER request payment thuộc người khác | 403 | 403 | không chấp nhận request |

Lỗi thực tế phát hiện: orphan payment gây NoSuchElementException → HTTP 500. Fix duy nhất production batch này: ánh xạ missing Booking trong nhánh internal refund thành ResponseStatusException 404. Regression test mới kiểm status và không gọi publisher/refund service/payment update lock. Gateway sau rebuild trả code NOT_FOUND/message Booking not found, không lộ stack trace/secrets. Các response negative khác dùng error contract hiện có.

## F. Timeout & Retry

**PASS — Method B, lost HTTP response after commit.** Test-only localhost HTTPServer nhận POST, forward execute bằng ADMIN JWT thật qua Gateway, đọc upstream response rồi query DB từ connection khác xác nhận commit. Sau đó đóng socket downstream mà không gửi HTTP status/body. Không chỉnh production logic, không phá Gateway/broker, không sleep làm bằng chứng commit.

Client nhận `RemoteDisconnected`; upstream HTTP 200; DB trước đóng socket: Payment/Request REFUNDED, paid_amount 0, một execution/audit/outbox. Retry trực tiếp Gateway HTTP 200. Poll outbox/Revenue bounded 60s xác nhận singleton và original metadata giữ nguyên. Payment `b7634191-a320-4dfc-96e1-8c3b9f6535df`. Đây là mất response do harness, không claim mạng thật/timeout mọi điểm; Method A short client timeout không chạy.

## G. Revenue Aggregate

Source: RevenueReportService → RevenueCashFlows.read(hotelId,from,to) → immutable `revenue_cash_transactions`; CREDIT được map COMPLETED dương, DEBIT map REFUNDED âm. Adapter kiểm unresolved gaps/review receipts trước khi đọc; không lấy aggregate bằng cách chỉ nhìn Payment projection REFUNDED.

SQL thực thi theo từng payment:

```sql
SELECT count(*), count(*) FILTER (WHERE kind='CREDIT'),
       count(*) FILTER (WHERE kind='DEBIT'),
       sum(CASE WHEN kind='CREDIT' THEN amount ELSE -amount END)
FROM revenue_cash_transactions WHERE payment_id = :payment;
```

Ba successful fixtures sau fix đều có 2 rows, 1 CREDIT +1.000.000, 1 DEBIT −1.000.000, net 0; DEBIT execution_mode SANDBOX_INTERNAL, CREDIT transaction_id original giữ nguyên. Các query còn kiểm đúng một original CREDIT identity và một SANDBOX_INTERNAL DEBIT.

Gateway `GET /api/reports/revenue?hotelId=<fixture-hotel>&from=2026-11-13&to=2026-11-13` bằng ADMIN: HTTP 200, `data=[{"periodStart":"2026-11-13","amount":0}]` cho từng successful fixture. Hotel UUID riêng chỉ chứa payment tương ứng, tránh trộn fixtures lịch sử; không so tổng DB với một payment. **SQL và existing report API PASS**. Không thêm endpoint/dashboard hoặc đổi financial semantics.

## H. Booking Lifecycle Gateway

**BLOCKED**, tách khỏi Refund Gateway **PASS**. Đã gửi POST `/api/bookings` bằng CUSTOMER JWT với UUID hotel/room và body/date/contact/policy hợp lệ; HTTP 503 DEPENDENCY_UNAVAILABLE. Booking log xác nhận `No servers available for service: hotel-service`.

Source createBooking cần HotelCatalogClient.getRoom/getHotel (catalog active/approved/price/capacity), sau đó HotelAvailabilityClient.holdRooms. Isolated stack không có hotel-service/Hotel DB/catalog/inventory; chỉ seed booking DB không đáp ứng dependency. Full cancellation cần hold/reservation hợp lệ, endpoint DELETE `/api/bookings/{id}` (customer) hoặc POST cancel-reviewed (admin), free cancellation policy snapshot và inventory cancellation; không có reservation thật nên không tuyên bố cancellation PASS. Không dựng thêm toàn booking stack/kiến trúc, không sửa Place. Create→cancel qua Gateway chưa nghiệm thu; seeded CANCELLED Refund fixtures vẫn dùng độc lập đúng scope cho phép.

## I. Regression

| Group | Executed PASS | FAIL/ERROR | SKIPPED | Build |
|---|---:|---:|---:|---|
| Focused sau fix, 7 suites | 96 | 0/0 | 2 | BUILD SUCCESS |
| PostgreSQL Booking initial | 1 | 0/0 | 0 | BUILD SUCCESS |
| PostgreSQL fresh JVM retry | 1 | 0/0 | 0 | BUILD SUCCESS |
| PostgreSQL/Rabbit Revenue pipeline | 1 | 0/0 | 0 | BUILD SUCCESS |

Focused: BookingAuthorizationTest 47, ManualRefundServiceTest 3, RefundRequestServiceTest 22, PaymentServiceTest 22, SandboxRefundTest 2; RefundPostgresTest/RefundPipelineTest opt-in mỗi suite 1 skipped trong focused. Hai opt-in đã chạy riêng bằng runner fresh DB/broker; không cộng skipped vào PASS. Integration chạy trước fix orphan (không thay financial logic); sau fix focused và Gateway suite được chạy lại trên code/image mới. Không dùng XML cũ: số liệu lấy logs Maven vừa chạy có BUILD SUCCESS và timestamp hôm nay.

Lệnh focused:

```text
mvn.cmd -o -B -ntp -pl booking-service,revenue-service -Dtest=RefundRequestServiceTest,PaymentServiceTest,SandboxRefundTest,ManualRefundServiceTest,BookingAuthorizationTest,RefundPostgresTest,RefundPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test
python docker/refund-integration.py
```

Logs/evidence ở `C:/Users/ACER/IdeaProjects/DA1/`: BATCH1_6_regression_final.log; BATCH1_6_integration.log; BATCH1_6_booking-initial.log; BATCH1_6_booking-retry.log; BATCH1_6_revenue.log; BATCH1_6_gateway_postfix.log; BATCH1_6_gateway_evidence.json; BATCH1_6_lifecycle_final2.log; BATCH1_6_image.log; BATCH1_6_disabled.log. Integration runner cũ ghi BATCH1_3_* nên đã copy nội dung lượt mới sang BATCH1_6_* ngay sau hoàn tất.

Lượt harness đầu có assertion sai do so chuỗi PostgreSQL numeric `0` với `0.00`; sửa so Decimal và dùng đúng field report `amount`. Financial data đã đúng, đây không phải production bug. Log đầu BATCH1_6_gateway.log giữ lại để minh bạch; bằng chứng chốt dùng postfix run. Một probe ngay sau recreate gặp Gateway 503 trước readiness; không tính là acceptance result, đã đợi route Booking ready rồi chạy lại.

Disabled regression sau sửa PaymentService: request 201, approve 200, ADMIN execute 503; Payment COMPLETED, Booking paid_amount 1000000.00, execution=0, refund outbox=0, Revenue DEBIT=0. Cả Booking/Revenue đã được recreate với REFUND_INTERNAL_ENABLED=false; không mutation tài chính. **PASS**, evidence BATCH1_6_disabled.log.

## J. Files Changed

Chỉ các thay đổi của batch này (cumulative working tree vẫn gồm Batch 1–1.5):

- `booking-service/src/main/java/com/example/bookingservice/payment/service/PaymentService.java`: missing booking 404 trong nhánh Refund.
- `booking-service/src/test/java/com/example/bookingservice/payment/service/SandboxRefundTest.java`: regression orphan payment fail before mutation.
- `docker/refund-acceptance-smoke.py`: barrier clients, per-scenario SQL fixtures, auth/invalid/replay, lost-response proxy, SQL + report API assertions, sanitized JSON evidence.
- `docker/refund-lifecycle-smoke.py`: Gateway creation dependency probe và orphan payment negative.
- `docker/refund-disabled-acceptance.py`: tái sử dụng disabled runner, kiểm thêm paid_amount/execution/outbox/Revenue DEBIT.
- `docker/docker-compose.refund-acceptance.yml`: image Booking tag riêng để không ghi đè image stack khác.
- `Docs/BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md`: báo cáo này.

Không redesign Refund, không frontend UI, không đổi provider/cancellation policy/shared contracts, không migration production mới. Không thay VNPay credentials hoặc gọi VNPay Refund API.

## K. Protected Scope

Trước và sau batch `git -C checkinn diff --exit-code -- place-service` PASS, không untracked Place mới trong status. `git -C checkinn-frontend status --porcelain` rỗng. Place backend/frontend không thay đổi; không sửa showDistance. `git diff --check` PASS. Hai .idea thay đổi đã có từ trước, batch không sửa.

## L. Cleanup

Giữ stack smoke, fixtures và image riêng để review/debug; cuối batch flag false trên Booking/Revenue như lúc bắt đầu. Không reset DB. Integration UUID resources do runner tạo đã remove đúng tên với --volumes; không global prune, không đụng volumes/containers stack khác.

Sau khi review nếu muốn xóa smoke project, trước hết kiểm lại labels/mounts như mục B rồi dùng lệnh cụ thể (không tự chạy trong batch):

```text
docker compose -p checkinn-refund-smoke --project-directory docker --env-file docker/.env.refund-smoke -f docker/docker-compose.yml -f docker/docker-compose.refund-smoke.yml -f docker/docker-compose.refund-acceptance.yml --profile app down -v --remove-orphans
```

Reproduce enabled: build image riêng `docker build --build-arg SERVICE=booking-service -t checkinn/refund-acceptance-booking:batch1.6 .`; đặt process env REFUND_SMOKE_INTERNAL_ENABLED=true, compose với cùng ba files `up -d --no-deps --no-build booking-service revenue-service`, đợi healthy/Gateway route ready rồi chạy `python docker/refund-acceptance-smoke.py`. Chỉ chạy trong project đã verify disposable; không dùng harness vào database khác.

## M. Final Status

**DONE — Refund Backend/API Demo Accepted.** Happy path Batch 1.5 và smoke sau fix chứng minh request/approve/execute Gateway với JWT thật; true concurrent execute, auth/invalid-state negatives, lost-response-after-commit retry, outbox→RabbitMQ→Revenue, SQL/report net 0, singleton effects, disabled fail closed và focused/integration không failure/error. Một ADMIN approve/execute; Booking vẫn CANCELLED, paid_amount giảm một lần, original receipt giữ nguyên.

Booking lifecycle Gateway BLOCKED như H; frontend Refund UI chưa triển khai; live VNPay checkout/provider refund không chạy; production migration, multi-instance/broker failover ngoài scope, không claim PASS. Không nâng provider-backed Refund thành verified từ demo internal.

Đóng nhóm Refund demo Backend/API; không cần thêm batch Refund chỉ để bao phủ optional scope. Đề xuất quay lại issue standardization ưu tiên tiếp theo trong plan/audit (booking catalog/inventory lifecycle có thể là scope riêng nếu cần demo full booking); production rollout/provider refund chỉ mở với yêu cầu và contract riêng.
