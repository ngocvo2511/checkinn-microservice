# Docker local development

Latest independent regulation checkpoint, migration and commands:
[regulations-service/README.md](../regulations-service/README.md). Apply its
REGULATIONS-only additive outbox script before deploying that writer. This
checkpoint does not implicitly merge the separate booking/revenue review branch.

## Standardization regressions (29/10/2026)

After rebuilding the changed booking/hotel images and starting **only the test
overlay**, run from the backend repository root:

```powershell
python docker/booking-security-smoke.py
python docker/inventory-smoke.py
python docker/catalog-security-smoke.py
python docker/review-security-smoke.py
python docker/review-eligibility-smoke.py
python docker/loyalty-ledger-smoke.py
python docker/loyalty-ledger-audit.py
python docker/loyalty-preview-smoke.py
python docker/points-reservation-smoke.py
python docker/p0-boundary-smoke.py
python docker/hold-concurrency-smoke.py
python docker/cancellation-workflow-smoke.py
```

The booking check is read-only, uses short-lived test JWTs in memory, and checks
direct REST, gateway REST and unauthenticated/forged-header gRPC access. It prints
only case names and status codes. `--baseline` records pre-fix statuses and does
not enforce regression success; do not use that flag for acceptance.
The inventory check creates fresh room fixtures **only in the isolated clone**,
checks null/zero/negative/explicit-ten inventory and rejects invalid holds without
counter writes. Its `finally` cleanup removes only the generated fixture UUIDs.
The booking/inventory scripts refuse datasource configuration outside the test PostgreSQL clone.
They never print credentials, booking IDs, response bodies or copied room data.

The catalog check also verifies the running hotel datasource is the isolated
clone. It uses fresh UUIDs confirmed absent from city/hotel/room tables, so its
DELETE/PUT/PATCH requests cannot change existing records. It verifies admin/owner/
anonymous status codes through both direct service and the gateway rewrite.

The review eligibility check requires both hotel and booking clone datasources.
It creates synthetic booking UUIDs, tests guest/hotel/CHECKED_OUT binding through
direct REST and gateway REST, then removes only its synthetic reviews/bookings.
Deploy booking-service with the new CheckReviewEligibility RPC before hotel-service;
an older or unavailable booking server causes review creation to return 503.

Loyalty command validation (02/11/2026): rebuild/start **user-service before
booking-service** on the test overlay. Flyway V3 creates only `loyalty_commands`;
it does not rewrite existing accounts or ledger rows. The new user-service must
be present before the booking quote/receipt client is used. Keep the services
coordinated when publishing: older booking code still has the old rate/cap logic.

`loyalty-ledger-smoke.py` checks same-command retries, concurrent first accounts,
overspending, cancellation races, legacy ambiguity and transaction rollback. It
uses fresh user/booking UUIDs. Its temporary PostgreSQL trigger rejects receipt
insertion **only for one generated synthetic user**, then removes the trigger,
function and fixture rows in `finally`. It never changes Regulations or copied
accounts. The cancellation workflow now also covers quoted points, actual net
earning receipts and reversal through onsite and locally simulated VNPay paths.

Command receipts bind booking/type/user/input and retain the original discount.
Transaction locks serialize booking then account, including a first account.
Direct SQL writers bypass this protocol; ambiguous pre-existing accounts/ledger
return 409 for reconciliation. General inventory/provider/event outbox remains
open; durable loyalty work is described in batch13 below. Frontend quote preview coverage
is described below; pending reservations are covered by batch12 below.

Update 02/11/2026 (batch11): authenticated point preview is available at
`POST /api/loyalty-points/{userId}/quote`, guarded by the existing points read
scope. Body identity is ignored; the verified path user is authoritative. The
original `POST /quote` remains booking-service-only, as do all mutations.
`loyalty-preview-smoke.py` checks self/cross-user/anonymous scope, parity with the
internal quote, mutation denials and no ledger/account changes on synthetic
fixtures. Frontend booking and PointsDiscountSection now use this read-only
preview through a caller-token proxy.

Update 02/11/2026 (batch12): Flyway V4 adds `points_reservations` without
rewriting historical accounts or journals. Deploy the booking state RPC and
user-service reservation API together, then use the new booking creation flow;
avoid mixed old/new points writers. `BOOKING_GRPC_ADDRESS` defaults to
`booking-service:9093` in Compose. A signed user-service/USER_SERVICE credential
can read only the narrow recovery state RPC, not general booking data.
Available points exclude HELD funds. Creation reserves; payment commits its
bound snapshot; cancellation releases/reverses once. UI previews remain read-only.

Recovery checks HELD rows after the inventory deadline. ACTIVE, in-flight creation
and failed/unknown lookups retain funds with backoff; only MISSING/CANCELLED
releases them. A PostgreSQL transaction lock prevents uncommitted creation from
appearing missing. Expiry alone never releases a financial hold. Defaults are
30 seconds initial/scheduling delay, 60 seconds retry, and 20 candidates per pass.
The test overlay accelerates these intervals only for regression.

`points-reservation-smoke.py` covers concurrent reserve/commit/reversal, rollback
of reserved USE on receipt failure and conservative orphan recovery. It briefly
stops/restarts **only checkinn-test booking-service** to test lookup outage; run
it separately from booking workflow tests. Its receipt fault trigger is scoped
to a fresh booking UUID. `cancellation-workflow-smoke.py` also injects a deferred
commit fault scoped to its generated room UUID to test a remote hold surviving
local rollback. Both remove only their triggers/functions and synthetic rows.
These tests do not establish general inventory/event outbox recovery or actual
provider settlement/refunds.

Update 03/11/2026 (batch13, STD-022 completion): booking/payment transactions
persist `loyalty_status=PENDING` and the next attempt timestamp on the booking
row. They do not call USE/EARN/CANCEL before local commit. A worker locks the
committed booking, checks its paid receipt and reservation status, then replays
idempotent user-service commands. CANCELLED source always reverses, including
when an earlier settlement candidate was queued. Completion marker failure
keeps the durable intent pending; a crash/restart resumes it. No callback replay
is required. This is a transactional work queue using the source row, not an
in-memory task or a general RabbitMQ outbox.

Default cadence is 5 seconds (30 seconds initial delay), 30 seconds after a
transient failure, 20 candidates per pass. Two scheduling threads keep this
worker separate from booking expiry tasks. Unknown transport failures retry;
invalid receipts/source state or 400/409 move to `REVIEW_REQUIRED`, without
claiming an award/reversal. Read `loyalty_attempts`/`loyalty_last_error` from the
booking clone for diagnostics; do not rewrite ambiguous legacy journals.
`BookingResponse.loyaltyStatus` exposes PENDING/SETTLED/REVERSED/REVIEW_REQUIRED;
booking details explains pending/review and lets the customer refresh.
Cancellation still records the booking/refund request under the agreed policy;
point reversal may complete asynchronously. Cash/provider refund status is
separate and remains unavailable until provider integration is verified.

Legacy protection (03/11): Flyway V5 adds only two read indexes on the old
journal. New quotes/reservations, unreserved spending and new earnings reject409
when an account participates in duplicate EARN/USE for a booking, including
cross-user duplicates. Original command receipts and already backed reservations
remain replayable; valid reversal is still allowed. This does not normalize old
balances or assume that a missing journal opening entry is corruption. Customer
quote409 explicitly asks for reconciliation and permits booking without points.

`loyalty-ledger-audit.py` checks the running user clone and executes an explicit
read-only RepeatableRead transaction. It prints counts only and writes detailed
identifiers to **ignored** `docker/logs/loyalty-ledger-audit-private.json`. Keep that
report private; do not commit copied financial data. Duplicate accounts, duplicate
EARN/USE, missing receipts and unverified opening differences are review queues,
not automatic correction instructions. Verified opening provenance is required
before any manual backfill; direct SQL/older writers bypass the protocol.

