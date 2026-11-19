# Batch 9C — AI WRITE Pre-gate Verification + F1

Ngày **18/11/2026, Asia/Saigon** (bắt đầu 17/11). **DONE ở service boundary + F1 frontend; Gateway-level smoke NOT_RUN; không production-ready.**

## 1. Executive Summary

- **C1/C2/C3/C4 (WRITE):** thêm `booking-service` `AgentWriteContractTest` (9 test, PASS) — route Agent WRITE yêu cầu user ký, bỏ qua header danh tính giả, chặn CUSTOMER khác và route ngoài phạm vi; create gắn principal, forward nguyên idempotency key; input sai → 400 `fieldErrors`; giá cũ/key reuse/dependency/lỗi bất ngờ có mã ổn định. Booking full suite: 298 reported / 294 PASS / 4 opt-in skipped / 0 fail, BUILD SUCCESS.
- **F1 (frontend source change):** `lib/api/hotels.ts` không còn `fetch` không deadline hoặc lỗi chứa `statusText`; thêm `tests/hotel-adapter-deadline.test.mjs` (4). Frontend: Node regression 218/218, `tsc` PASS, production Webpack build PASS.
- **Gateway-level smoke: DONE trong addendum 18/11 (mục 10) trên stack canonical disposable mới, 87/87.** Ban đầu dừng có chủ đích: Volume Test `checkinn-test_postgres-test-data` là clone DB developer cũ (trước UUID) (volume tạo 27/10/2026, trước UUID/canonical 13/11) và profile `docker` dùng `ddl-auto: update`; khởi động source hiện tại trên đó vi phạm AGENTS.md. Không container/volume nào bị tạo hoặc khởi động.
- **C5 finding:** Booking tạo `RestTemplate` bằng `new RestTemplate(factory)` (không qua `RestTemplateBuilder`), nên lời gọi REST Booking→Hotel (`HotelCatalogClient`, `HotelAvailabilityClient`, `BookingAccess`) không được Micrometer instrument → trace ID nhiều khả năng không đi tiếp. Source-confirmed; runtime NOT_VERIFIED; không sửa (cần duyệt).
- Status/counts không đổi: parent 17/12/3; in-scope VERIFIED 17, PARTIAL 11, IN_SCOPE_DONE/PLACE_SCOPE_EXCLUDED 3, DEFERRED 1.

## 2. Git Preflight

Backend `feat/inventory-approval-read-batching` @ `96bfc4f`; frontend `fix/inventory-approval-workflow` @ `6942747` (không đổi). Backend có sẵn thay đổi chưa commit của 9A/9B + hai IDE edit; frontend sạch trước 9C. Không stage/commit/push/reset/restore/checkout/clean/stash.

## 3. Coverage map

Phần lớn semantics WRITE đã có test service-level; 9C không nhân bản mà ghim phần Agent quan sát qua HTTP.

| Gate | Đã có trước 9C (BookingServiceTest/BookingIdempotencyTest/BookingAuthorizationTest) | Thêm trong 9C |
|---|---|---|
| C1 | 48 authorization cases (đọc/hủy chéo, impersonate create, refund roles, signed callback) | Ma trận gom theo route Agent: 7 route × anonymous 401 envelope + header giả; 6 route × CUSTOMER khác 403; 10 route ngoài phạm vi × CUSTOMER 403 (gồm `manual-refunds`, `confirm`, `hotel/{id}`) |
| C2 | Service: empty items, quantity ≤ 0, ngày lệch, giá âm, giá đổi, room khác hotel, capacity, voucher, contact | HTTP: `{`, `{}`, adults 1.5 (strict JSON), UUID sai, email sai, quantity 0 → 400 `fieldErrors` qua `InputValidation` thật |
| C3 | ApiErrorContractTest (500 sanitized, 405) | Envelope cho 400 giá cũ, 409 key reuse, 409 business, 503 `DEPENDENCY_UNAVAILABLE`, 500 không lộ host; cancel 204/409; VNPay URL chỉ owner, bookingId sai 400 |
| C4 | `acceptedBookingRetryDoesNotHoldInventoryOrRepriceAgain`, `idempotencyKeyCannotBeReusedForChangedInput`, `holdLookupTimeoutIsUnavailableWithoutCancellingOrPublishing`, `databaseFailureAfterHoldTriggersInventoryCompensation`, `reserveUnknownOutcomeReleasesInventoryAndRequestsPointCompensation` | HTTP: hai lần POST cùng key → cùng `id`, principal ghi đè `userId`, key forward nguyên vẹn |
| C5 | — | Source finding (mục 6) |

