# Batch 9H — F-9G-1 (CORS) và phần error contract còn lại của STD-026 (21/11/2026)

Phạm vi: F-9G-1 và mọi phần không thuộc Place còn lại của STD-026 (error envelope thống nhất). Không đổi route, status thành công, schema, proto, event; không Place; không hosted/provider. Thay đổi 9F–9H **chưa commit**.

## 1. Kết quả

| Mục | Trạng thái | Thay đổi | Bằng chứng |
|---|---|---|---|
| **F-9G-1** booking/regulations CORS khác Gateway | **FIXED_VERIFIED** | `booking-service` `SecurityConfig` + `WebConfig` và `regulations-service` `SecurityConfig`: allow-list đọc từ `cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`), mặc định `http://localhost:3000,http://127.0.0.1:3000,http://localhost:3001` (hợp của danh sách cũ và Gateway); filter chain dùng bean qua `Customizer.withDefaults()` | `AgentWriteContractTest`, `RegulationBoundaryTest`: hai origin của Gateway pass (GET + preflight), origin lạ 403; **negative control** với danh sách cũ → 403; runtime: Booking qua Gateway trả `Access-Control-Allow-Origin` cho cả hai origin, origin lạ 403; **browser không intercept chạy ở `127.0.0.1:3000` PASS** (9G: 2/3) |
| **Envelope thống nhất** | **FIXED_VERIFIED** | `ApiErrorSupport` (cùng hợp đồng Booking/Hotel) thêm vào auth/user/revenue/regulations: `{timestamp,status,error,code,message,path,requestId}` + `X-Request-ID` (= trace id). auth: global handler + `failure()` (giữ `success:false`); user: mọi handler advice + 8 body lỗi ở `UserController` (giữ nhãn `error` và message cũ); revenue: giữ code/message cũ (`BAD_REQUEST`…, `REPORT_RECONCILIATION_REQUIRED`); regulations: giữ code/message, 405 giữ cả `Allow` và `X-Request-ID` | `ErrorEnvelopeContractTest` (auth, user, revenue), `RegulationBoundaryTest#errorsUseTheStandardEnvelopeWithMatchingRequestId`; toàn bộ test cũ khẳng định code/message vẫn PASS |
| **Lỗi không có body** | **FIXED_VERIFIED** | 18 chỗ `ResponseEntity.notFound()/badRequest()/status(...).build()` ở Booking (`getBooking`, VNPay return, `getPayment*`), Hotel (review lookup, 4 hold endpoint), Regulations (`getRegulation`) → envelope với message cố định, **status giữ nguyên**. Booking/Hotel `ApiErrorSupport.respond(status,message)` | grep: 0 chỗ còn lại trong 6 service; Booking client của Hotel và FE xử lý theo status nên không đổi hành vi |
| **Envelope qua Gateway (runtime)** | **VERIFIED** | Smoke sweep: một lỗi mỗi service | auth (sai mật khẩu), user (route admin với CUSTOMER), revenue (ngày đảo), regulations (key lạ 404), booking (payment lạ), hotel (hotel lạ): đủ 7 trường, `X-Request-ID` = `requestId`, không chuỗi `Exception` |

**Sửa test-order coupling (đính chính 9G):** `auth-service` `UserRpcClientTest` không phải flaky do tải như báo cáo 9G nói. Từ khi F-9E-5 cho `AuthServiceApplicationTests` khởi động được, context Spring của grpc-spring-boot-starter chạy trước trong cùng JVM làm discovery resolver thành scheme mặc định, nên `forAddress("127.0.0.1", port)` bị hiểu là tên service ("No servers found"). Sửa test: target `dns:///127.0.0.1:port`. auth 50/50 trong cùng JVM.

## 2. Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Backend 12 module (không Place) | **1084 run / 0 fail / 0 error** sau hai sửa (regulations compile generic `Optional.map`, test auth); skipped 51 (opt-in) |
| Từng module | user 122, auth 50, hotel 452, media 31, booking 307, revenue 57, regulations 43, notification 14, gateway 5, eureka/config/admin 1 |
| FE regression / tsc | 222/222 / PASS (không đổi code app FE; chỉ `playwright.live.config.ts`) |
| Canonical smoke (stack mới từ source, live browser ở `127.0.0.1:3000`) | **144 PASS / 0 FAIL** (`results-9i.json`), down: 0 container/network/volume |

Multi-node (run B của 9G, 138/0) không chạy lại: 9H không đổi đường booking/hold/idempotency.

## 3. Trạng thái

STD-026: in-scope **PARTIAL → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED** (chỉ còn Place SDK); parent giữ IN_PROGRESS theo quy tắc Place. Counts parent không đổi: **FIXED_VERIFIED 18 · IN_PROGRESS 11 · FIXED_UNVERIFIED 3**. In-scope: **VERIFIED 18 · PARTIAL 7 · IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED 6 · DEFERRED 1**.

Ghi chú phạm vi: media-service và notification-service không có route REST qua Gateway nên không thuộc client error contract; `@CrossOrigin("*")` ở một số controller (không credentials) giữ nguyên — CORS có hiệu lực do Gateway và filter bảo mật từng service quyết định.
