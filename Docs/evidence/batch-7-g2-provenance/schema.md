# STD-021 — canonical schema và migration provenance, 16/11/2026

## Phạm vi và đối chiếu

Batch3 kết luận thiếu fresh baseline là checkpoint lịch sử: Batch5B đã giải quyết bằng năm V1 độc lập; Batch6B đã apply/validate/no-op và kiểm Hibernate trên năm Supabase projects. Không mở lại các defect legacy thành lỗi của canonical V1. Evidence hosted được tham chiếu từ `../batch-6b-supabase-local-integration/five-project-migration-acceptance-2026-11-16.json`; Batch7 không kết nối hosted để kiểm lại hoặc ghi thêm dữ liệu.

| Acceptance criterion | Phân loại | Evidence / giới hạn |
|---|---|---|
| Năm fresh schema reproducible, 46 bảng / 458 cột | DONE trước Batch7; tái kiểm local Batch7 | V1 hashes đối chiếu Batch5B; five canonical PostgreSQL/JPA tests và catalog diff trong `schema-verification.json` |
| Applied canonical history | DONE theo evidence Batch6B | Mỗi project một SQL V1 success, validate và no-op; không baseline/repair/clean |
| Service-specific source/history manifest | PARTIAL trước, bổ sung Batch7 | `migration-manifests.json`: canonical/legacy/security, SHA256, owner, prerequisites/risk từ Batch3, scope applied evidence |
| Runtime hosted không tự DDL/migrate/init | DONE | Cả năm `application-supabase-dev.yml` validate / SQL never / Flyway false; environment guards reject unsafe overrides trước context; external runner riêng |
| Runtime canonical local | DONE trong contract profile | Cả năm `application-canonical.yml` validate / SQL never / Flyway false; migration runner explicit trước startup |
| Legacy representative release upgrade | NOT VERIFIED | Không representative historical corpus/release baseline; synthetic local fixtures không chứng nhận lịch sử thực tế |
| Legacy cutover/import Supabase | DEFERRED | Batch5A U03 chọn fresh databases không import demo; Batch7 không tự mở cutover |
| Production recovery/restore | DEFERRED | Không phải database integration hoặc historical compatibility implementation scope hiện tại |

## Runtime/config review

`docker/stack.ps1 -Environment Supabase` chọn guarded `run-docker-stack.py`, tạo env `supabase-dev`, tắt Config Server; không dùng base legacy `ddl-auto=update`. Guards kiểm resolved validate/SQL never/Flyway disabled, JDBC owner endpoint/TLS/schema/runtime role, chặn alternative Hibernate/JPA schema actions và Hikari datasource overrides. Không sửa cấu hình đã được Batch6B kiểm chứng.

`-Environment Test` chỉ nhận chính xác URL `postgres-test:5432/checkinn_<owner>` và user `checkinn_test`; private `.env.test` tách khỏi `.env`. Test overlay là disposable/test-clone đường cũ, giữ legacy update; không chứng nhận dùng overlay này với canonical hoặc hosted. Bốn base application.yml update và User legacy Flyway baseline là đường lịch sử, không phải entrypoint Supabase. Không khẳng định canonical YAML chống mọi explicit operator override; hosted path có guard fail-closed.

Source giữ owner datasource riêng; canonical physical FKs trong một database/public, không FDW/dblink hay cross-database FK. Existing real canonical tests xác nhận runtime denied DDL/history và cross-owner CONNECT. Runtime roles trong harness nhằm kiểm contract canonical5B; không suy local role grants giống mọi managed Supabase permission.

## Manifest và quy tắc sử dụng

`migration-manifests.json` được sinh bởi `python tools/batch7-schema-verify.py`, có năm records theo owner. Canonical location duy nhất `classpath:db/canonical`; không đưa `db/migration`, root SQL hoặc `docker/sql` vào scan. Security grant scripts là provisioning ngoài Flyway, không coi chúng là legacy DDL hoặc version history. Hosted scoped runtime grants có evidence6B; không tự chạy scripts này trong Batch7.

- Fresh canonical: migrate V1 đúng owner bằng dedicated runner, validate, no-op; runtime validate.
- Legacy PostgreSQL: áp dụng theo đúng release schema/type/history đã chứng minh; không chạy toàn bộ SQL theo tên. User chain thiếu core V1; Hotel V2 unsupported ADD CONSTRAINT IF NOT EXISTS và V5 duplicate expiry đã được Batch3 tái hiện. Booking schema.sql chứa local users FK không hợp canonical ownership. Root NULL→10 script không được chạy. Existing bytes giữ nguyên.
- Disposable test: chỉ tài nguyên mới do run sở hữu; assertions/fake fixtures không là actual legacy approval/history.
- Hosted Supabase: V1 đã applied; không fresh-run, không historical replay, không sửa history/checksum; thay đổi schema tương lai cần additive V2 proposal và approval riêng.

