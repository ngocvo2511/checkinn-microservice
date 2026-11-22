# Batch 9G — Remaining core gaps: STD-026 / STD-030 / STD-028 (21/11/2026)

Phạm vi theo yêu cầu: STD-026 (F-9E-2, F-9F-1, Gateway 504 envelope), STD-030 (tracing bridge ở Gateway, trace id trong log Gateway, log ingestion), STD-028 (E2E booking→payment→cancel→refund, browser không intercept, multi-node, F-9E-5). Không Place, không schema/migration/proto/event, không hosted/provider. Nền: backend `feat/standardization-hardening` @ `8e8bb4d`, frontend `fix/hotel-adapter-deadlines` @ `730e4a0`; thay đổi 9F và 9G **chưa commit**.

## 1. Kết quả

| Mục | Trạng thái | Thay đổi | Bằng chứng |
|---|---|---|---|
| **F-9F-1** user-service lộ message/sai status | **FIXED_VERIFIED** | `UserBusinessException` (message dành cho người dùng) thay 16 `RuntimeException` nghiệp vụ trong `UserService`/`UserController` — giữ nguyên status (401) và message; `GlobalExceptionHandler`: lỗi khác → **500 `INTERNAL_ERROR`** chung có `requestId` (trước: `RuntimeException`→401, `Exception`→400, kèm nguyên văn), `AccessDeniedException`→403, `SessionStoreUnavailableException`→503, lỗi dạng request (header/param/validation/method/media) → 4xx chung; 4 controller `catch (Exception)` chỉ còn bắt lỗi nghiệp vụ; gRPC chỉ chuyển message của lỗi nghiệp vụ (khác → "Request rejected") | `UserErrorDisclosureTest` (4: REST + gRPC); user-service 121 run/0 fail/5 skipped |
| **Gateway 504/503 envelope** | **FIXED_VERIFIED** | `GatewayErrorHandler` (`@Order(-2)`): lỗi do Gateway tự sinh (timeout, không có instance, kết nối bị từ chối/timeout, đóng sớm, không có route) → `{timestamp,status,error,code,message,path,requestId}` + `X-Request-ID`, không có text exception/địa chỉ nội bộ; lỗi downstream vẫn proxy nguyên | `GatewayObservabilityContractTest` (504 `DEPENDENCY_TIMEOUT` + requestId = trace; 503 `DEPENDENCY_UNAVAILABLE`; 404 `NOT_FOUND`), `GatewayErrorHandlerMappingTest`; runtime: `docker pause hotel-service` → 504 sau 20,1 s, envelope, `X-Request-ID` = `requestId`; `stop regulations-service` → 503 envelope không lộ `:8091` |
| **Gateway tracing bridge + log** | **FIXED_VERIFIED** | `api-gateway/pom.xml` thêm `micrometer-tracing-bridge-brave` (cùng dependency các service đang dùng) → Spring Cloud Gateway observability tiếp nhận/tạo W3C trace và inject `traceparent` xuống service; `GatewayAccessLogFilter` ghi `[GATEWAY_ACCESS] traceId=… METHOD path -> status in N ms` (không query string/header/token); Gateway ghi file log `logs/api-gateway-8080.log` như các service khác | Contract test: trace client được tiếp nối; client không gửi → Gateway tạo trace và downstream nhận `traceparent`; log có trace id, không có e-mail trong query. Runtime: Booking error `requestId` = trace Gateway tạo; access log không có `Bearer`/query |
| **Log ingestion** | **VERIFIED (local canonical)** | Stack smoke chạy Loki 2.9.8 + Promtail 2.9.8 với **chính** `monitoring/loki/local-config.yaml` và `monitoring/promtail/promtail-config.yml`; log file của mọi service mount vào `/logs` | Loki query `{job="spring"} |= "<trace>"` trả dòng từ `api-gateway-8080.log`, `booking-service-8084.log`, `hotel-service-8083.log`; 0 dòng giống JWT (`eyJ`) |
| **F-9E-2** 27 `fetch` không deadline | **FIXED_VERIFIED** | `bookingFetch` (deadline 15 s, lỗi typed, không retry) cho auth/admin/users/cities/profile/my-bookings/newHotel; `AbortSignal.timeout(10000)` cho proxy loyalty (timeout → 504) và geocoder Nominatim | `tests/fetch-deadline-source.test.mjs` (quét source, 0 vi phạm; negative control fail khi bỏ deadline); regression 222/222 |
| **F-9E-5** `contextLoads` phụ thuộc môi trường | **FIXED_VERIFIED** | auth: `SECRET_KEY` synthetic trong test → PASS offline; hotel/user/revenue: `@EnabledIfEnvironmentVariable(CHECKINN_CONTEXT_TEST=true)` (cần PostgreSQL thật) | auth context PASS; 3 context test skip mặc định |
| **E2E booking→payment→cancel→refund** | **VERIFIED (scoped)** | Harness: VNPay return **ký cục bộ** bằng secret synthetic của stack (provider không bị gọi); refund = **Sandbox Internal** (kế toán nội bộ, `REFUND_INTERNAL_ENABLED=true` chỉ trong stack disposable) | create 201 → VNPay URL (amount = 2 đêm × giá × 100) → return sửa amount 400, ký đúng nhưng sai amount 400 → return 00 COMPLETED, replay idempotent → CONFIRMED, booked 1 → Revenue 1 CREDIT → hủy trong hạn 204 CANCELLED + refund request tự tạo → CUSTOMER approve/execute 403 → ADMIN approve `APPROVED_PENDING_EXECUTION` → refund REFUNDED, lặp idempotent → Revenue 1 CREDIT + 1 DEBIT, net 0 → inventory 0/0/0 → `refund_requests` `REFUNDED/SANDBOX_INTERNAL`, `paid_amount` 0 |
| **Browser không intercept** | **VERIFIED (3 luồng)** | `playwright.live.config.ts` + `tests/browser-live/canonical-live.spec.ts` (ngoài `testDir` mặc định): Next dev thật → Gateway thật → service thật, không `page.route` | search hiển thị hotel từ catalog thật; detail chỉ phòng đã duyệt; payment page hiển thị booking thật + chính sách hủy từ API, không "Không hoàn tiền"; access log Gateway tăng ≥5 dòng |
| **Multi-node** | **VERIFIED (2 replica)** | `CANONICAL_SMOKE_SCALE=2`: 2 hotel-service + 2 booking-service (Eureka đăng ký theo IP) | Eureka 2 UP mỗi service; 6 create đồng thời khác key trên phòng stock 2 → đúng 2×201 + 4×409, không oversell (held ≤2); 4 create đồng thời cùng key → 1 booking/1 hold; cả hai replica booking đều phục vụ create |

