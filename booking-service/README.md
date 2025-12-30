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
