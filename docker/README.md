# Docker local development

Compose này phục vụ chạy và kiểm thử trước khi sửa nghiệp vụ. Không dùng database production: hotel/booking/revenue/regulations đang có `ddl-auto: update`; user dùng Flyway và `ddl-auto: validate`.

## Kiến trúc đã kiểm tra

Không module nào có Dockerfile trước thay đổi này. Một Dockerfile dùng chung build từng module độc lập với Maven 3.9.9 / Java 21; runtime JRE 21, user thường. Giữ nguyên các phiên bản Spring Boot/Cloud/gRPC trong POM (bao gồm Config Server Boot 4.0.0, Eureka Boot 3.3.0 và các module Boot 3.3.4).

| Thành phần | HTTP nội bộ | gRPC nội bộ | Dependency chính |
|---|---:|---:|---|
| eureka-server | 8761 | — | Registry |
| admin-server | 9095 | — | Spring Boot Admin |
| api-gateway | 8080 | — | Eureka, routes `lb://` giữ nguyên |
| config-server (profile config) | 8888 | — | Native config tại `docker/config` |
| auth-service | 8081 | 9091 | Redis, user-service |
| user-service | 8082 | 9097 | PostgreSQL `checkinn_user`, Redis, RabbitMQ, regulations |
| hotel-service | 8083 | 9092 | PostgreSQL `checkinn_hotel`, Redis, user/media gRPC |
| booking-service | 8084 | 9093 | PostgreSQL `checkinn_booking`, Redis, RabbitMQ, user/hotel, VNPay |
| media-service | 8085 | 9096 | Supabase Storage bên ngoài |
| notification-service | Không có HTTP listener (YAML có port 8086) | — | AMQP worker, SMTP; không có Web/Eureka client dependency |
| revenue-service | 8088 | — | PostgreSQL `checkinn_revenue`, RabbitMQ, hotel/regulations |
| regulations-service | 8091 | — | PostgreSQL `checkinn_regulations`, Redis, RabbitMQ |
| frontend (profile web) | 3000 | — | Next.js 16 / React 19, Gateway, NextAuth; Google OAuth tùy chọn |
| redis / rabbitmq | 6379 / 5672 | — | Named volumes; RabbitMQ management 15672 |

Không có payment-service riêng trong Maven: payment nằm trong booking-service. Không có MongoDB dependency trong cấu hình database đã khảo sát.

## Chuẩn bị

Docker Desktop Linux engine hoạt động, Docker Compose v2 có `--wait`, Internet để tải Maven/npm/base images; PowerShell để dùng script. Không cần cài Java/Node/Maven trên host để build image. Hai repo phải nằm cạnh nhau: `DA1/checkinn` và `DA1/checkinn-frontend`.

Chạy tại `checkinn/docker`:

```powershell
Copy-Item .env.example .env  # chỉ lần đầu, không ghi đè .env đã có
```

Điền `.env` (đã được Git ignore):

- `SECRET_KEY`: chuỗi ngẫu nhiên riêng ít nhất 32 ký tự ASCII, dùng chung cho các backend; `NEXTAUTH_SECRET`: secret ngẫu nhiên riêng cho NextAuth. Có thể sinh từng giá trị bằng `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`.
- Năm nhóm `USER/HOTEL/BOOKING/REVENUE/REGULATIONS_DB_URL`, `_DB_USERNAME`, `_DB_PASSWORD`: database **test** đã chuẩn bị schema. JDBC URL có thể chứa tham số SSL theo nhà cung cấp.
- `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` và hai bucket test. Storage vẫn do Supabase host; Compose không thay thế bằng local database/storage.
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`: SMTP test có STARTTLS/auth (cấu hình ứng dụng hiện có yêu cầu cả hai).
- VNPay sandbox: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`; thiếu chúng thì chưa kiểm thử được payment. Không cần cho health/read smoke tests.
- Google OAuth tùy chọn: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, callback `${NEXTAUTH_URL}/api/auth/callback/google` tại nhà cung cấp.

Không chép credentials thật vào file example hoặc image. `.dockerignore` loại `.env`, Git, build output, logs; backend chỉ gửi POM và source. `.env` của frontend trên host không được copy vào image. Docker Compose dùng `docker/.env`, không tự load `.env` từ từng module. Biến shell ghi đè giá trị từ `.env`.

### Database và cấu hình từ xa

Đã xác minh ngày 28/10/2026: hệ thống hiện dùng PostgreSQL 18 local với đủ 5 database trong bảng trên; kết nối chỉ đọc từ host và container đều thành công. Compose **không tự tạo, di chuyển hoặc truy cập database** trong quá trình validate/build. Khi bật App, ứng dụng sẽ kết nối database đã cấu hình và có thể chạy DDL/migration. Việc chuyển sang Supabase chưa được thực hiện.

Mặc định URL trỏ `host.docker.internal:5432` (PostgreSQL trên host); cần host cho phép kết nối từ Docker. Nếu database ở ngoài, dùng hostname thật của database test. `localhost` bên trong container là chính container đó. Từ host vẫn dùng `localhost:<published-port>`.