Schema addition: `sql/loyalty-settlement-upgrade.sql` is the equivalent additive
DDL for reviewed deployments. Only the test clone's existing ddl-auto=update
was used here. Historical NULL markers are not automatically backfilled; no
copied account/ledger is normalized. Reservation booking ID plus immutable
rate/actual-points/discount snapshot identifies the accepted quote; later rate
changes cannot reprice its payment or command retries. Browser preview remains
indicative until booking creation returns that authoritative quote.

The workflow smoke now tests EARN failure after committed USE, a booking process
restart with pending work, remote success with failed local completion commit,
local cancellation rollback before remote reversal and reversal completion
commit failure. Triggers are scoped to generated UUIDs and removed in finally.
It briefly restarts test booking-service, so run separately from other booking
runtime suites. VNPay callbacks are still locally simulated, never settlement.

These checks do not verify actual VNPay sandbox settlement/refunds, legacy
inventory provenance or the remaining P0 surfaces. Current results and limits
are recorded in `../../PROJECT_STANDARDIZATION_PLAN.md`.

Update 30/10/2026: `hold-concurrency-smoke.py` now exercises PostgreSQL-backed
concurrent last-room holds, confirm/cancel replay, expiry cleanup and
confirm-versus-release. `cancellation-workflow-smoke.py` creates synthetic room,
booking and payment fixtures, validates price tampering, policy snapshots,
cancellation, refund request/ADMIN approval and revenue event compatibility.
Its VNPay return is **locally simulated with the test signing configuration**;
it never calls the provider or claims actual settlement/refund. When that config
is absent it uses the onsite flow and reports the provider scenario NOT_RUN.
The script requires Mailpit and clone datasources for affected services, and
cleans only generated fixture UUIDs in hotel/booking/revenue databases. Mailpit
may retain synthetic test messages; queues are never purged. Existing inventory
smoke tests mint the dedicated internal service JWT in memory for hold calls.

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

Review authorization (30/10/2026): `review-security-smoke.py` requires healthy
hotel-service/gateway and the isolated PostgreSQL clone. It waits for gateway
discovery before creating fresh synthetic review UUIDs under a cloned hotel;
it tests header/body impersonation, hotel-owner response scope and persisted
feedback identity, then removes only those generated review/feedback/response
rows. It also checks anonymous and forged-metadata gRPC deletion are rejected.
Review gRPC mutations now require a valid Bearer JWT; proto user/owner/admin
fields and x-user-id metadata do not grant authority. The four public review
read RPCs remain anonymous. Other hotel gRPC services are outside this change.

P0 media/loyalty/report boundaries (30/10/2026): rebuild and deploy hotel,
media, booking, user and revenue together. Hotel storage calls use signed
hotel-service/HOTEL_SERVICE Bearer tokens (5 minute expiry); media gRPC rejects
user/admin identities for all storage and private signing RPCs. This uses the
existing shared SECRET_KEY convention; standalone media/revenue now also need
that configured key. Revenue checks JWT expiry/signature and Redis revocation.
Booking loyalty calls use signed booking-service/BOOKING_SERVICE tokens scoped
to that Feign client, not a global interceptor. Loyalty mutations cannot be called
directly by users or ADMIN; personal reads remain available to self and ADMIN.

`p0-boundary-smoke.py` checks clone datasources/readiness, creates synthetic
room/media/points fixtures, tests direct/gateway ownership and internal points
calls, and cleans only generated UUIDs. Storage RPC tests deliberately use
unauthenticated/admin calls that must fail before any Supabase request. Valid
storage upload/delete/signing are covered with mocks, not real bucket writes.
`cancellation-workflow-smoke.py` additionally verifies the booking Feign caller
creates exactly one EARN ledger row after a simulated callback replay and cleans
the generated user's points fixtures in the verified user clone. It still does
not contact VNPay. Wait for Eureka convergence after image recreation before
interpreting gateway transport failures as application authorization failures.


Review consistency (03/11/2026): run `python docker/review-consistency-smoke.py`
after rebuilding hotel-service in `checkinn-test`. The script verifies clone
connections, creates a separate synthetic hotel and CHECKED_OUT bookings, and
cleans only generated fixtures. Its private recovery manifest is in ignored
`docker/logs`; do not publish copied data or credentials.

The existing UNIQUE(hotel_id, guest_id, booking_id) constraint is retained.
Concurrent duplicate creation returns REST400 / gRPC ALREADY_EXISTS without SQL
details. PENDING/REJECTED reviews and their owner responses return public404 /
NOT_FOUND; votes are rejected. Authenticated self booking lookup retains private
reviews and uses the signed actor, including when gRPC supplies a booking ID.
Row locking serializes edits/votes with moderation; the smoke test holds a real
PostgreSQL lock and checks that a waiting vote cannot republish a rejected row.
This covers review status only; hotel active/approval and authorized hotel preview
remain separate work under STD-023. No schema migration or copied-data rewrite is
part of this change.


Hotel REST publication (03/11/2026): public `/hotels`, `/hotels/search`,
`/hotels/city/{cityId}` and `/hotels/{hotelId}` require active + APPROVED.
Detail returns404 for unpublished hotels even with an owner token. Owners/admins
use authenticated `GET /hotels/{hotelId}/preview` (Cache-Control: no-store).
The signed actor must own the hotel or have ADMIN role; identity headers do not
grant access. Own hotel lists retain all approval states. The host editor uses
preview for initial load and refresh and never falls back to public detail.

After rebuilding hotel-service, `python docker/hotel-publication-smoke.py` checks
direct/gateway REST behavior using five fresh synthetic hotels in the clone and
removes those rows. It does not change existing hotel state or call storage.
Hotel gRPC, room/availability parent-publication policy and location consistency
remain separate STD-023 follow-up work; internal raw hotel lookup is unchanged.


The cloned hotel schema originally had an outdated approval CHECK rejecting
DRAFT despite the existing Java create workflow. Reviewed deployment SQL is
`docker/sql/hotel-draft-approval-upgrade.sql`: it expands the named CHECK inside
a transaction, validates existing rows and rewrites no values. Applied only to
the test clone on03/11. Apply this schema upgrade before testing/deploying the
DRAFT workflow where the old constraint remains; `ddl-auto=update` did not
repair that legacy CHECK. Smoke scripts do not execute migrations automatically.


STD-023 completion (03/11/2026, supersedes the earlier follow-up boundaries):
public Hotel gRPC calls now use the same active+APPROVED hotel and active room
policy as REST. Missing/unpublished returns NOT_FOUND; invalid UUID returns
INVALID_ARGUMENT. Public hotel DTOs include only active rooms; owner/admin hotel
preview retains inactive rooms. Room preview endpoints are authenticated
`GET /api/v1/{roomTypeId}/preview` and `/api/v1/hotel/{hotelId}/preview`.
The host room editor uses private preview. Public media and review reads also
check hotel publication; `GET /medias/preview` requires actual target ownership
or ADMIN. Own booking-review lookup remains private and independent of catalog
publication, and CHECKED_OUT review creation still uses booking eligibility.

Public availability and new holds require a published hotel and active room.
Existing accepted hold confirmation, release, expiration and cancellation do not
reapply publication policy. Booking ownership lookup uses signed booking-service
JWT at `GET /internal/hotels/{id}/owner`, returning only ownerId, so unpublishing a
hotel does not remove the host's authority over historical bookings. User/admin
or a different subject with BOOKING_SERVICE role cannot use that endpoint.

