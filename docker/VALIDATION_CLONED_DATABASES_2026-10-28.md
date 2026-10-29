# Kiểm thử trên bản sao database thực — 28/10/2026 (Asia/Saigon)

## Kết quả chính

- Tạo và xác minh bản sao riêng của đủ 5 PostgreSQL database từ server local đang dùng; không lấy schema.sql làm nguồn chuẩn.
- Project `checkinn-test` có **16 containers healthy**: 12 module backend, Redis, RabbitMQ, PostgreSQL test và Mailpit. Không bao gồm frontend.
- `./smoke.ps1 -Test -Scope App`: **PASS**, kể cả sau khi restart RabbitMQ với volume/user/permissions đã có.
- `python ./integration-smoke.py`: **11 PASS / 1 FAIL**, exit code 1 đúng với lỗi revenue summary không truyền hotelId.
- Supabase Storage: đọc metadata cả hai bucket cấu hình thành công. SMTP thật: kết nối/EHLO/STARTTLS được nhưng ngắt ở bước AUTH, chưa xác minh đăng nhập thành công; không gửi email thật.
- Chưa sửa business logic, chưa chuyển database lên Supabase, chưa khởi động frontend.

## Bản sao database và bảo toàn nguồn

Người dùng đã điền `.env` và chọn dùng bản sao test. Script `clone-test-databases.py` đọc cấu hình nguồn qua Compose, dùng PostgreSQL 18 `pg_dump -Fc --no-owner --no-acl` trên từng database local. Source connections dùng `PGOPTIONS=-c default_transaction_read_only=on`.

Restore vào PostgreSQL 18 container `postgres-test` trên network riêng `checkinn-test_checkinn`, với tài khoản test và password ngẫu nhiên trong `.env.test` đã Git ignore. Không publish cổng database ra host.

| Database được sao chép | Số bảng public trước khi app chạy | Xác minh sau restore |
|---|---:|---|
| checkinn_user | 6 | Số dòng từng bảng và metadata cột khớp nguồn |
| checkinn_hotel | 16 | Số dòng từng bảng và metadata cột khớp nguồn |
| checkinn_booking | 3 | Số dòng từng bảng và metadata cột khớp nguồn |
| checkinn_revenue | 2 | Số dòng từng bảng và metadata cột khớp nguồn |
| checkinn_regulations | 2 | Số dòng từng bảng và metadata cột khớp nguồn |

Dump và manifest nằm trong `docker/logs/db-clones/20261027T172024Z` (tên thư mục dùng UTC; thời gian local là ngày 28/10). Đây là bản sao dữ liệu riêng tư, đã Git ignore; không đưa vào commit/tài liệu công khai. Mỗi dump có snapshot nhất quán riêng, không có transaction chung xuyên 5 database.

Sau khi chạy ứng dụng, đã kiểm tra **environment thực tế của cả 5 container datasource** đều trỏ `postgres-test:5432`, không chỉ đọc file cấu hình dự kiến. PostgreSQL test có application connections tới đủ 5 database. Flyway history của bản sao user có V1/V2 thành công; service user khởi động với cấu hình validation/Flyway hiện có, không bị tắt để che lỗi schema. Hibernate/scheduler có thể thay đổi bản sao theo cấu hình ứng dụng.

Sau test, đọc lại số dòng từng bảng trên cả 5 database nguồn: vẫn khớp inventory trước test. Đây là kiểm tra row counts, không phải checksum toàn bộ nội dung từng bản ghi. Tất cả thao tác của đợt này với server nguồn là đọc; không chạy application services hoặc DDL/migration trên nguồn.

Lần so sánh ban đầu phát hiện khác biệt biểu diễn newline Windows/Linux trong chuỗi metadata; đã sửa script so sánh theo từng dòng và tiếp tục kiểm chứng bản sao hiện có bằng `--resume`, không xóa hoặc ghi đè database đã restore. Script từ chối resume khi đã có application containers test.

## Những lỗi Docker/config đã sửa qua runtime

### RabbitMQ bootstrap và definitions

