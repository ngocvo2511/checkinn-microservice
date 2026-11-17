# Batch 8 — Hosted E2E acceptance & performance, 17/11/2026

## 1. Executive Summary

**PARTIAL / HOSTED OWNER→ADMIN→CUSTOMER E2E BLOCKED.** Đã chạy thực tế local Gateway→Hotel→Supabase, khởi động Booking/User/Auth, kiểm tra năm hosted database và benchmark đọc. Không đủ điều kiện HOSTED ACCEPTANCE DONE hoặc production-ready.

12 scenario HTTP khác nhau: 11 đạt ở lượt đầu; anonymous booking trả503 khi discovery chưa sẵn sàng, kiểm lại trả401 đúng contract. Giữ nguyên failed attempt, tổng13 lần kiểm tra acceptance. Không dùng các negative404 làm successful detail/availability benchmark. Hosted lifecycle approve/booking thành công **NOT RUN**. Năm native canonical tests chạy lại trên PostgreSQL18 disposable, kết quả bên dưới, không thay hosted acceptance.

## 2. Environment & Target Verification

Backend branch `feat/supabase-dev-runtime`, HEAD `ea46f3cb7168039b0160d05a05822aa906a81a17`. Working tree có sẵn staged/unstaged/untracked; initial state tại [evidence](evidence/batch-8-hosted-e2e-performance/initial-git-status.txt). SHA256 staged diff trước/sau bằng nhau; không stage/commit/push/reset/checkout/restore.

Năm target User/Loyalty, Hotel/Inventory, Booking/Payment, Revenue, Regulations: control-plane identity PASS, JDBC TLS1.3 verify-full PASS, dedicated runtime flags/public-DDL/migrator-membership checks PASS. `postgres/public`, PostgreSQL17.11; columns/constraints/indexes và exact Flyway V1 checksum/history khớp canonical baseline năm project. Inherited database TEMP được giữ như checkpoint6B. Không migrations/DDL hoặc sửa cấu hình hosted.

Session pooler port5432; host region codes lần lượt User `ap-south-1`, Hotel `ap-northeast-1`, Booking `ap-southeast-1`, Revenue `ap-northeast-2`, Regulations `ap-southeast-2`. Đây là region của hostname pooler, không phải bằng chứng độc lập về vị trí backend PostgreSQL. Identity hash và hostname sanitized ở [targets](evidence/batch-8-hosted-e2e-performance/target-identities.json); không lưu passwords/tokens/project refs.

Windows11, Intel Family6 Model154, Temurin Java21.0.3; client timezone Asia/Saigon, chưa geolocate physical IP. Hikari max2/minIdle1, timeout10000ms/service. Dataset hiện hữu: 1hotel, 0 room, 0approval, 0hold/booking; 34province+40city; 2users (CUSTOMER1/OWNER1, ADMIN0). Public catalog/search trả0, location trả74. Dữ liệu hiện hữu không thuộc fixture scope.

## 3. Runtime Architecture

Khởi động Eureka8761, Gateway8080, Hotel8083, User8082, Auth8081, Booking8084 cùng RabbitMQ/Redis/Mailpit có session ownership. Năm JVM đầu healthUP; Booking cũng healthUP sau startup. Hotel/Booking build mới từ working tree, package skipTests được ghi rõ; local native tests chạy riêng sau benchmark.

Profile guarded `supabase-dev`: Hibernate validate, SQL init never, Flyway disabled. Không khởi động Regulations/Revenue vì workflow đọc không cần và Regulations có4 outbox rows hiện hữu. Không khởi động Notification/Media/frontend; SMTP thật/Storage/VNPay provider không được gọi. Booking chỉ dùng simulated provider config trong process, không sửa docker/.env. Service health không chứng minh successful booking chain.

## 4. Functional E2E Results