Address JSON adds coordinateSource without a database migration. Missing
coordinates stay UNKNOWN; legacy unmarked coordinates are LEGACY_UNVERIFIED on
read and are not returned as an exact hotel pin. CITY_CENTROID is never a hotel
location. OWNER_PROVIDED/GEOCODED retain valid coordinates. No historical address
rows are rewritten. A city change requires a new address and resolves its city
name from the selected city; old street/coordinates cannot silently carry over.
The frontend removes city-coordinate fallback, labels city-area suggestions and
hides hotel travel distances when hotel coordinates are unavailable.

`python docker/publication-completion-smoke.py` verifies REST/gRPC and private
identity scopes on five generated hotels, active/inactive rooms, CHECKED_OUT
bookings and reviews, then cleans all generated records. It also tests legacy
coordinate provenance, city updates, historical booking access and confirm/cancel
after unpublication. The script fingerprints existing hotel data before/after;
all writes remain restricted to fresh UUIDs in checkinn-test clones.


Revenue reports also need unpublished historical catalog context. Their hotel
facade and capacity/info clients now use service-only REST endpoints
`/internal/reports/hotels/{id}` and `/internal/reports/rooms/{id}` with a signed
revenue-service/REVENUE_SERVICE JWT (5 minute expiry). They return only IDs,
name/city/owner and room capacities, without contact/media/documents/coordinates.
The booking-service credential cannot access report context, and revenue cannot
access the booking ownership endpoint. No public gRPC bypass was added. Report
owner/admin authorization remains in revenue-service; dependency failures return
503 and are not cached permanently as missing owner or zero capacity.
Deploy hotel, booking and revenue changes together on the test stack before
running `publication-completion-smoke.py`. Its report cases check all five hotel
states and inactive-room capacity for historical occupancy, with cross-owner
and exact machine-identity denial. Hotel service adds coordinate_source field6 to its public gRPC address. The
older revenue DTO proto remains wire-compatible; report lookup now uses REST
and never consumes hotel coordinates.


### Canonical room offers and search regressions (04/11/2026)

Deploy the new hotel-service before the corresponding frontend. The additive
public `GET /hotels/offers` endpoint (gateway `/api/hotel/hotels/offers`) accepts
`cityId` or `provinceId`, legacy `cityName`, `hotelName`, paired ISO `checkIn` /
`checkOut`, `rooms`, `adults`, `children`, and inclusive `minPrice` / `maxPrice`.
Prices are base prices per room/night, matching booking's existing basePrice
model. Defaults: one room, one adult, zero children, no backend price restriction.
Dates may be omitted together. Invalid criteria return400. Unknown inventory
returns409; dependency failures are not converted to sold-out results.

Each result wraps the existing public `hotel` DTO plus an `offer` binding one
roomTypeId, quantity, unitPrice, totalPerNight and optional date-aware
availableRooms/checkIn/checkOut/checkedAt. Capacity uses requested quantity,
with adult and child capacities checked independently on that same room type.
Undated offers have null availability and checkedAt; totalRooms is not current
availability. One repeatable-read transaction keeps catalog/inventory reads
consistent within the response. Results use hotel UUID order, offer price ties
use room UUID. This search is read-only and does not reserve inventory.

Province expansion uses the city/province relation; known IDs do not fall back
to display names. City/location DTOs now include additive parentId; no database
migration is needed. Legacy name/city hotel list routes keep their response shape.

After rebuilding/starting hotel-service **only in checkinn-test**, run:

```powershell
python docker/search-offers-smoke.py
```

The script verifies the hotel's datasource points to the clone, creates fresh
hotel/room UUIDs, checks capacity/price/per-night supply, public visibility,
HTTP validation, gateway routing and unknown inventory, then deletes only those
fixtures in finally. It does not contact payment, media or email providers.
No previous cloned hotel/room/inventory rows are modified.


### Accommodation classification — FEAT-ACCOMMODATION-TYPE (04/11/2026)

Hotel-service has **no Flyway dependency/history** in the inspected hotel clone.
The SQL files in `src/main/resources/db/migration` are not a migration runner.
Apply `sql/hotel-accommodation-type-20261104.sql` explicitly with PostgreSQL
`psql -X -v ON_ERROR_STOP=1 -f ...` against the reviewed target **before** starting
the new hotel image. This is a transactional, advisory-locked, receipted migration
(`checkinn_feature_migrations`), not a guessed Flyway V6. It adds nullable
`hotels.accommodation_type` without a default/backfill, a numeric hotel revision,
validated code CHECKs and a review table with one PENDING row per hotel enforced
by a partial unique index. The application refuses an incomplete expansion if
the deployment receipt/CHECK/index is missing. Keep the migration immutable once
applied; corrections require a new migration. Global clean-schema provisioning
is still separate work (STD-021).

For the **existing verified test clone only**, from the backend repository:

```powershell
python docker/accommodation-migration-test.py
# Rebuild/up hotel-service with -p checkinn-test and the .env.test/compose.test overlay.
python docker/accommodation-type-smoke.py
python docker/search-offers-smoke.py
python docker/hotel-publication-smoke.py
```

The migration harness verifies the resolved clone datasource, Compose project,
actual database/user, two applications of the migration, legacy fingerprint,
nullability, no default, validated constraint and absence of backfill. It refuses
non-clone execution. The workflow harness waits for gateway discovery, writes only
fresh synthetic hotel/room/request UUIDs, tests real PostgreSQL lock contention,
and checks that existing hotel values are identical after cleanup. It never calls
payment, storage or SMTP. Do not run it concurrently with other fixture writers.

The four codes/labels are HOTEL/Hotel, VILLA/Villa, APARTMENT/Apartment and
GUEST_HOUSE/Guest House. New create requests must include `accommodationType`,
even for DRAFT; old records may remain NULL. Omitted/null update keeps the current
classification. Invalid values and malformed query codes return400. Public
`GET /hotels/accommodation-types` is the catalog; repeated `accommodationTypes`
on `/hotels/offers` uses OR within types and AND with existing offer criteria.
NULL hotels remain eligible without this filter and do not match a type filter.

Owner `GET/POST /hotels/{id}/accommodation-type-changes` returns private history
or proposes a change. For APPROVED hotels, the published type and active state
remain unchanged. ADMIN `GET /hotels/accommodation-type-changes/pending` and
`POST /hotels/accommodation-type-changes/{requestId}/approve|reject` use a
specific immutable request ID; reject body is `{ "reason": "..." }`. Locks are
always hotel then request; duplicate submissions share the pending request,
replacement supersedes it, exact completed reviewer retries do not replay writes,
and stale/superseded/opposing decisions return409. History/queue responses are
no-store. Public DTOs contain only the published code/label, no review metadata.

Initial hotel approval now requires `PUT /hotels/{id}/approve?revision=N` using
`revision` from the pending-detail response. Editing a PENDING registration changes
that revision; an older decision returns409 and the admin must reload/review.
Generic hotel approval never approves a separate classification proposal.

Coordinate backend/frontend release: old read clients can ignore additive fields,
but an old create client without a selected type intentionally gets400. Do not
re-enable that client after enforcement. Roll back the search surface/disable the
new filter while retaining compatible create/approval forms and the enforcing
backend. Retain the expanded schema and all review history; never drop columns,
backfill HOTEL or promote a pending request as a rollback. A binary rollback to
pre-enforcement code is **not** a supported rollback target. No production rollout,
source database write or historical classification has been performed here.


### Booking create commands / verified hold gate (05/11/2026)

Deploy the additive SQL in `sql/booking-idempotency-20261105.sql` to the booking
DB and `sql/hold-idempotency-20261105.sql` to the hotel DB before the new binaries.
Both migrations are transactional/replayable, add nullable fields and unique
indexes, and never backfill historical rows. The hotel service must accept the
new inventory command before releasing the booking service and frontend.
`python docker/booking-idempotency-schema-test.py` applies/replays only against
verified running clones and checks historical row fingerprints and constraints.
No production migration was performed. Retain the schema on rollback; disable
new booking writes before reverting a writer that ignores these command fields.

