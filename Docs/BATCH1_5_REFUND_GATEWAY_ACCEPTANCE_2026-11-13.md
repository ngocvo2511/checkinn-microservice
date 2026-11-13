# Batch 1.5 — Isolated Refund Gateway Acceptance

Ngày 13/11/2026, Asia/Saigon. **Kết quả: PARTIAL.**

## A. Initial State

Backend `master` tại `27f1d98`; working tree đã có toàn bộ thay đổi chưa commit từ Batch 1–1.4. Batch này không reset/stage/commit/push. Batch 1.4 đã xác nhận Gateway và anonymous boundary nhưng bị chặn vì chỉ có test volume cũ. Không có Place backend/frontend nào được sửa.

## B. Isolated Environment

Project Docker riêng: `checkinn-refund-smoke`; network và named volumes đều có prefix project. Tạo mới PostgreSQL 18, RabbitMQ, Redis, Eureka, Gateway, Auth, User, Booking và Revenue. Host ports riêng: Gateway `18080`, Eureka `18761`, Rabbit management `25672`. Không dùng `checkinn-test_postgres-test-data`, `hotel-postgres`, `demo-*`, không clone/copy database, và không start Place.

Bootstrap test-only dùng volume PostgreSQL mới. User-service có Flyway V2+ nhưng thiếu V1 tạo `users`; `refund-smoke-init.sql` tạo minimal legacy `users`/`points_transactions` *chỉ trong DB trống*. Flyway chạy sau đó, JPA `ddl-auto=update` chỉ tạo baseline disposable, rồi áp dụng SQL thực tế: refund requests V3; booking idempotency/onsite/paid/loyalty/transaction time/outbox/manual/sandbox migrations; revenue source version/cash/receipt/gap/sandbox migrations. Không suy diễn đây là chứng nhận upgrade production legacy.

Phát hiện PostgreSQL 18 cần mount `/var/lib/postgresql` thay vì `/var/lib/postgresql/data`; overlay đã sửa. Đây là lỗi smoke infrastructure, không thay business schema.

## C. Actors & Fixtures

CUSTOMER đăng ký và login thật qua `POST /api/auth/register` và `/api/auth/login` Gateway; email được đánh dấu verified trong fresh test DB để hoàn tất lifecycle đang có. ADMIN là test-only row trong fresh DB với `pgcrypto` bcrypt tương thích `BCryptPasswordEncoder`, role `ADMIN`, sau đó login qua Auth/Gateway thật. Không hardcode JWT hay tạo public ADMIN endpoint.

Fixture là `SEEDED_COMPLETED_PAYMENT` trong fresh Booking DB: booking CUSTOMER, `CANCELLED`, policy/cancellation times hợp lệ, one booking item, Payment `COMPLETED`, positive VND amount, original VNPay receipt/transaction metadata. Original Revenue `CREDIT` immutable được seeded với cùng payment identity. Không gọi VNPay API/callback; không claim live VNPay Sandbox.

## D. Smoke Execution

Tất cả business HTTP requests dùng Gateway `http://127.0.0.1:18080`, không gọi Booking port trực tiếp.

- CUSTOMER refund request: `201`, `REQUESTED`.
- ADMIN approve: `200`, `APPROVED_PENDING_EXECUTION`.
- CUSTOMER execute: `403`.
- ADMIN execute: `200`, Payment/RefundRequest `REFUNDED`.
- Retry and two concurrent ADMIN execute HTTP calls: all `200` replay; no duplicate effect.
- Bounded polling confirmed Booking outbox `payment.refunded` `DELIVERED` and Revenue one `DEBIT`.
- Final database checks: Booking `CANCELLED`, `paid_amount=0.00`; original VNPay transaction number unchanged; one completion audit; Revenue exactly two movements (original CREDIT plus one DEBIT).
- Recreated only smoke Booking/Revenue containers with `REFUND_SMOKE_INTERNAL_ENABLED=false`. ADMIN execute returned `503`; payment stayed `COMPLETED` and no refund outbox was created.

## E. Verification Matrix

