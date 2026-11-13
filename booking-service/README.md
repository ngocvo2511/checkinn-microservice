# Booking Service

Spring Boot microservice cho quản lý đặt phòng (bookings) và thanh toán (payments).

## Features

- Tạo đặt phòng mới (Booking)
- Quản lý từng phòng trong một đặt phòng (BookingItems)
- Thanh toán tại khách sạn (Hotel Payment)
- Thanh toán qua VNPay
- Hủy đặt phòng
- Sandbox Internal Refund (demo opt-in, không chuyển tiền thật qua VNPay)

## Database Schema

### bookings
- id (UUID)
- user_id, hotel_id, hotel_name
- check_in_date, check_out_date
- adults, children
- status (PENDING, PENDING_PAYMENT, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW)
- total_amount, paid_amount
- voucher_code, voucher_discount
- contact_name, contact_email, contact_phone
- special_requests
- created_at, updated_at

### booking_items
- id (UUID)
- booking_id (FK)
- room_type_id, room_type_name
- rate_plan_id
- check_in_date, check_out_date
- quantity, unit_price, nights, subtotal
- tax_fee, cancellation_policy, guest_name

### payments
- id (UUID)
- booking_id (FK)
- amount
- method (HOTEL, VNPAY)
- status (PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED)
- transaction_id, vnpay_order_id, vnpay_response_code
- paid_at
- created_at, updated_at

## API Endpoints

### Authorization (standardization batch, 29/10/2026)

REST booking/payment APIs require a verified Bearer JWT. The signed VNPay return
(`GET /api/payments/vnpay/return`) remains public and validates its provider HMAC.
Create booking derives userId from the principal; an explicitly different userId
is rejected. Booking reads/cancellation are scoped to the customer, the hotel's
verified OWNER, or ADMIN. User lists are self/ADMIN; hotel lists and status writes
require the hotel's OWNER or ADMIN. Global booking counts require ADMIN.
Payment creation requires the booking's customer; reads and refund requests use
the booking resource scope. **Refund approval/execution requires ADMIN**, as
agreed on 30/10/2026. The hotel's OWNER may request a refund but cannot execute it.
Onsite receipt confirmation requires the hotel's OWNER or ADMIN.

Booking/payment gRPC methods now require `authorization: Bearer <JWT>` metadata
and the same resource checks. `x-user-id` is not authentication. The legacy gRPC
VNPay callback is rejected because its message cannot carry the signed provider
parameters; use the existing signed REST return. The verifier checks merchant,
amount against the persisted payment, provider transaction status and transaction
number. No caller of the unsigned RPC was found in either repository.

### Cancellation/refund V1 (30/10/2026)

- `GET /api/bookings/cancellation-policy` is public. V1 uses the existing 14:00
  check-in convention explicitly in `Asia/Ho_Chi_Minh`; free cancellation is at
  least 24 hours before that instant, including the exact boundary.
- New clients display the returned policy and send
  `acceptedCancellationPolicyVersion=STANDARD_24H_V1`. The booking stores the
  version, check-in instant and free-cancellation deadline. Legacy bookings and
  callers without acceptance are not assigned that policy retroactively.
- `DELETE /api/bookings/{id}` cancels an eligible reservation and restores HELD
  or CONFIRMED inventory once. Paid cancellation creates a full-amount refund
  request; it does not set payment to REFUNDED or erase paidAmount. Late/legacy
  paid bookings require review. Unpaid bookings may cancel before check-in.
- `POST /api/bookings/{id}/cancel-reviewed`, ADMIN only, accepts a mandatory
  `reason` for exceptions such as `HOTEL_UNAVAILABLE`. Checked-in/out/no-show
  bookings are excluded from this automated full-cancellation workflow.
- `POST /api/payments/{id}/refund-request`, body `{"reason":"..."}`, records
  the authenticated requester and server payment amount. Repeated requests use
  the same request. GET on that path reads the request.
- `POST /api/payments/{id}/refund-request/approve`, ADMIN only, requires a
  cancelled reservation and records the approver/time as
  `APPROVED_PENDING_EXECUTION`. It does not claim provider settlement.
- `POST /api/payments/{id}/refund-request/reject`, ADMIN only, changes REQUESTED
  to REJECTED; rejected requests cannot execute.