REST create and booking gRPC accept additive UUID `idempotencyKey`. It is scoped
to the authenticated customer; body userId never changes the signed actor.
Concurrent same-key/same-input requests return the same current booking,
including after publication/price changes; a changed business input returns409.
Keys identify an attempt, not a guarantee that two intentionally different keys
represent the same stay. Legacy requests without a key retain their response
shape and cannot obtain command retry guarantees. New frontend forms always
send a key, retain it after an ambiguous response, and rotate it when input
changes. Reloading/remounting a form starts a new attempt: retain the caller's
key outside the form if implementing durable/reload retries in another client.

PostgreSQL transaction locks serialize initial creation across processes. The
key yields a stable booking ID and inventory command; hotel inventory serializes
and binds that command to room/dates/quantity. Replays return the original hold
state/deadline without adding stock or extending TTL. If local commit fails
after a remote hold succeeded, retry recovers the same hold. A compensated,
released or expired hold cannot silently become a new reservation; request409
requires reviewing the attempt. Unknown hold outcomes remain unavailable503.
Historical unkeyed orphans are not automatically linked or rewritten.

Payment eligibility checks never publish/cancel and then throw from a rolled
back transaction. Known missing/terminal/expired holds block a new payment409;
transport/discovery/malformed responses return503 and preserve booking/payment
state. The existing explicit cancellation and scheduled expiry workflows still
own cancellation. New bookings require an actual inventory expiry even without
points. `BookingResponse.holdExpiresAt` is now an absolute UTC ISO timestamp;
legacy local timestamp storage is unchanged and assumes the same deployment
zone used by its writer. Changing a historical database's writer timezone still
requires provenance review (STD-021), not a guessed backfill.

Payment UI never derives TTL from creation time or a local fifteen-minute clock;
unknown/expired deadlines disable payment and offer reload. Initial auth loading
and stale booking reads are handled. The browser suites use intercepted responses,
not a real provider. Native `python docker/booking-hold-verification-smoke.py`
uses API-created synthetic bookings on verified clones/Mailpit, temporarily
stops/restarts only test hotel-service and injects a UUID-scoped deferred booking
commit fault. Run separately from other runtime suites. It cleans generated
booking/room/hold/report records and its own trigger, without purging queues.
These changes do not implement provider query/refund/settlement, distributed
payment confirmation/cancellation recovery, or a general event outbox.


### Public hotel facts (05/11/2026)

Public hotel/offer DTOs expose only active amenity categories/items. Private
management previews keep inactive data. Search filters use the complete label
set, exact hotel-level names with AND, without inferring aliases, room scope or
shared semantic IDs. No catalog mapping/backfill was performed. Missing facts
never synthesize amenities, review scores, crossed-out prices, loyalty awards,
location, policies or FAQ. Detail labels totalRooms as configured total, not
availability for the selected dates. Unknown review data blocks an active rating
filter with retry; it is not a successful empty result. Autocomplete hides legacy
seed hotelCount; that legacy API field is not certified as a live aggregate.
`search-offers-smoke.py` now also verifies inactive item/category exclusion with
fresh fixtures and removes only those fixtures. Frontend regression uses the
actual production artifact with intercepted synthetic API responses.


### Room description round-trip (05/11/2026)

Apply `sql/room-description-20261105.sql` to the hotel database before deploying
the new hotel writer. It adds nullable TEXT with no default/backfill. Existing
clones can verify apply/replay and unchanged legacy fingerprints with:

```powershell
python -X utf8 docker/room-description-smoke.py --migrate-only
# Rebuild/up hotel-service only in checkinn-test.
python -X utf8 docker/room-description-smoke.py
```

REST create/update/response now round-trip plain room `description`. Omitted/null
partial update preserves existing text; an empty string clears it. New/legacy
create may omit it. No synthetic description is assigned to old rooms. Explicit
MapStruct expressions prevent generic JSON conversion of this String; frontend
room adapters do not JSON-decode user-entered quotes or JSON-looking text.
RoomTypeDto gRPC adds optional description field9, which older readers ignore.
The native harness verifies clone identity, uses only fresh UUID fixtures and
checks real PostgreSQL/API persistence plus owner authorization; its finally
block discovers lost-response fixtures under the generated hotel before cleanup.
Retain the additive column if reverting the binary. No production migration or
historical string normalization was performed.


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


## Request validation (05/11/2026 rollout notes)

Hotel/room create and partial update validate supplied fields at both MVC and
service boundaries, before persistence/geocoding. Nested address/capacity are
validated. New room capacity requires adults >= 1 and children >= 0; room price
and room count must be positive. Optional size/breakfast counts accept unknown
NULL and zero, reject negatives. Star rating, when supplied, follows the
existing 1..5 form contract. Existing hotel legal/contact constraints are now
active. No new optional update field becomes mandatory.

Booking validates contact, UUID identifiers, guest/item counts and nested items
before catalog/hold/points effects, including direct RPC/service entry. Existing
stay-date, same-item-date, quantity-overflow and rate-plan rules remain in force.
JSON fractional integers are rejected rather than truncated. Validation HTTP400
includes a fieldErrors map and never echoes rejected contact or raw JSON values.
Malformed JSON uses fieldErrors.request; nested bean paths retain their field
path. Authentication/authorization remains required on the original routes.

A legacy room with NULL/nonpositive total_rooms may still receive unrelated
partial edits. Attempting to assign a new room count returns409 for inventory
review; it does not infer a previous capacity or silently reset booked supply.
No schema/data migration is part of this change.

Run `python -X utf8 docker/input-validation-smoke.py` on checkinn-test only, after
building/updating hotel-service and booking-service. It verifies actual clone
datasources, uses fresh synthetic hotel/room UUIDs, checks direct and gateway
HTTP400/fieldErrors, source fingerprints, zero invalid booking/hold writes and
legacy inventory409, then removes only its generated fixtures. Run separately
from inventory/onsite recovery harnesses.


## Internal authentication/session contract (05/11/2026)

The frontend uses usernameOrEmail/password for both custom and NextAuth
credentials. The canonical backend response is token, UUID-string userId and
persisted uppercase CUSTOMER/OWNER/ADMIN role. External Google access tokens are
never accepted as internal bearer credentials; Google UI/provider is disabled
until a verified backend exchange is implemented. Existing OAuth cookies and
malformed storage are treated as an unauthenticated client state.

`python -X utf8 docker/auth-session-smoke.py` checks real registration/login,
UUID/JWT principal and roles, wrong-password/unverified-email denials, owner/
customer cross-user denials, logout and cross-service401 after token revocation.
It verifies user/booking clone datasources and the running Mailpit configuration.
It uses random-password synthetic accounts, sets verified email/ADMIN role only
as explicit fixture preparation, and removes its generated accounts/OTP rows
and token-hash revocation keys. No copied account is modified and no shared queue
or Redis database is purged. Native acceptance does not imply actual OTP/OAuth
or external identity-provider integration has been tested.


## Verified-payment inventory recovery V1 (05–06/11/2026)

New successful verified VNPay callbacks commit their immutable financial receipt,
paidAmount and a receipt-bound paid_reservation_status=V1_PENDING intent together.
They do not call inventory before that transaction commits. A bounded two-thread
wake-up pool and durable due-row scheduler confirm the original hold afterward;
CONFIRMED is written only after inventory acknowledges it. Unknown transport
retries; missing/expired/ambiguous source requires V1_REVIEW_REQUIRED, with money
retained and no replacement hold or second payment. Local completion commit
failure replays the same hold after restart without another provider callback.
Loyalty settlement queues only after room confirmation and actual receipt.