| Scenario | Expected | Actual | Status | Evidence |
|---|---|---|---|---|
| Fresh isolated DB/broker | no reused data | project-scoped volumes/network | PASS | Compose resource names |
| Migration/bootstrap | required tables/constraints | migrations applied to fresh DB | PASS | runner SQL `ON_ERROR_STOP` |
| CUSTOMER/Auth | real JWT | registration/login PASS | PASS | smoke runner |
| ADMIN/Auth | real ADMIN JWT | bcrypt seed then login PASS | PASS | smoke runner |
| Request/approve/execute | REQUESTED→APPROVED→REFUNDED | expected states/HTTP | PASS | smoke runner |
| Authorization | customer denied | execute `403` | PASS | smoke runner |
| Outbox/Rabbit/Revenue | one delivered debit | DELIVERED + one DEBIT | PASS | bounded SQL polling |
| Idempotency/concurrency | no duplicate effect | retry + two concurrent 200 replays | PASS | singleton DB counts |
| Disabled flag | no mutation | `503`, completed payment, no outbox | PASS | disabled smoke runner |
| Frontend | Refund UI | absent by existing scope | NOT IMPLEMENTED | no UI/client added |
| Live VNPay | sandbox checkout | not called | NOT VERIFIED | seeded fixture only |
| Focused Maven regression | zero failure/error | BUILD SUCCESS: 95 executed PASS, 2 opt-in skipped | PASS | Surefire XML of seven selected suites, 13/11 |

## F. Financial Consistency

Fixture amount is unchanged. One original VNPAY `CREDIT` remains; successful refund adds exactly one `DEBIT` tagged `SANDBOX_INTERNAL`/VND with `sandbox-refund:<request-id>`, not the original provider transaction identity. Payment and request are `REFUNDED`, booking stays `CANCELLED`, paid amount is reduced once to zero, outbox is delivered, and completion audit exists.

## G. Files Changed

- `docker/docker-compose.refund-smoke.yml`: isolated project overlay, opt-in flag on both producer/consumer.
- `docker/refund-smoke-init.sql`: empty-database User Flyway baseline only.
- `docker/refund-gateway-smoke.py`, `docker/refund-disabled-smoke.py`: disposable acceptance runners.
- `Docs/BATCH1_5_REFUND_GATEWAY_ACCEPTANCE_2026-11-13.md`.

## H. Protected Scope

`git diff --exit-code -- place-service` passes; frontend Place remains unchanged. No `showDistance` change.

## I. Cleanup

Created only `checkinn-refund-smoke_*` containers, network and volumes. They are retained temporarily for reproducibility/debug, not shared with pre-existing stacks. Safe removal only:

```text
docker compose -p checkinn-refund-smoke --project-directory docker --env-file docker/.env.refund-smoke -f docker/docker-compose.yml -f docker/docker-compose.refund-smoke.yml --profile app down -v --remove-orphans
```

This command targets only the named smoke project; no global prune was used. Existing volumes were untouched.

## J. Remaining Gaps

Lượt tiếp tục: chạy lại anonymous execute → 401, CUSTOMER execute → 403 qua Gateway; disabled fixture request/approval/execute → 201/200/503, payment COMPLETED và không refund outbox. Focused Maven regression hoàn tất BUILD SUCCESS: 95 PASS, 2 SKIPPED (RefundPostgresTest và RefundPipelineTest chưa bật opt-in); không cộng hai skipped vào passed. GrpcBookingAuthorizationTest XML cũ không thuộc lượt selected suite này và không cộng vào tổng.

Chưa đủ acceptance DONE: concurrent HTTP hiện chỉ là replay sau REFUNDED, chưa phải hai execute cạnh tranh từ APPROVED. Booking/cancellation và original CREDIT được seeded, chưa chạy create/cancel qua Gateway; chưa xác minh đầy đủ OWNER/invalid-state negatives, timeout retry, net aggregate và regression summary. Những tiêu chí này không được coi là PASS từ source hoặc các kiểm thử Batch 1.3.

Frontend Refund UI is not implemented. Live VNPay Sandbox checkout/refund is not tested and no provider Refund API was called. The fixture is deliberately seeded rather than a real provider payment. Production migration/rollout, multi-instance failover and provider settlement remain outside demo scope.
