# Batch 9F — Core follow-up fixes (20/11/2026)

Phạm vi do [Batch 9E](BATCH9E_FINAL_CORE_STANDARDIZATION_AUDIT_2026-11-18.md) §6 đề xuất: F-9E-1, F-9E-3/4, STD-030 (client REST còn lại), STD-026 (DB outage ở service còn lại). Không đổi route, schema, migration, gRPC/proto, event; không Place; không hosted/provider. Nền: backend `feat/standardization-hardening` @ `8e8bb4d`, frontend `fix/hotel-adapter-deadlines` @ `730e4a0` (9A–9E đã được người dùng commit → điều kiện R1 của 9E đạt). Thay đổi 9F **chưa commit**.

## 1. Kết quả

| Mục | Trạng thái | Thay đổi | Bằng chứng |
|---|---|---|---|
| F-9E-1 (STD-012) dữ kiện bịa trên trang đặt phòng/thanh toán | **FIXED_VERIFIED** | `app/booking/page.tsx`: bỏ "Chỉ còn 8 phòng…"/"Chỉ còn 1 phòng" (trang không có nguồn tồn phòng). `app/booking/payment/page.tsx`: bỏ "1 giường", "Bữa sáng cho 2 người", "WiFi miễn phí", "Trước 12:00"; giờ nhận phòng và điều khoản hủy lấy từ `GET /api/bookings/cancellation-policy` (cùng nội dung trang đặt phòng); thay "Không hoàn tiền"/"Không đổi lịch" bằng chính sách thật; khi không tải được chính sách hiển thị trạng thái chưa biết, không mặc định | `tests/booking-facts-source.test.mjs` (3); regression 221/221; tsc; build; ESLint 2 file như HEAD (2 `no-explicit-any` có sẵn) |
| F-9E-3 default browser config lẫn spec area | **FIXED_VERIFIED** | `playwright.config.ts` `testIgnore` 7 spec area | `npm run test:browser` **105/105** (trước: 105 + 45 fail) |
| F-9E-4 area config Turbopack abort | **FIXED_VERIFIED** | `playwright.{content,documents,reviews}.config.ts` thêm `--webpack` như các area config khác | content 8/8, documents 4/4, reviews 4/4 |
| STD-030 client REST chưa instrument | **FIXED_VERIFIED (unit)** | `auth-service` `AuthServiceApplication#restTemplate`, `revenue-service` `RestClientConfig#restTemplate`, `user-service` `RegulationClient` dựng từ `RestTemplateBuilder` của Boot (giữ timeout); test cũ cập nhật chữ ký | `RestTemplateTracePropagationTest` (auth, revenue), `RegulationClientTracePropagationTest` (user): header `traceparent` mang trace hiện tại; cùng cơ chế với test Booking đã chứng minh fail ở code cũ (9D) |
| STD-026 DB outage ở service còn lại | **FIXED_VERIFIED** | user/revenue/regulations vốn map `DataAccessException` → 503, nhưng `CannotCreateTransactionException` (pool không có kết nối) **không** là `DataAccessException`: user trả **401 kèm nguyên văn exception**, revenue/regulations trả 500. Thêm vào handler 503 hiện có. auth-service không có datasource (N/A) | `DatabaseOutageMappingTest` ×3 (user test **fail ở HEAD: 401**); runtime: User profile và Owner revenue summary với Postgres dừng → 503 trong ≤15 s, không lộ text |

**Đính chính 9E:** câu "auth/user/revenue/regulations: DB outage vẫn generic 500" không chính xác — các service này đã map `DataAccessException`; lỗ hổng thật là nhánh không lấy được connection (`CannotCreateTransactionException`), và ở user-service là 401 chứ không phải 500.

## 2. Finding mới

**F-9F-1 (P2, STD-026):** `user-service` `GlobalExceptionHandler` còn map mọi `RuntimeException` → **401** và mọi `Exception` khác → **400**, cả hai trả nguyên `ex.getMessage()` và không có `code`/`requestId`. Lỗi lập trình/phụ thuộc khác bị trình bày như lỗi xác thực/đầu vào và có thể lộ chi tiết nội bộ. Không sửa trong 9F vì client có thể đang dựa vào 401 cho các `RuntimeException` nghiệp vụ — cần rà soát các nơi ném `RuntimeException` trong user-service trước khi đổi.

## 3. Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| `mvn -pl auth-service,user-service,revenue-service,regulations-service -Dtest='!*ApplicationTests' test` | user 116/0/4, auth 48/0/0, revenue 55/0/4, regulations 41/0/2 (run/fail/skipped), BUILD SUCCESS |
| Negative control `DatabaseOutageMappingTest` (user) với handler HEAD | FAIL: `Status expected:<503> but was:<401>` → fix khôi phục |
| FE `npm run test:regression` | 221/221 |
| FE `npx tsc --noEmit --incremental false` / `npm run build` | PASS / PASS |
| FE `npm run test:browser` | 105/105 |
| FE content / documents / reviews configs | 8/8, 4/4, 4/4 |
| Canonical disposable Gateway smoke (stack mới từ source hiện tại) | **106 PASS / 0 FAIL** (`results-9g.json`), down: 0 container/network/volume |

Browser cases intercept backend bằng dữ liệu synthetic. Trace của auth/revenue/user chỉ được chứng minh ở unit level (không có kịch bản smoke gọi các client này kèm `traceparent`).

## 4. Trạng thái sau 9F

Parent không đổi (17/12/3/0). In-scope: **STD-012 PARTIAL → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED** (phần không thuộc Place đã rà soát hệ thống ở 9E và counterevidence duy nhất F-9E-1 đã sửa; PlaceCard/PlaceReviewsModal vẫn EXCLUDED) → VERIFIED 17, PARTIAL 10, IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED 4, DEFERRED 1.

Còn lại:
- **STD-026:** F-9E-2 (27 `fetch` thô không deadline ở FE), F-9F-1, Gateway 504 không có envelope.
- **STD-030:** Gateway không có tracing bridge (không sinh trace khi client không gửi; log Gateway không có trace id); log ingestion NOT_VERIFIED.
- **STD-028:** full booking→payment→cancel→refund E2E, browser non-intercepted, multi-node; F-9E-5 (`contextLoads` phụ thuộc DB local).

Gate trước AI WRITE còn: STD-030 Gateway bridge, STD-007 crash/multi-node, draft user-bound + xác nhận một lần (phạm vi Agent), F-9F-1 nếu Agent dùng user-service write.