Repo chưa có baseline tạo đầy đủ schema: user chỉ có V2 sửa bảng `users`, hotel V1 đã `ALTER TABLE hotels`; chưa xác minh schema hiện có. **Không thể bảo đảm database trống khởi chạy được.** Chuẩn bị schema/snapshot test đã loại dữ liệu nhạy cảm trước; không tắt validate/Flyway hoặc tự tạo schema giả để che lỗi. Không chạy payment/booking integration tests trên database thật.

Config Server gốc clone Git repo ngoài. Docker dùng profile `native` với config rỗng tại `docker/config/application.yml`; ứng dụng Docker tắt Config Client và dùng cấu hình trong module + environment để không bị cấu hình Git ngoài ghi đè. Profile `config` cho phép kiểm tra Config Server độc lập; nó không nằm trên critical startup path. Muốn dùng remote config, phải review nội dung và chủ động sửa các override này.

### Host và container

- Browser → `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`; giá trị được đóng vào frontend lúc build. Đổi giá trị cần build lại frontend.
- Next.js server → `API_INTERNAL_URL=http://api-gateway:8080` cho hai API routes server-side. `NEXTAUTH_URL=http://localhost:3000` là URL công khai.
- Backend → Eureka `eureka-server:8761`, Admin `admin-server:9095`, Redis `redis:6379`, RabbitMQ `rabbitmq:5672`. REST user/hotel/regulations dùng service DNS và port thật. gRPC discovery giữ cấu hình trong module.
- Chỉ publish loopback 3000, 8080, 8761, 9095, 15672; 8888 khi bật config. Có thể chỉnh các port backend qua `.env.example`; frontend cố định 3000 vì CORS hiện có. Đổi gateway port phải đổi `NEXT_PUBLIC_API_BASE_URL` và build lại frontend. HTTP/gRPC nghiệp vụ, Redis và AMQP không publish ra host.

## Build, chạy và quan sát

### Test bằng bản sao PostgreSQL đang dùng

Đây là cách đang dùng để kiểm tra hệ thống mà không khởi động ứng dụng trên database gốc. `schema.sql` và các file SQL khởi tạo có thể đã cũ; script clone dùng `pg_dump` trực tiếp từ 5 database trong `.env`, rồi so sánh metadata cột và số dòng từng bảng sau restore. Snapshot từng database nhất quán riêng, không phải một transaction chung cho cả 5 database.

Yêu cầu thêm Python và PostgreSQL 18 client binaries trên host. Mặc định script tìm `C:/Program Files/PostgreSQL/18/bin`; chỉnh `CHECKINN_PG_BIN` nếu cài ở chỗ khác. Credentials database nguồn chỉ đọc từ `.env`; source connections dùng `default_transaction_read_only=on`.

```powershell
# Chỉ lần đầu: tạo bản sao trong PostgreSQL Docker riêng.
python ./clone-test-databases.py
# Nếu bản sao đã có, bỏ qua lệnh clone; script từ chối ghi đè database.
./stack.ps1 -Test -Action Validate -Scope App
./stack.ps1 -Test -Action Up -Scope App
./stack.ps1 -Test -Action Up -Scope Config
./smoke.ps1 -Test -Scope App
python ./integration-smoke.py
./stack.ps1 -Test -Action Status -Scope App
./stack.ps1 -Test -Action Logs -Scope App
./stack.ps1 -Test -Action Down
```

`-Test` chọn project `checkinn-test`, overlay `docker-compose.test.yml` và `.env.test` (secret riêng, tự sinh, Git ignore). PostgreSQL test không publish port ra host; 5 datasource trong container trỏ `postgres-test:5432`, username `checkinn_test`. User/Flyway và Hibernate vẫn chạy đúng cấu hình hiện có nhưng chỉ tác động bản sao. Không tạo lại schema từ SQL cũ, không tắt schema validation/migrations để che lỗi.

