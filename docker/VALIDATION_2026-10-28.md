# Kiểm tra Docker và PostgreSQL local — 28/10/2026 (Asia/Saigon)

Đây là đợt kiểm tra trước khi người dùng điền `.env` và chọn dùng bản sao database. Xem [kết quả tiếp theo trên database sao chép](VALIDATION_CLONED_DATABASES_2026-10-28.md) để biết trạng thái hiện tại và các sửa lỗi RabbitMQ/health check được xác minh sau đó.

## Kết quả đã thực hiện

Docker Engine 29.1.3 đã hoạt động. PostgreSQL local đang nghe cổng 5432, server version 18.0. Có thể dùng cấu hình DB/JWT/Supabase hiện có trong IntelliJ; không in hoặc chép secrets vào tài liệu. `docker/.env` hiện tồn tại nhưng rỗng ở thời điểm kiểm tra.

### PostgreSQL: kiểm tra chỉ đọc

Kết nối bằng psql 18 với `PGOPTIONS=-c default_transaction_read_only=on`. Chỉ đọc tên database, phiên bản server và metadata schema; không đọc bản ghi nghiệp vụ hoặc sửa database.

| Database | Số bảng public |
|---|---:|
| checkinn_user | 6 |
| checkinn_hotel | 16 |
| checkinn_booking | 3 |
| checkinn_revenue | 2 |
| checkinn_regulations | 2 |

`checkinn_user` có bảng `flyway_schema_history`. Chưa kiểm chứng Hibernate validate, nội dung migration history hoặc tính tương thích đầy đủ giữa entity và schema.

Một container client `postgres:18-alpine` trên network `checkinn-local_checkinn` kết nối `host.docker.internal:5432` bằng cùng chế độ chỉ đọc, đọc được đủ tên 5 database: **PASS Docker → PostgreSQL local**. Container probe đã tự dừng/xóa với `--rm`; không tạo PostgreSQL server hoặc volume database thay thế. Database gốc không bị clone/di chuyển/ghi dữ liệu trong đợt kiểm tra này.

### Build image

Đã build thành công đủ 12 image backend:

- eureka-server, admin-server, api-gateway, config-server;
- auth-service, user-service, notification-service, media-service;
- hotel-service, booking-service, revenue-service, regulations-service.

Các lệnh build thành công (tại `checkinn/docker`):

```powershell
docker compose build eureka-server admin-server api-gateway config-server
docker compose --profile app build auth-service user-service notification-service media-service
docker compose --profile app build hotel-service booking-service revenue-service regulations-service
```

Lần build toàn bộ đầu tiên dừng tại notification-service: `-DskipTests` vẫn compile test nhưng POM không cung cấp JUnit/Spring Test cho test hiện có. Dockerfile đã đổi sang `-Dmaven.test.skip=true` để chỉ đóng gói runtime image. Đây không phải xác nhận unit tests pass; lỗi cấu hình test của notification-service chưa được sửa. Không đổi framework/dependency versions.

### Startup và health

**8 containers đã khởi động và healthy**: Redis, RabbitMQ, Eureka, Admin Server, API Gateway, Config Server, Auth Service và Media Service.

Các kiểm tra đã pass:

- `docker compose up -d --wait --wait-timeout 180 redis rabbitmq`.
- `./smoke.ps1 -Scope Infra`: Redis PING, RabbitMQ check_running/check_local_alarms.
- RabbitMQ list_exchanges/list_queues: hotel.events, user.events, notification.dlx và 5 queue từ definitions đã có; queue trống trong lần kiểm tra.
- `docker compose --profile config up -d --wait --wait-timeout 180 eureka-server admin-server api-gateway config-server`.
- `./smoke.ps1 -Scope Platform`: health Eureka/Gateway, trang Admin và infrastructure.
- Config Server `/actuator/health`: UP; `/booking-service/docker`: trả native config rỗng từ file mount, không clone remote Git config.
- Khởi động riêng `auth-service` và `media-service` bằng cấu hình JWT/Supabase hiện có, truyền qua environment của process mà không ghi secret ra console/file cấu hình theo dõi bằng Git. Hai service này không có datasource hoặc scheduled DB job trong source đã kiểm tra; các service dùng database vẫn dừng.
- Health trực tiếp của Auth và Media đều UP.
- Qua Gateway: GET `/api/auth/actuator/health` và `/media-service/actuator/health` đều UP. Kiểm chứng được discovery/routing tới hai service đang chạy.
- Từ Gateway mở TCP tới auth-service:9091 và media-service:9096: PASS. Đây chỉ là kiểm tra listener gRPC, chưa gọi RPC contract.