## 4. Changes

| File | Loại | Nội dung |
|---|---|---|
| `checkinn/booking-service/src/test/java/com/example/bookingservice/booking/controller/AgentWriteContractTest.java` | Test mới | 9 test, `@WebMvcTest` với `SecurityConfig`, `JwtFilter`, `BookingAccess`, `CancellationPolicy`, `ApiJsonConfig` thật |
| `checkinn-frontend/lib/api/hotels.ts` | **Source (F1)** | `hotelRequest`/`hotelJson`/`jsonHeaders`; reads → `readJson` (10 s); writers → `bookingFetch` (15 s, deadline gồm body) + `responseError`; một attempt, không retry; submit-approval fallback chỉ khi 404; previews giữ message có status; bỏ `extractErrorMessage` |
| `checkinn-frontend/tests/hotel-adapter-deadline.test.mjs` | Test mới | 4 test: signal/timeout/typed codes; 8 writer × (409 envelope, AbortError → TIMEOUT không replay, 500 HTML không lộ); fallback 404-only; approve giữ CONFLICT/FORBIDDEN |
| `checkinn-frontend/package.json` | Script | Thêm test mới vào `test:regression` |

Lần chạy đầu `AgentWriteContractTest` có 1 FAIL (500 thay vì 400) do slice mock `BookingService` mà production gọi `InputValidation.require` ở dòng đầu; test được sửa để mock chạy validator thật và import `ApiJsonConfig` — không nới assertion. Lần đầu regression frontend có 2 FAIL ở `hotel-publication.test.mjs` vì preview message mất status; source được sửa để giữ message cũ (test không đổi).

## 5. Verification Commands

| Command | Kết quả |
|---|---|
| `mvn -o -B -ntp -pl booking-service '-Dtest=AgentWriteContractTest' …` | 9/9 PASS |
| `mvn -o -B -ntp -pl booking-service '-Dtest=*,!*ApplicationTests' …` (opt-in flags false) | 298 reported / 294 PASS / 4 opt-in skipped / 0 fail, BUILD SUCCESS |
| `node --experimental-strip-types --test tests/hotel-adapter-deadline.test.mjs` | 4/4 PASS |
| `npm run test:regression` | 218/218 PASS |
| `npx tsc --noEmit --incremental false` | PASS |
| `npx eslint lib/api/hotels.ts` | 14 `no-explicit-any` errors — giống hệt HEAD; 1 warning cũ (`_` unused) đã hết |
| `npm run build -- --webpack` | PASS (compiled 19.4 s) |

**NOT_RUN:** Gateway READ/WRITE smoke, runtime fault injection (timeout thật), runtime trace propagation, hosted/provider. Hotel suite không chạy lại (không đổi hotel source/test trong 9C; 9B: 447/413/34/0).

## 6. Findings

| # | Finding | Mức | Đề xuất |
|---|---|---|---|
| F-9C-1 | Volume Test DB là legacy clone + `ddl-auto: update` → không thể dùng cho Gateway smoke an toàn | Môi trường | Tạo stack canonical disposable mới (project/volume riêng, V1 qua runner guarded, profile `canonical`, ADMIN test row trong DB disposable) |
| F-9C-2 | Booking `RestClientConfig` không dùng `RestTemplateBuilder` → REST Booking→Hotel không có observation/trace | STD-030, trước AI WRITE | Fix nhỏ: inject `RestTemplateBuilder` (giữ `@LoadBalanced`, timeouts), thêm test chứng minh header `traceparent`; cần duyệt |
| F-9C-3 | `DELETE /api/bookings/{id}` khi service ném `IllegalArgumentException` trả 400 **không body** (không có envelope/requestId) | STD-026, thấp | Agent coi 400 rỗng là "không hủy được, đọc lại trạng thái"; chuẩn hóa body trong batch sửa lỗi riêng |
| F-9C-4 | `POST /api/payments/vnpay/create` bookingId sai → 400 qua advice (có envelope) nhưng `IllegalArgumentException` từ service → 400 không body | STD-026, thấp | Như trên |
| F-9C-5 | Submit-approval fallback sang route trực tiếp `/hotels/...` khi Gateway 404 (giữ hành vi cũ, chỉ 404) | Frontend, thấp | Xem xét bỏ fallback khi Gateway route ổn định |

