# Batch 8D — Loại bỏ read amplification & tối ưu hotel search, 17/11/2026

## 1. Executive Summary

**DONE trong phạm vi Batch 8D (scoped hosted development acceptance). Không phải production-ready.**

- Root cause đã được quy về call site bằng SQL fingerprint theo đúng W3C trace ID: lazy media theo từng room/hotel, 6 truy vấn metadata theo từng hotel (rooms, policies, FAQ, amenity categories, amenity items, effective amenity codes), approval đọc lặp (mapper + offer matching) và availability snapshot theo từng hotel.
- Sửa source nhỏ, cục bộ trong `hotel-service` (mapper/repository/read service/controller), không đổi schema/Flyway/API/authorization/business rule.
- Controlled BEFORE/AFTER trên **cùng hosted fixture IDs** ở cả 3 dataset (AFTER đo 5×10 rồi thu về đúng tập ID của 3×5 và 2×2 bằng exact-ID deletion):

| Endpoint (5×10) | SQL BEFORE → AFTER | Gateway C1 p50 | Direct C1 p50 |
|---|---|---|---|
| Search undated | 96 → 12 (−87,5%) | 12022 → 1919 ms (−84,0%) | 12296 → 2026 ms (−83,5%) |
| Search dated | 111 → 15 (−86,5%) | 13291 → 4701 ms (−64,6%)*; lặp lại 2327 ms | 14336 → 2369 ms (−83,5%) |
| Public catalog | 91 → 11 (−87,9%) | 11535 → 1944 ms (−83,1%) | 12379 → 1478 ms (−88,1%) |
| Hotel detail | 19 → 11 (−42,1%) | 2360 → 1821 ms (−22,8%) | 2542 → 1476 ms (−41,9%) |
| Room availability (đối chứng, code không đổi) | 4 → 4 | 983 → 962 ms (+2,1%) | 1020 → 984 ms |

\* Cell dated C1 gốc có cửa sổ chậm tạm thời (3,4–8,4 s, trong khi trace và direct cùng artifact ~2,4 s); đo lặp riêng (`after-larger-dated-repeat`) cho p50 2327 ms / p95 2604 ms. Cả hai cell đều được giữ và công bố.

- Query-count slope theo số room type (4 → 15 → 50): search undated 1,587 → **0** statement/room, search dated 1,783 → **0**, catalog 1,522 → **0**, detail 0,174 → **0**.
- Response BEFORE/AFTER **tương đương 15/15** (3 dataset × 5 endpoint, kể cả thứ tự raw; chỉ khác timestamp `checkedAt`), phòng PENDING không lộ ở cả hai.
- Gateway **2760/2760** + direct **600/600** measured samples thành công, 0 lỗi/timeout, 0 Hikari timeout/pending cuối, 0 role-limit error.
- Hosted functional E2E PASS (proposal/approval/booking/idempotency/race/cancel/authz). Cleanup exact-ID **202 rows**, 14 broker messages, 4 Redis keys; schema/Flyway/counts/full-row HMAC khớp 5/5; ADMIN `admin` nguyên vẹn.
- Regression: hotel 401 PASS/34 skip, booking 285 PASS/4 skip, native PostgreSQL 18 disposable 2/2 PASS (SQL-count không đổi theo kích thước dữ liệu).

## 2. Environment

- Backend `feat/supabase-dev-runtime`, HEAD `ea46f3cb7168039b0160d05a05822aa906a81a17` (không đổi). Working tree có sẵn 21 staged / 51 unstaged / 349 untracked (ghi tại `initial-git-backend.json` **trước** khi tạo file 8D nào); frontend `main` `91a2751c…`, 5 unstaged, không bị sửa.
- Năm hosted Supabase projects (User/Loyalty, Hotel/Inventory, Booking/Payment, Revenue, Regulations): runtime preflight exit 0 cả 5; TLS verify-full (client TLS true), PostgreSQL 17.11, `max_connections` 60, reserved 3, runtime role cap 4; schema fingerprint/Flyway (1 row V1)/constraints/indexes khớp canonical baseline 5/5 trước và sau run. Không migration/DDL/index/role/grant/dashboard change.
- Runtime: guarded lifecycle Batch 8C (`batch8c-runtime.py` qua `tools/batch8d.py`), Windows/Java 21, `-Xmx256m`, Hikari max 2/min 1/timeout 10 s, timezone UTC. Revenue/Regulations/Media/Notification không chạy; VNPay `simulated`, Storage `disabled`; không gọi SMTP/Storage/VNPay thật.
- `hotel-service/target/*.jar` (generated output) hiện là bản build AFTER; hai artifact có hash nằm riêng trong `.local/batch8d/artifacts` (git-ignored).
- Hotel JVM chạy đúng JAR theo giai đoạn: BEFORE `27b4b87a…` (build từ tree ban đầu, chưa sửa), AFTER `763ee42e…` (tree ban đầu + thay đổi 8D) — `before-artifact.json`, `after-artifact.json`, `hotel-artifact-events.json`.