Auth healthy chưa chứng minh login/OTP hoạt động vì user-service chưa chạy. Media healthy chưa chứng minh upload/download hoặc quyền bucket Supabase hợp lệ; chưa gọi storage API ngoài. Không gửi email hoặc tạo giao dịch payment/booking.

### Frontend: còn bị chặn ở TypeScript

Đã thực sự chạy `docker compose --profile web build frontend` với Node 22 và `npm ci` theo lockfile. Leaflet/react-leaflet cài được, không còn lỗi thiếu module như node_modules trên host. Next.js compile production thành công, nhưng **image frontend chưa build thành công** do typecheck.

Lần đầu lỗi tại NextAuth `user.accessToken`: bổ sung hai field optional `accessToken`/`role` vào interface User trong file khai báo sẵn có `types/next-auth.d.ts`. Đây là sửa type, không đổi callback hoặc luồng đăng nhập.

Lần build lại đi qua lỗi NextAuth và dừng tại:

```text
app/host/hotels/[id]/rooms/[roomId]/page.tsx:419:72
Argument of type 'boolean | undefined' is not assignable to parameter of type 'boolean'.
```

Đã chạy thêm `npx --no-install tsc --noEmit --incremental false` trên host: có 32 diagnostics. Các nhóm lỗi gồm nullable Date/boolean, City type không đồng nhất, MediaAsset thiếu displayOrder, AmenityResponse không render được như ReactNode, state suy ra never, `setOtpCode` chưa khai báo; ngoài ra host vẫn thiếu leaflet/react-leaflet trong node_modules. Kết quả host không thay thế clean image typecheck; lỗi đầu tiên trong image được ghi riêng phía trên.

Chưa sửa các màn hình nghiệp vụ hoặc tắt TypeScript checking để vượt build. Chưa khởi động hoặc smoke-test frontend.

## Thay đổi trong lần kiểm tra này

- Backend `Dockerfile`: bỏ compile test trong bước đóng gói runtime image.
- Frontend `types/next-auth.d.ts`: khai báo User.accessToken và User.role optional theo callback hiện có.
- Docker README và báo cáo validation: cập nhật bằng chứng PostgreSQL local, kết quả build/startup/test và giới hạn.

Không thay business logic, schema, migration hoặc plan chuẩn hóa. Không xóa dữ liệu/volumes có sẵn. Các container đã test được để chạy để người dùng quan sát; không có DB business service nào được bật.

## Cần người dùng quyết định trước phần test database ứng dụng

User/hotel/booking/revenue/regulations chưa khởi động: một số service dùng Hibernate update, user có Flyway, booking có scheduler. Khởi động trực tiếp có thể thay đổi schema hoặc dữ liệu ngay cả khi chỉ dự định GET smoke tests. Đã hỏi người dùng chọn bản sao test riêng của 5 database (khuyến nghị) hoặc dùng trực tiếp database local và chấp nhận tác động này; chưa nhận được lựa chọn tại thời điểm báo cáo.

Thông tin DB/JWT/Supabase đã tìm được nên không cần gửi lại secrets trong chat. SMTP test chưa có cấu hình trong docker/.env; VNPay/Google OAuth vẫn cần thiết lập riêng nếu muốn test chức năng tương ứng. Không thực hiện chuyển database lên Supabase trong phạm vi kiểm tra này.

Sau khi người dùng chọn môi trường DB: cấu hình môi trường riêng phù hợp, khởi động 5 service dùng DB và notification khi SMTP sẵn sàng, kiểm tra schema/connectivity, Eureka registrations, gateway GET cities/hotels và các gRPC connections tới user/hotel. Các test ghi booking/payment/email/storage cần fixtures và phạm vi riêng; chưa được coi là hoàn tất.