New V1 paid cancellations commit CANCELLED/refund-request/points-reversal intent
before remote inventory release. Source cancellation wins against a queued
confirmation. Inventory acknowledgement updates V1_RELEASED; it does not claim
provider refund execution. Release completion does not emit the already-published
CANCELLED/NO_SHOW booking transition again. Monetary refunds still require the
existing separate approved execution contract; no fake provider success is added.

Apply BOOKING SQL in order before the new binary:
1. docker/sql/paid-reservation-v1-20261105.sql (five nullable queue/bound-receipt fields + due index).
2. docker/sql/inventory-review-audit-20261106.sql (three nullable reviewer fields).
Neither defaults/backfills old rows. NULL identifies legacy work provenance and
is not automatically promoted. Deploy compatible truthful UI before the writer;
retain expanded schema and do not run an older writer over new V1 intents.

If a cancelled V1 onsite/paid booking needs inventory review, reconcile its
original hold reference through the existing controlled operational process.
An ADMIN may then POST /api/bookings/{id}/cancel-reviewed with a nonblank reason
to retry release against that same reference. Reviewer identity comes from the
signed principal; inventory_reviewed_by/reason/at and attempt history are stored.
Customer cancel replay cannot restart reviewed work. The endpoint does not
substitute a hold, reopen a booking, create another refund request or alter money.

Verified-clone-only commands, run each runtime harness separately:

```powershell
python -X utf8 docker/paid-reservation-smoke.py --migrate-only
python -X utf8 docker/paid-reservation-smoke.py
python -X utf8 docker/cancellation-workflow-smoke.py
python -X utf8 docker/onsite-reservation-smoke.py
python -X utf8 docker/booking-security-smoke.py
```

The paid harness signs SIMULATED local callbacks using test configuration, tests
wrong signatures/amounts, deferred receipt/cancellation/completion commit faults,
actual HTTP timeout, restart, concurrency, terminal inventory review and cleanup.
It never contacts a provider or performs actual payment/refund/QueryDR settlement.
Browser return-page refresh/polling reads booking state, never resubmits the
provider callback. Receipt success alone is not a room-confirmation acknowledgement.
This source-row queue is not a reliable general RabbitMQ outbox; delivery/order
recovery for reporting/notifications remains separately tracked in STD-029.


### Reviewed global amenity catalog (STD-016, 2026-11-06)

Apply `docker/sql/amenity-catalog-v1-20261106.sql` to the **hotel database** before
starting this hotel-service version. The script is transactional, additive and
replayable, with no catalog seeds or automatic label backfill. Startup checks the
required columns. Back up using the established deployment procedure; this is not
a command to modify a production database from the test harness. Rollback the
application only if necessary, leaving the new tables/audit rows intact.

- `GET /amenity-catalog`: active codes and labels, public.
- `GET/POST /admin/amenity-catalog`, `PUT /admin/amenity-catalog/{code}`: ADMIN only;
  code is immutable; label/description/active edits require the current revision.
  Create requires revision 0. Duplicate creates and stale edits return 409.
- `GET /hotels/{hotelId}/amenity-mappings`: owner of that hotel or ADMIN.
- `POST /hotels/{hotelId}/amenity-mappings/{amenityId}/requests`: owned existing
  raw amenity ID, existing active `codes`, `revision` and optional `note`.
  `codes: []` explicitly proposes clearing classification; it does not delete raw data.
- `GET /admin/amenity-mappings?page=0`: ADMIN pending queue, 50 per page.
  `/admin/amenity-mappings/history?page=0` retains past requests, actors and reasons.
- `POST /admin/amenity-mappings/{id}/review`: ADMIN `{approve,revision,reason}`;
  nonblank reason required for both decisions. A stale, superseded or changed-label
  approval is rejected. Deleted raw targets can be rejected, never approved.
- `GET /hotels/offers?...&amenityCodes=CODE_A&amenityCodes=CODE_B`: all codes must
  be effective on that property. Unknown, inactive and empty codes return 400.
  Existing list responses and literal `amenity` frontend URLs remain supported.
  Public amenity items add `verifiedCodes` without changing their original title.

An owner proposes a classification; an ADMIN checks the actual meaning before
approving. There is no automatic Wi-Fi/free/paid equivalence or production seed
list. The host detail Amenities section and `/admin/amenities` provide this workflow.
Mapping state is serialized with legacy raw amenities CRUD under the hotel row
lock. A checked title snapshot, active item/category/catalog and current raw UUID
are required for public effect. Physical replacement keeps audit records but does
not transfer mappings to the replacement UUID. Rejection keeps a previous valid
approval; approval of an empty set clears it. Concurrent stale writers get 409.

This catalog currently classifies **property-level** hotel amenities. Room JSON
labels remain literal and cannot fulfill a property-level code. Operators must
review legacy mappings individually; unclassified labels stay visible and usable
through their existing literal flow. No historical data migration or alias merge
is implied by deploying the schema.

Run `python -X utf8 docker/amenity-catalog-smoke.py` only with the isolated
`checkinn-test` clone stack. It verifies the running datasource, migration replay
and legacy fingerprints, real JWT authorization, revisions/parallel proposals,
review/clear/deactivate behavior, search semantics, legacy replacement and audit
retention, then removes only that run's UUID-scoped fixtures.


### Core HTTP errors and read deadlines (STD-026, 2026-11-06)

Hotel/booking JSON endpoints retain their HTTP statuses and successful DTOs. Errors
now include `status`, `error`, `code`, `message`, `path`, `timestamp`, `requestId`
and optional `fieldErrors`; `X-Request-ID` matches the body, with `Cache-Control:
no-store`. Codes distinguish INVALID_REQUEST/VALIDATION_ERROR, UNAUTHORIZED,
FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, CONFLICT, INVENTORY_NOT_CONFIGURED,
INSUFFICIENT_INVENTORY, DEPENDENCY_UNAVAILABLE/DEPENDENCY_TIMEOUT and INTERNAL_ERROR.
Unexpected 5xx responses never expose exception messages, class names or stack
traces. Legacy String/empty errors are adapted; 204/success bodies remain intact.
Unknown resources are404 and unsupported methods405 (with Allow), rather than500.
Malformed/revoked bearer tokens remain denied401; unavailable revocation storage
in hotel-service is503 and never grants access.

Availability check derives `available` from a single `availableRooms` observation;
its room/hotel/date reads use a repeatable snapshot. It is not a reservation or a
promise that stock will remain unchanged after the response. Hold creation is the
stock authority: insufficient inventory is now409/INSUFFICIENT_INVENTORY, not400.
Consumers must retain legacy error compatibility during rollout, not retry a write
automatically merely because it timed out.

Booking outbound HTTP defaults remain connect3000/read5000ms (`clients.*-timeout-ms`).
Hotel user-profile RPC now uses `clients.user-grpc-deadline-ms` (positive, default5000);
RPC failure is a503 dependency error. This change does not broaden RPC permissions.
The frontend booking/payment/loyalty adapter bounds both headers and body to15000ms,
keeps safe error metadata and never automatically retries mutations. A timeout is
an **unknown outcome**: read the source booking/payment state and reuse an existing
idempotency command where applicable. These are per-call/client deadlines, not a
claim of a distributed end-to-end latency SLA or rollback of an already committed
transaction.

On isolated clones run `python -X utf8 docker/api-error-smoke.py`,
`python -X utf8 docker/hold-concurrency-smoke.py` and existing validation/security/
paid-reservation harnesses. The error harness uses scoped synthetic room/catalog
records, concurrent two-night commits and a deferred trigger restricted to one new
catalog code. Finally removes only that run's trigger/function and fixtures.


### User RPC credential boundary and password reset (STD-020 P0 follow-up, 2026-11-06)

