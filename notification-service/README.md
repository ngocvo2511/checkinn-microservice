# Notification Service - Hướng dẫn sử dụng

## Tổng quan
Service nhận thông báo từ RabbitMQ khi thanh toán thành công và gửi email xác nhận cho khách hàng.

## Cấu trúc

### 1. Model
- **BookingNotificationEvent**: Nhận thông tin booking từ RabbitMQ
  - Thông tin booking: ID, hotel, room type
  - Thông tin khách hàng: email, tên
  - Thông tin thanh toán: số tiền, phương thức, thời gian

### 2. RabbitMQ Configuration
- **Exchange**: `hotel.events` (Topic Exchange)
- **Queue**: `notification.payment.queue`
- **Routing Key**: `payment.completed`

### 3. Services
- **EmailNotificationService**: Gửi email thông báo
- **BookingPaymentListener**: Lắng nghe events từ RabbitMQ

## Cấu hình

### application.properties
```properties
# Server
server.port=8086

# RabbitMQ
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Email Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

### Cấu hình Gmail
1. Bật 2-Step Verification trong Google Account
2. Tạo App Password tại: https://myaccount.google.com/apppasswords
3. Sử dụng App Password cho `spring.mail.password`

## Workflow

1. **Booking Service** thanh toán thành công → Gửi PaymentEvent qua RabbitMQ
2. **RabbitMQ** định tuyến message đến `notification.payment.queue`
3. **Notification Service** nhận event → Gửi email cho khách hàng

## Build và chạy

```bash
# Build
cd notification-service
mvn clean install

# Chạy
mvn spring-boot:run

# Hoặc
java -jar target/notification-service-0.0.1-SNAPSHOT.jar
```

## Kiểm tra

### 1. RabbitMQ Management UI
- Truy cập: http://localhost:15672
- Login: guest/guest
- Kiểm tra queue `notification.payment.queue`

### 2. Test gửi message thủ công
```bash
# Truy cập RabbitMQ Management UI
# Vào Queues → notification.payment.queue → Publish message
# Payload:
{
  "bookingId": "test-123",
  "hotelName": "Test Hotel",
  "userName": "Test User",
  "userEmail": "test@example.com",
  "checkInDate": "2026-01-10",
  "checkOutDate": "2026-01-12",
  "nights": 2,
  "rooms": 1,
  "numberOfGuests": 2,
  "amount": 1000000,
  "roomType": "Deluxe Room",
  "paymentMethod": "VNPAY",
  "paymentStatus": "COMPLETED"
}
```

## Logs

Service sẽ log các thông tin:
- ✓ Nhận được event từ RabbitMQ
- ✓ Gửi email thành công
- ✗ Lỗi nếu có

## Troubleshooting

### Email không gửi được
1. Kiểm tra cấu hình Gmail App Password
2. Kiểm tra network/firewall cho port 587
3. Xem logs để biết lỗi chi tiết

### Không nhận được message từ RabbitMQ
1. Kiểm tra RabbitMQ đang chạy: `docker ps`
2. Kiểm tra queue binding trong RabbitMQ UI
3. Kiểm tra booking-service có gửi message không

## Dependencies

```xml
<!-- RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Email -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- Thymeleaf (optional cho template) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```