| Nhóm | Kết quả |
|---|---|
| Search undated/dated, public catalog, locations | 4/4 HTTP200, kết quả0/0/0/74 |
| Anonymous proposal/review/hold, forged header/JWT | 5/5 bị401 |
| Anonymous booking | Lần đầu503 lúc discovery; lần kiểm lại401 |
| Missing hotel/room availability | 2/2 HTTP404; negative cases |
| OWNER create/propose; ADMIN approve/reject/stale; CUSTOMER book | BLOCKED hosted |
| Idempotency, expiry/release/cancel, quantity change/oversell | NOT RUN hosted; native fallback có assertions theo suite hiện hữu |

Chi tiết [functional](evidence/batch-8-hosted-e2e-performance/functional-results.json), [booking retry sau readiness](evidence/batch-8-hosted-e2e-performance/booking-readiness-recheck.json).

## 5. Authorization & Inventory Approval

Existing registration contract chỉ tạo USER/OWNER, không có quy trình bootstrap ADMIN an toàn được cung cấp. Aggregate users xác nhận ADMIN0. Không sửa role/account thật, không ký token ADMIN giả từ shared secret và không replay private identities của6B. Vì vậy OWNER/CUSTOMER authenticated negatives, ownership A/B và successful ADMIN workflow chưa được hosted xác minh. Anonymous/header/JWT negatives không thay thế những checks này.

Không mở hosted fixture writes khi thiếu identity để hoàn tất lifecycle; manifest được tạo trước, exact fixture ID set rỗng. Async/cleanup review: hotel public triggers0; managed event triggers6/project; outbox/ledger các owner được đếm baseline, worker Regulations không start. Không có exact-ID DELETE cần chạy. Không dùng cleanup lịch sử6B để xóa dữ liệu hiện hữu.

## 6. Performance Benchmark

4 read endpoints ×4 concurrency levels ×30 successful samples = **480/480**, 0error/timeout. Warmup5/endpoint; không unlimited load hoặc retry. Python thread pool, timeout15s; percentiles dùng linear interpolation, p99 từ30 samples có độ tin cậy hạn chế. Requests/second là throughput trong cửa sổ đo, không saturation capacity. Probe SQL đọc ngắn được chạy trong phần đầu benchmark, nên số liệu là development baseline, không benchmark độc lập chuẩn production.

| Endpoint | Concurrent | n | p50 ms | p95 ms | p99 ms | Mean ms | Min–max ms | req/s | Error/timeout |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| search_undated | 1 | 30 | 646.4 | 825.3 | 860.7 | 666.9 | 634.7–868.9 | 1.50 | 0% / 0% |
| search_undated | 3 | 30 | 817.5 | 1430.5 | 1470.7 | 957.2 | 636.5–1475.5 | 3.06 | 0% / 0% |
| search_undated | 5 | 30 | 1785.4 | 2476.2 | 2581.6 | 1688.7 | 758.6–2613.2 | 2.82 | 0% / 0% |
| search_undated | 10 | 30 | 3126.7 | 3544.2 | 3545.2 | 2835.8 | 760.5–3545.3 | 3.02 | 0% / 0% |
| search_dated | 1 | 30 | 652.6 | 786.2 | 879.6 | 684.1 | 629.8–915.7 | 1.46 | 0% / 0% |
| search_dated | 3 | 30 | 757.4 | 1265.5 | 1381.8 | 934.4 | 632.3–1421.2 | 3.13 | 0% / 0% |
| search_dated | 5 | 30 | 1560.0 | 2108.7 | 2210.7 | 1573.4 | 1005.4–2251.9 | 3.00 | 0% / 0% |
| search_dated | 10 | 30 | 3115.6 | 3289.3 | 3391.3 | 2763.7 | 757.4–3422.8 | 3.06 | 0% / 0% |
| public_catalog | 1 | 30 | 154.9 | 271.2 | 281.2 | 164.8 | 132.3–282.2 | 6.07 | 0% / 0% |
| public_catalog | 3 | 30 | 215.8 | 264.6 | 352.9 | 199.9 | 137.0–388.5 | 14.58 | 0% / 0% |
| public_catalog | 5 | 30 | 316.0 | 385.0 | 472.6 | 318.2 | 247.8–508.3 | 14.89 | 0% / 0% |
| public_catalog | 10 | 30 | 623.5 | 696.8 | 751.9 | 585.6 | 260.8–752.4 | 14.90 | 0% / 0% |
| location_catalog | 1 | 30 | 531.3 | 651.0 | 813.0 | 570.2 | 511.8–878.6 | 1.75 | 0% / 0% |
| location_catalog | 3 | 30 | 721.2 | 1142.8 | 1173.4 | 786.3 | 521.0–1178.5 | 3.73 | 0% / 0% |
| location_catalog | 5 | 30 | 1157.2 | 1513.9 | 1632.3 | 1229.0 | 636.2–1673.4 | 3.83 | 0% / 0% |
| location_catalog | 10 | 30 | 2532.9 | 2728.6 | 2794.0 | 2277.6 | 639.0–2820.6 | 3.75 | 0% / 0% |

