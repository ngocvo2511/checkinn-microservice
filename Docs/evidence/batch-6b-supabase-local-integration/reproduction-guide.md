# Reproduction — Batch6B local development

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.

Runtime hiện tại dùng Docker; xem [lệnh chạy và kết quả Docker](../../BATCH6B_DOCKER_RUNTIME_2026-11-16.md).
Từ `checkinn`: `.\docker\stack.ps1 -SupabaseDev -Action Up` hoặc `Status` / `Down`.
Các bước host JVM bên dưới là bằng chứng giai đoạn tích hợp trước đó; không chạy
đồng thời với Docker và không chạy lại migration/acceptance đã hoàn tất.
Tất cả lệnh backend chạy từ `C:/Users/ACER/IdeaProjects/DA1/checkinn`, frontend từ sibling checkinn-frontend. Java21/Maven/Python/PostgreSQL binaries cho local guard tests. Không đưa passwords/API keys vào chat, command-line arguments hoặc reports.

## 1. Local checks (đã thực hiện)
```powershell
mvn -o -B -ntp -pl user-service,hotel-service,booking-service,revenue-service,regulations-service '-Dtest=SupabaseDevEnvironmentGuardTest' test
python tools/supabase-runner/test-local.py
mvn -B -ntp -f tools/supabase-runner/pom.xml package dependency:copy-dependencies '-DincludeScope=runtime'
# Frontend:
node node_modules/typescript/bin/tsc --noEmit --incremental false
npm run test:regression
```
`test-local.py` chỉ tạo cluster mới trong .local/batch6b-guard-<random>, loopback random port, SCRAM, synthetic roles/table; dừng trong finally, giữ data/logs, không xóa. PG_BIN override chọn binary directory, không data directory hoặc target URL. Không chạy harness5B vào hosted. Offline package lần đầu thiếu dependency-plugin transitives đã được resolve online; build cuối PASS. Follow-up15/11: standalone runner1.1.0/Flyway10.22.0 và PostgreSQL17.11 local13 tests PASS; xem [report mới](../../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md).

## 2. Cấu hình riêng tư trên máy (chưa thực hiện)
Copy `config/supabase-dev/.env.example` và `targets.properties.example` vào ignored `.local/batch6b/`; nhập trực tiếp từ từng Dashboard → Connect. Không đọc/reuse .env cũ. Runtime và migrator URL/username/password tách biệt, mỗi service ref/role riêng. Database postgres, public, direct5432 hoặc session5432 sau review; không transaction6543. URL template:
```text
jdbc:postgresql://<CONNECT_HOST>:5432/postgres?sslmode=verify-full&sslrootcert=<URL_ENCODED_LOCAL_CA_PATH>&currentSchema=public&connectTimeout=10
```
Không gửi URI có password. CA path phải URL encoded, `caSha256` đúng file CA. Java properties dùng forward-slash path hoặc double backslashes. Runtime env owner slots: DB_PROJECT_REF, DB_RUNTIME_ROLE, DB_URL, DB_USERNAME, DB_PASSWORD, DB_POOL_MAX, DB_CONNECTION_BUDGET. Pool max không nhập cho đến khi project budget xác minh; budget>=max+2+3 cho một instance local. Tham khảo cả Supavisor limits.

**IntelliJ IDEA:** từng service Run/Debug Configuration → Active profiles `supabase-dev`; Environment variables nhập owner contract, SECRET_KEY và dependency configuration hiện có từ secret source trực tiếp trên máy. Không share run config chứa secrets. Spring Boot không tự đọc .env; cấu hình env của process hoặc dùng UI hỗ trợ env file đã xác nhận. Không giữ SPRING_DATASOURCE_*, SPRING_JPA_*, SPRING_FLYWAY_* overrides cũ. Guard sẽ từ chối effective mismatch. Không inject migrator password hoặc Management token vào runtime process. Profile local kế thừa defaults cũ, không chạy canonical V1 vào demo; dùng canonical profile cũ chỉ với fresh local canonical DB đã được kiểm riêng.

