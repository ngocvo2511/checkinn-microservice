# Batch 9B — AI READ Contract Verification

Ngày **17/11/2026, Asia/Saigon**. **DONE trong phạm vi service boundary (hotel-service MockMvc + SecurityConfig/advice thật); Gateway-level execution NOT_RUN. Không production-ready.** Không sửa application source, API, schema, runtime hoặc Place.

## 1. Executive Summary

- Mục tiêu (từ [Batch 9A](BATCH9A_STANDARDIZATION_RECONCILIATION_2026-11-17.md)): verification gates C1 (public-read route subset, STD-003), C2 (negative contract cho search/availability, STD-018), C3 (agent-facing READ contract + error envelope, STD-028/026).
- Thêm **12 test** (test-only): `AgentReadContractTest` (11) và `AvailabilityReadValidationTest` (1). **12/12 PASS lần chạy đầu**; hotel full suite **447 reported / 413 PASS / 34 opt-in skipped / 0 fail, BUILD SUCCESS** (trước 9B: 435/401/34).
- Thêm [READ tool contract](ai-agent/READ_TOOL_CONTRACT.md) cho Agent: tham số, ràng buộc, response, bảng mã lỗi → hành vi Agent, các route cấm.
- In-scope STD-003 và STD-018: NOT_VERIFIED → **PARTIAL**. Parent statuses và counts không đổi (17/12/3/0).
- 5 findings ghi nhận, **không sửa** (thay đổi contract cần duyệt riêng) — mục 6.
- Next: **Batch 9C — AI WRITE pre-gate verification + F1**, gồm Gateway-level READ smoke.

## 2. Git Preflight

Backend `feat/inventory-approval-read-batching` @ `96bfc4f`, frontend `fix/inventory-approval-workflow` @ `6942747` (không đổi). Trước 9B, backend có các thay đổi chưa commit của 9A (PLAN/AUDIT, báo cáo 9A) và hai IDE edit có sẵn; frontend sạch. Không stage/commit/push/reset/restore/checkout/clean/stash, không đổi branch.

## 3. Scope

| Gate | Trong 9B | Ngoài 9B |
|---|---|---|
| C1 STD-003 | Mọi public GET trong ma trận AI READ + private GET lân cận | WRITE/RPC actor matrix (9C/G4) |
| C2 STD-018 | `GET /hotels/offers/page` criteria, `GET /api/v1/availability/check` | Booking create (9C) |
| C3 STD-028/026 | Success shape + error envelope tại service boundary | Gateway-level smoke (9C) |

Place/Nearby: EXCLUDED — EXTERNAL OWNER, không có trong ma trận.

## 4. Tests Added

`hotel-service/src/test/java/com/example/hotelservice/security/AgentReadContractTest.java` — `@WebMvcTest` 11 controller thật (`HotelController`, `HotelOfferController`, `RoomTypeController`, `AvailabilityController`, `AccommodationTypeController`, `AmenityCatalogController`, `AmenityController`, `PolicyController`, `CityController`, `ReviewSummaryController`, `ReviewController`) + `SecurityConfig` thật + `GlobalExceptionHandler`/`ApiErrorBodyAdvice` thật; services mock.