Direct Hotel service đo riêng30 samples/endpoint sau workload Gateway (120samples, 0error). Không coi hiệu số hai lượt không đồng thời là pure Gateway overhead:

| Endpoint | Direct successful n | Direct mean ms |
|---|---:|---:|
| search_undated | 30 | 678.6 |
| search_dated | 30 | 661.2 |
| public_catalog | 30 | 154.7 |
| location_catalog | 30 | 540.5 |

Hotel detail/room availability thành công, proposal/approval/create booking/idempotency: **N/A**, không có fixture/identity hợp lệ. Không điền percentiles giả. [Raw480](evidence/batch-8-hosted-e2e-performance/benchmark-raw.json), [summary16](evidence/batch-8-hosted-e2e-performance/benchmark-summary.json), [direct](evidence/batch-8-hosted-e2e-performance/direct-service-raw.json).

## 7. SQL/N+1 Analysis

Lượt instrumentation riêng, serialized3 samples/endpoint sau khi restart Hotel, không gộp vào480 samples. Lượt đầu logger class qua env không xuất session metrics; lưu attempt `query-counts-unavailable-attempt.json`, sau đó cấu hình exact class logger bằng process-local JSON và chạy lại:

| Endpoint | JDBC statements mỗi request (3lần) | Mean JDBC execute ms, gồm network |
|---|---|---:|
| search_undated | 1,1,1 | 121.9 |
| search_dated | 1,1,1 | 122.0 |
| public_catalog | 1,1,1 | 121.4 |
| location_catalog | 2,2,2 | 247.0 |

Đây là Hibernate JDBC statement count, không bao gồm transaction-control round trips, Redis, HTTP hoặc scheduler sessions khác. JDBC execution ~120ms/statement trong khi EXPLAIN server execution nhỏ trên empty/missing-ID data; không đồng nhất JDBC time với PostgreSQL CPU. Không đo riêng pure network RTT/pure application CPU; phần còn lại E2E gồm transaction/session round trips, pool wait, Gateway, serialization và network. Không quy toàn latency cho PostgreSQL.

Hikari đo tổng632acquisitions, tổng wait310.036s trong cửa sổ benchmark+direct+functional/health, không riêng480requests; timeout delta0. MAX quan sát khoảng2.604s; pending cuối0. Tăng concurrency làm xếp hàng với pool2; không tăng pool vượt approved connection budget.

EXPLAIN ANALYZE **SELECT-only** cho predicate `hasEffectiveInventoryApproval` lấy từ source, search/catalog/lowest-price candidate/availability. Server execution lần lượt0.068/0.031/0.041/0.763/0.732ms trong lượt ghi nhận; chỉ một plan/sample, dữ liệu cực nhỏ. Hold lock dùng EXPLAIN không ANALYZE, không tạo/confirm hold. Truy vấn lowest-price là representative aggregate, không phải exact full mapper path. [Plans](evidence/batch-8-hosted-e2e-performance/sql-plans.json).

**Runtime N+1 theo room chưa xác minh vì0 room.** Source có per-room `hasEffectiveInventoryApproval` trong `HotelMapper.toPublishedRoomTypeResponses` và `calculateApprovedLowestPrice`; `HotelOfferSearchService` kiểm thêm theo eligible room/availability. Đây là nguy cơ N+1/duplicate lookups có căn cứ source, không được kết luận resolved từ1query trên empty catalog. Cần dataset approved nhiều hotel/room để đo slope query-count. Seq scans trên bảng nhỏ chưa chứng minh thiếu index; **chưa có evidence bắt buộc thêm index**, không apply/propose index suy đoán.