UserService RPCs no longer trust network location or `x-user-id`. Auth-service
attaches a signed, five-minute `auth-service`/`AUTH_SERVICE` identity to register,
login and reset-password calls. Only that exact subject+role may call those RPCs;
end-user ADMIN tokens cannot bypass the HTTP OTP reset flow. Hotel-service attaches
its existing `hotel-service`/`HOTEL_SERVICE` identity for its read-only account
enrichment. GetUserById also allows a verified self or ADMIN user; cross-user reads
and unknown service identities are denied. Revoked/expired/invalid tokens fail closed.
This preserves existing trusted internal consumers; it does not expose service
credentials to the browser or grant a client-supplied role.

Roll out the updated auth/hotel clients **before** enabling the user-service RPC
interceptor. There is no anonymous compatibility fallback. In the local test stack
all three images are updated and health checked before regression tests. Auth/hotel
profile RPC deadlines default to5000ms and must remain positive.

Password reset preserves the existing verify-then-reset UI. The final reset requires
one stored verified, unexpired OTP proof, locks it, consumes it and updates the hash
in one user-database transaction. Commit failure restores proof/password together;
concurrent/reset replay cannot reuse the proof. Missing/expired/unverified/ambiguous
proof is rejected. Email verification updates only its flag so a concurrent request
cannot merge an old detached password over a successful reset. OTP format,10-minute
expiry and5-attempt policy are unchanged; generation uses SecureRandom. There is
no schema migration or historical-user/password modification in this change.

`python -X utf8 docker/user-rpc-security-smoke.py` is restricted to running clones
and Mailpit. It creates random-password synthetic accounts, sends RPC credentials
and payloads through memory/stdin, records status/boolean assertions only, exercises
OTP/replay/concurrency/deferred commit failure and actual client enrichment, then
removes only those accounts/OTPs/profiles, scoped fault objects and its own revoked
credential key. It never resets an existing account, sends real email, logs a
password/token/OTP/hash, or purges a shared database/queue/Redis keyspace.

Full-account field minimization remains separate. Account-wide OTP-reset revocation
is implemented by the approved policy below; individual-token logout still revokes
only the selected token.

### OTP reset: account-wide session revocation

A successful final OTP reset invalidates every prior session for that UUID account.
User/booking/hotel/revenue/regulations check an opaque per-account Redis generation,
including legacy tokens without the additive `sessionGeneration` claim. Accounts
never reset retain their existing valid legacy sessions. Auth signs the generation
captured by user-service while validating credentials under the account row lock;
it must not read a newer generation after validating an old password. Unique JWT
`jti` values preserve separate logout sessions. Machine identities retain their
narrow subject/role checks. HTTP security chains are stateless; a stored HTTP
security context cannot replace a verified bearer. The anonymous debug token
controller has been removed entirely.

The reset transaction locks the account, consumes the verified OTP, writes a new
`auth:session-generation:<UUID>` marker without TTL and waits for local Redis AOF
fsync (`WAITAOF 1 0 1500`) on the same connection before changing the password.
**Redis 7.2+ with AOF enabled and a persistent volume is required** (test stack:
7.4.9). No database schema change is needed. Every UUID-user session fails closed
when the revocation store is unavailable, even with the legacy fail-open option.
Do not expire, evict, flush, or restore old generation markers as ordinary cache:
a marker loss/old backup could make legacy sessions valid again. Coordinate any
Redis disaster recovery with signing-key/session invalidation; AOF confirmation
protects local process restart, not disk loss or stale-backup restore.

Revocation is monotonic: a later DB commit failure rolls back password and OTP,
but never restores the old generation. The old password can then log in again,
issuing a new session. A Redis write/fsync error aborts password reset; a timeout
may already have invalidated prior sessions, which is safe. Do not compensate by
restoring old session generations. This is deliberate fail-closed ordering, not
a claim of a distributed PostgreSQL/Redis transaction.

Deploy compatible token readers in all five services and the auth issuer before
enabling reset in the updated user-service. Drain old replicas before resuming
reset traffic; mixed old readers can accept revoked sessions. Verify AOF/durability
and preserve existing blacklist data. No anonymous fallback is provided.

Verification against isolated clones/Mailpit only:
- `python -X utf8 docker/session-store-integration.py`: opt-in real Redis/AOF and
  PostgreSQL transaction tests, temporary read-only ACL/loopback transport, scoped
  cleanup; no shared keyspace purge.
- `python -X utf8 docker/reset-session-smoke.py`: real login/reset JWTs across all
  five consumers, legacy compatibility, rollback/replay/signature tampering,
  account isolation, individual logout, concurrent login/reset and hard restart
  of the test Redis while retaining its volume. Never run against production.

`session-store-integration.py` supplies clone credentials through child-process
environment only. Integration classes are opt-in with `CHECKINN_SESSION_TEST=true`;
the helper establishes and cleans their isolated transport/fixtures. Existing
application-context tests remain unchanged.


### Public search review summaries (additive)

`GET /api/v1/reviews/summaries?hotelIds=<UUID>&hotelIds=<UUID>` (gateway prefix
`/api/hotel`) accepts 1–100 raw identifiers, deduplicates them, and returns rows in
request order. Only active APPROVED properties appear. Each row contains exactly
`hotelId`, `totalReviews`, `averageRating`; count includes PUBLISHED reviews only,
average is rounded HALF_UP to two decimals. Zero reviews is count0/average null.
Unknown/private properties are omitted, not fabricated as known zero. Bad/missing
IDs or an oversized batch return400. No schema/index migration is required.

One aggregate repository query returns these facts without hydrating review text,
feedback, responses or user profiles. Existing detail statistics/review APIs retain
their contracts. Search uses sequential chunks of at most100 and rejects invalid,
unsolicited or duplicate rows. Failed/missing summaries remain unknown; a selected
guest-rating filter cannot silently turn that failure into empty matches. Browser
credentials are unnecessary for these public aggregates.

`python -X utf8 docker/review-summary-smoke.py` verifies actual clone query/API
behavior, direct+gateway publication/privacy/rounding/input boundaries and legacy
detail compatibility. It reports response-byte and sequential-client timing
observations with data sizes and Compose/curl overhead. This small fixture is not
a throughput benchmark or an SLO. Cleanup targets only the generated property and
review fixtures. Full offer projection, global sorting/pagination and other
inventory/location query baselines remain separate STD-014/025 work.


### Global offer sorting and offset pages

The additive public endpoint is `GET /hotels/offers/page` (gateway prefix
`/api/hotel`). Legacy `/hotels/offers` remains a list with its original shape.
All existing location/name/dates/capacity/price/type/reviewed-code parameters apply.
Additional repeated parameters: `starRatings` (integers1..5), `guestRatings`
(thresholds0..10; OR), `amenities` (exact property-level literal labels; AND).
A comma inside one `amenities` value is preserved, not split into aliases/filters.
No room-only labels are promoted to hotel amenities.

Controls: `page=0` by default (zero-based), `size=20` by default (range1..50),
`sort=DEFAULT|PRICE_ASC|PRICE_DESC|RATING_DESC`. DEFAULT is lexical hotel UUID,
not a popularity recommendation. Other sorts use ascending UUID for ties;
known-unrated hotels sort last by rating and cannot satisfy a guest-score filter.
Invalid controls/filter values return400. Prices come from the same matched room
offer that satisfies occupancy, stock and dates; no client price/inventory guesses.

Response: `content[{hotel,offer,reviews}]`, `page`, `size`, `sort`, `totalElements`,
`totalPages`, `availableAmenities`. Review facts use the published aggregate from
the same read snapshot. All global filters and ordering precede the page boundary;
all totals follow filters. An out-of-range page is empty but keeps the true total.
`availableAmenities` contains observed literal labels from the room-eligible
candidate set before star/score/literal filters and pagination, so moving pages
does not remove filter choices. It contains labels, not per-hotel detail objects.