## 3. Historical Context

Batch 8B thêm set-based approval (`findEffectivelyApprovedRoomIds`, batched availability). Batch 8C ổn định lifecycle và đo AFTER-only (search undated 22/41/92 statements; p50 3,2/6,2/12,5 s) nhưng không có controlled BEFORE. Các số 8C chỉ dùng làm tham chiếu lịch sử; **không** được dùng làm BEFORE ở đây. Số BEFORE 8D (2×2: 21+2 JdbcTemplate) nhỏ hơn 8C 1 statement vì room 0 được giữ PENDING (bị loại khỏi public mapping nên không có SELECT media của nó).

## 4. Controlled BEFORE

Run ID `batch8d-20261117T120501Z-83cc83bf`; manifest exact-ID được tạo trước mọi write. Fixtures qua Gateway API: 2 OWNER + 2 CUSTOMER (OTP đọc đúng identity synthetic từ DB, không SMTP), hotel/room/inventory proposal/ADMIN approval qua API; publication hotel bằng exact-ID SQL synthetic setup (không phải publication API acceptance). Room 0 cố ý để PENDING.

Dataset tăng dần 2×2 → 3×5 → 5×10 (tổng tối đa 5 hotel/50 room types). Mỗi stage: SQL trace (1 warmup + 3 traced samples/endpoint), rồi matrix Gateway (3 warmup riêng mỗi cell, 30 measured samples, C1/3/5/10 với safety gate p95×(⌈C/2⌉−1) > 8 s → SKIPPED_SAFETY), direct (bypass Gateway) cho Small/Larger. Không retry che lỗi. Mọi sample BEFORE thành công (Gateway 1170/1170, direct 300/300).

## 5. Root Cause Analysis

SQL/JdbcTemplate/transaction DEBUG logger chỉ bật trên Hotel JVM của run trong cửa sổ trace (không trong lúc đo latency), không log bind value. Phân loại fingerprint → call site (`root-cause-attribution-before.json`):

| Nhóm (BEFORE) | 2×2 | 3×5 | 5×10 | Call site |
|---|---:|---:|---:|---|
| Media lazy, 1 SELECT/collection | 5 | 17 | 54 | `Hotel.mediaAssets` / `RoomType.mediaAssets` init trong `HotelMapper`/`RoomTypeMapper.toMediaAssetResponses` |
| Approval set (search) | 4 | 6 | 10 | `findEffectivelyApprovedRoomIds`: 1/hotel trong mapper + 1/hotel trong `HotelOfferSearchService.match` |
| Rooms by hotel | 2 | 3 | 5 | lazy `Hotel.roomTypes` |
| Policies / FAQ / amenity categories / items | 2+2+2+2 | 3+3+3+3 | 5+5+5+5 | `findAllByHotelId`, `findAllByCategoryHotelId` trong mapper |
| Effective amenity codes (JdbcTemplate) | 2 | 3 | 5 | `AmenityCatalogService.effectiveCodes(hotelId)` — **không** nằm trong Hibernate statistics |
| Dated availability snapshot | +6 | +9 | +15 | `getAvailableRoomCounts` (rooms/hotels/peaks/approval) gọi 1 lần/hotel |
| Hotel list, city | 2 | 2 | 2 | cố định (city nằm trong first-level cache) |

Kết luận: N+1 thật ở cấp room (media) và cấp hotel (6 metadata + approval + availability). Catalog (OSIV, không transaction ở controller) không có transaction thừa cho repository query methods; transaction vật lý duy nhất là `effectiveCodes` (1/hotel). Mỗi statement ~110–130 ms gồm network tới hosted DB, nên latency tỉ lệ gần tuyến tính với số round trip; Hikari acquisition max ≤ 7,6 ms trong các snapshot sau benchmark BEFORE, pending 0, timeout 0 → C1 không phải pool contention (ở C≥3 có xếp hàng do pool 2, đúng như thiết kế safety gate).