| Test | Chứng minh |
|---|---|
| `everyAgentReadEndpointIsAnonymous` | 14 public GET (search page/list, hotel detail, rooms by hotel, room, availability check, accommodation types, amenity catalog, amenity/policy categories, cities, location search, review summaries, review stats) → 200 không cần token |
| `publicReadsUsePublicPolicyEvenWithSignedOwnerAndForgedAdminHeaders` | Owner token + `X-Is-Admin`/`X-User-Id` giả vẫn gọi `getDetail`/`getPublicByHotel`/`getPublicById`; không bao giờ gọi preview hoặc catalog inactive |
| `neighbouringPrivateReadsRejectAnonymousCallersWithTheErrorEnvelope` | 20 private GET (preview, documents, owner, pending, mapping/type-change, inventory-review, `/admin/**`, `/internal/**`, hold, media preview, reviews me/owner) → 401 envelope + `WWW-Authenticate: Bearer`, header giả bị bỏ qua, không service call |
| `customerTokenCannotReachAdminInternalOrHoldReads` | 8 admin/internal/hold GET → 403 cho CUSTOMER kể cả với `X-Is-Admin: true` |
| `emptySearchIsASuccessfulPageNotAnError` | Không có kết quả = 200 với `content:[]`, `page/size/totalElements/totalPages/sort/availableAmenities`, không có `code` |
| `availabilityCheckReturnsCountAndBooleanForTheRequestedQuantity` | `{available:false, availableRooms:1}` khi yêu cầu 2 |
| `invalidSearchCriteriaAre400BeforeAnyCatalogQuery` | 19 case (thiếu điểm đến, 0 đêm, ngày đảo, nửa khoảng ngày, ngày sai ISO, rooms/adults/children sai, giá âm/đảo, loại hình/mã tiện nghi sai, UUID sai, page/size/sort/star/guest rating sai) → 400 `INVALID_REQUEST`/`VALIDATION_ERROR` + `requestId`; **không** chạm HotelService/candidates/offers/city/location/review |
| `invalidAvailabilityParametersAre400WithoutReadingInventory` | 6 case (thiếu quantity/checkIn, quantity 0/-1, UUID sai, ngày sai format) → 400 envelope, không đọc inventory |
| `unapprovedInventoryIsAStableBusinessConflictNotSoldOut` | Availability check và `get_room` → 409 `INVENTORY_APPROVAL_REQUIRED`, không lộ reviewer |
| `serviceDateRejectionAndMissingResourcesMapToClientErrors` | Ngày đảo từ service → 400 với message; hotel không public → 404 `NOT_FOUND`; UUID sai → 400 |
| `unexpectedAndDatabaseFailuresAreSanitized5xxNeverEmptyResults` | DB failure và lỗi bất ngờ → 5xx envelope, không có `content`, không lộ host/password/message/tên exception |

`hotel-service/src/test/java/com/example/hotelservice/Room/availability/service/AvailabilityReadValidationTest.java` — `AvailabilityService` thật: stay 0 đêm, ngày đảo, null, quantity 0, batch read sai ngày → `IllegalArgumentException` **trước mọi repository read**.

Envelope helper kiểm `status`, `code`, `message`, `path`, `requestId` và không có `trace`/`exception`.

## 5. Verification Commands

| Command (`checkinn`) | Kết quả |
|---|---|
| `mvn -o -B -ntp -pl hotel-service '-Dtest=AgentReadContractTest,AvailabilityReadValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` | 12 run, 0 fail/error/skip (lần đầu lỗi compile do `startsWith` ambiguous Hamcrest/Mockito — sửa thành `org.hamcrest.Matchers.startsWith`, không đổi assertion) |
| `mvn -o -B -ntp -pl hotel-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`, opt-in flags = false | **447 reported, 413 PASS, 34 opt-in skipped, 0 failure/error, BUILD SUCCESS** |

**NOT_RUN:** Gateway-level READ smoke. Lý do: image Test mode `checkinn/*:local` build 4 ngày trước (trước source 7B/8D), smoke có ý nghĩa cần rebuild và fixture inventory được ADMIN duyệt qua workflow thật trên stack test — mở rộng phạm vi/side effects (container, volume) vượt mục tiêu test-only của 9B. Chuyển vào 9C. Không có hosted/Supabase/provider access. Frontend không đổi nên không chạy lại (9A: 214 PASS, tsc/build PASS).

## 6. Findings (ghi nhận, không sửa)