## 8. Optimizations

Không sửa business Java/JPA/frontend, không cache approval hoặc thay consistency. Chỉ bổ sung tooling/evidence Batch8 và process-local instrumentation. Đề xuất sau khi có representative dataset: batch approval predicate cho presentation/read path, giữ authoritative check sau room lock khi hold; đo query budget trước khi triển khai. Đây là đề xuất, chưa implementation.

## 9. Before/After Comparison

Không có source optimization nên không có before/after improvement claim. Gateway-vs-direct và instrumentation pass là những phép đo riêng, không phải before/after. Không thay canonical V1 hoặc migration history.

## 10. Supabase Cleanup Verification

Hosted fixtures tạo **0**, xóa **0**, exact IDs còn lại **0**. Cleanup N/A, không khẳng định đã tạo/xóa fixture acceptance. Sau khi dừng toàn bộ JVM của phiên: row counts và schema/history fingerprints năm projects UNCHANGED. Existing outbox/audit/ledger nằm trong baseline comparison.

Không lấy row-content fingerprints trước runtime; equality về counts không tự chứng minh nội dung mọi row bất biến. Không phát sinh business writes từ test requests được gửi; không có fixture còn sót. Native local cluster dừng finally, synthetic data local giữ trong ignored run-owned directory để review. [Verification](evidence/batch-8-hosted-e2e-performance/cleanup-verification.json).

## 11. Test Coverage & Limitations

Local disposable PostgreSQL18: 5 canonical native tests, 0failure/error, năm owner catalogs so baseline; details [native results](evidence/batch-8-hosted-e2e-performance/local-disposable/schema-verification.json). Suite Hotel gồm mandatory approval/provenance/concurrency/lifecycle assertions hiện hữu; test count5 không phải5HTTP hosted scenarios. Native writes không được gọi là hosted PASS hoặc write performance benchmark. Không đo local write latency/throughput.

Không full frontend regression, không successful authenticated OWNER/ADMIN/CUSTOMER E2E, không real booking/payment, không production backup/load readiness. Empty search data không đại diện quy mô thật; thiếu query slope theo số room. Read-only core catalog comparison không phải exhaustive managed-schema/extension/permission audit. Không dùng test skips trong package làm PASS.

## 12. Remaining Risks

Thiếu ADMIN synthetic identity được provision theo security contract; dữ liệu approved synthetic để hoàn tất A–E và benchmark writes. Startup/discovery có transient503; runner cần chờ từng target route trước functional tests. Region/network/transaction round trips và pool queue làm latency cao dù server SELECT nhỏ, cần đo network instrumentation riêng. N+1 read path khi córoom vẫn mở; chưa có distributed trace, CPU profiling hoặc representative dataset.

## 13. Final Status

**PARTIAL — HOSTED READ BENCHMARK DONE / HOSTED APPROVAL & BOOKING ACCEPTANCE BLOCKED.** Không HOSTED ACCEPTANCE DONE, không production-ready. No hosted fixture residue; source/index/HEAD preserved. Người dùng cần quyết định/cung cấp cơ chế provision ADMIN test hợp lệ cho lần tiếp theo; không cần nới quyền/reset schema.

## 14. Recommended Next Workstream

Provision riêng synthetic ADMIN qua quy trình được duyệt, không sửa account thật; giữ quyền fixture writes+exact-ID cleanup. Chạy OWNER→ADMIN→CUSTOMER A–E qua Gateway trên2–5synthetic hotels, xác minh quiescence/outbox/ledger/cleanup, rồi benchmark populated reads và safe writes. Nếu N+1 đo được, triển khai batched read predicate và so before/after cùng dataset; approval gate sau lock giữ nguyên. Runtime và local cluster đã dừng trước shutdown theo yêu cầu người dùng.
