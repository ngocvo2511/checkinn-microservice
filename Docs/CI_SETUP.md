# GitHub Actions CI

Repository đã có thư mục `Docs/`; trên Windows đường dẫn `docs/CI_SETUP.md`
trỏ tới cùng thư mục. File dùng casing `Docs/CI_SETUP.md` theo repository để tránh
tạo hai cây tài liệu chỉ khác chữ hoa/thường.

## Phạm vi và triggers

Workflow [ci.yml](../.github/workflows/ci.yml) chạy khi `push` hoặc
`pull_request` vào `master`. Nhánh này được xác nhận bằng
`git ls-remote --symref origin HEAD` ngày 22/11/2026. Không lọc đường dẫn,
không triển khai CD, publish package hay push Docker image.

PR từ fork dùng `pull_request`, không dùng `pull_request_target`, không nhận
repository secrets, chỉ có `contents: read`; checkout không lưu credentials.
Dùng GitHub-hosted Ubuntu 24.04, timeout 30 phút/job. Run cũ cùng PR/nhánh
được hủy khi có run mới. GitHub có thể yêu cầu maintainer duyệt run từ fork.

## Audit và jobs

Backend là Java 21, Maven reactor 13 module, Spring Boot/Cloud, gRPC/Protobuf,
JPA/PostgreSQL, Redis và RabbitMQ. Boot chủ yếu 3.3.4, Eureka 3.3.0,
Config Server 4.0.0/Cloud 2025.1.0-RC1; giữ nguyên các phiên bản khác nhau.
Không có Node manifest/lockfile hoặc script lint/format/typecheck trong repo
này; frontend thuộc repository riêng, không nằm trong CI này. Chưa có workflow
trước thay đổi này. Không tìm thấy cấu hình Checkstyle/Spotless/PMD/JaCoCo
hay Failsafe trong các POM đã kiểm tra.

| Required status check | Mục đích |
| --- | --- |
| `Maven verify (backend)` | `clean verify` reactor trừ Config/Eureka Server: generate Protobuf, compile main/test, chạy Surefire tests và package các service |
| `Maven verify (eureka-server)` | `clean verify` Eureka Server, giữ client bean nội bộ cần cho server; cấu hình source không đăng ký/fetch registry ngoài |
| `Maven verify (config-server)` | `clean verify` Config Server với native backend, không clone Git config ngoài |
| `Maven verify (supabase-runner)` | `clean verify` tool có POM độc lập, không nằm trong reactor |

Matrix dùng `fail-fast: false`; Maven dùng `--fail-at-end` để thu thập lỗi ở
các module độc lập. Không dùng cờ skip, danh sách loại test, hay
`continue-on-error`. Các test opt-in vẫn tuân theo annotation hiện có.

Cả bốn job dùng Temurin 21 và `auth-service/mvnw` (Maven 3.9.11 đã pin trong
wrapper properties), không dùng Maven trôi nổi của runner. Wrapper các module
khác đang có cả 3.9.12/3.9.15; không sửa chúng. `setup-java` cache Maven theo
`**/pom.xml` và wrapper properties. Actions được pin SHA. Maven không có lockfile
tương đương npm; wrapper pin công cụ, POM/BOM quản lý dependency, không bảo đảm
build bit-for-bit hoặc khóa mọi artifact transitive/SNAPSHOT.

## Chạy tương đương ở local

Từ root `checkinn`, cần JDK 21 và mạng tới Maven Central, nguồn phân phối JDK
và GitHub. Không nạp `.env`, không bật profile canonical/supabase-dev.

