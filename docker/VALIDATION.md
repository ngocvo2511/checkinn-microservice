# Kết quả triển khai Docker — 27/10/2026 (Asia/Saigon)

Đây là kết quả lần đầu. Sau khi bật Docker, xem [kết quả kiểm tra tiếp theo ngày 28/10/2026](VALIDATION_2026-10-28.md).

## Đã triển khai

- `checkinn/Dockerfile`: multi-stage Java 21, chọn module bằng build arg, cache Maven, runtime non-root có curl.
- `checkinn/.dockerignore`: giới hạn build context vào POM/source; loại environment và output.
- `docker/docker-compose.yml`: đủ 12 backend module, frontend, Redis, RabbitMQ; app/web/config profiles; DNS nội bộ, health checks, dependencies, loopback ports, persistent Redis/RabbitMQ volumes và project name riêng.
- `docker/rabbitmq/rabbitmq.conf` và `definitions.json`: import definitions qua cấu hình image, bổ sung vhost `/` cho exchanges/queues/bindings. Giữ các queue và routing key đã có.
- `docker/.env.example`: mẫu không chứa credentials thật, 5 kết nối PostgreSQL độc lập và biến dịch vụ ngoài.
- `docker/config/application.yml`: native config rỗng cho Config Server Docker; tránh tự clone cấu hình ngoài.
- `docker/stack.ps1`: validate/build/start/status/logs/stop; kiểm tra cấu hình bắt buộc trước khi start App/Full; không xóa volumes.
- `docker/smoke.ps1`: health, Redis/broker, Eureka registration, gateway read endpoints, TCP gRPC và frontend → gateway.
- `docker/README.md`, link trong backend README: topology, prerequisites, variables, commands và các giới hạn.
- `booking-service/src/main/resources/application.yml`, `notification-service/src/main/resources/application.yml`: thay credentials hardcode VNPay/SMTP bằng biến môi trường. Không thay logic payment/email.
- `checkinn-frontend/Dockerfile`, `.dockerignore`, `next.config.ts`: Node 22, npm ci theo lockfile, Next standalone, runtime non-root.
- Hai frontend server routes `app/api/auth/[...nextauth]/route.ts` và `app/api/loyalty-points/[userId]/route.ts`: thêm `API_INTERNAL_URL` để Next.js server kết nối gateway qua Docker DNS; browser giữ public URL.

Không thay business logic, framework/dependency versions, migration, schema hoặc `PROJECT_STANDARDIZATION_PLAN.md`. Không chỉnh/reset/stage các thay đổi có sẵn trong working tree, không xóa volume/data. Stack monitoring cũ được khảo sát và giới hạn sử dụng được ghi rõ trong README; không khởi chạy hoặc xác nhận nó hoạt động.

## Lệnh đã thực sự chạy