Quan sát phụ trong khi sửa: `@BatchSize` trên collection có `@Where` (hai collection media) **không** batch được với Hibernate 6.5 của module — native test bắt được (media vẫn 1 SELECT/room); collection `Hotel.roomTypes` (không `@Where`) batch đúng. Vì vậy media được đọc bulk tường minh.

## 6. Source Optimizations

Chỉ `hotel-service` (danh sách hunk 8D; một số file đã có thay đổi chưa commit từ trước, được giữ nguyên):

- `Hotel/entity/Hotel.java`: `@BatchSize(size=100)` cho `roomTypes` (lazy, không eager, không JOIN FETCH nhiều collection).
- `Hotel/mapper/HotelMapper.java` + `PublicHotelFacts.java` (mới): `toPublicHotelResponses(List<Hotel>)` đọc facts **một lần cho cả response** theo chunk 500 ID: approval (cùng predicate effective-approval, chỉ room active), media phòng (chỉ room đã approved) và media hotel theo `target_type`, policies (JOIN FETCH category), FAQ, amenity categories/items, effective codes. Facts là request-local record, không cache xuyên request. `toPublicHotelResponse` dùng chung đường này. Logic lọc amenity public/private gom về một hàm `assembleAmenities` dùng cho cả hai đường.
- `Room/mapper/RoomTypeMapper.java`: `toRoomTypeResponseWithMedia` (kế thừa cấu hình `toRoomTypeResponse`, media từ bulk read).
- Repository: `HotelPolicyRepository.findAllByHotelIdIn`, `QuestionRepository.findAllByHotelIdIn`, `HotelAmenityCategoryRepository.findAllByHotelIdIn`, `HotelAmenityRepository.findAllByCategoryHotelIdIn`, `MediaAssetRepository.findByTargetTypeAndTargetIdIn`; `AmenityCatalogService.effectiveCodesForHotels` (cùng điều kiện join/review như `effectiveCodes`).
- `HotelOfferSearchService`: `approvalFor` (undated: 1 approval read cho mọi eligible room của request), `match(..., approvedSnapshot)`; dated vẫn dùng `availabilityFor` (1 snapshot/request).
- `HotelOfferController.search`/`searchPage`, `HotelController` (catalog, `/search`, `/city/{id}`): gọi mapping theo lô; page chỉ map hotel của trang đã chọn.

Giữ nguyên: response DTO/route/status, authorization, inventory approval predicate, `hasEffectiveInventoryApproval` và kiểm tra sau `FOR UPDATE` khi hold, transaction boundary của booking/hold, REPEATABLE_READ của search, pagination/sort sau global filter. Không thêm dependency, không đổi Hikari/pool/role cap.

## 7. Regression Tests

- Mới: `HotelMapperPublicBatchTest` (bulk read đúng 1 lần, gom đúng theo hotel, loại pending/inactive, category/item inactive, verified codes, media rỗng/nhiều media, approval vẫn là điều kiện bắt buộc dù total_rooms > 0); `HotelOfferSearchServiceTest` thêm 2 case (approval 1 lần cho nhiều hotel; dated/empty không đọc approval; inventory chưa cấu hình vẫn lỗi).
- Cập nhật theo đường gọi mới (giữ nguyên ý định): `HotelOfferControllerTest` (hotel bị lọc không được map; ngân sách map chi tiết = page size), `HotelMapperTextTest` (thêm mock repository media).
- `PublicReadAmplificationPostgresTest` (mới, opt-in `BATCH5B_ACCEPTANCE`, chạy bởi `tools/batch8d-native.py` trên cluster PostgreSQL 18 mới, canonical V1 qua Flyway trên DB disposable): DataSource đếm mọi statement (Hibernate + JdbcTemplate). Kết quả 2×2 / 1×10 / 3×5 / 5×10 đều **12 / 15 / 11 / 11** (search undated / dated / catalog mapping / detail). Đồng thời kiểm tra: approval, pending/inactive bị loại, media `@Where` (một media `ROOM_TYPE` trỏ vào hotel id không xuất hiện ở hotel media), policies/FAQ/amenities/verified codes, dated availability (held 1 → availableRooms 1), lowest price, phân trang 3 trang PRICE_ASC không mất/trùng, tổng 5/trang 3.
- Lần chạy cuối: hotel `435 tests, 0 fail/error, 34 skipped (opt-in)` → 401 PASS; booking `289, 0, 4 skipped` → 285 PASS; native `CanonicalPostgresAcceptanceTest` + `PublicReadAmplificationPostgresTest` PASS, cluster dừng, 0 hosted write. User/Auth/frontend không đổi → **không chạy** trong batch này. `*ApplicationTests` bị loại như Batch 8C.