## 3. Hosted read-only preflight (chưa chạy)
Trong terminal riêng cho runner, cấu hình env riêng tư: BATCH6B_BACKEND_ROOT, BATCH6B_TARGET_MANIFEST, BATCH6B_MANAGEMENT_TOKEN (read project access), OWNER_MIGRATION_DB_URL/USERNAME/PASSWORD. Manifest phải đủ5 project refs/login độc lập, org identity và exact host/sqlUser/major/V1/CA. Dedicated migrator-as-owner, runtime role riêng. Management API token không phải service-role API key. Runner fail closed nếu response khác expected identity; không tự đoán/ref fallback.
```powershell
java -cp 'tools/supabase-runner/target/supabase-runner-1.3.0.jar;tools/supabase-runner/target/dependency-10.22.0/*' com.checkinn.db.HostedRunner preflight user
# Lặp lại hotel, booking, revenue, regulations với env owner tương ứng.
```
Preflight không DDL/DML; read-only transaction/rollback. SQL_PASS chưa là security/full hosted PASS. Review catalogs qua read-only SQL6A/Dashboard trong private storage; publish alias/hash/results, không account metadata/secrets. History/unexpected objects/event triggers => STOP và review; không baseline/repair/clean/delete. PostgreSQL14..17 gate (runner1.1.0/Flyway10.22.0); actual version khác cần targeted dependency upgrade/acceptance. Không giả giới hạn đã PASS trên PG18 local là vendor support hosted.

## 4. Approval gate (chưa có phê duyệt)
Chỉ sau mapping, database fresh, TLS/permissions/default ACL/Data API security đã được xác minh và supported runner PASS: ghi inventory SHA vào exact manifest, lấy manifest SHA (`Get-FileHash`), trình user review project↔service, V1/hash và thao tác DDL. Không tự bật approval fields. User phải phê duyệt cụ thể từng target. Sau đó operator mới tạo private approval file từ example, approvedBy/expiry UTC ISO8601<=24h, exact hashes và các gates. File không phải cơ chế chữ ký hay thay bằng chứng approval trong chat; không tự tạo giả approval. Security/dashboard changes cần scope riêng, template runner không làm thay.

Sau phê duyệt, env BATCH6B_APPROVAL_FILE trỏ đúng file per-owner:
```powershell
# CHƯA CHẠY — chỉ chạy khi gates và exact user approval đã hoàn tất.
java -cp 'tools/supabase-runner/target/supabase-runner-1.3.0.jar;tools/supabase-runner/target/dependency-10.22.0/*' com.checkinn.db.HostedRunner migrate user
```
Lặp serialized User→Hotel→Booking→Revenue→Regulations, dừng lỗi đầu. Runner chỉ kiểm/apply fresh V1; validates exact history và immediate rerun0. Subsequent migrate gặp history sẽ dừng; không dùng như repair/retry tool. Active event triggers/non-extension managed objects cần review và nâng guard có allowlist chính xác trước khi dùng. Không cấp role/DB quyền rộng để vượt lỗi.

## 5. Sau5 migrations + catalog/security/runtime grants PASS (chưa chạy)
Xác nhận grants đúng owner/immutable tables, deny history/DDL/API; chạy runtime SQL/API negatives và Hibernate validate. Chỉ dùng synthetic data trên target dev được duyệt; giữ audit/test data, không cleanup destructive. Kiểm broker/cache fresh rồi mới bật SUPABASE_DEV_DATABASES_READY=true/SUPABASE_DEV_INFRA_ISOLATED=true.

Infra local có compose riêng. Chọn SUPABASE_DEV_SESSION unique chưa tồn tại (không replay retained volumes), dedicated non5672/non6379 ports, vhost khác `/`, local credentials/Redis database rõ. Không set isolation flag nếu chưa kiểm empty queues/cache.
```powershell
# Chỉ validate syntax; không in resolved config có password.
docker compose -f docker/docker-compose.supabase-dev-infra.yml config --quiet
# CHƯA CHẠY — sau approved readiness, không dùng compose gốc cho5 hosted DB owners:
docker compose -f docker/docker-compose.supabase-dev-infra.yml up -d
# Native local Maven/IDE service process:
mvn -pl regulations-service spring-boot:run '-Dspring-boot.run.profiles=supabase-dev'
```
Compose này chỉ RabbitMQ/Redis local; JVMs chạy host localhost. Không cloud hosting/backend/frontend deployment. Eureka→Regulations→User→Hotel→Booking→Revenue→Gateway→frontend theo actual health/dependency. Cần JWT/internal HTTP/gRPC, SMTP test, Media và VNPay Sandbox riêng; tuyệt đối không giao dịch/refund thật. Run từng workflow trong business-workflow-results.md, latency/pool/recovery checks bounded. Không tự động Batch7.


## User event-trigger review follow-up15/11