Import definitions trên node mới đã làm RabbitMQ bỏ bước tạo default user. Broker có queue/exchange và process healthy nhưng không có user; consumer bị ACCESS_REFUSED. Thêm `rabbitmq/start.sh` để đợi broker boot, tạo user nếu thiếu, cấp quyền vào vhost ứng dụng nếu chưa có và xác thực credentials.

Definitions cũ thiếu auto_delete/internal/arguments, dẫn tới PRECONDITION_FAILED khi Spring khai báo lại exchange. Đã bổ sung thuộc tính đầy đủ. Ứng dụng dùng vhost mới `checkinn`, giữ nguyên vhost `/` và dữ liệu cũ thay vì xóa/recreate topology có sẵn. Không reset password hoặc ghi đè các grants đã có.

Health check chỉ pass sau bootstrap marker, listener checks và authenticate_user. Đã restart broker với persistent volume và user/permissions hiện có: broker healthy trở lại, consumer reconnect và App smoke test pass. Không xóa volume để chữa lỗi.

### Notification là worker, không có HTTP server

POM notification-service không có Web hoặc Eureka client dependency. `server.port: 8086` trong YAML không tạo HTTP listener. Kiểm tra HTTP actuator hoặc đòi Eureka registration cho worker là sai với runtime thực tế.

Health worker hiện xác minh hai notification queue subscriptions và SMTP TCP. App smoke kiểm tra worker riêng, không yêu cầu worker đăng ký Eureka. Luồng gửi email được xác minh thêm qua Mailpit. Không thêm Web/Eureka dependency chỉ để đáp ứng một health check không phù hợp.

## Smoke và integration checks

`./smoke.ps1 -Test -Scope App` đã pass: Redis, broker, PostgreSQL test, Mailpit, HTTP health của các service có HTTP server, Admin, worker health, Eureka registration của các service có Eureka client, GET cities/hotels qua Gateway và TCP gRPC User/Hotel/Media.

| Kiểm tra mở rộng | Kết quả |
|---|---|
| Gateway GET /cities | PASS, JSON array |
| Gateway GET /hotels | PASS, JSON array |
| Gateway GET /api/regulations/typed/points | PASS |
| Gateway GET /api/regulations/typed/commission | PASS |
| Gateway revenue summary không có hotelId | **FAIL: HTTP 500** |
| Gateway revenue summary có hotelId lấy từ bản sao | PASS; kiểm chứng thêm Revenue → Hotel gRPC |
| Application connections tới đủ 5 database test | PASS |
| UserService/GetUserById trên fixture trong bản sao | PASS |
| HotelGrpcService/GetHotelById trên fixture trong bản sao | PASS |
| MediaGrpcService/GetSignedUrls với batch rỗng | PASS, không phát sinh request Storage |
| Consumers notification/revenue trên RabbitMQ | PASS |
| Event OTP giả → RabbitMQ → Notification → SMTP Mailpit | PASS |

User RPC được gọi bằng proto trong repository vì reflection list hoạt động nhưng truy vấn service descriptor qua reflection trả application error. RPC thực tế với proto được cung cấp đã pass; chưa sửa lỗi reflection hoặc dependency.

Test email dùng địa chỉ `docker-smoke@example.invalid`, chỉ tới Mailpit, không relay. Test script kiểm tra environment của container Notification trước khi phát event, tránh dùng SMTP thật. Không tạo payment/booking hoặc sửa user thật để tạo fixture; các RPC đọc dùng dữ liệu đã có trong bản sao. Không lưu response bodies, PII, token hoặc credentials trong kết quả kiểm tra. Kết quả máy đọc ở `docker/logs/integration-results.json` đã Git ignore.

## Lỗi và phần chưa hoàn tất

### Revenue summary không truyền hotelId

Request tái hiện trên stack test:

```powershell
curl.exe 'http://localhost:8080/api/reports/summary?from=2026-01-01&to=2026-10-28'
```

Controller cho phép hotelId vắng mặt nhưng đường gọi summary → occupancy vẫn gọi `hotelGrpcClient.getHotelById(hotelId)` tại `RevenueReportService.java:154`; client gọi builder.setHotelId với giá trị null. Runtime trả NullPointerException/HTTP 500. Cùng endpoint với hotelId hợp lệ pass. Đây là lỗi ứng dụng đã tái hiện, không phải lỗi kết nối Docker; chưa sửa theo phạm vi chờ review nghiệp vụ.