## 8. Controlled AFTER

JAR AFTER build, hash ghi lại, hotel restart qua guarded lifecycle (graceful exit verified, headroom gate, full-chain readiness, ADMIN login/queue PASS). Đo trên **cùng dataset**: 5×10 trước, sau đó exact-ID shrink về đúng 15 room/3 hotel của BEFORE Medium (`shrink-3x5.json`: xóa 35 states + 35 requests + 35 rooms + 2 hotels trong một transaction, rowcount khớp) rồi về đúng 4 room/2 hotel của Small. Readiness gate chạy lại sau mỗi shrink. Cùng endpoint/query/warmup/concurrency/pool. AFTER: Gateway 1590/1590 (gồm 90 sample lặp dated), direct 300/300.

## 9. SQL Reduction

| Dataset | Endpoint | SQL BEFORE | SQL AFTER | Giảm |
|---|---|---:|---:|---:|
| 2×2 | search undated / dated / catalog / detail / availability | 23 / 29 / 21 / 11 / 4 | 12 / 15 / 11 / 11 / 4 | 47,8% / 48,3% / 47,6% / 0% / 0% |
| 3×5 | như trên | 43 / 52 / 40 / 14 / 4 | 12 / 15 / 11 / 11 / 4 | 72,1% / 71,2% / 72,5% / 21,4% / 0% |
| 5×10 | như trên | 96 / 111 / 91 / 19 / 4 | 12 / 15 / 11 / 11 / 4 | 87,5% / 86,5% / 87,9% / 42,1% / 0% |

SQL = Hibernate statements + JdbcTemplate statements (ổn định 3/3 sample). Transaction vật lý: search 1 → 1; catalog 2/3/5 → 1. AFTER fingerprint: hotel list, rooms (batch), approval ×2 ở search (mapper + `approvalFor`, cùng snapshot REPEATABLE_READ — giữ làm kiểm tra phòng thủ), media hotel, media room, policies, FAQ, categories, items, codes, city.

## 10. Query-Count Slope

| Endpoint | BEFORE (4/15/50 room types) | AFTER | statement/room thêm |
|---|---|---|---|
| Search undated | 23 / 43 / 96 | 12 / 12 / 12 | 1,587 → 0 |
| Search dated | 29 / 52 / 111 | 15 / 15 / 15 | 1,783 → 0 |
| Public catalog | 21 / 40 / 91 | 11 / 11 / 11 | 1,522 → 0 |
| Hotel detail | 11 / 14 / 19 | 11 / 11 / 11 | 0,174 → 0 |
| Room availability | 4 / 4 / 4 | 4 / 4 / 4 | 0 → 0 |

Hotel detail không còn SELECT media theo room; approval không lặp theo room/hotel; availability không regression.

## 11. HTTP Latency

Gateway, chỉ các cell đo được ở cả hai phía (đầy đủ trong `before-after.json`):