Runner hiện1.2.0, dependency folder10.22.0 không đổi. User có private descriptor/body pin đã review; actual connections vẫn phải so khớp. Không tự allowlist cho4 project khác; Data API User đang expose public/graphql_public và security/roles/identity/approval vẫn chưa PASS. [Review/15 local tests](../../BATCH6B_USER_EVENT_TRIGGER_REVIEW_2026-11-15.md).


## Actual User read-only preflight —16/11/2026

Runner1.3.0 verifies actual client SSLSocket separately from pooler backend TLS. User Management API identity, both JDBC roles, schema/default ACL and reviewed function metadata PASS; backend TLS=false still blocks migration.18 local tests PASS. [Latest hosted scope/results](../../BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md). Private BATCH_NEXT_STEPS.md consolidates the one User SSL setting and4 remaining combined snapshots. No settings/DDL/startup performed.

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).

## Sử dụng stack hiện tại — không migrate lại

Frontend: http://localhost:3000 ; Gateway: http://localhost:8080 ; Eureka: http://localhost:8761 ; Mailpit: http://localhost:56825 . Có thể đăng ký account development và xem OTP ở Mailpit; không email ngoài. Không dùng payment redirect để thử provider thật: merchant/HMAC key hiện chỉ fake cho callback local. Storage upload chưa cấu hình/fail closed.

Từ backend root PowerShell:

```powershell
python tools/supabase-runner/run-local-stack.py check
# Dừng đúng JVM/Next processes của session, giữ dữ liệu:
python tools/supabase-runner/run-local-stack.py stop
python tools/supabase-runner/run-local-stack.py stop-deps
# Bật lại đúng container có ownership label, giữ vhost/volumes:
python tools/supabase-runner/run-local-stack.py start-deps
# Chờ Rabbit/Redis healthy; launcher kiểm health/label trước start:
python tools/supabase-runner/run-local-stack.py start eureka notification media auth regulations user hotel booking revenue gateway frontend
python tools/supabase-runner/run-local-stack.py check
```

Không xóa private runtime.private.json/.env/targets/review/CA để reset một session đang chạy. Prepare chỉ chứng nhận fresh namespace lần đầu; không tái gắn nhãn queues/cache populated. Reuse đúng session sau restart không phải replay broker cũ sang DB khác. Không chạy legacy docker stack/SQL migrations để chữa Supabase. IntelliJ nạp owner-only runtime vars và profile supabase-dev; không dùng migrator datasource cho ứng dụng.

Acceptance đã seal. Hosted acceptance scripts từ chối write trên session hoàn tất; cần một lượt development mới được phê duyệt, fixture identifiers riêng trước khi lặp lại. Không reset loyalty balances của account đã tương tác hoặc xóa test rows. Sources ở tools/supabase-runner, helper Java ở acceptance/java; compile JDK21 với standalone runner jar/dependency10.22 và runtime library jars của service. Không đưa secrets vào java arguments. OTP/JWT/fixture secrets chỉ private, không paste raw logs.

Backend regression cuối: từng owner chạy `mvn -o -B -ntp -f <owner>-service/pom.xml test '-Dtest=!*ApplicationTests'`. Không dùng default context với database demo. Opt-in local canonical tests giữ guard loopback/name/data-dir, không chuyển test tạo/migrate local DB sang hosted. Frontend: `npm run test:regression` và `npx tsc --noEmit --incremental false`.

## Search dev-mode fix16/11

Launcher frontend now explicitly uses --webpack after reproducing the local Turbopack /search compilation stall. Actual home form to search results PASS. Manual start: npm run dev -- --webpack. [Evidence](search-navigation-fix-2026-11-16.json).

## Daily commands sau consolidation — trạng thái hiện hành

Chạy từ backend root; các lệnh native/private-state ở phần trước là lịch sử staged integration. Không migrate lại hoặc replay fixtures.

```powershell
.\docker\stack.ps1 -Environment Supabase -Action Validate
.\docker\stack.ps1 -Environment Supabase -Action Up
.\docker\stack.ps1 -Environment Supabase -Action Status
.\docker\stack.ps1 -Environment Supabase -Action Down
# Môi trường PostgreSQL local test riêng:
.\docker\stack.ps1 -Environment Test -Action Validate
```

Supabase đọc private `docker/.env`; Test đọc private `docker/.env.test`. Không chạy hai application modes đồng thời. Down giữ volumes. Validate không chứng nhận provider hoặc test stack startup: consolidation evidence ghi testStackStarted=false. Xem [runbook](../../../docker/README.md).