### Frontend

Lần build gần nhất đã compile production code nhưng dừng ở TypeScript, sau khi sửa khai báo User cho NextAuth, tại `app/host/hotels/[id]/rooms/[roomId]/page.tsx:419:72` (boolean | undefined truyền vào boolean). Các lỗi khác đã ghi trong báo cáo trước. Không tắt typecheck, không sửa các màn hình nghiệp vụ, không rebuild lại frontend trong đợt test database này. Frontend chưa có container chạy thành công.

### Dependencies bên ngoài

- Supabase Storage: GET metadata của public-storage bucket và private-document bucket cấu hình đều PASS bằng credentials trong `.env`. Chưa kiểm tra upload, download, signed URL thật hoặc thay đổi object/quyền bucket. Không chuyển 5 database local lên Supabase.
- SMTP thật trên port 587: hai lần probe bị SMTPServerDisconnected; lần chẩn đoán theo từng bước đã qua connect/EHLO/STARTTLS và dừng ở AUTH. Không đủ bằng chứng để kết luận sai password hay nguyên nhân phía mạng/provider. Chưa xác nhận SMTP thật hoạt động, không gửi email ra ngoài. SMTP nội bộ Mailpit và notification pipeline đã pass độc lập.
- VNPay: chưa tạo giao dịch sandbox/thật; không coi payment flow đã được nghiệm thu.
- Google OAuth: credentials vẫn trống, chưa test; không cần cho backend smoke này.
- `.env` gốc còn trống NEXTAUTH_SECRET; test-only NEXTAUTH_SECRET riêng đã được sinh trong `.env.test` để dùng về sau. Lỗi build frontend vẫn cần xử lý trước.
- Chưa chạy toàn bộ unit/business/integration suite hoặc kiểm thử concurrency/transaction/booking correctness; 16 containers healthy không chứng minh các nghiệp vụ đó đúng.

## File mới/chỉnh trong đợt này

- `docker-compose.test.yml`: overlay explicit cho database copies, credentials test, Mailpit và SMTP flags phù hợp sink.
- `clone-test-databases.py`: clone live DB, đối chiếu metadata/row counts, từ chối overwrite.
- `integration-smoke.py`: HTTP/RPC/DB connection/consumer/email checks trên project test.
- `stack.ps1`, `smoke.ps1`: hỗ trợ `-Test`; chỉnh worker/Eureka/TCP checks theo kiến trúc thật.
- `docker-compose.yml`, `rabbitmq/start.sh`, `rabbitmq/definitions.json`: sửa bootstrap user, vhost, topology và health.
- `notification-health.sh`: health phù hợp worker không có HTTP.
- `.gitattributes`, `.gitignore` trong docker: giữ shell scripts LF trên Windows, bỏ Python cache/dump khỏi Git.
- README và validation reports: cập nhật quy trình, kết quả thực tế và giới hạn.

Không sửa source Java/business logic, schema.sql, migrations hoặc kế hoạch chuẩn hóa. Không thay framework/dependency versions.

## Cách tiếp tục dùng môi trường test

Tại `checkinn/docker` (5 database copies đã tồn tại, không chạy lại clone):

```powershell
./stack.ps1 -Test -Action Up -Scope App
./stack.ps1 -Test -Action Up -Scope Config
./smoke.ps1 -Test -Scope App
python ./integration-smoke.py  # hiện exit 1 cho lỗi revenue đã ghi phía trên
./stack.ps1 -Test -Action Status -Scope App
./stack.ps1 -Test -Action Logs -Scope App
./stack.ps1 -Test -Action Down  # giữ các volumes/bản sao
```

Hiện để 16 test containers chạy cho review. Stack `checkinn-local` trước đó đã được stop để giải phóng cổng/RAM; containers và volumes của nó còn nguyên. Gateway 8080, Eureka 8761, Admin 9095, Config 8888, Rabbit management 15672 (hoặc giá trị `.env`), Mailpit 8025 đều bind loopback. Luôn dùng `-Test` cho bản sao; lệnh Up App không có `-Test` có thể kết nối database nguồn trong `.env`.