Each request uses one REPEATABLE_READ database snapshot. Separate offset requests
are not pinned to one historical snapshot: live changes may move rows between
pages. Reload applies current data; the UI resets page on sort/size/filter changes,
keeps URL/back/reload consistent and offers return-to-first on an empty stale page.
Search consumes server pages and review facts together, without browser filtering
or extra per-hotel review/profile calls. API errors remain errors with retry.

This bounds returned hotel content, not the server candidate scan: the current
matching pipeline still maps candidates and reads dated inventory before offset
selection. Broader projection/query/throughput work remains STD-025; no unsupported
latency SLO or distributed snapshot guarantee is claimed.

`python -X utf8 docker/search-pagination-smoke.py` uses exact synthetic clone
hotel/room/review/amenity fixtures to verify direct+gateway global sort/ties/counts,
dated supply/capacity/price consistency, invalid input, literal comma binding,
publication and legacy list compatibility. Reads preserve inventory; cleanup
removes only those fixture UUIDs. Do not run the smoke script against production.


### Canonical text encoding and rolling compatibility

HotelResponse and RoomTypeResponse now include additive `textEncoding: PLAIN_V1`.
Text is ordinary JSON string content, including intentional quotes, Unicode,
multiline text and literal null/undefined strings. Real optional nulls remain null.
Address/capacity/amenities remain object/object/array as before; no schema migration.
MapStruct JSON helpers are qualified so they only serve explicit JSON-column
expressions, not implicit conversion of every scalar String.

Roll out the compatible frontend adapter first, then the PN-tagged backend and
drain old replicas. Frontend supports absent-tag responses from the old generated
mappers: decode exactly one proven encoding layer for hotel scalar/nested city
and room name fields. Manual policy/FAQ/amenity and room-description text is
already plain even in the old contract. Never recursively strip quotes, interpret
plain null-like strings as null, or guess an unrecognized encoding version.
Existing address-object compatibility remains bounded and does not decode its
interior text fields. The compatibility fallback is deliberately retained.

This fixes DTO encoding, not a claim that JSONB+String is inherently invalid.
Aggregate-only clone audit and actual Hibernate API writes prove current JSONB
shapes. Do not auto-strip existing quoted database names: historical encoding
artifacts and intentional punctuation need provenance/manual review. No automatic
historical-data rewrite or migration is included.

`python -X utf8 docker/canonical-text-smoke.py` verifies actual validated owner API
writes, canonical direct/gateway text/shape/version, and real frontend Node
adapters against that verified test gateway's host port. It records assertions,
not data/credentials; exact synthetic cleanup also verifies existing hotel/room
fingerprints remain unchanged. Browser tests separately verify literal card/modal
rendering and description display. Use isolated clones only.

### Room facts and owner mutations

Unknown capacity, configured stock and optional descriptive metadata remain null;
zero children and explicit false breakfast are known values. Dated availableRooms
is never a replacement for configured totalRooms. Public selection requires known
positive price/stock and valid core capacity. Owner edits send only changed fields;
a description-only edit does not rewrite price, capacity or stock. Existing six
capacity fields roundtrip; descriptive metadata never changes booking prices.
Unknown historical stock requires review, rather than assigning an invented count.

Room update/activate/deactivate use the existing pessimistic RoomType row lock in
their transaction, shared with hold creation. This prevents Hibernate full-row
flushes from overwriting a concurrent price/stock change and makes the stock
no-decrease guard evaluate the committed current value. Explicit client updates
still replace the requested field; this is not optimistic versioning for stale UI
payloads or a global distributed transaction guarantee.

On verified checkinn-test clones only, run:

```powershell
python -X utf8 docker/unknown-room-facts-smoke.py
python -X utf8 docker/room-mutation-concurrency-smoke.py
```

The latter uses synthetic fixtures and scoped temporary trigger/advisory barriers
to observe real PostgreSQL blocking between two actual owner API requests. Cleanup
drops only its own objects/rows and checks existing hotel/room fingerprints. It
does not run against production or print credentials.

### Dated inventory read budget (STD-025)

`getAvailableRoomCount` reads configured stock and publication, then one scalar
MAX(held + booked) aggregate over [checkIn, checkOut). Missing dated rows mean no
recorded load; they are not created by a read. It retains the repeatable-read
snapshot and clamps the result to 0..configured stock. Counters widen to long
before addition; held/booked maxima from different nights are never combined.
Hold/confirm/release locks and mutations remain separate and unchanged.

```powershell
python -X utf8 docker/availability-range-integration.py availability-range-integration
```

This opt-in test boots JPA repositories only against a verified checkinn-test clone
through a temporary loopback transport. No application jobs or provider clients
start. Fixtures exist only inside a rolled-back PostgreSQL transaction; schema
creation is disabled. Hibernate Statistics measures prepared statements: 1-night
and 30-night single-room reads both use 3 statements (room, parent, stay aggregate),
versus 32 previously for 30 nights. This is a query-count result, not a latency SLO
or a claim that the complete search candidate pipeline is optimized. Default
unit runs skip the environment-dependent integration test; run it explicitly.


Selective location search now filters with escaped case-insensitive contains in
repository queries and fetches each matched city's province in the same query.
It retains literal Unicode labels, stable parent IDs and province/city grouping;
percent/underscore/backslash in input remain literal. No accent-folding, aliasing,
arbitrary limit, or new index is inferred. Explicit all-locations remains a catalog
API. On the measured selective fixture, loaded entities decrease from214 to4;
this is not a no-table-scan or latency claim.

```powershell
python -X utf8 docker/availability-range-integration.py location-search-integration location
python -X utf8 docker/location-search-smoke.py
```

The former is another opt-in actual JPA test with rollback and two-query/loaded
entity budgets. The latter checks direct/gateway lookup and legacy text-location
hotel offers with scoped fixtures and unchanged existing-record fingerprints.


Paged offers use internal eligibility/filter facts before the global page boundary,
then map the complete public hotel DTO only for selected content. Internal facts
are never emitted as partial public representations. Off-page hotel/room media,
policy, FAQ, city/address mapping is avoided; global inventory/review/filter/facet
semantics and the final response shape remain intact. The legacy list endpoint
continues to use full DTOs. This is not SQL LIMIT/projection across the entire
candidate pipeline: room/facet reads are grouped by published property IDs, dated
availability is grouped by eligible room IDs, and selected amenities can be read again during
full mapping. Invocation-budget unit tests are not reported as measured SQL SLOs.


Paged candidates now batch dated inventory reads: one room query, one parent
publication query, and one grouped peak-load query per at most500 unique eligible
room IDs. This is an internal SQL parameter chunk size, not a result cap. All
requested IDs and parent publication/configured stock are validated; missing rooms
or invalid inventory fail rather than returning partial/guessed counts. Undated
offers remain unverified (availableRooms/checkedAt null). Snapshot matching never
falls back to single-room reads or treats a missing snapshot entry as sold out.
Legacy single-room/list APIs remain compatible. The opt-in range JPA test also
checks3-room outcomes8/3/10 and an actual3-statement bulk budget (previous loop7,
with shared-parent first-level cache reuse). Unit501-room coverage verifies the
chunk boundary does not truncate results; it is not a real501-room load test.


### Persisted counter/hold safety

Malformed stored counters are not repaired automatically. Single and grouped
availability aggregates detect either held<0 or booked<0 even when their sum is
positive; reads fail409 with INVENTORY_INCONSISTENT rather than advertising a
configured count. This validation shares the existing aggregate query/budget.
Positive overcommitted load still reads as zero supply. Holding widens subtraction
before comparing quantity. Confirm refuses already-overcommitted load; positive
release/cancel can reduce load and retain the existing unknown-stock compatibility.
Persisted zero/negative hold quantity is rejected before state/counter mutations.
Release checks the held bound before subtraction, so corrupt negative values
cannot overflow into positive stock. Existing terminal idempotency is preserved.

