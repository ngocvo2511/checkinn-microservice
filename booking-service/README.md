# Booking Service

Spring Boot microservice cho quản lý đặt phòng (bookings) và thanh toán (payments).

## Features

- Tạo đặt phòng mới (Booking)
- Quản lý từng phòng trong một đặt phòng (BookingItems)
- Thanh toán tại khách sạn (Hotel Payment)
- Thanh toán qua VNPay
- Hủy đặt phòng
- Hoàn tiền

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
- Legacy `POST /api/payments/{id}/refund` and gRPC refund return unavailable
  (HTTP 503 / gRPC UNAVAILABLE) until provider refund/query integration is
  implemented and verified. No production adapter or response is faked.
- VNPay receipt is recorded independently of stock confirmation. Hold failure
  leaves the booking unconfirmed for reconciliation, prevents another charge
  and prevents automatic unpaid-hold cancellation. Email/UI distinguish money
  received from room confirmed. Automatic distributed reconciliation/outbox and
  the legacy onsite-reservation TTL policy remain open in STD-007/029.
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
- `POST /api/payments/{id}/refund` - Hoàn tiền
- `POST /api/payments/vnpay-callback` - VNPay callback

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