Email test đi vào Mailpit tại [localhost:8025](http://localhost:8025), không có cấu hình relay ra ngoài. SMTP thật trong `.env` không bị sửa. PostgreSQL clone, Redis/RabbitMQ test và Mailpit dùng volumes riêng. Dump và manifest nằm trong `docker/logs/db-clones` đã Git ignore; chúng có dữ liệu sao chép, không đưa vào Git hoặc chia sẻ công khai. `--resume` của script clone chỉ dành cho lần clone bị gián đoạn trước khi bất kỳ app container test nào được tạo; không ghi đè các database đã tồn tại.

Stack `checkinn-local` trước đã được dừng để giải phóng cổng/RAM, không xóa containers hoặc volumes. Test dùng các public ports giống `.env`, cộng Mailpit 8025. Các lệnh **không có `-Test` vẫn trỏ `.env` gốc**, có thể kết nối và chạy DDL/migration trên database gốc khi Up App/Full; không dùng chúng nếu chỉ muốn test bản sao.

`integration-smoke.py` dùng grpcurl container để gọi RPC đọc theo proto/fixture có sẵn, kiểm tra consumers và phát một OTP event giả chỉ tới Mailpit. Không gọi VNPay, không ghi Supabase hoặc gửi email thật. Nó trả exit code 1 khi bất kỳ mục nào lỗi; hiện kiểm tra revenue summary không truyền hotelId còn HTTP 500, còn summary với hotelId hợp lệ pass. Xem báo cáo mới nhất bên dưới.

### Chạy với môi trường trong .env (không phải bản sao)

```powershell
./stack.ps1 -Action Validate -Scope Full
./stack.ps1 -Action Build -Scope Full
./stack.ps1 -Action Up -Scope Infra
./stack.ps1 -Action Up -Scope Platform
./smoke.ps1 -Scope Platform
# Sau khi đã cấu hình database và dịch vụ test bên ngoài:
./stack.ps1 -Action Up -Scope Full
./smoke.ps1 -Scope Full
./stack.ps1 -Action Status -Scope Full
./stack.ps1 -Action Logs -Scope Full
./stack.ps1 -Action Down
```

`Infra`: Redis/RabbitMQ. `Platform` (default): thêm Eureka/Admin/Gateway. `App`: thêm 8 business services. `Full`: thêm frontend và Config Server. `Config`: chỉ Config Server. Script Up kiểm tra cấu hình bắt buộc của App/Full trước khi tạo container; chưa kiểm chứng credentials, schema hoặc khả năng kết nối chỉ bằng kiểm tra đó. Build image bỏ qua chạy unit tests để không yêu cầu dependency runtime; không đồng nghĩa test suite đã pass.

Lệnh Compose tương đương (sau khi đã điền `.env`):

```powershell
docker compose --profile app --profile web --profile config config --quiet
docker compose --profile app --profile web --profile config build
docker compose up -d --wait redis rabbitmq
docker compose up -d --wait
docker compose --profile app --profile web --profile config up -d --wait --wait-timeout 300
docker compose --profile app --profile web --profile config ps -a
docker compose logs -f --tail 100 booking-service
docker compose --profile app --profile web --profile config down
```

`down` giữ named volumes; không dùng `down -v`, prune hoặc xóa data để khắc phục lỗi. Project name `checkinn-local` tách khỏi tên project cũ; không tiếp quản container/volume cũ. Dữ liệu Redis/RabbitMQ cũ không tự copy sang volumes mới. Credentials RabbitMQ chỉ được khởi tạo trên volume mới; sửa `.env` không đổi user/password trong volume đã có.

Health backend HTTP dùng `/actuator/health` (bao gồm contributor DB/Redis/Rabbit khi có), Admin dùng trang `/`, frontend dùng NextAuth providers. Notification là worker không mở HTTP: health kiểm tra subscriptions của hai notification queues và SMTP TCP; test Mailpit xác minh thêm gửi email thật qua worker tới SMTP sink. Health không chứng minh nghiệp vụ thành công.

RabbitMQ import definitions qua `rabbitmq.conf`. Definitions khai báo đầy đủ durable/auto_delete/internal/arguments và vhost ứng dụng `checkinn`; backend dùng cùng vhost. Script startup tạo user nếu chưa có và cấp quyền cho vhost mới nếu thiếu; không reset password hoặc ghi đè quyền đã có. Health chỉ pass sau bootstrap, listener và xác thực credentials đều thành công. Nếu đổi credentials trong `.env` nhưng user đã tồn tại với mật khẩu khác, startup sẽ báo lỗi thay vì tự thay password. Vhost `/` và dữ liệu từ cấu hình cũ vẫn giữ nguyên, không tự chuyển hoặc xóa; ứng dụng mới dùng `checkinn`.

Smoke script kiểm tra health trong container, broker/Redis, Eureka registration, GET `/cities` và `/hotels` qua gateway, TCP gRPC giữa container, frontend → gateway. Discovery có độ trễ: nếu registration chưa UP, đợi rồi chạy lại. Chưa gọi RPC thật, gửi SMTP, ghi Supabase, tạo booking hoặc gọi VNPay; các bước này cần integration fixtures và dịch vụ sandbox. Với App/Full thiếu dependency ngoài, khắc phục dependency rồi kiểm tra logs/health và chạy lại; không che lỗi bằng health check chỉ kiểm tra process.

Monitoring cũ tại `monitoring/docker-compose.yml` đã được khảo sát nhưng vẫn là stack tùy chọn riêng: Prometheus dùng `host.docker.internal:8761`, promtail đọc `checkinn/logs` của host. Stack ứng dụng mới ghi stdout và log bên trong container, nên promtail cũ **không tự nhận log của stack mới**. Dùng `docker compose logs` làm cách quan sát được tài liệu này hỗ trợ. Chưa xác minh Prometheus/Loki/Grafana và không tự khởi chạy stack monitoring cũ.

Kết quả mới nhất trên 5 database sao chép: [VALIDATION_CLONED_DATABASES_2026-10-28.md](VALIDATION_CLONED_DATABASES_2026-10-28.md). Báo cáo trước khi clone: [VALIDATION_2026-10-28.md](VALIDATION_2026-10-28.md); lần đầu chưa có Engine: [VALIDATION.md](VALIDATION.md).