| Dataset / endpoint | C | p50 BEFORE → AFTER | p95 BEFORE → AFTER | Throughput |
|---|---:|---|---|---:|
| 2×2 search undated | 1/3/5 | 3428→2115 / 4927→2993 / 8069→5132 | 3468→2349 / 6540→3911 / 9586→5889 | ×1,60 / 1,63 / 1,62 |
| 2×2 search dated | 1/3 | 4205→2527 / 6001→3654 | 4382→2782 / 7648→4819 | ×1,64 / 1,64 |
| 2×2 catalog | 1/3/5 | 2910→1600 / 4228→2221 / 6867→3718 | 3034→1770 / 5322→2914 / 8067→4364 | ×1,79 / 1,87 / 1,87 |
| 2×2 detail | 1/3/5/10 | 1539→1566 / 2199→2214 / 4384→3654 / 8690→7234 | 1952→1853 / 2872→2878 / 5708→4396 / 9479→7743 | ×0,99 / 0,99 / 1,21 / 1,11 |
| 3×5 search undated | 1/3 | 5704→1909 / 8640→2855 | 6627→2091 / 10813→3599 | ×2,99 / 3,06 |
| 3×5 search dated | 1/3 | 6801→2323 / 10451→3388 | 7289→2624 / 14609→4184 | ×2,88 / 3,13 |
| 3×5 catalog | 1/3 | 5241→1522 / 7970→2275 | 5630→2037 / 9996→2916 | ×3,37 / 3,58 |
| 3×5 detail | 1/3/5 | 1838→1610 / 2791→2352 / 4546→3729 | 2070→1708 / 3514→3115 / 5470→4488 | ×1,17 / 1,20 / 1,21 |
| 5×10 search undated | 1 | 12022→1919 | 17505→2012 | ×6,63 |
| 5×10 search dated | 1 | 13291→4701 (lặp 2327) | 14435→7236 (lặp 2604) | ×2,69 |
| 5×10 catalog | 1 | 11535→1944 | 12231→2403 | ×6,03 |
| 5×10 detail | 1/3/5 | 2360→1821 / 2896→2338 / 4896→3128 | 2726→2675 / 4529→2811 / 7221→4405 | ×1,27 / 1,52 / 1,61 |

Nhiều cell C3/C5 trước đây SKIPPED_SAFETY nay đo được (ví dụ 5×10 search undated C3 p50 2890, C5 5836 ms; catalog C3 2587, C5 6266 ms) — không tính % vì thiếu BEFORE. Endpoint đối chứng room availability (code không đổi) lệch −8,9%…+9,5% giữa các cửa sổ → biên trôi môi trường; cải thiện ở Small detail (0 statement giảm) nằm trong biên đó và **không** được coi là tối ưu. Khi SQL không giảm thì latency không giảm — nhất quán với RCA.

Latency còn lại: AFTER ~12–15 round trip × ~110–160 ms network/driver ≈ 1,5–2,4 s; JDBC mean (Hibernate) ~1,3–1,9 s trong trace. Heap Hotel sau benchmark 73–123 MB / 256 MB, không tăng bất thường (AFTER larger 83 MB vs BEFORE larger 123 MB); CPU process < 1%. C10 search vẫn SKIPPED_SAFETY do pool 2 + timeout 10 s.

## 12. Functional Correctness

Trên hosted dataset Small với chain chạy JAR AFTER (`e2e-results.json`, `concurrency-results.json`): booking room PENDING 409 + không ghi hold/counter; OWNER/CUSTOMER approve 403; OWNER B sửa hotel OWNER A 403; header ADMIN giả mạo 403; ADMIN approve 200; stale revision 409; availability 2; booking 201; retry cùng key cùng booking; đổi payload cùng key 409; race 3 request còn 1 phòng → đúng 1 thành công, held=2; proposal giảm quantity dưới holds 409, reject giữ approved stock; cancel 204 trả counters 0. Concurrency stock 2: 201/201/409, held 2; same-key đồng thời → 1 booking/1 hold; cancel → 0. Trước approval phòng 0 không xuất hiện trong search; sau approval xuất hiện ở search undated/dated (available 2) và detail. 200/200 check PASS. Không gọi payment provider.

## 13. Runtime Stability

Log JVM trong cửa sổ 8D (UTC ≥ 12:06): 0 `too many connections for role`, 0 Hikari "Connection is not available", 0 HTTP 500; 3 dòng ERROR ở booking là 409 có chủ đích ("Failed to hold rooms" của booking trước approval/người thua race); 1 dòng ERROR Eureka "Cannot fetch registry" tạm thời ở hotel, không có request lỗi kèm theo. Hikari cuối: active 0, pending 0, timeout 0, acquisition max 0,17 s. Mỗi readiness gate: app-role backends ≤ 2/DB, headroom ≥ 2 trên cap 4. 503 chỉ xuất hiện trong lần khởi động đầu khi route Gateway chưa propagate (smoke attempt 0–2), không trong đo. Graceful stop 6/6 JVM, backend app-role về 0 cả 5 DB. Không tăng pool/role cap.

## 14. Cleanup