## 7. AI WRITE readiness sau 9C

| Capability | Trước 9C | Sau 9C |
|---|---|---|
| Tạo booking unpaid sau xác nhận | READY_WITH_CONSTRAINTS (thiếu C2/C4 HTTP) | READY_WITH_CONSTRAINTS ở service boundary; còn Gateway smoke + trace |
| Đọc trạng thái booking/payment | READY | READY |
| Hủy booking unpaid | READY_WITH_CONSTRAINTS | READY_WITH_CONSTRAINTS (lưu ý F-9C-3) |
| Trao URL VNPay Sandbox | READY_WITH_CONSTRAINTS | Không đổi; Agent không nhập thẻ/OTP |
| Refund execution | NOT ALLOWED | NOT ALLOWED (verified 403 cho CUSTOMER) |
| Correlation/error recovery | NOT_VERIFIED | NOT_VERIFIED — F-9C-2 là nguyên nhân source-level |

## 8. Next

Cần quyết định: (a) xây stack canonical disposable cho Gateway smoke (HIGH, test-only harness + Docker local), (b) fix F-9C-2 (LOW, source booking-service + test). Sau đó Batch 10 (Agent READ MVP) có thể bắt đầu song song vì READ không phụ thuộc hai việc này.

## 9. Final Status

**Batch 9C: DONE (service boundary + F1 + Gateway smoke trên stack canonical disposable).** PLAN §0/§79, AUDIT §0/checkpoint 9C cập nhật từ cùng dữ liệu. Standardization tổng thể PARTIAL; không production-ready.

## 10. Addendum (18/11) — canonical disposable Gateway smoke

Người dùng chọn xây stack canonical mới. Thêm harness test-only: `checkinn/docker/canonical-smoke.py` (prepare/package/build/infra/migrate/apps/smoke/down, guards: loopback ports trống, project label sở hữu, DB fresh rỗng, không đọc `.env`), `checkinn/docker/docker-compose.canonical-smoke.yml` (tmpfs, không named volume), `checkinn/docker/CanonicalSmokeMigrate.java` (Flyway 10.22, cùng cấu hình canonical acceptance test, grants DML-only). Image = `checkinn/<module>:local` + jar đóng gói từ source hiện tại, không pull image.

| Bước | Kết quả |
|---|---|
| V1 migrate | user 7 / hotel 24 / booking 7 / revenue 5 / regulations 3 bảng = 46; 1 V1 history row mỗi owner; rerun 0 |
| Startup | 13/13 container healthy lần đầu, profile `canonical` (`validate`) |
| Smoke run 1 | 82 PASS / 5 FAIL — lỗi định nghĩa check (counter theo đêm, `redirectUrl`, cancel idempotent); giữ `results-run1.json` |
| Smoke run 2 | Dừng: tên city fixture trùng run 1 (suffix dùng chung) |
| Smoke run 3 | **87 PASS / 0 FAIL / 1 observation** |
| Cleanup | 0 container/network/volume của project; stack khác không đổi |

Hai bước SQL có nhãn trong DB disposable: nâng một tài khoản synthetic mới lên ADMIN; publish hotel DRAFT→APPROVED (không có Storage/document workflow). Tất cả bước khác qua Gateway thật với JWT thật và OTP từ Mailpit local.

Kết quả quan trọng: lost response + retry cùng key → 1 booking/1 hold; cancel lặp idempotent 204; F-9C-2 runtime-confirmed (Hotel log dùng trace mới, trace client xuất hiện 0 lần; Booking 8 lần; error `requestId` = trace). Chi tiết: [evidence](evidence/batch-9c-canonical-smoke/README.md).