These safeguards concern malformed persisted data; they do not establish that
healthy valid booking requests produce such values or that production has them.
Keep malformed records for reviewed repair using trusted source data. No automatic
backfill, new fee/payment policy, destructive migration, or production write is
included. Cleanup isolates each expired hold so one bad record does not stop the
remaining holds from being processed.

```powershell
python -X utf8 docker/inventory-counter-safety-smoke.py
python -X utf8 docker/availability-range-integration.py inventory-counter-range-integration
```

The native harness inserts only exact scoped malformed fixtures in verified clones,
checks authorized API conflict/unchanged-state behavior, and removes them while
verifying existing-record fingerprints. It never repairs historical data.


Province/city candidate hotel lookup is batched by stable city IDs with the same
active/APPROVED conditions; old single-city lookup remains compatible. Room and
literal facet facts for published candidates now use two JPA queries per at most500
properties (active rooms plus active category/item scalar projection). There is no
per-property lazy room/category/item read in this phase. Candidate facts never
become partial public DTOs: final selected hotels still use the full mapper. No
reviewed amenity codes are invented by raw-label projection; the existing validated
catalog eligibility and reviewed-code full response remain separate authorities.
Optional code-filter matching still has its previous per-property lookup until
its own measured/regression-tested change. No full-search latency SLO is inferred.

```powershell
python -X utf8 docker/availability-range-integration.py candidate-facts-integration candidates
```

This actual JPA test measures the candidate phase only (two hotels6→2 statements),
excluding initial hotel lookup/final selected detail/JDBC reviewed-code reads.
Fixtures verify inactive exclusions, literal comma/Vietnamese labels and per-hotel
room/capacity isolation. They roll back without starting application jobs.

### Reviewed legacy room stock (07/11/2026)

Apply `sql/room-inventory-review-v1-20261107.sql` to the reviewed HOTEL database
before deploying the updated hotel service. The script creates two additive audit
tables/indexes; it does not seed approvals, backfill counts or change bookings.
Replay was tested twice on the isolated clone with unchanged room fingerprints.
Keep the tables/audit if rolling application code back; do not drop production data.

OWNER proposes a positive room quantity with evidence/source in the room editor.
ADMIN reviews from `/admin/inventory-reviews`, with a mandatory decision reason.
Both operations use the displayed revision. A superseded/stale/replayed request
cannot be approved. Proposal never changes stock. Approval atomically updates only
configured quantity and review metadata, using the same room lock as inventory
holds and room edits. It rejects changed snapshots, negative counters, and counts
below the maximum combined held+booked on any recorded night. This conservative
legacy verification does not introduce date-effective capacity or rewrite history.

Review-managed quantities cannot be changed through the old room update API;
unchanged quantities/other edits and unmanaged legacy behavior remain compatible.
Unknown counts require sourced proposal and approval. Existing values such as10
remain unverified until humans review them; no value is automatically declared
wrong or trusted. Pending new proposals preserve an unchanged previously approved
quantity. An out-of-band quantity change makes the verified flag false.
Actual physical counts and source review remain human work, not test fixtures.

```powershell
python -X utf8 docker/room-inventory-review-smoke.py
```

The harness verifies the running clone before inserting scoped synthetic fixtures,
checks backend permissions, revisions, concurrent administrators, ledger bounds,
deferred-commit rollback, audit and exact cleanup. Never run it against production.

### Auth failure and validation contract (07/11/2026)

Auth logs exclude raw identity/credentials/OTP/JWT/provider payload and throwable
causes. OTP proxy preserves plus aliases, keeps known invalid proof false, and
reports unavailable outcomes503 instead of success or credential errors. Account
RPC infrastructure faults also return UNAVAILABLE; known business rejections keep
their existing contract. Reset commit faults are503/successfalse and leave password
and proof unchanged. ValidateToken RPC now applies the same logout/generation gate
as the resource consumers; Redis failure is UNAVAILABLE without issuing claims.
These changes do not claim User REST/service/notification logging is fully reviewed.

```powershell
python -X utf8 docker/auth-validation-smoke.py
python -X utf8 docker/reset-session-smoke.py
python -X utf8 docker/user-rpc-security-smoke.py
```

Run these sequentially on the test stack with Mailpit. Reset harness deliberately
crashes/restores test Redis and verifies AOF plus all six token-validation surfaces;
it never prints raw application logs or generated private inputs. Latest actual
acceptance:11 validator,117 reset,46 User RPC assertions passed. Auth-session fresh
retry33 passed; first registration failure remains recorded with undetermined cause.

### Historical working-tree checkpoint (07/11/2026, before final review)

Both repositories currently use `fix/user-otp-boundary`; earlier batch branches
carried the same uncommitted changes forward. They are not separate ready-made PRs.
Review the complete working tree, including new untracked source/tests/SQL, before
committing. No staging/commit/push was performed. The two pre-existing `.idea`
changes were preserved and should be reviewed separately from application code.
Local `.env` files remain ignored; runtime logs/build outputs are outside the
pending application changes.

Review in these dependency groups:

1. Auth/User plus session validators in resource services: reset proof, shared
   generation/revocation, internal RPC authorization, removal of debug token issuer,
   safe account/OTP errors and logs; corresponding NextAuth/custom session changes.
2. Booking/payment: durable paid/onsite inventory outcomes, recovery, validation and
   client payment-state interpretation. A paid receipt alone cannot promise a room.
3. Hotel/search: unknown facts, transaction locks/counter checks, global pagination,
   bounded candidate/review reads, stable location/amenity contracts, and reviewed
   OWNER/ADMIN physical inventory workflow; associated frontend forms/filter state.

Additional SQL introduced by these pending changes must be reviewed in its correct
database **before** deploying the new backend:

| Database | Additive scripts under `docker/sql` |
| --- | --- |
| HOTEL | `amenity-catalog-v1-20261106.sql`, `room-inventory-review-v1-20261107.sql` |
| BOOKING | `paid-reservation-v1-20261105.sql`, `inventory-review-audit-20261106.sql` |

Keep prior baseline migrations described above. Do not run HOTEL scripts against
BOOKING or infer rollout from Hibernate updates in the clone. No production schema
or data was changed. Do not discard audit/financial tables to roll application code
back; retain pending work and reconcile its authoritative state.

Remaining findings are explicit follow-up scope, not half-written features in this
checkpoint: real legacy physical counts/source review; provider-specific settlement/
refund integration and full recovery/outbox contracts; optional reviewed-code search
fan-out/full-load SLO; notification SMTP failure propagation and payload-safe retry/
DLQ logging. Notification source was inspected but not modified in this checkpoint.
Auth/User log probes do not certify the notification worker's logs.

Final checkpoint verification: the selected offline six-module package command
passed673 executed tests (User99/Auth47/Hotel274/Booking227/Revenue18/Regulations8),
with five opt-in integrations skipped explicitly. Environment-dependent
ApplicationTests remain unchanged and excluded from this selected command; they
are not counted as passes. Final User OTP runtime14, User RPC46 and extended
reset117 assertions all passed on fresh fixtures after all test services became
healthy. User OTP tests check safe503 through Auth's global HTTP error contract
and explicit503/verifiedfalse at the User DTO boundary, plus rollback and private
account log probes. Account-email marking and intermediate proof verification
retain their existing separate transactions; no combined atomicity is claimed.

```powershell
python -X utf8 docker/user-otp-boundary-smoke.py
```

The workspace-root PROJECT_STANDARDIZATION_PLAN.md is outside both Git repos;
its local update is not automatically pushed with either repository. This README
and the frontend tests README retain the operational checkpoint inside Git.


## Final review and PR preparation (07/11/2026)

See [REVIEW_2026-11-07.md](REVIEW_2026-11-07.md) for the final branch order,
fresh test results, ownership exclusions and current frontend build blocker.
The historical uncommitted checkpoint above is superseded by that review.