- `POST /api/payments/{id}/refund` executes **Sandbox Internal Refund** when
  enabled: ADMIN only, approved/cancelled/full completed-payment preconditions.
  The same ADMIN may approve and execute. Request and Payment become REFUNDED;
  Booking remains CANCELLED, paidAmount decreases once, original receipt/VNPay
  metadata stays intact. Retry is idempotent. No VNPay Refund API or actual money
  movement occurs. Internal execution also uses the existing gRPC service path;
  Batch 1.6 final Gateway acceptance is HTTP, not a new deployed RPC certification.
- `refund.internal.enabled` / `REFUND_INTERNAL_ENABLED` defaults **false** on
  Booking and Revenue; enable both only on isolated demo data. Disabled valid
  execution returns 503 without financial mutation. Consumer-disabled internal
  events require review, not a successful DEBIT. Smoke variable mapping and
  migration prerequisites: [Docker runbook](../docker/README.md#sandbox-internal-refund-demo-reviewed-2026-11-13).
- Completion emits transactional `payment.refunded`, with
  `executionMode=SANDBOX_INTERNAL`, `currency=VND`, stable
  `transactionId=sandbox-refund:<request-id>` and local completion Instant.
  Revenue retains immutable CREDIT and adds one DEBIT; this is internal demo
  accounting, not a provider refund reference/time or settlement.
- Backend/API demo accepted: [Batch 1.6](../Docs/BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md).
  Frontend Refund UI, real provider execution/query/reconciliation remain absent;
  live checkout and full booking/payment/cancellation/refund E2E are not verified.
  Manual evidence/verify endpoints are not required for this demo; legacy verify
  remains fail closed because no trusted settlement verifier exists.
- VNPay receipt is recorded independently of stock confirmation. Hold failure
  leaves the booking unconfirmed for reconciliation, prevents another charge
  and prevents automatic unpaid-hold cancellation. Email/UI distinguish money
  received from room confirmed. Durable paid/onsite reservation recovery and the source outbox now exist
  (sections below); provider reconciliation and full deployed crash/multi-instance
  acceptance remain open in STD-007/029.
- A verified late VNPay callback still records received money after local payment
  cancellation, without reopening the reservation. Replays do not add money twice;
  earlier receipts are preserved for reconciliation.
- Mutating workflows lock booking before payment. Scheduler candidates and payment
  lookups use scalar IDs before locking, and booking disables Open EntityManager
  in View so authorization/controller reads cannot supply stale managed entities
  to a subsequent transaction. Booking DTO reads load lazy items inside read-only
  transactions.

Inventory hold/get/confirm/release/cancel endpoints now require a short-lived
signed `BOOKING_SERVICE` JWT with subject `booking-service`; the REST client
supplies it. User/OWNER/ADMIN JWTs cannot call those internal endpoints. Public
date-specific availability checks remain readable.

### Additive schema deployment

`src/main/resources/db/migration/V3__Cancellation_Policy_And_Refund_Requests.sql`
adds nullable policy snapshots and a separate refund-request table. It does not
backfill policy or rewrite booking/payment history. Booking currently uses
Hibernate `ddl-auto:update` in local Docker; adding the SQL file does not enable
Flyway or constitute production deployment. The migration was applied and
reapplied transactionally only on the isolated PostgreSQL clone. Run the reviewed
SQL through the deployment's existing migration process before starting the new
booking binary; do not infer a complete empty-database baseline from this file.

Regression commands, from the backend repository root:

```powershell
mvn -B -ntp -pl booking-service verify
python docker/booking-security-smoke.py # isolated checkinn-test stack only
```

### Bookings
- `POST /api/bookings` - Tạo đặt phòng mới
- `GET /api/bookings/{id}` - Lấy thông tin đặt phòng
- `GET /api/bookings/user/{userId}` - Lấy danh sách đặt phòng của user
- `PUT /api/bookings/{id}/status` - Cập nhật trạng thái
- `DELETE /api/bookings/{id}` - Hủy đặt phòng

### Payments
- `POST /api/payments` - Tạo thanh toán
- `GET /api/payments/{id}` - Lấy thông tin thanh toán
- `GET /api/payments/booking/{bookingId}` - Lấy thanh toán của đặt phòng
- `POST /api/payments/{id}/refund` - ADMIN Sandbox Internal Refund (opt-in demo; không chuyển tiền VNPay thật)
- `GET /api/payments/vnpay/return` - Signed VNPay return (HMAC/merchant/amount verified)

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/checkinn_booking
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

server:
  port: 8084
```

## Build & Run

```bash
cd booking-service
mvn clean install
mvn spring-boot:run
```

## Notes

- Tất cả items trong một booking phải cùng room_type khi quantity > 1
- Voucher validation được gọi tới voucher service (cần implement)
- VNPay integration cần setup webhook callback


## Onsite reservation V1 — approved 05/11/2026

New HOTEL payment choices persist ONSITE_PENDING and a durable V1_PENDING
reservation intent atomically. This is not a receipt: paidAmount remains zero.
Only a successful inventory confirmation changes the booking to CONFIRMED and
V1_CONFIRMED. Inventory is booked for the existing check-in/check-out dates,
independent of the original short HELD deadline. Same-choice retries return the
same existing payment; they do not create another payment or renew inventory.

A bounded two-thread wake-up executor starts work after local commit, with a
one-second durable DB polling fallback (ten seconds initial delay). It does not
block a committing request opening a second database transaction, avoiding
connection-pool starvation. Work locks booking before inventory, validates the
committed HOTEL payment and zero paid amount, and replays the existing idempotent
hold confirmation. Transport/unknown outcomes keep intent pending with five-second
backoff. Missing/terminal/inconsistent source needs review; no new hold is guessed.
A local completion commit failure leaves the intent retryable after process restart.

V1 bookings bypass calendar expiry/auto check-in/check-out/no-show jobs. Host
records attendance explicitly; no-show cannot be recorded before the arrival
date. Cancellation/no-show commit their source decision and queue an idempotent
inventory release; a queued confirmation never reopens a terminal booking.
Unpaid no-show releases the unused points reservation; an actual paid receipt is
not erased or automatically refunded. Existing cancellation/refund policy applies.

Host/ADMIN records an onsite receipt only after reservation confirmation. Its
amount comes from the persisted server total. Receipt retries do not add money;
loyalty USE/EARN is queued only after actual receipt commit. New onsite payment/
status events are sent after their local transaction commits, not on rollback.
This is not a general reliable RabbitMQ outbox: delivery gaps/ordering and actual
provider refund/query/settlement remain separate standardization work.

Apply docker/sql/onsite-reservation-v1-20261105.sql to the BOOKING database before
releasing the writer. Four nullable fields and an index have no default/backfill;
NULL policy identifies legacy rows and is never auto-promoted. Deploy the truthful
frontend first (it safely displays legacy/unknown states), then the new backend.
Keep expanded schema on rollback. Do not revert to an old writer/scheduler that
ignores V1 fields while V1 bookings exist; disable writes and forward-fix instead.
Historical onsite bookings require review, not a guessed inventory backfill.

Verified-clone commands (run runtime harnesses separately):

```powershell
python -X utf8 docker/onsite-reservation-smoke.py --migrate-only
# Build/up only booking-service in checkinn-test with the test overlay.
python -X utf8 docker/onsite-reservation-smoke.py
python -X utf8 docker/booking-security-smoke.py
python -X utf8 docker/booking-hold-verification-smoke.py
```

The onsite harness uses fresh synthetic hotel/room/guest/booking UUIDs, validates
clone datasources and Mailpit, injects UUID-scoped HTTP-timeout and deferred-commit
faults, restarts only test booking-service, then removes its faults and fixtures.
It records synthetic host cash receipts, never real cash or provider transactions.
BookingResponse.onsiteReservationStatus exposes V1_PENDING/V1_CONFIRMED/
V1_REVIEW_REQUIRED/V1_RELEASED. Clients must read authoritative booking state;
HTTP201 ONSITE_PENDING alone never proves the room was confirmed or money received.


### Booking input validation (05/11/2026)

Create requests are validated at HTTP and service/RPC boundaries before catalog,
inventory or points effects. Contact name/email/phone, canonical UUID hotel/room
identifiers, dates, positive quantity/price and guest counts are checked; nested
items cannot be NULL. Body userId remains optional and is replaced with the
signed principal at HTTP entry. Existing idempotency and cross-field stay rules
remain authoritative. Fractional JSON integers return400, never truncated counts.
Validation responses include fieldErrors without rejected values. Optional
fields and legacy stored rows are not backfilled or promoted.


### Verified payment and room confirmation (06/11/2026)

A verified successful provider callback commits receipt/money and durable V1
inventory intent atomically, then confirms its original hold after commit.
BookingResponse.paidReservationStatus distinguishes pending/confirmed/review/
released stock work; COMPLETED payment is not automatically CONFIRMED booking.
No callback replay, replacement stock, new charge or invented refund is used to
recover a timeout/local completion failure. Legacy NULL policy is not backfilled.
Apply paid-reservation-v1-20261105.sql and inventory-review-audit-20261106.sql
from docker/sql in the BOOKING database before the new binary. See Docker README
for clone-only test commands, reviewer audit and rollback/deployment constraints.
