# Batch 9D — Xử lý finding 9B/9C (18/11/2026)

Phạm vi: các finding F-9B-1..5 và F-9C-2..5 ghi trong [9B](BATCH9B_AI_READ_CONTRACT_VERIFICATION_2026-11-17.md) và [9C](BATCH9C_AI_WRITE_PREGATE_VERIFICATION_2026-11-18.md). Không đụng Place, schema, migration, Supabase, `.env`; không commit.

## 1. Kết quả theo finding

| Finding | Trạng thái | Thay đổi | Bằng chứng |
|---|---|---|---|
| F-9C-2 Booking→Hotel không propagate trace | **FIXED_VERIFIED** | `booking-service/.../config/RestClientConfig.java`: `RestTemplate` dựng từ `RestTemplateBuilder` (giữ `@LoadBalanced`, connect/read timeout) | `RestClientTracePropagationTest` (header `traceparent` mang trace hiện tại + timeout giữ nguyên; **fail với code cũ**: "Expected header <traceparent> to exist"); runtime: trace client xuất hiện 6 lần trong log Hotel (trước: 0) |
| F-9C-3/4 400 không body (cancel, VNPay create, …) | **FIXED_VERIFIED** | `ApiErrorSupport.rejected(msg)`; các nhánh `IllegalArgumentException` của `DELETE /api/bookings/{id}`, `cancel-reviewed`, `PUT …/status`, `POST /api/payments`, `vnpay/create`, `refund`, `confirm` trả envelope 400 `INVALID_REQUEST` với message cố định (không lộ text exception). `vnpay/return` (callback công khai) giữ nguyên | `AgentWriteContractTest` (cancel + VNPay: envelope, message cố định, không chứa text exception); runtime: VNPay create cho booking đã hủy → 400 envelope, `X-Request-ID` = `requestId` |
| F-9B-1 DB outage → 500 | **FIXED_VERIFIED (service) / bounded qua Gateway** | Hotel `GlobalExceptionHandler` và Booking `ValidationAdvice`: `DataAccessResourceFailureException`, `TransientDataAccessResourceException`, `CannotCreateTransactionException` → 503 `DEPENDENCY_UNAVAILABLE` (body generic). `createBooking` không còn nuốt các exception này thành 500 | `AgentReadContractTest` (search/detail 503, bug khác vẫn 500), `AgentWriteContractTest` (create/read 503); runtime: Postgres dừng → service log "Database unavailable … CannotCreateTransactionException", 0 "Unhandled"; client nhận **504 của Gateway ở 20 s** vì Hikari mặc định chờ 30 s |
| F-9B-5 không `checkedAt`, Gateway không timeout | **FIXED_VERIFIED** | `AvailabilityCheckResponse` thêm `checkedAt` (additive, UTC); FE type `checkedAt?`. Gateway `spring.cloud.gateway.httpclient.connect-timeout=${GATEWAY_CONNECT_TIMEOUT_MS:3000}`, `response-timeout=${GATEWAY_RESPONSE_TIMEOUT:20s}` (trên mọi deadline FE ≤15 s) | Test controller; runtime: `checkedAt` ISO UTC; outage → 504 đúng 20.0 s |
| F-9B-2 public `HotelResponse` có `ownerId` | **FIXED_VERIFIED** | `HotelMapper.toPublicHotelResponseWithFacts`: `ownerId` = null cho mọi response public (detail/search/list). Owner/admin view (`toHotelResponse`) và `/internal/reports` (Revenue) giữ nguyên. FE không đọc `hotel.ownerId`; type đổi `ownerId?: string \| null`. `contactEmail/Phone` giữ (thông tin liên hệ công khai của khách sạn) | `HotelMapperPublicBatchTest`; generated `HotelMapperImpl` gán `ownerId = null`; runtime: detail + search không có `ownerId` |
| F-9C-5 submit-approval fallback route | **FIXED_VERIFIED** | `lib/api/hotels.ts`: một lần gọi Gateway; 404 là "không tìm thấy", không replay write sang `/hotels/...` (cả hai route đều qua Gateway vào cùng controller) | `tests/hotel-adapter-deadline.test.mjs` (1 call, 404 → `NOT_FOUND`) |
| F-9B-3 không chặn ngày quá khứ / cận trên guests/rooms/stay | **FIXED_VERIFIED (addendum §4)** | Quy tắc do người dùng quyết định 18/11 | §4 |
| F-9B-4 room chưa duyệt → 409 thay vì 404 | **KEPT — đã xem xét lại (addendum §4)** | Thử đổi sang 404 rồi hoàn tác: Booking phụ thuộc vào 409 này | §4 |