Dừng load → dừng toàn bộ JVM của run → discover descendants → exact-ID cleanup với expected rowcount:
- Shrink 3×5: 107 rows; shrink 2×2: 34 rows; cleanup cuối: User 19 + Booking 20 + Hotel 22 = 61 rows. **Tổng 202 rows**, mọi ID trong manifest được xác minh vắng mặt (`remaining: []`).
- Broker: 10 message `revenue.booking.queue` + 4 OTP `notification.otp.queue` khớp đúng bookingId/email fixture được ack; 0 message không khớp; mọi queue về 0. (Batch 8C ghi `notification.otp.queue` = 4 lúc kết thúc; lúc dọn 8D chỉ có 4 message và cả 4 khớp email `b8d_` — trạng thái các message 8C trước đó không được suy đoán.)
- Redis: 4 key `auth:session-generation:<fixture>` vắng mặt, ADMIN loại trừ, không flush.
- Không DELETE theo prefix/wildcard, không TRUNCATE/DROP/reset sequence, không kill session lạ. Dependencies dừng, data retained.
- Sau cleanup: schema fingerprint, Flyway history, row counts và full-row HMAC digest mọi bảng khớp trước run **5/5** (`final-schema-history-data-verification.json`).

## 15. ADMIN Preservation

`admin` không thuộc manifest; mọi login dùng password từ `docker/.env` trong bộ nhớ. Login thật qua Gateway + ADMIN queue PASS nhiều lần (sau mỗi restart và trước khi dừng). Identity HMAC, role ADMIN, active và digest toàn bảng `users` (gồm cột password, không xuất hash) giống hệt trước/sau. Không tạo/sửa/xóa ADMIN.

## 16. Remaining Risks

- Thứ tự phần tử trong media/policies/FAQ/amenities vẫn do PostgreSQL quyết định (trước đây cũng không có ORDER BY); trên dataset đo, thứ tự raw giống hệt, nhưng không phải bảo đảm hợp đồng.
- Fixture không có policies/FAQ/amenities/media trên hosted; các nhánh này được kiểm bằng native PostgreSQL và unit test, không phải hosted rich-content dataset.
- Latency vẫn ~1,5–2,5 s do round trip tới hosted DB; không có SLO được phê duyệt nên không đặt ngưỡng PASS. C10 search vẫn bị safety gate chặn với pool 2.
- Một cell Gateway (5×10 dated C1) chịu nhiễu môi trường; đã đo lặp và công bố cả hai.
- Approval đọc 2 lần trong search undated (mapper + `approvalFor`) — cố ý giữ, chi phí cố định 1 statement.
- Hosted chỉ là development runtime local + Supabase; không chứng minh multi-replica, capacity hay production SLA. Legacy force-stop launcher vẫn tồn tại (không dùng).
- `pg_stat_statements` không được cấp quyền; attribution dựa trên log có trace ID, không phải server-side statistics.

## 17. Final Status

**DONE (scoped).** 14 acceptance criteria của Batch 8D đều có evidence: SQL attribution; controlled BEFORE/AFTER trên cùng hosted IDs; SQL và slope giảm rõ; response tương đương; approval/booking/hold/idempotency/authorization PASS; không connection exhaustion; cleanup sạch; ADMIN nguyên vẹn; schema/Flyway không đổi; regression PASS; evidence đầy đủ. **Không production-ready.** Không stage/commit/push/reset/checkout/restore/clean/stash; thay đổi có sẵn được giữ nguyên.

## 18. Recommended Next Workstream

1. Giảm round trip cố định còn lại (12–15/request): cân nhắc gộp các fact query nhỏ (policies/FAQ/categories/items/codes) hoặc projection DTO, và bỏ lần approval thứ hai khi có review chấp thuận rằng snapshot của mapper là đủ — chỉ sau khi đo lại với cùng phương pháp.
2. Hosted rich-content dataset (media/policies/FAQ/amenity mapping) để đo payload/serialization thực tế.
3. Xem xét đặt runtime gần region Supabase hoặc đo RTT tách bạch để xác định latency floor; chỉ thay pool/role budget qua quy trình hosted đã duyệt.
4. ORDER BY ổn định cho các collection public nếu frontend cần thứ tự xác định (thay đổi hợp đồng — cần phê duyệt).

Evidence: [batch-8d-read-optimization](evidence/batch-8d-read-optimization/README.md).