| Lệnh / kiểm tra | Kết quả thực tế |
|---|---|
| `docker version` | Client 29.1.3; không kết nối được server qua named pipe `dockerDesktopLinuxEngine`. |
| `docker compose version` | v2.40.3-desktop.1. |
| Mở Docker Desktop bằng `Start-Process ... -WindowStyle Hidden` | Có process backend sau lần thử; chưa có Engine API khả dụng. |
| `wsl --status` | Default distribution `docker-desktop`, WSL 2; không chứng minh Docker engine hoạt động. |
| `docker info --format '{{.ServerVersion}}'` | Thất bại: named pipe không tồn tại. |
| `docker desktop status` | Không lấy được trạng thái; gợi ý chạy desktop start. |
| `docker desktop start` | Không trả kết quả sau nhiều lần chờ; dừng riêng CLI bị treo, không dừng backend Docker. |
| `docker compose --profile app --profile web --profile config config --quiet` | PASS, chạy lại sau các thay đổi cuối. |
| Cùng profiles + `config --services` | PASS: 15 services; khớp 12 module thật trong parent POM + frontend/Redis/RabbitMQ. |
| `./stack.ps1 -Action Validate -Scope Full` | PASS. |
| Kiểm tra JSON của resolved Compose bằng Python | PASS: inventory, paths Dockerfile/context/POM, healthcheck mọi service, depends_on service_healthy, published ports chỉ loopback, DB overrides, RabbitMQ vhosts, Eureka/internal frontend URL. Đây là kiểm tra cấu hình tĩnh. |
| PowerShell parser cho hai script; merge ordered map của smoke script | PASS; chưa xác minh toàn bộ đường chạy script với containers thật. |
| `docker compose --profile app --profile web --profile config build` | FAIL trước build: không kết nối được Engine API. Không image nào được xác minh build thành công. |
| `docker compose up -d redis rabbitmq` | FAIL tại bước lấy thông tin image: Engine API chưa có. |
| `docker compose ps` / `docker compose logs --tail 20` | FAIL: cùng lỗi Engine API; không có container status/log runtime để đánh giá. |
| `./stack.ps1 -Action Up -Scope App` | Dừng ở preflight vì thiếu `SECRET_KEY`; không tạo app containers hoặc truy cập database. |
| `./smoke.ps1 -Scope Infra` | FAIL tại bước exec Redis vì Engine API chưa có; không coi là lỗi Redis ứng dụng. |
| `npm run build` tại frontend trên host | FAIL: 6 lỗi module resolution tại hai map components, thiếu `leaflet`, CSS của nó và `react-leaflet` trong node_modules hiện có. Cả hai package đã có trong manifest và lockfile (1.9.4 / 5.0.0). Không đổi dependency hay lockfile. Không chạy npm ci trên host; Dockerfile thực hiện npm ci trong image khi engine sẵn sàng. |
| `git diff --check` trong hai repo | PASS (chỉ có cảnh báo chuẩn hóa LF/CRLF). |

## Trạng thái runtime và việc còn lại

**Chưa xác minh service nào khởi chạy thành công trong lần triển khai này.** Docker Engine không khả dụng là blocker chung cho build/start/logs/smoke/integration; việc có process Docker Desktop không được coi là engine đã chạy.

Ngoài engine, App/Full còn cần `.env` riêng với JWT/NextAuth secrets và PostgreSQL test có schema phù hợp, Supabase test, SMTP test. Hosting/schema/database connectivity hiện chưa xác minh. Không thêm local PostgreSQL vì chưa đủ bằng chứng về môi trường database hiện tại và repo thiếu baseline đầy đủ. VNPay sandbox và Google OAuth vẫn cần cấu hình riêng để test chức năng tương ứng.

Các bước chưa hoàn tất: clean Docker image builds, container health/startup, DB/broker connectivity thực tế, Eureka registration thực tế, gateway smoke endpoints, gRPC RPC contract, email/storage/payment/booking integration. Local frontend build thất bại cũng không chứng minh clean image build sẽ thất bại giống vậy; cần thử lại trong môi trường npm ci.

## Chạy lại từ đầu

1. Khắc phục Docker Desktop Linux engine cho đến khi `docker info` trả server information; không reset/xóa Docker data.
2. Tại `checkinn/docker`, tạo `.env` từ `.env.example` nếu chưa có và điền các giá trị test theo README; chuẩn bị database test từ schema/snapshot phù hợp.
3. Chạy `./stack.ps1 -Action Validate -Scope Full`, rồi `./stack.ps1 -Action Build -Scope Full`.
4. Chạy `./stack.ps1 -Action Up -Scope Infra`, `./stack.ps1 -Action Up -Scope Platform`, rồi `./smoke.ps1 -Scope Platform`.
5. Chạy `./stack.ps1 -Action Up -Scope Full`, `./stack.ps1 -Action Status -Scope Full`, `./stack.ps1 -Action Logs -Scope Full`, `./smoke.ps1 -Scope Full`. Nếu Eureka chưa propagate, chờ và chạy lại smoke.
6. Dừng an toàn bằng `./stack.ps1 -Action Down` (giữ persistent volumes).

Dừng ở phạm vi Docker để review; chưa bắt đầu sửa các lỗi nghiệp vụ trong kế hoạch chuẩn hóa.
