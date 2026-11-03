# Docker local development

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
