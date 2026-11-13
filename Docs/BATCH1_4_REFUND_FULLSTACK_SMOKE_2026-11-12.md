# Batch 1.4 — Refund Full-Stack Smoke Test & Final Acceptance

Ngày 12/11/2026 (Asia/Saigon).

## A. Environment

- Backend: branch `master`, HEAD `27f1d98` tại thời điểm bắt đầu.
- Working tree đã **dirty từ trước Batch 1.4** (toàn bộ thay đổi Batch 1–1.3, hai file `.idea` và các file Docs/SQL/test chưa theo dõi). Batch này không reset, stage, commit, push hoặc sửa các thay đổi đó.
- Batch này chỉ thay đổi `docker/docker-compose.yml`: thêm biến opt-in `REFUND_INTERNAL_ENABLED: ${REFUND_INTERNAL_ENABLED:-false}` cho `booking-service` và `revenue-service`. Giá trị mặc định vẫn là `false`; không thay VNPay configuration.
- Docker Desktop có các container `checkinn-test-*` đã tồn tại, tất cả đều dừng trước batch. Metadata cho thấy chúng thuộc Compose project `checkinn-test`, dùng volume riêng `checkinn-test_postgres-test-data`, không phải database/volume `hotel-postgres` hay các container `demo-*`.
- Đã start tạm, không thay dữ liệu, các component sau cùng healthy: PostgreSQL test, Redis, RabbitMQ, Eureka, API Gateway (8080), Auth, User, Hotel, Booking và Revenue. Không start database/volume dùng chung khác.
- Test overlay hiện có dùng `postgres-test` và database `checkinn_user`, `checkinn_hotel`, `checkinn_booking`, `checkinn_revenue`, `checkinn_regulations`; không expose PostgreSQL host port. Tuy nhiên volume này đã có sẵn, vì vậy **không được coi là database smoke mới**.

## B. Smoke Test Setup

Đã đọc plan/audit và các báo cáo Batch 1, 1.1, 1.2, 1.3 trước khi chạy. Source xác nhận Gateway route `/api/payments/**` đi `lb://booking-service`; Auth route `/api/auth/**` đi `lb://auth-service`; refund execution là `POST /api/payments/{id}/refund` và chỉ ADMIN được gọi.

Migrations Refund đã được xác định theo thứ tự source/harness: `V3__Cancellation_Policy_And_Refund_Requests.sql`, migration outbox, `manual-refund-v1-20261112.sql`, `sandbox-internal-refund-v1-20261112.sql`, `revenue-cash-transactions-v1-20261107.sql`, rồi `revenue-sandbox-refund-v1-20261112.sql`. Các SQL chứa `refund_requests`, `manual_refund_executions`, `manual_refund_audit`, `booking_event_outbox`, `revenue_cash_transactions` và `execution_mode` như yêu cầu.

Không chạy migration trên volume đang tồn tại: Compose test chỉ hỗ trợ clone database bằng `clone-test-databases.py`, trái với yêu cầu batch không clone/chạm database có dữ liệu. Hai service local cũng đang cấu hình Hibernate `ddl-auto:update`; không thể dùng điều đó thay migration. Vì không có runner tạo fresh baseline đã được xác nhận an toàn, không tạo fixture, user, booking, payment hoặc token giả.

Authentication source có `POST /api/auth/register` và `POST /api/auth/login`; không có cơ chế provision ADMIN qua flow test hợp lệ được xác nhận trong batch này. Không hardcode JWT hoặc bypass security.

Frontend được kiểm tra theo source: không có Refund API client, route/page, component, hook hoặc browser test cho `refund-request` hay `/api/payments/{id}/refund`. Vì vậy không khởi động Browser E2E và không tuyên bố frontend PASS.

## C. Execution Results

Các request runtime đều qua `http://127.0.0.1:8080` (Gateway), không gọi trực tiếp port Booking Service.

- `GET /actuator/health` trả `200` với `{"status":"UP","groups":["liveness","readiness"]}`.
- `POST /api/auth/login` với body `{}` trả `400`: route Gateway → Auth Service hoạt động và validation downstream nhận request.
- Anonymous `POST /api/payments/does-not-exist/refund` trả `401` sau khi Booking Service healthy (trước readiness, Gateway trả 503; không dùng kết quả startup đó làm kết quả smoke).
- Anonymous `POST /api/payments/does-not-exist/refund-request` trả `401`.
- CORS preflight `OPTIONS /api/payments/does-not-exist/refund` với Origin frontend trả `200`.

Không chạy happy path, approve/reject, execute, retry, concurrent HTTP, outbox/RabbitMQ ledger assertions hoặc disabled-flag mutation scenario: các scenario này cần database fresh và CUSTOMER/ADMIN hợp lệ; chạy trên volume cũ sẽ vi phạm safety scope. Không gọi VNPay API/callback và không thay đổi VNPay credentials.

Lệnh focused regression đã được thử:

```text
mvn -o -B -ntp -pl booking-service,revenue-service \
  -Dtest=RefundRequestServiceTest,PaymentServiceTest,SandboxRefundTest,ManualRefundServiceTest,BookingAuthorizationTest,RefundPipelineTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Sandbox chặn lần đầu. Lần retry được phê duyệt đạt Maven protobuf compile nhưng không trả Surefire summary/BUILD SUCCESS trước khi process kết thúc, nên **không ghi nhận kết quả test mới PASS**. Bằng chứng Batch 1.3 vẫn là 300 PASS/4 skipped cho service/PostgreSQL/RabbitMQ harness, nhưng không thay thế HTTP full-stack smoke batch này.

## D. Verification Matrix

| Scenario | Execution Method | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|
| API Gateway health | HTTP Gateway | 200/UP | 200/UP | PASS | `GET /actuator/health` |
| Gateway route Auth | HTTP Gateway | Request reaches Auth | malformed login returns 400 | PASS | `POST /api/auth/login` |
| Anonymous execute refund | HTTP Gateway | denied | 401 | PASS | `POST /api/payments/does-not-exist/refund` |
| Anonymous refund request | HTTP Gateway | denied | 401 | PASS | `POST /api/payments/does-not-exist/refund-request` |
| Refund CORS preflight | HTTP Gateway | allowed preflight | 200 | PASS | `OPTIONS /api/payments/.../refund` |
| CUSTOMER login with valid credentials | HTTP | valid JWT | no safe fresh actor | BLOCKED | fresh DB/admin setup blocker |
| ADMIN login/authorization | HTTP | valid ADMIN JWT | no valid ADMIN provisioned | BLOCKED | same blocker |
| Create/cancel booking and seeded completed payment | HTTP | valid fixture | no fresh database/fixture | BLOCKED | safety restriction |
| Request → approve → execute | HTTP Gateway | REFUNDED state | not run | BLOCKED | safety/auth blocker |
| Original receipt/paidAmount/booking state | SQL + HTTP | immutable receipt, paidAmount 0, CANCELLED | not run | BLOCKED | no fixture |
| Outbox → RabbitMQ → Revenue DEBIT | runtime + SQL polling | exactly one debit | not run | BLOCKED | no fixture |
| Retry/concurrent execute | HTTP Gateway | no duplicate outbox/debit | not run | BLOCKED | no fixture |
| Flag false runtime execution | HTTP Gateway | fail closed/no mutation | not run | NOT VERIFIED | existing stack flag unset but no valid ADMIN/fixture |
| Other authorization negatives (CUSTOMER/OWNER) | HTTP Gateway | denied | not run | BLOCKED | no valid actor |
| Frontend customer/admin flow | browser E2E | API/UI update | no Refund UI/client exists | NOT IMPLEMENTED | frontend source search |
| Live VNPay Sandbox checkout | VNPay | completed sandbox payment | deliberately not called | NOT VERIFIED | no provider call |

## E. Issues & Fixes

### Finding: Compose did not wire the Batch 1.3 flag

Root cause: `docker/docker-compose.yml` passed VNPay values but not `REFUND_INTERNAL_ENABLED` to Booking and Revenue, so a Compose-launched demo could not opt into internal refund execution using the documented environment flag.

Fix: added the flag to both service environments with a safe default of `false`. The setting is only activated when the operator explicitly provides `REFUND_INTERNAL_ENABLED=true`; it does not change existing local behavior, client contract, VNPay handling, Place code, or credentials.

No other source change was made. `git diff --check` passes.

## F. Financial Consistency

No new financial record was written in this batch. Therefore refund request, execution/audit, payment status, booking `paidAmount`, outbox state and Revenue cash transactions are **not verified by Batch 1.4 runtime data**.

Batch 1.3 remains the available lower-level evidence: service/PostgreSQL/RabbitMQ harness verified immutable original CREDIT/receipt, one SANDBOX_INTERNAL DEBIT, payment/request `REFUNDED`, booking remaining `CANCELLED`, and replay deduplication. This report does not promote those harness results to HTTP full-stack evidence.

## G. Protected Scope

- `git diff --exit-code -- place-service` passes.
- `checkinn-frontend` working tree is clean after the inspection.
- No Place backend/frontend file, including `showDistance`, was edited.

## H. Remaining Gaps

1. A fresh, explicitly disposable database baseline/migration mechanism is needed. The only current Compose pathway clones existing databases and is unsuitable under this batch safety rule.
2. Need a valid Customer and valid ADMIN provisioned through supported registration/seed flow, without token fabrication.
3. Rebuild/recreate the isolated stack with `REFUND_INTERNAL_ENABLED=true` on **both** Booking and Revenue, apply the listed migrations to its new databases, then run the complete Gateway fixture flow and bounded ledger polling.
4. Implement Refund frontend UI/API client before claiming frontend E2E.
5. VNPay Sandbox checkout has not been tested and provider Refund API was not called.

## I. Final Status

**PARTIAL.** Gateway, service discovery/routing, anonymous refund denial and CORS were demonstrated in a running isolated stack; Compose feature-flag wiring was corrected safely. The acceptance criteria for Backend/API Full-Stack Smoke are not met because no fresh migrated database, legitimate CUSTOMER/ADMIN actors, HTTP happy path, financial outbox/Revenue evidence, idempotency/concurrency evidence, or frontend Refund UI verification was available. No claim is made for live VNPay payment or real refund settlement.