Finding mới: **F-9D-1** — Hikari `connection-timeout` mặc định 30 s (profile `canonical`) > Gateway 20 s, nên outage DB qua Gateway là 504 (không có envelope) thay vì 503. Đặt qua env `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` làm app không khởi động ("pool is sealed once started") → cần chỉnh trong cấu hình datasource của profile, batch riêng có duyệt. Cũng lưu ý: auth-service và revenue-service cũng dựng `RestTemplate` không qua builder (cùng loại F-9C-2, chưa sửa — ngoài phạm vi finding).

## 2. Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| `mvn -pl booking-service,api-gateway test` | Booking 301 run / 0 fail / 4 skipped; Gateway không có test, build OK |
| `mvn -pl hotel-service -Dtest=!HotelServiceApplicationTests test` | 447 run / 0 fail / 34 skipped |
| `HotelServiceApplicationTests.contextLoads` | **ERROR, môi trường**: test `@SpringBootTest` không guard kết nối `localhost:5432`, nơi có PostgreSQL cài trên máy từ chối mật khẩu. Không liên quan thay đổi; không đụng DB đó |
| Negative control F-9C-2 | Test fail với `new RestTemplate(factory)`, pass với bản sửa |
| FE `npx tsc --noEmit --incremental false` | PASS |
| FE `npm run test:regression` | 218/218 |
| FE eslint file đổi | `hotels.ts` 14 `no-explicit-any` (như HEAD); `availability.ts`, test: 0 |
| Canonical disposable Gateway smoke | **94 PASS / 0 FAIL** (`results-fixes.json`), stack sạch mới; sau đó down: 0 container/network/volume |

Các lần chạy smoke trước lần xanh (không có lỗi sản phẩm mới): (1) Gateway chưa thấy hotel-service trong Eureka cache → 503 ở fixture đầu; thêm chờ readiness. (2) 90/4: hai check cũ so sánh nguyên body availability (vỡ vì `checkedAt` mới) + hai check outage kỳ vọng 503 nhưng nhận 504 Gateway (→ F-9D-1). (3) Thử đặt Hikari timeout qua env → app không khởi động; đã bỏ. (4) 94/0.

## 3. Tác động hợp đồng

- Additive: `checkedAt` trong availability check; body cho các 400 trước đây rỗng.
- Thay đổi: `ownerId` public = null; DB outage 503 thay vì 500 (tại service); Gateway 504 sau 20 s; submit-approval không còn gọi route thứ hai.
- Không đổi route, status code thành công, gRPC/proto, event, schema.

## 4. Addendum (18/11) — F-9B-3, F-9B-4, F-9D-1