## 2. Finding mới

- **F-9G-1 (P3, CORS):** Gateway cho phép origin `http://localhost:3000` và `http://127.0.0.1:3000`, nhưng `booking-service` `SecurityConfig` chỉ cho `http://localhost:3000`/`:3001` → trình duyệt mở app ở `127.0.0.1:3000` nhận **403** cho mọi lời gọi Booking (kể cả `cancellation-policy` public). Phát hiện nhờ browser không intercept; chưa sửa (đổi allow-list bảo mật cần quyết định). Live smoke dùng `localhost:3000`.
- **Startup race (harness):** Booking có cache discovery riêng (~35 s); ngay sau khi stack healthy, Booking→Hotel có thể 503 dù Gateway đã thấy Hotel. Harness chờ tới khi một request stale-price trả 400 (không hold). Đây là đặc tính khởi động, không phải lỗi nghiệp vụ; triển khai thật cần readiness tương tự.
- **Đính chính (9H):** `UserRpcClientTest` không phải flaky do tải — là test-order coupling do F-9E-5 (context Spring chạy trước làm discovery resolver thành scheme mặc định); đã sửa test ở [9H](BATCH9H_CORS_AND_ERROR_CONTRACT_2026-11-21.md). Flaky dưới tải thật: `document-boundary` spec 1/4 lỗi timeout 5 s lần đầu dưới tải, rerun 4/4.