```powershell
$env:TZ = 'UTC'
$env:SPRING_CLOUD_CONFIG_ENABLED = 'false'
$env:EUREKA_CLIENT_ENABLED = 'false'
$env:SPRING_BOOT_ADMIN_CLIENT_ENABLED = 'false'
$env:SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP = 'false'
$env:SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP = 'false'
.\auth-service\mvnw.cmd -version
.\auth-service\mvnw.cmd -B -ntp -f pom.xml -pl '!config-server,!eureka-server' --fail-at-end clean verify
.\auth-service\mvnw.cmd -B -ntp -f tools/supabase-runner/pom.xml --fail-at-end clean verify
Remove-Item Env:EUREKA_CLIENT_ENABLED
.\auth-service\mvnw.cmd -B -ntp -f eureka-server/pom.xml --fail-at-end clean verify
$env:SPRING_PROFILES_ACTIVE = 'native'
.\auth-service\mvnw.cmd -B -ntp -f config-server/pom.xml --fail-at-end clean verify
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

Linux/macOS: export các biến trên, thay `.\auth-service\mvnw.cmd` bằng
`bash auth-service/mvnw`. `clean` xóa build output trong `target`, không đụng DB.
Dùng shell mới sau khi chạy để không mang cấu hình test sang runtime ứng dụng.

## Env và secrets

Không cần cấu hình repository secret/variable cho pipeline hiện tại. Các biến
được workflow thiết lập:

- `TZ`
- `SPRING_PROFILES_ACTIVE`
- `SPRING_CLOUD_CONFIG_ENABLED`
- `EUREKA_CLIENT_ENABLED`
- `SPRING_BOOT_ADMIN_CLIENT_ENABLED`
- `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP`
- `SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP`

Chỉ job Config Server đặt `SPRING_PROFILES_ACTIVE=native`, tránh clone config Git
bên ngoài. Không đặt profile này cho reactor: nó ghi đè default properties trong
bootstrap tests của Supabase guard và gây failure do cấu hình CI. Job Eureka
bỏ `EUREKA_CLIENT_ENABLED`: server cần client bean nội bộ; source đã cấu hình
không đăng ký/fetch registry ngoài. Tắt đăng ký
Eureka/Admin và tự khởi động Rabbit listeners để context test không kết nối hạ
tầng dùng chung. Không tắt JWT, authorization hoặc kiểm tra session. Auth/Media
context tests đã có fixture tổng hợp trong source; không truyền khóa runtime.

## Hạn chế và integration tests

Integration DB/Redis/provider **NOT CONFIGURED** trong workflow này. Các cờ
opt-in hiện có không được bật:

- `CHECKINN_CONTEXT_TEST`
- `CHECKINN_RANGE_TEST`
- `CHECKINN_UUID_TEST`
- `CHECKINN_SESSION_TEST`
- `CHECKINN_REFUND_TEST`
- `BATCH5B_ACCEPTANCE`
- `BATCH6B_LOCAL_CANONICAL_TEST`
- `BATCH6B_LOCAL_GUARD_TEST`

Surefire báo skipped cho các test này; không được coi là full-stack acceptance.
Không thêm PostgreSQL/Redis service đơn thuần: DB suites yêu cầu schema, role,
port/URL và guard riêng; canonical acceptance còn xác nhận data directory local.
Không chạy startup Hibernate update, migration lịch sử hay đọc hosted credentials
để làm CI xanh. Rabbit/SMTP delivery và VNPay/Storage thực cũng chưa được kiểm
chứng bởi context/loopback/mock tests.

Hướng cải thiện: thiết kế job integration riêng trên DB/Redis disposable, bootstrap
đúng canonical chain và guarded harness đã được review; xem
[Docker runbook](../docker/README.md) và
[migration plan](../Docs/database-design/MIGRATION_IMPLEMENTATION_PLAN.md).
Không dùng volume test cũ hoặc tài khoản hosted/business. Lint/format riêng
**NOT CONFIGURED** vì chưa có công cụ; Java compile nằm trong `verify`.

## Kết quả local ngày 22/11/2026

Windows, Temurin 21.0.3, Maven Wrapper 3.9.11:

| Hạng mục | Kết quả |
| --- | --- |
| YAML parser, actionlint 1.7.7, liên kết tương đối tài liệu | PASS |
| Compile/test/package 11 module thuộc job backend cuối cùng | PASS, theo từng module trong reactor đã chạy |
| Config Server, Eureka Server và Supabase runner chạy riêng | PASS |
| Báo cáo Surefire mới nhất của 13 module + tool | PASS: 1.056 test đạt, 54 skipped, không failure/error |
| Lint/format riêng; DB/Redis/provider integration opt-in | NOT CONFIGURED |
| GitHub Actions run | Chưa xác minh ở thời điểm kiểm tra local; xem tab Checks của PR để biết kết quả thực tế |

Lệnh reactor `-pl '!config-server' --fail-at-end clean verify` đã thực sự chạy
và kết thúc **FAIL** do tắt Eureka client bean; 11 module backend khác đều
SUCCESS. Đã sửa workflow để tách Eureka, rồi chạy riêng `clean verify` Eureka
thành công. Không chạy lại toàn reactor sau thay đổi cách nhóm job; không coi
log reactor cũ là BUILD SUCCESS. Lần thử trước dùng native profile toàn reactor
cũng FAIL do ghi đè profile trong guard test; sau bỏ profile đó, User/Hotel/
Booking/Revenue/Regulations guard tests đều đạt. Lần gọi Maven đầu tiên bị
PowerShell tách tham số `-D`; các lệnh xác minh sau dùng biến môi trường.
Không phát hiện failure source còn tồn tại trong các kiểm tra đã chạy.

## Branch Protection

Sau khi commit/push và có run thực tế, vào GitHub Settings → Rules → Rulesets
(hoặc Branches → Branch protection), chọn `master`, yêu cầu PR và status checks:

- `Maven verify (backend)`
- `Maven verify (eureka-server)`
- `Maven verify (config-server)`
- `Maven verify (supabase-runner)`

Chọn check từ GitHub Actions sau khi chúng xuất hiện; có thể bật yêu cầu nhánh
up-to-date và review theo chính sách nhóm. Không khai báo required check không
tồn tại. Nếu bật merge queue, cần bổ sung trigger `merge_group` trước khi dùng.
Quyền Actions/fork approval và branch rules cần maintainer cấu hình trên GitHub.
Các kết quả local phía trên được ghi trước khi commit/push. Kết quả GitHub
Actions cần kiểm tra riêng trong tab Checks của PR hoặc tab Actions.

Tham khảo chính thức: [Maven CI](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven),
[setup-java cache](https://github.com/actions/setup-java),
[cache access restrictions](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).