SQL report `docker/batch7-inventory-provenance.sql` là read-only classification report; không phải migration, không thuộc Flyway manifest. Historical root/manual SQL dùng ownership inventory Batch3; file mới/unknown ngoài inventory cần review trước execution, không mặc nhiên được cấp quyền chạy vì thiếu trong manifest.

## Reproduction và verification

Chạy `python tools/batch7-schema-verify.py` tại repository root. Yêu cầu PostgreSQL18 binaries tại `C:/Program Files/PostgreSQL/18/bin`, Maven offline dependencies sẵn, loopback55486 trống. Script không nhận URL/credentials từ người dùng. Tạo UUID run directory mới dưới ignored `.local`, cluster mới, passwords ngẫu nhiên/private, năm owner role pairs và năm fresh test databases. Existing canonical test guards xác nhận database name/loopback/data_directory chính xác trước migrations. Maven chạy tuần tự để tránh shared module target compile race.

Catalog compare exact columns (type/nullability/default/identity), constraint expressions/validated flags, indexes/predicates, sequences (không so current value vì tests có fixtures), history V1/checksum so PostgreSQL18 Batch5B catalogs. Không chỉ so số bảng; không tự sửa drift. New catalog JSON không chứa business rows. Manifest V1 byte hashes phải match trước start.

`schema-verification.json` là kết quả authoritative của run Batch7, gồm testRunId, identities database local, exact Maven counts, catalog differences và cleanup. PostgreSQL stop trong finally; data directory mới giữ lại để review, không drop/reset development DB. Không hosted fixtures nên hosted cleanup không áp dụng. Không dùng scan PID rộng để dừng unrelated PostgreSQL processes.

Giữ failed attempt `schema-verification-76292d75545040178160426939ddbdbd.json`: User1 PASS/catalog match, Hotel1 ERROR do synthetic standalone MapStruct fixture thiếu MediaAssetMapper dependency; helper đã inject mapper thật trước fresh rerun. Lỗi đọc Maven log UTF8 trên Windows che row Hotel trong summary đầu cũng đã sửa bằng decode giữ ASCII summary. Attempt đầu stopped=true; không đổi thành PASS hoặc xóa bằng chứng. Script mới lưu summary theo từng testRunId bên cạnh current summary và trả failure nếu bất kỳ canonical test/catalog fail.

Python runtime config regressions: `python tools/supabase-runner/test_runtime_config.py` — **2 PASS**, zero database/Docker process side effects (mocks). Không thay config source.

Standalone migration runner regressions: `mvn -o -B -ntp -f tools/supabase-runner/pom.xml -Dtest=HostedRunnerTest test` — **11 PASS / 0 failures / 0 errors / 0 skipped**. Không kết nối hosted.

Giới hạn phiên bản giữ nguyên Batch5B: service test Flyway10.10 cảnh báo PostgreSQL18 mới hơn phiên bản được chứng nhận; local assertions PASS không phải vendor certification. Standalone hosted runner dùng Flyway10.22 và PostgreSQL17 acceptance đã được Batch6B kiểm riêng; Batch7 không thay dependency để làm im cảnh báo.

## Kết quả final run

`testRunId=09203d90ba4d425d8457ea940adf0444`, hoàn tất 16/11/2026 19:34:04 Asia/Saigon. `python tools/batch7-schema-verify.py` exit0: **5 canonical tests PASS / 0 failure / 0 error / 0 skip**, năm service Flyway migrate/validate/no-op, Hibernate mappings/repositories và runtime negative privilege probes. Hotel test bao gồm các assertions mới của inventory/concurrency/provenance/encoding. **5 catalog diffs rỗng**, toàn bộ 458 business columns cùng constraints/validated state/expressions/indexes/predicates/sequences/history match evidence PostgreSQL18 Batch5B. Năm V1 SHA256 không đổi. Manifest hiện có **68 SQL sources** (bao gồm legacy archived copies được Batch3 inventory, không coi bản sao là applied receipt).

Cleanup local PASS: PostgreSQL do run mới tạo đã stop trong finally, postmaster.pid không còn; data giữ lại trong ignored `.local/batch7-schema-09203d90ba4d425d8457ea940adf0444` để review. Không reset development DB, không hosted fixture/schema/history changes. Helper inventory latest `RoomInventoryReviewService.java` timestamp19:31:35+07 trước successful Hotel compilation bắt đầu sau User test19:31:51+07.

## Migration proposal

STD-021 không cần V2 để giải quyết gaps đã xác định trong phạm vi Batch7. Không sửa bất kỳ V1/historical SQL/grants nào. Nếu owner chọn import legacy sau này: sanitize representative baseline + approved mapping UUID/encoding/financial source receipts; rehearsal trên new disposable database; compare clean/upgrade; snapshot immutable histories; additive migration proposal theo owner. Không dùng synthetic passing tests thay bằng chứng dữ liệu thật.
