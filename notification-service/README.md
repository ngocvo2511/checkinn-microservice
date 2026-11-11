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

Service ghi các mã sự kiện cố định cho nhận/xử lý/gửi/lỗi và retry hết lượt.
Không ghi email, OTP, booking payload, headers hoặc exception từ SMTP vào log.
Đối chiếu lỗi qua trace đang có và mã sự kiện; không bật mail/Rabbit payload debug
trên dữ liệu người dùng để thay thế các thông báo này.

## Delivery regression (07/11/2026)

Lỗi dựng/gửi payment email được trả về listener, tương tự OTP và booking
confirmation. Với cấu hình hiện tại, listener thử tối đa 3 lần (chờ 3 rồi 6 giây),
sau đó reject không requeue để broker chuyển vào DLQ tương ứng. Retry recoverer
và container error handler cũng không log body/header/cause khi SMTP hoặc JSON lỗi.
Fatal message được phát lại với `x-death` tiếp tục được reject vào DLQ, thay vì
nhánh mặc định bỏ message và log toàn nội dung. Không có binding tự replay DLQ.

```powershell
mvn -o -pl notification-service '-Dtest=PaymentNotificationTest,NotificationDeliveryTest,NotificationBrokerPrivacyTest' package
docker compose -p checkinn-test --project-directory docker --env-file docker/.env --env-file docker/.env.test -f docker/docker-compose.yml -f docker/docker-compose.test.yml --profile app build notification-service
python -X utf8 docker/notification-delivery-smoke.py
```

Chạy từ repo backend. Smoke cần RabbitMQ/Mailpit của `checkinn-test` đang healthy;
không cần database. Nó tạo vhost và hai worker dùng image mới, kiểm tra SMTP lỗi,
retry/DLQ/conversion, gửi thật tới Mailpit và private log markers, rồi xóa đúng
worker/vhost/thư synthetic của lần chạy. Không purge queue hay mailbox hiện có.
Vhost được ép bằng command-line property để tránh cấu hình Compose ưu tiên sai.
Script chỉ in tên case/kết quả, không in credentials, email/OTP hoặc nội dung log.

Đây là bounded retry và quarantine, chưa phải exactly-once delivery: SMTP có thể
đã nhận thư trước khi kết nối lỗi hoặc trước khi worker ACK broker. DLQ giữ nguyên
dữ liệu cần gửi, bao gồm thông tin cá nhân/OTP; chỉ người vận hành được phép đọc.
Không tự replay OTP cũ, không có automatic DLQ recovery hay source event outbox.
`NotificationServiceApplicationTests` phụ thuộc môi trường không nằm trong lệnh
unit/package chọn lọc trên; smoke chạy image thực với broker và SMTP local riêng.

Kết quả cập nhật07/11: selected notification **13/13 PASS + JAR**, image build PASS,
native smoke **17/17 PASS** gồm exact cleanup. Selected cross-module package của
User/Auth/Hotel/Booking/Revenue/Notification/Regulations **685 executed PASS**,5
opt-in integrations skipped; ApplicationTests phụ thuộc môi trường excluded,
không tính vào số pass. Frontend Node regression103 PASS, không sửa frontend/place.
Lệnh đầy đủ và phân loại STD-029/030 được ghi ở mục59 của plan workspace.

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


## Final review

See [REVIEW_2026-11-07.md](REVIEW_2026-11-07.md) for the independent PR scope and
fresh 13-test package / 17-assertion SMTP-broker validation. Earlier cross-module
checkpoint totals above are historical.