| Finding | Trạng thái | Thay đổi | Bằng chứng |
|---|---|---|---|
| F-9B-3 | **FIXED_VERIFIED** | `hotel-service/.../validation/StayLimits.java` (mới): checkIn không trước "hôm nay" theo `Asia/Ho_Chi_Minh`, checkOut > checkIn, tối đa 30 đêm (đếm theo ngày lịch), rooms 1–20, adults 1–40, children 0–40 (tổng của lượt tìm). Áp dụng cho `HotelOfferCriteria.validate()` (mọi đường tìm kiếm) và `GET /api/v1/availability/check` (`quantity` 1–20). Hold/booking admission **không đổi**. FE `HotelSearchBar`: chặn >30 đêm với thông báo tiếng Việt (picker đã giới hạn dưới mức server) | `HotelOfferCriteriaLimitsTest` (biên 30/31 đêm, 20/21, 40/41, ngày theo múi giờ nghiệp vụ qua `Clock`), `AgentReadContractTest` (+5 search, +4 availability → 400 không query); runtime: 6 case 400 + 30 đêm 200 qua Gateway |
| F-9B-4 | **KEPT (by design, có bằng chứng)** | Đã thử trả 404 cho room chưa duyệt; smoke phát hiện regression: `WRITE unapproved room booking` thành 400 vì `booking-service/.../HotelCatalogClient` đọc `GET /api/v1/{id}` và map **409** → "Inventory requires review". Đã hoàn tác; giữ 409 và ghi lý do ở `RoomTypeServiceImpl` + test | Run lỗi 100/1 (đã thay bằng run xanh); `RoomTypeServiceImplTest`, `AgentReadContractTest` khóa hợp đồng 409; runtime 409 cả READ và WRITE |
| F-9D-1 | **FIXED_VERIFIED** | Năm `application-canonical.yml` (user/hotel/booking/revenue/regulations): `hikari.connection-timeout: ${CANONICAL_DB_CONNECTION_TIMEOUT_MS:10000}` (bằng supabase-dev). Lỗi "pool is sealed" lần trước là do tên env sai (`..._CONNECTION_TIMEOUT` bind thành `hikari.connection.timeout`); YAML đúng không gặp | Runtime: Postgres dừng → Hotel search và Booking read trả **503 `DEPENDENCY_UNAVAILABLE` envelope sau 10.0 s** qua Gateway (trước: Gateway 504 sau 20 s) |

Kiểm tra: user 115/1 error/4, hotel 451/0/34, booking 301/0/4, revenue 54/1 error/4, regulations 40/0/2, gateway build OK. Hai error là `contextLoads` của user/revenue — cùng nguyên nhân môi trường với hotel (PostgreSQL local ở 5432 từ chối mật khẩu). FE tsc PASS, regression 218/218, eslint `HotelSearchBar.tsx` như HEAD (1 `no-unused-vars` có sẵn). Canonical Gateway smoke trên stack mới: **101 PASS / 0 FAIL** (`results-9e.json`), down sạch.

Đính chính: booking create **đã** chặn ngày đến trong quá khứ và ngày không thứ tự từ trước (`BookingService.validateBookingInput`, `cancellationPolicy.today()` theo `Asia/Ho_Chi_Minh`); câu "booking vẫn nhận ngày quá khứ" ở bản trước là sai. Phần còn thiếu được bổ sung ở §5.

## 5. Addendum (18/11) — giới hạn F-9B-3 cho booking create

Người dùng duyệt áp cùng giới hạn cho tạo booking. `BookingService.validateBookingInput` thêm: tối đa 30 đêm (`checkOutDate`), tổng phòng ≤ 20 (`items`), adults ≤ 40, children ≤ 40 — trả 400 `VALIDATION_ERROR` có `fieldErrors`, trước mọi đọc catalog/hold/ghi. Kiểm tra chạy **sau** replay idempotency nên booking đã chấp nhận vẫn được trả lại cho key cũ. Hold API của Hotel (chỉ service identity của Booking gọi) không đổi.

Kiểm tra: `BookingServiceTest` (+5: 4 trường hợp vượt giới hạn không chạm catalog/hold/save, 1 biên 30 đêm/20 phòng/40/40 qua được validation); booking-service 306/0/4. Canonical Gateway smoke trên stack mới: **104 PASS / 0 FAIL** (`results-9f.json`; thêm 31 đêm, 41 người lớn, ngày đến quá khứ → 400 field error, không hold), down sạch.