## 3. Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Backend `mvn -pl '!place-service' test` (12 module) | **1078 run / 0 fail / 51 skipped** (48 opt-in cũ + 3 context guard mới); 1 error flaky `UserRpcClientTest` → rerun PASS |
| api-gateway (lần đầu có test) | 5/5 (`GatewayObservabilityContractTest` 4, `GatewayErrorHandlerMappingTest` 1) |
| FE `tsc` / regression / build | PASS / **222/222** / PASS |
| FE `npm run test:browser` | 105/105 |
| FE area configs | amenities 7, content 8, documents 4 (rerun), media-write 13, regulations 3, revenue 6, reviews 4 |
| FE ESLint file đổi | như HEAD (`newHotel` 4 `no-explicit-any` + 6 `no-unescaped-entities`, `admin.ts` 1); file mới 0 |
| Canonical stack **run A** (1 replica, live browser) | **135 PASS / 0 FAIL** (`results-9h.json`) |
| Canonical stack **run B** (2 replica) | **138 PASS / 0 FAIL** (`results-9h-multinode.json`) |
| Cleanup | mỗi run `down`: 0 container/network/volume |

Lần chạy trước khi xanh: (1) Loki không ghi được tmpfs root → đặt owner uid 10001; (2) run A đầu: Booking→Hotel 503 do race khởi động → thêm chờ; (3) live browser 2/3 do F-9G-1 → dùng `localhost:3000`; (4) lỗi harness (trùng tên biến/khóa JSON) → sửa, rerun. Một lần debug 132/0 trên stack đã ấm không được giữ làm evidence.

## 4. Trạng thái sau 9G

| Issue | Trước | Sau | Lý do |
|---|---|---|---|
| STD-028 | IN_PROGRESS / PARTIAL | **FIXED_VERIFIED / VERIFIED** | Acceptance gốc "thiếu bằng chứng security/DB/API contract end-to-end": nay có contract suites + native DB + Gateway E2E READ/WRITE/paid lifecycle/outage/multi-node + browser không intercept trên stack canonical, cộng hosted 8B–8D. Phạm vi ghi rõ: refund Sandbox Internal, VNPay return ký cục bộ, stack local |
| STD-030 | IN_PROGRESS / PARTIAL | IN_PROGRESS / **IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED** | Runtime log path (Promtail→Loki), trace xuyên Gateway→Booking→Hotel, trace id trong log Gateway đã chứng minh; phần còn lại là "place type blocker" (Place) |
| STD-026 | IN_PROGRESS / PARTIAL | IN_PROGRESS / PARTIAL | F-9E-2, F-9F-1, Gateway envelope xong; còn: body lỗi nghiệp vụ của user-service vẫn dạng legacy (không `code`/`requestId`); Place SDK EXCLUDED |

Counts: **FIXED_VERIFIED 18 · IN_PROGRESS 11 · FIXED_UNVERIFIED 3**; open P0 3/7, P1 6/17, P2 4/7, P3 1/1. In-scope: VERIFIED 18 · PARTIAL 8 · IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED 5 · DEFERRED 1.

Không phải production-ready: provider refund/QueryDR/IPN vẫn DEFERRED (STD-002), deploy cloud + log pipeline hosted (STD-027), crash/restart (STD-007).

## 5. Chạy lại

Từ `checkinn/`: `python -X utf8 docker/canonical-smoke.py prepare|package|build|infra|migrate|apps|smoke <out.json>|down`. `CANONICAL_SMOKE_LIVE=1` (smoke) bật browser không intercept (cần Edge, cổng 3000 trống); `CANONICAL_SMOKE_SCALE=2` (apps **và** smoke) chạy 2 replica. Context test DB: `CHECKINN_CONTEXT_TEST=true` (chỉ với PostgreSQL local dùng cho dev, không hosted).