| # | Finding | Ảnh hưởng Agent | Đề xuất |
|---|---|---|---|
| F-9B-1 | DB/connection failure trả **500 `INTERNAL_ERROR`**, không phải 503 `DEPENDENCY_UNAVAILABLE` (`GlobalExceptionHandler` generic handler) | Agent không phân biệt bug với outage; cả hai phải xử lý là "tạm không có dữ liệu" | Map `DataAccessResourceFailureException`/`CannotGetJdbcConnectionException` → 503 trong batch riêng (STD-026), kèm test |
| F-9B-2 | Public `HotelResponse` chứa `ownerId`, `contactEmail`, `contactPhone` | Dữ liệu nội bộ (`ownerId`) có thể vào context LLM | Agent lọc field (đã ghi trong tool contract); cân nhắc bỏ `ownerId` khỏi public DTO (thay đổi API, cần duyệt) |
| F-9B-3 | `HotelOfferCriteria` không chặn `checkIn` trong quá khứ, không có cận trên `rooms/adults/children`, không giới hạn độ dài stay | Agent có thể sinh truy vấn vô nghĩa/tốn kém | Agent tự ràng buộc; cân nhắc validation server (STD-018) |
| F-9B-4 | `GET /api/v1/{roomTypeId}` của phòng chưa duyệt trả 409 `INVENTORY_APPROVAL_REQUIRED` thay vì 404 | Lộ sự tồn tại của room chưa public; Agent phải map đúng nghĩa | Giữ hiện trạng; đánh giá cùng C1 WRITE matrix |
| F-9B-5 | `AvailabilityCheckResponse` không có `checkedAt`/ngày; Gateway không có response timeout | Agent phải tự ghi thời điểm và đặt deadline | Ghi trong tool contract; timeout Gateway thuộc C5/9C |

Không finding nào là blocker cho Agent READ MVP chỉ dùng public API kèm các ràng buộc trong tool contract.

## 7. Issue Status Changes

| Issue | Parent | In-scope trước → sau | Evidence |
|---|---|---|---|
| STD-003 | FIXED_UNVERIFIED (giữ) | NOT_VERIFIED → **PARTIAL** | Public-read subset VERIFIED service boundary; WRITE/RPC matrix + Gateway còn thiếu |
| STD-018 | FIXED_UNVERIFIED (giữ) | NOT_VERIFIED → **PARTIAL** | Search + availability negative contract VERIFIED; booking create còn thiếu |
| STD-026 | IN_PROGRESS (giữ) | PARTIAL (giữ) | Backend READ envelope VERIFIED; F-9B-1; FE F1 còn |
| STD-028 | IN_PROGRESS (giữ) | PARTIAL (giữ) | Agent READ suite service-level; Gateway/WRITE/E2E còn |

Counts hiện hành: parent FIXED_VERIFIED 17 / IN_PROGRESS 12 / FIXED_UNVERIFIED 3; in-scope VERIFIED 17 / PARTIAL 11 / IN_SCOPE_DONE–PLACE_SCOPE_EXCLUDED 3 / NOT_VERIFIED 0 / DEFERRED 1 / BLOCKED 0. PLAN §0/§78 và AUDIT §0/checkpoint 9B được cập nhật từ cùng dữ liệu.

## 8. AI READ Readiness after 9B

READY_WITH_CONSTRAINTS ở **service boundary** cho search, detail, rooms, availability, catalogs, reviews, cancellation policy, public/private boundary — theo [READ tool contract](ai-agent/READ_TOOL_CONTRACT.md). Còn thiếu trước khi khẳng định trên runtime: Gateway-level smoke (9C). Không có code-fix blocker. Không bao gồm Place/Nearby.

## 9. Recommended Next Batch — 9C

AI WRITE pre-gate verification + F1: C1 cho route Agent WRITE dùng (booking create/read/cancel, payment URL), C2 booking create negative contract, C3 WRITE contract + **Gateway-level READ/WRITE smoke** trên Test stack được rebuild với fixture duyệt qua workflow thật, C4 TIMEOUT/UNKNOWN recovery (cùng idempotency key), C5 correlation Gateway→Booking→Hotel, F1 frontend `hotelsApi.getHotelById`/writers deadline. Cần xác nhận trước khi rebuild/khởi động Test stack (container/volume local). Complexity MEDIUM.

## 10. Final Status

**Batch 9B: DONE (service-boundary scope) / Gateway-level NOT_RUN.** Files: 2 test mới trong `hotel-service`, `Docs/ai-agent/READ_TOOL_CONTRACT.md`, báo cáo này, PLAN (§0, §78), AUDIT (§0, checkpoint 9B). Không production-ready; standardization tổng thể PARTIAL.
