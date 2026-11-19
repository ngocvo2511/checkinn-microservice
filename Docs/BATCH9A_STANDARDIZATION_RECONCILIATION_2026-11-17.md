# Batch 9A — Standardization Documentation Reconciliation & Current-State Audit

Ngày **17/11/2026, Asia/Saigon**. Loại batch: documentation reconciliation + read-only verification. **Không sửa application source, database, provider hoặc runtime configuration.**

## 1. Executive Summary

- Đã đọc hai tài liệu canonical (`Docs/standardization/PROJECT_STANDARDIZATION_PLAN.md`, 3891 dòng; `PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md`, 537 dòng), inventory 36 báo cáo batch/PR review (đọc đầy đủ 7B, 8D, VNPay 17/11, search-navigation, PR review 17/11; các report còn lại đọc phần status/kết luận và đối chiếu với checkpoint AUDIT) và kiểm tra source hiện tại cho STD-001…STD-032.
- **Current parent status (32 issue):** FIXED_VERIFIED **17**, IN_PROGRESS **12**, FIXED_UNVERIFIED **3**, NEEDS_ENVIRONMENT **0** (trước 9A: 16/12/3/1). Open (≠ FIXED_VERIFIED): P0 3/7, P1 7/17, P2 4/7, P3 1/1 — tổng 15.
- **In-scope status (không gồm Place):** VERIFIED 17, PARTIAL 9, IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED 3, NOT_VERIFIED 2, DEFERRED 1, BLOCKED 0.
- **Status thay đổi:** STD-006 IN_PROGRESS → FIXED_VERIFIED (scope: canonical admission; legacy provenance DEFERRED); STD-027 NEEDS_ENVIRONMENT → IN_PROGRESS (local scope có evidence; log ingestion/cloud còn thiếu). STD-011/023/032 tách scope → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED (parent giữ IN_PROGRESS).
- **Verification thực chạy 9A:** backend 8 module 1021 reported / **973 PASS** / 48 opt-in skipped / 0 fail; reactor compile 13/13 SUCCESS (gồm Place, không diagnostic); frontend regression **214/214**, `tsc` PASS, production Webpack build PASS; ESLint FAIL (baseline 133 errors/152 warnings, không phải acceptance gate hiện hành).
- **Mâu thuẫn tài liệu đã xử lý:** executive summary/status counts AUDIT (13/11), blocker TypeScript `showDistance` đã hết, checklist VNPay/Storage PLAN `[x]` vs AUDIT `[ ]`, "Checkpoint hiện hành Batch 2" trong PLAN, G0/G2 "NEXT" cũ, H readiness cũ. Tất cả được đánh dấu `HISTORICAL — SUPERSEDED BY BATCH 9A`, không xóa.
- **AI READ:** không có code-fix blocker; READY_WITH_CONSTRAINTS sau verification gates C1–C3. **AI WRITE:** không có code-fix blocker cho "tạo booking unpaid sau xác nhận"; cần C2–C5 và cơ chế xác nhận phía Agent. Payment chỉ trao URL Sandbox; refund bị loại khỏi Agent.
- **Đề xuất tiếp theo:** Batch 9B — AI READ contract verification (LOW–MEDIUM). Không production-ready; không tuyên bố toàn dự án DONE.

## 2. Git Preflight

| Repo | Branch | HEAD | Staged | Unstaged | Untracked |
|---|---|---|---|---|---|
| `checkinn` | `feat/inventory-approval-read-batching` | `96bfc4f2e1864ad3dc44743999bfd267152143ac` (17/11 22:38, inventory approval + batched reads) | 0 | `.idea/compiler.xml`, `.idea/encodings.xml` (có từ trước) | 0 |
| `checkinn-frontend` | `fix/inventory-approval-workflow` | `6942747ac63840550adf05a09cf52d157bfc3653` | 0 | 0 | 0 |

PLAN/AUDIT không có thay đổi chưa commit lúc bắt đầu (commit gần nhất chạm tới chúng: `96bfc4f`). Root `DA1/PROJECT_STANDARDIZATION_*.md` là pointer 5 dòng tới bản canonical (Batch 2), không phải bản sao độc lập — không sửa. Không reset/restore/checkout/clean/stash/add/commit/push, không đổi branch. Sau verification `git status` frontend vẫn sạch; backend chỉ còn hai IDE edit cũ (ngoài ba file Markdown của batch này).

## 3. Documents Reviewed

- PLAN: header checkpoints, §1–§13 (inventory, acceptance từng STD, dependency map, roadmap, gates, Definition of Ready), nhật ký §14–§76 (đọc §58/§65/§74–76 chi tiết; §14–§73 đối chiếu qua status headings và acceptance), checkpoint Batch 4→8D.
- AUDIT: toàn bộ §A–§H, appendix 12/11 và checkpoints Batch 3→8D.
- Đối chiếu: phần đuôi PLAN (Batch 4→8D) trùng nội dung AUDIT ngoại trừ checklist provider (PLAN `[x]`, AUDIT `[ ]`) — đã đồng bộ.

**Mâu thuẫn giữa phần đầu và phần cuối tài liệu (trước 9A):**

| Vị trí | Nội dung cũ | Thực tế hiện tại |
|---|---|---|
| AUDIT §A, §C, §D | 16/12/3/1; STD-006 IN_PROGRESS; "nearby `showDistance` ngăn typecheck sạch" | `tsc` + build PASS (5B, PR review 17/11, 9A); STD-006 có mandatory approval (7B) + hosted E2E (8B/8D) |
| AUDIT §G0/§G2, PLAN header | "G2 là NEXT duy nhất" | G2 đã chạy (Batch 3/7, PARTIAL); 4/4.1/5A/5B/6A/6B/7/7B/8–8D đã diễn ra |
| AUDIT §H, PLAN §8/§13 | Readiness theo trạng thái 13/11 | Thay bằng ma trận 9A (mục 11–12) |
| AUDIT checklist 6B | VNPay/Storage `[ ]` BLOCKED | Đóng 17/11 (VNPay Sandbox live, Storage upload/private signing) |
| PLAN header dòng 12/16 | "cập nhật hiện tại 13/11", "Checkpoint hiện hành Batch 2" | Lịch sử; current = Batch 9A |
| PLAN §4 cột "Xác minh" | Trạng thái khảo sát gốc | Giữ nguyên làm lịch sử; current ở bảng 9A |

## 4. Evidence Inventory

| Batch | Status (theo report) | Scope nghiệm thu | Evidence loại | Không được suy diễn |
|---|---|---|---|---|
| 1–1.6 | DONE — Refund Backend/API Demo | Sandbox Internal refund qua Gateway/PostgreSQL/RabbitMQ/Revenue | Disposable local | Không phải provider refund/money movement |
| 1.7 | DONE (docs) | Đồng bộ audit | Docs | Không test mới |
| 2 | DONE (scoped) | Unpaid Booking–Hotel Gateway lifecycle, 57 assertions | Disposable local | Không paid/provider/multi-node |
| 3 | Audit DONE / DB PARTIAL | Schema/history inventory | Disposable | Không legacy upgrade certification |
| 4 / 4.1 | Design DONE | Canonical schema, UUID, 5 Supabase projects | Static | Không migration |
| 5A | PARTIAL | UUID contracts source | Local | — |
| 5B | DONE local canonical | 5 V1 chains, Hibernate 5/5, showDistance fix tối thiểu | Local PostgreSQL | Không hosted |
| 6A | Design/audit DONE | Supabase readiness | Static | — |
| 6B | DONE local development + VNPay Sandbox/Storage (17/11) | Hosted V1 5/5, 117 workflow checks, NextAuth, VNPay NCB/OTP live, Storage live | Hosted dev + provider sandbox | Không IPN, provider refund, production, cloud deploy |
| 7 | PARTIAL | G2 provenance fixes, zero drift | Local native | Physical stock/historical corpus NOT VERIFIED |
| 7A | Không có report riêng | Chỉ được nhắc trong 7B/AUDIT | — | Không giả định nội dung |
| 7B | LOCAL DONE / HOSTED NOT VERIFIED (tại thời điểm) | Mandatory inventory approval | Local native; hosted về sau ở 8B/8D | — |
| 8 | PARTIAL | Hosted read benchmark; booking E2E BLOCKED (thiếu ADMIN) | Hosted dev | — |
| 8B | PARTIAL | Hosted OWNER→ADMIN→CUSTOMER E2E, read batching | Hosted dev | Không perf AFTER |
| 8C | PARTIAL | Runtime stability, race/expiry PASS | Hosted dev | Không controlled BEFORE |
| 8D | DONE (scoped) | Read amplification 96→12 SQL, E2E PASS | Hosted dev + native | Không production-ready, không SLO |
| PR review 17/11 | Review | Hotel 401/Booking 285 PASS, FE 214/tsc/build PASS | Local | Không hosted rerun |

Không có test SKIPPED nào được tính là PASS trong bảng trên hoặc trong 9A.

## 5. Source Verification

Các kiểm tra source thực hiện trong 9A (đọc, không sửa):

- **Gateway** `api-gateway/src/main/resources/application.yml`: routes khớp CLAUDE.md; không cấu hình response timeout; `api-gateway/pom.xml` không có tracing dependency; `api-gateway` không có `src/test`.
- **Hotel `SecurityConfig`**: `/internal/hotels/**` chỉ `booking-service` JWT, `/internal/reports/**` chỉ `revenue-service`, `/admin/**` ADMIN, hold endpoints chỉ booking service, preview/write authenticated; GET còn lại `permitAll` (public read, publication enforced trong service).
- **Booking/Payment controllers**: mọi route có `@PreAuthorize` qua `BookingAccess`; `/api/bookings/count/**` ADMIN trong `SecurityConfig`; refund approve/execute/manual ADMIN; VNPay return public có HMAC; `0` class `implements RefundProvider`.
- **Booking pricing**: `BookingService` từ chối `unitPrice ≠ basePrice` ("Room price has changed; refresh the offer before booking").
- **Profiles**: `canonical`/`supabase-dev` dùng `ddl-auto: validate` cho 5 owner; `application.yml` mặc định hotel/booking/revenue/regulations vẫn `update` và Test-mode compose dùng profile `docker` kế thừa.
- **Frontend adapters**: search/booking dùng `readJson` (10 s) / `bookingFetch` (15 s); `hotelsApi.getHotelById` và owner/admin writers trong `lib/api/hotels.ts` dùng `fetch` không deadline, lỗi chứa `statusText`. Không tồn tại Refund client/UI (`refund-request`, `manual-refunds` không xuất hiện trong `app/components/lib`).
- **UI facts (STD-012)**: `HotelCard` chỉ hiện discount khi có `originalPrice`; search mapping không gán field này; `app/page.tsx` có tag marketing tĩnh (không phải fact khách sạn).
- **Place**: không audit logic. Chỉ ghi nhận compiler/typecheck: không có lỗi.

## 6. Verification Commands

| Command (thư mục) | Kết quả | Ghi chú |
|---|---|---|
| `mvn -o -B -ntp -fae -pl auth-service,user-service,hotel-service,booking-service,revenue-service,regulations-service,notification-service,media-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` với `CHECKINN_RANGE_TEST/UUID/SESSION/REFUND=false`, `BATCH5B_ACCEPTANCE=false` (`checkinn`) | **BUILD SUCCESS**, 5:30 min | user 114/4 skip, auth 47/0, hotel 435/34, media 30/0, booking 289/4, revenue 53/4, notification 13/0, regulations 40/2 → **1021 reported, 973 PASS, 48 skipped, 0 failure/error** |
| `mvn -o -B -ntp -fae compile` (`checkinn`, 13 module) | **SUCCESS 13/13** | Gồm place-service, api-gateway, eureka, config, admin; không diagnostic Place |
| `npm run test:regression` (`checkinn-frontend`) | **214 PASS**, 0 fail/skip | Bao gồm `tests/place-card-distance.test.mjs` (Place, chỉ ghi nhận đã chạy, không audit) |
| `npx tsc --noEmit --incremental false` | **PASS** exit 0 | Blocker `showDistance` lịch sử không còn |
| `npm run build -- --webpack` | **PASS** (Next.js 16.1.1, compiled 11.9 s) | Docker build dùng Webpack; `git status` sạch trước/sau |
| `npm run lint` | **FAIL** exit 1: 133 errors / 152 warnings | 112 `no-explicit-any`, 15 `no-unescaped-entities`, 4 `set-state-in-effect`; file Place: `PlaceReviewsModal.tsx` 1 error/6 warnings, `NearbyPlacesSection.tsx` 0/7. Debt có từ trước (PR review 17/11 ghi baseline giống origin/main cho file liên quan); toàn repo chưa từng được claim lint sạch |

**NOT_RUN (có lý do):** opt-in PostgreSQL/Redis suites (cần owned DB/flags), `*ApplicationTests` (cần services), Playwright browser suites (khởi động server; intercepted, không full-stack), native harness `tools/batch*-native.py` (tạo cluster local — không cần cho reconciliation), mọi hosted/provider/Supabase check, `docker/stack.ps1` (không khởi động runtime chỉ để audit). Generated `target/`/`.next` được ghi lại bởi các lệnh trên; `hotel-service/target/*.jar` (artifact 8D) không bị repackage vì chỉ chạy `test`/`compile`.

**GLOBAL_BUILD_BLOCKED_BY_EXTERNAL_PLACE_SCOPE:** không áp dụng — build chung PASS.

## 7. Master Issue Matrix

Status taxonomy: parent status giữ nhãn AUDIT (FIXED_VERIFIED = DONE trong acceptance scope; IN_PROGRESS = PARTIAL; FIXED_UNVERIFIED = implementation có, acceptance chưa chứng minh). In-scope = phạm vi không gồm Place. `IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED` không đóng toàn issue.

| Issue | Pri | Original acceptance | Historical | Current implementation | Latest evidence | Current | In-scope | Remaining work | AI | Rec. |
|---|---|---|---|---|---|---|---|---|---|---|
| STD-001 Booking resource authorization | P0 | Không đọc/ghi chéo; create không nhận chủ thể tùy ý; 401/403/404 theo policy; trạng thái trái phép bị chặn | FIXED_VERIFIED | `BookingAccess` + `@PreAuthorize` mọi route booking/payment; `/api/bookings/count/**` ADMIN; gRPC JWT interceptor | BookingAuthorizationTest/GrpcBookingAuthorizationTest (9A rerun PASS); hosted Gateway authz 8B/8D (forged header/cross-owner 403) | **FIXED_VERIFIED** | VERIFIED | External RPC caller inventory NOT_VERIFIED (G4); không phải code fix | WRITE | VERIFY (preserve) |
| STD-002 Payment trust boundary / refund | P0 | Endpoint nhạy cảm không chạy bằng ID/header tùy ý; callback hợp lệ vẫn hoạt động; route discovery không bypass | IN_PROGRESS | Payment create/read/refund guards; HMAC signed return; ADMIN refund/manual-refund; `RefundProvider` interface không có implementation | Batch 1.6 internal refund demo; [6B VNPay Sandbox 17/11](BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md) return00/HMAC/replay/amount-mismatch 400; 9A grep: 0 class `implements RefundProvider` | **IN_PROGRESS** | PARTIAL | Provider refund/QueryDR/IPN reconciliation NOT IMPLEMENTED → DEFERRED (G1b). Sub-scope live Sandbox checkout nay VERIFIED | WRITE | DEFER provider refund; Agent không thực thi payment/refund |
| STD-003 Hotel/catalog/media/review authorization | P0 | Không mutation công bố dựa chỉ vào header client; 401/403 nhất quán | FIXED_UNVERIFIED | Hotel `SecurityConfig`: `/internal/**` chỉ service JWT, `/admin/**` ADMIN, preview/write authenticated + ownership trong service; GET fallback `permitAll` cho public read | Catalog/Media/Review authorization tests (9A rerun PASS); Storage live upload/private signing (6B); hosted OWNER cross-hotel/forged X-Is-Admin 403 (8B/8D) | **FIXED_UNVERIFIED** | NOT_VERIFIED | Consolidated route-by-route REST/RPC actor matrix chưa có; public-read subset cần cho AI READ | BOTH | VERIFY |
| STD-004 Loyalty / reports authorization | P0 | Không điều khiển điểm bằng request tùy ý; reports giới hạn đúng actor | FIXED_VERIFIED | Loyalty requireService/requireRead; report actor↔hotel binding | LoyaltyAuthorizationTest/ReportAuthorizationTest (9A rerun PASS); historical native | **FIXED_VERIFIED** | VERIFIED | Caller/deployment recheck (G4) | NONE (WRITE nếu Agent dùng điểm) | — |
| STD-005 Server pricing & relationships | P0 | Server quyết định giá/quan hệ; không tạo hold/booking cho request không hợp lệ; snapshot server xác minh | FIXED_VERIFIED | Catalog room/hotel/capacity/basePrice check trước hold; client `unitPrice` khác basePrice → 400 "Room price has changed" (`BookingService` ~L325) | [Batch 2](BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); hosted booking 201 server price 8B/8D; VNPay signed amount mismatch 400 (6B) | **FIXED_VERIFIED** | VERIFIED | Không hỗ trợ rate plan/tax/partial payment — không được ngụ ý | WRITE | — |
| STD-006 Inventory không fallback 10 / mandatory approval | P0 | Không có con đường coi inventory thiếu là tồn thật; phòng lỗi được báo cấu hình thay vì bán | IN_PROGRESS | Canonical V1 nullable stock/no default10 (5B); Batch 7B effective-approval predicate sau room lock; unapproved → 409 `INVENTORY_APPROVAL_REQUIRED`, loại khỏi search/catalog/lowestPrice | [7B](BATCH7B_MANDATORY_INVENTORY_APPROVAL_2026-11-16.md) native race/contract; hosted 8B/8D: room PENDING 409 + không hold/counter, không xuất hiện trong search; sau ADMIN approve available=2 | **FIXED_VERIFIED** | VERIFIED | Legacy physical-stock provenance → DEFERRED (canonical DB fresh, không import legacy theo U03). Physical count thật thuộc OWNER/ADMIN workflow, không phải code | BOTH | Preserve; DEFER legacy provenance |
| STD-007 Booking/payment ↔ inventory consistency | P0 | Không trả CONFIRMED khi hold confirm chưa thành công; các trạng thái có recovery được kiểm thử | IN_PROGRESS | Paid/onsite durable intents + recovery; cancellation/release replay; internal refund demo | [Batch 2](BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md) unpaid lifecycle; 8C natural expiry CANCELLED/RELEASED; VNPay Sandbox paid → CONFIRMED/V1_CONFIRMED + 1 CREDIT + replay (6B 17/11) | **IN_PROGRESS** | PARTIAL | Provider refund/QueryDR DEFERRED; UNKNOWN commit boundary, crash/restart, multi-node NOT_VERIFIED | WRITE | VERIFY (UNKNOWN/timeout cho Agent create) + DEFER provider |
| STD-008 Hold expiry & concurrency | P1 | Expiry không phụ thuộc cleanup; một transition hợp lệ/hold; test đa transaction | FIXED_VERIFIED | Expiry check dưới lock; room/hold locks; counter guards | AvailabilityServiceTest (9A rerun PASS); hosted stock2 race 201/201/409 (8C/8D); natural expiry (8C) | **FIXED_VERIFIED** | VERIFIED | Multi-node throughput NOT_VERIFIED (D) | WRITE | — |
| STD-009 Idempotency / orphan hold / timeout | P1 | Một command ID không tạo hai booking; known orphan release/reconcile; outage không bị coi hold hết hạn | FIXED_VERIFIED | Command hash binding, idempotent hold, compensation; same key + payload khác → 409 | BookingIdempotencyTest/BookingCreationCompensationTest (9A rerun PASS); hosted same-key concurrent → 1 booking/1 hold (8C/8D) | **FIXED_VERIFIED** | VERIFIED | Prolonged orphan monitoring khi deploy | WRITE | — |
| STD-010 Same-offer matching | P1 | Giá/capacity/availability mỗi kết quả truy về cùng offer; hard constraints đúng | FIXED_VERIFIED | `HotelOfferSearchService` eligible rooms; 8D `approvalFor` + availability snapshot một lần/request | HotelOfferSearchServiceTest (9A rerun PASS); 8D response BEFORE/AFTER tương đương 15/15 | **FIXED_VERIFIED** | VERIFIED | Một room type/base price; availability là quan sát, không bảo đảm | READ | — |
| STD-011 API error ≠ empty/sold-out | P1 | Không trả NO_RESULTS/SOLD_OUT chỉ vì API lỗi; mã lỗi ổn định | IN_PROGRESS | Hotel search/availability typed errors (`readJson`, `ReadApiError`); backend `ApiErrorSupport` | Node search-errors/api-errors (9A regression 214 PASS) | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Phần còn lại chỉ là Nearby/Place → EXCLUDED — EXTERNAL OWNER; không coi là DONE toàn issue | READ | EXCLUDE (Place) |
| STD-012 Truthful UI facts | P1 | Mọi fact dùng filter/giải thích có source field; placeholder rõ | IN_PROGRESS | Search/room facts unknown-preserving; HotelCard chỉ hiện discount khi có `originalPrice` (search mapping không gán) | Node room-facts/inventory (9A PASS); 9A spot check `components/HotelCard.tsx`, `app/page.tsx` (tag marketing tĩnh, không phải fact khách sạn) | **IN_PROGRESS** | PARTIAL | Rà soát có hệ thống các legacy surface ngoài search/detail NOT_VERIFIED; PlaceCard/PlaceReviewsModal EXCLUDED | READ (frontend; Agent đọc backend) | VERIFY |
| STD-013 Filter/URL sync | P1 | Giá hiển thị = giá áp dụng; criteria round-trip ổn định | FIXED_VERIFIED | `lib/search/criteria.ts`, SearchFilters | Node search-filters (9A PASS) | **FIXED_VERIFIED** | VERIFIED | Giữ default/unit/AND-OR trong tool schema | READ | — |
| STD-014 Global sort/pagination | P2 | Không có control vô hiệu; công bố page/size/sort/default | FIXED_VERIFIED | Global filter/sort trước paging, tie-break identity | HotelOfferPagingTest (9A PASS); Node search-pagination | **FIXED_VERIFIED** | VERIFIED | Thứ tự collection lồng (media/policies/FAQ/amenities) không có ORDER BY — không thuộc pagination contract (8D risk) | READ | — |
| STD-015 Accommodation type model | P1 | Filter công bố có dữ liệu và query thật | FIXED_VERIFIED | Model/DTO/catalog/query + reviewed change | AccommodationTypeServiceTest (9A PASS) | **FIXED_VERIFIED** | VERIFIED | — | READ | — |
| STD-016 Canonical amenity catalog | P1 | Canonical code có nghĩa chung; đổi label không đổi nghĩa | FIXED_VERIFIED | AmenityCatalogService, ADMIN reviewed mapping | AmenityCatalogServiceTest (9A PASS) | **FIXED_VERIFIED** | VERIFIED | Mapping completeness là data review | READ | — |
| STD-017 Location by ID | P1 | Chọn ID không bị hiểu lại; province expansion theo relation; DTO/TS đồng nhất | FIXED_VERIFIED | Typed city/province IDs | LocationSearchIntegrationTest; tsc PASS (9A) | **FIXED_VERIFIED** | VERIFIED | — | READ | — |
| STD-018 Validation before side effects | P1 | 4xx fieldErrors ổn định; dữ liệu không hợp lệ không tạo side effect | FIXED_UNVERIFIED | Bean/manual validation, HotelOfferCriteria cross-field, booking validation | BookingValidationControllerTest/CatalogValidationTest/AmenityWriteValidationTest (9A PASS) | **FIXED_UNVERIFIED** | NOT_VERIFIED | Consolidated negative-contract inventory (REST/RPC) chưa có; tối thiểu cần cho search/availability/booking-create mà Agent dùng; Place boundary EXCLUDED | BOTH | VERIFY |
| STD-019 Room description round-trip | P1 | Không báo lưu thành công cho thông tin không được giữ | FIXED_VERIFIED | RoomType description DTO/entity | RoomDescriptionTest (9A PASS) | **FIXED_VERIFIED** | VERIFIED | — | READ | — |
| STD-020 Auth/session contract | P1 | Không gửi external token như internal bearer; principal/ID nhất quán; token không log | FIXED_VERIFIED | Internal credentials/session generation; Google disabled | Session/Revocation tests (9A PASS); NextAuth hosted E2E (6B); ADMIN Gateway login (8C/8D) | **FIXED_VERIFIED** | VERIFIED | Google vẫn unsupported (không phải verified exchange) | WRITE | — |
| STD-021 Schema/migration baseline | P1 | Manifest lịch sử áp dụng; diff entity/DDL/runtime được giải thích; không sao chép users sang booking | IN_PROGRESS | 5 canonical V1 chains; `canonical`/`supabase-dev` profiles `ddl-auto: validate`; default `application.yml` hotel/booking/revenue/regulations vẫn `update` (Test-mode `docker` profile kế thừa) | 5B; hosted V1 5/5 + Hibernate 32 entities (6B); Batch 7 zero drift; 9A source grep `ddl-auto` | **IN_PROGRESS** | PARTIAL | Canonical sub-scope VERIFIED; Test/legacy profile `update` chưa đóng; backup/PITR/restore NOT_VERIFIED; legacy import/cutover DEFERRED (U03) | NONE (runtime canonical) | DEFER (production ops) |
| STD-022 Loyalty ledger | P1 | Discount/points khớp quote; mỗi command đổi ledger một lần | FIXED_VERIFIED | Reservation quote + receipts + durable settlement | LoyaltyPointsServiceTest (9A PASS); §24 | **FIXED_VERIFIED** | VERIFIED | — | WRITE (nếu bật điểm) | — |
| STD-023 Public publication boundary | P1 | Public chỉ trả trạng thái công bố; private preview có quyền; không khẳng định tọa độ từ centroid | IN_PROGRESS | Publication filter REST/gRPC + owner/admin preview; 7B public rooms chỉ approved | HotelGrpcPublicationAuthorizationTest (9A PASS); hosted 8D PENDING room không lộ | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Phần còn lại: nearby request dùng tọa độ thô → Place, EXCLUDED — EXTERNAL OWNER | READ | EXCLUDE (Place) |
| STD-024 JSON/text encoding | P2 | Biết nguồn encoding, response một shape; không xóa adapter trước kiểm migration | FIXED_UNVERIFIED | Canonical text codecs/mappers; nullable amenities (Batch 7) | HotelMapperTextTest (9A PASS); Batch 3/7 synthetic shapes | **FIXED_UNVERIFIED** | DEFERRED | Historical corpus chỉ tồn tại ở legacy DB không import (U03) → DEFERRED cùng legacy cutover | NONE | DEFER |
| STD-025 Search fan-out baseline | P2 | Baseline có data size/environment và giới hạn result | FIXED_VERIFIED | Bulk/batched reads; 8D `@BatchSize`, `toPublicHotelResponses`, media bulk | [8D](BATCH8D_READ_OPTIMIZATION_2026-11-17.md) SQL 96→12/111→15/91→11/19→11, slope 0; native 2/2 | **FIXED_VERIFIED** | VERIFIED | 12–15 round trip cố định, p50 ~1,5–2,5 s hosted; không SLO → DEFERRED | READ (latency) | DEFER further perf |
| STD-026 Error schema & deadlines | P1 | Empty chỉ từ thành công; lỗi có code ổn định; request kết thúc trong timeout | IN_PROGRESS | Backend `ApiErrorSupport` hotel/booking; FE `readJson` 10 s, `bookingFetch` 15 s | api-errors Node (9A PASS); 9A source: `hotelsApi.getHotelById` và owner/admin writers trong `lib/api/hotels.ts` (L320+) dùng `fetch` không deadline, lỗi chứa `statusText` | **IN_PROGRESS** | PARTIAL | FE hotel detail/owner writers chưa bounded/typed (FIX LOW); Agent-facing backend error envelope cần contract test; Place SDK EXCLUDED | BOTH | VERIFY (backend) + FIX (FE adapter) |
| STD-027 Reproducible topology | P2 | Dựng được scope đã công bố hoặc runbook rõ; log path được kiểm chứng | NEEDS_ENVIRONMENT | `docker/stack.ps1` Test/Supabase modes; Dockerfile; production frontend | [6B Docker runtime](BATCH6B_DOCKER_RUNTIME_2026-11-16.md); VNPay run dùng `stack.ps1 -Environment Supabase -Action Up` (6B 17/11); FE production build PASS (9A) | **IN_PROGRESS** | PARTIAL | Log ingestion Loki/Promtail NOT_VERIFIED; backend cloud deployment DEFERRED | NONE | DEFER |
| STD-028 Executed integration evidence | P1 | Có command/environment/output thực; chưa chạy ghi chưa chạy | IN_PROGRESS | Unit/MVC/socket, native PostgreSQL, hosted Gateway harness; `api-gateway` không có `src/test` | 9A: backend 8 module, FE 214/tsc/build; hosted 8B–8D E2E; VNPay live (6B) | **IN_PROGRESS** | PARTIAL | Agent-facing API contract suite chưa có; full booking→payment→cancel→refund E2E, Gateway route suite, browser non-intercepted, multi-node NOT_VERIFIED | BOTH | VERIFY |
| STD-029 Events/reporting recovery | P2 | Recovery report/notification được mô tả và test; AI search không dựa read model trễ | IN_PROGRESS | Outbox, confirms, receipts/dedup, DLQ | Batch 1.6 DEBIT/net0; VNPay → 1 CREDIT + replay (6B) | **IN_PROGRESS** | PARTIAL | Operator replay/DLQ/retention, legacy cash reconciliation DEFERRED; SMTP không exactly-once | NONE | DEFER |
| STD-030 Observability/correlation | P2 | Trace được một lỗi; không ép shared library | IN_PROGRESS | micrometer-tracing ở 8 service (9A pom); `api-gateway` không có tracing dependency; requestId trong ApiErrorSupport | 8D SQL attribution theo W3C trace ID; AuthLogPrivacyTest/JwtMdcFilterTest (9A PASS) | **IN_PROGRESS** | PARTIAL | Correlated error Gateway→Booking→Hotel chưa chứng minh; log ingestion NOT_VERIFIED; blocker TypeScript Place cũ đã hết (tsc PASS) | BOTH (vận hành Agent) | VERIFY trước AI WRITE |
| STD-031 Stale search response guard | P2 | UI chỉ render response thuộc request hiện hành | FIXED_VERIFIED | SearchPage current guard | Historical browser; Node calendar/criteria (9A PASS) | **FIXED_VERIFIED** | VERIFIED | Nearby fetch lifecycle là Place → EXCLUDED | READ (frontend) | — |
| STD-032 Coordinate provenance | P3 | Có nguồn/quality trước filter địa lý; READ city-only không bị chặn | IN_PROGRESS | `hotelCoordinates` chỉ nhận OWNER_PROVIDED/GEOCODED bounded | Node canonical/location regressions (9A PASS); §35 | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Caller Nearby/PlaceCard/PlaceReviewsModal → EXCLUDED — EXTERNAL OWNER; Agent MVP không lọc địa lý | NONE (city-only READ) | EXCLUDE (Place) |

## 8. Status Changes

| Issue | Trước 9A | Sau 9A | Lý do / evidence |
|---|---|---|---|
| STD-006 | IN_PROGRESS | **FIXED_VERIFIED** (scoped) | Acceptance gốc là hành vi code: inventory thiếu không được coi là tồn thật, phòng lỗi được báo thay vì bán. Đạt bởi canonical no-default10 (5B) + mandatory approval 409 (7B native) + hosted Gateway 8B/8D (PENDING 409, không hold/counter, không xuất hiện trong search). Legacy physical-stock provenance không thuộc acceptance gốc và không tồn tại trong DB canonical fresh (U03) → DEFERRED, không bị xóa khỏi tài liệu |
| STD-027 | NEEDS_ENVIRONMENT | **IN_PROGRESS** | Môi trường local đã có: `docker/stack.ps1` Test/Supabase, chạy 10 JVM + production frontend (6B Docker runtime, dùng trong VNPay 17/11), FE production build PASS 9A. Log path/ingestion chưa kiểm chứng nên chưa FIXED_VERIFIED |
| STD-011, STD-023, STD-032 | IN_PROGRESS | IN_PROGRESS + in-scope `IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED` | Remaining work chỉ còn Nearby/Place |
| STD-002, STD-007 | IN_PROGRESS | IN_PROGRESS (sub-scope cập nhật) | Live VNPay Sandbox checkout → CONFIRMED + 1 CREDIT + replay VERIFIED (6B 17/11); provider refund DEFERRED |
| STD-030 | IN_PROGRESS | IN_PROGRESS (remaining thu hẹp) | Blocker TypeScript Place cũ hết; còn correlation/ingestion |

Không có issue nào bị hạ trạng thái. Priority gốc không đổi.

## 9. PLAN/AUDIT Consistency

- Current statuses được sinh từ **một nguồn dữ liệu** cho cả AUDIT §0, PLAN §0 và báo cáo này; kiểm tra tự động sau khi ghi (mục 16).
- Bảng §C AUDIT, §A summary, §G0, §H và header PLAN (Batch 2 current, 13/11) được gắn `HISTORICAL — SUPERSEDED BY BATCH 9A`; nội dung giữ nguyên.
- Checklist 6B trong AUDIT được chú thích đã đóng 17/11 cho khớp PLAN.
- PLAN §4 (inventory/acceptance gốc) và §13 (DoR) không xóa; thêm đánh giá 9A ở PLAN §0/§77.

## 10. Place Scope Exclusion

- Không đọc/sửa/audit logic `place-service/**`, Nearby Places, PlaceCard, PlaceReviewsModal, `lib/api/places.ts`, Place hooks.
- Compiler/typecheck/build: Place compile SUCCESS (Maven), `tsc` PASS, Next build PASS → **không có diagnostic Place**. Lint ghi nhận số warning/error ở hai file Place chỉ để minh bạch (không tạo task).
- STD-011/012/023/026/031/032: phần Place = `EXCLUDED — EXTERNAL OWNER`, không DONE, không phải AI MVP blocker; lịch sử Place giữ nguyên trong PLAN/AUDIT.
- Hotel location/search (city/province ID, `hotelCoordinates` helper) vẫn thuộc phạm vi và được đánh giá.

## 11–12. AI READ / AI WRITE Readiness

#### AI READ readiness (không gồm Place/Nearby)

| Capability | Gateway API (đã có, không tạo mới) | Auth | Existing tests / evidence | Missing verification | Readiness |
|---|---|---|---|---|---|
| Hotel search + filtering + pagination | `GET /api/hotel/hotels/offers/page` (paged), `GET /api/hotel/hotels/offers` | Public | HotelOfferControllerTest, HotelOfferPagingTest, HotelOfferSearchServiceTest (9A PASS); Node search-filters/pagination/errors (9A PASS); hosted 8D Gateway 2760/2760, response tương đương 15/15 | Agent-facing contract test tại Gateway (schema, invalid criteria → 400 fieldErrors, 503 envelope); không có SLO (p50 hosted ~1,9–2,3 s) | READY_WITH_CONSTRAINTS |
| Hotel detail + policies/FAQ/amenities | `GET /api/hotel/hotels/{hotelId}`; `GET /api/hotel/hotels/policies/categories`, `/amenities/categories` | Public (published only) | Publication tests (9A PASS); hosted 8D detail 19→11 SQL | Thứ tự collection lồng không có ORDER BY; hosted rich-content dataset chưa đo | READY_WITH_CONSTRAINTS |
| Room types | `GET /api/hotel/api/v1/hotel/{hotelId}`, `GET /api/hotel/api/v1/{roomTypeId}` | Public (approved + published only) | 7B approval filter tests; hosted 8D PENDING room không lộ | Agent phải diễn đạt "không có phòng được duyệt" khác "hết phòng" | READY_WITH_CONSTRAINTS |
| Amenity / accommodation type / location catalogs | `GET /api/hotel/amenity-catalog`, `/api/hotel/hotels/accommodation-types`, `/cities`, `/cities/search/locations` | Public | AmenityCatalogServiceTest, AccommodationTypeServiceTest, LocationSearchIntegrationTest (9A PASS) | Contract snapshot cho tool schema | READY_WITH_CONSTRAINTS |
| Pricing | `basePrice`/`matchedOffer` trong offers | Public | STD-005/010 tests; booking từ chối giá cũ (400) | Chỉ base price/full payment; không tax/rate plan/discount | READY_WITH_CONSTRAINTS |
| Dated availability | `GET /api/hotel/api/v1/availability/check?roomTypeId&checkIn&checkOut&quantity`; dated offers | Public | AvailabilityServiceTest (9A PASS); hosted 8C/8D | Là quan sát, không giữ chỗ; unapproved → 409 `INVENTORY_APPROVAL_REQUIRED` cần map đúng nghĩa | READY_WITH_CONSTRAINTS |
| Cancellation policy | `GET /api/bookings/cancellation-policy` | Public | Batch 2 policy readback | Contract snapshot | READY_WITH_CONSTRAINTS |
| Review summaries | `GET /api/hotel/api/v1/reviews/summaries`, `/stats/hotel/{hotelId}` | Public | ReviewSummary tests; Node review-summaries (9A PASS) | — | READY_WITH_CONSTRAINTS |
| Public/private data boundary | Tất cả public GET ở trên | — | Publication/preview authz tests; hosted 8B/8D | Consolidated public-read route matrix (STD-003) | NOT_VERIFIED (gate C1) |
| API errors / timeouts | Backend `ApiErrorSupport` (code/status/requestId) | — | api-errors Node, MVC fault tests (historical) | Gateway không cấu hình response timeout → Agent tool phải tự đặt deadline; contract test lỗi tại Gateway | READY_WITH_CONSTRAINTS |
| Nearby Places / POI / road distance | `/api/places/**` | — | — | — | EXCLUDED — EXTERNAL OWNER (không thuộc AI MVP) |

**Kết luận READ:** không có issue nào cần **sửa code** trước một Agent READ MVP chỉ dùng public hotel API; còn **verification gates** C1–C3 (STD-003 public-read subset, STD-018 negative contract cho tham số search/availability, STD-028 agent-facing contract suite). Đây là READY_WITH_CONSTRAINTS trên local development runtime, không phải deployed/production READ.

#### AI WRITE readiness

| Capability | Required APIs | Auth | Existing tests / evidence | Missing verification | Readiness |
|---|---|---|---|---|---|
| User identity | `/api/auth/**` (NextAuth credentials → internal JWT) | User session của chính người dùng | Session/revocation tests (9A PASS); NextAuth hosted (6B) | Agent không được dùng service identity hay token người khác | READY_WITH_CONSTRAINTS |
| Agent hỗ trợ soạn booking (draft, không mutation) | READ APIs ở trên | Public/CUSTOMER | Như READ | Draft user-bound/expiry là phần việc của Agent (chưa triển khai) | READY_WITH_CONSTRAINTS |
| Agent tạo booking sau xác nhận rõ ràng | `POST /api/bookings` (idempotencyKey, unitPrice phải khớp basePrice) | CUSTOMER (principal) | BookingAuthorization/Idempotency/Validation tests (9A PASS); Batch 2; hosted 8B–8D (201, same-key, race, 409 approval) | Contract test cho TIMEOUT/UNKNOWN: phải đọc trạng thái và tái dùng cùng key, không tạo key mới; crash/multi-node NOT_VERIFIED | READY_WITH_CONSTRAINTS |
| Agent xác nhận/đọc trạng thái booking | `GET /api/bookings/{id}`, `/api/bookings/user/{userId}`, `GET /api/payments/booking/{bookingId}` | Owner of booking | BookingAuthorizationTest; hosted | — | READY |
| Hủy booking | `DELETE /api/bookings/{id}` + cancellation-policy | Owner | Batch 2; hosted 8B–8D cancel 204, counters 0 | Paid cancellation dẫn tới refund ADMIN-only → MVP chỉ unpaid/pending, có xác nhận | READY_WITH_CONSTRAINTS |
| Thanh toán | `POST /api/payments/vnpay/create` (trả URL) | Owner | VNPay **Sandbox** live 6B 17/11 | Production/IPN NOT_VERIFIED; Agent chỉ trao URL, không nhập thẻ/OTP | READY_WITH_CONSTRAINTS (handoff, Sandbox); Agent tự thanh toán: **NOT ALLOWED** |
| Yêu cầu hoàn tiền (CUSTOMER) | `POST /api/payments/{paymentId}/refund-request` | Owner | Batch 1.6 Gateway (CUSTOMER 201) | Frontend Refund UI NOT IMPLEMENTED; không thuộc MVP | DEFERRED |
| Thực thi refund | `/api/payments/{paymentId}/refund-request/approve`, `/api/payments/{id}/refund` | ADMIN | Batch 1.6 (Sandbox Internal) | Provider refund NOT IMPLEMENTED; Sandbox Internal ≠ hoàn tiền thật | **BLOCKED / NOT ALLOWED** cho Agent |
| Dùng điểm loyalty | User loyalty quote + booking | CUSTOMER | LoyaltyPointsServiceTest (9A PASS) | Không cần cho MVP | DEFERRED |
| Error recovery / correlation | requestId trong lỗi; idempotency key | — | ApiErrorSupport; JwtMdcFilterTest | Gateway không có tracing dependency; correlated Gateway→Booking→Hotel chưa chứng minh (STD-030) | NOT_VERIFIED (gate C5) |

**Kết luận WRITE:** backend có đủ principal binding, server pricing (giá cũ → 400), mandatory inventory approval, idempotency và hold lifecycle đã kiểm thử hosted. Không có issue nào đã chứng minh cần sửa code trước MVP "Agent tạo booking unpaid sau xác nhận"; còn gates C2/C4/C5 và phần xác nhận user-bound phía Agent. Payment chỉ ở dạng trao URL Sandbox; refund bị loại khỏi Agent.

## 13–14. Remaining Backlog & Recommended Next Batches

#### Remaining backlog (Batch 9A)

**A. Must Fix Before AI READ — không có issue nào.** Không tìm thấy defect code đã chứng minh chặn search correctness, public data, authorization, API errors hoặc read contract cho Agent READ MVP (public hotel API, không Place). Điều kiện còn lại là verification gates ở nhóm C.

**B. Must Fix Before AI WRITE — không có issue nào.** Principal binding (STD-001), server pricing + stale-price 400 (STD-005), mandatory inventory approval (STD-006), hold expiry/concurrency (STD-008), idempotency (STD-009) đều VERIFIED kể cả hosted Gateway 8B–8D. Phần còn thiếu là verification gates C2/C4/C5 và cơ chế draft/xác nhận user-bound thuộc phạm vi Agent.

**C. Verification Only** (implementation có, thiếu evidence):

| Gate | Issue | Phạm vi | Trước |
|---|---|---|---|
| C1 | STD-003 | Public-read route matrix (anonymous/CUSTOMER/OWNER khác hotel) cho mọi GET Agent dùng; full REST/RPC actor matrix giữ cho G4 | AI READ (subset) / AI WRITE (đầy đủ cho route Agent dùng) |
| C2 | STD-018 | Negative contract: search criteria, availability check, booking create → 4xx fieldErrors, không side effect | READ (search/availability), WRITE (create) |
| C3 | STD-028, STD-026 | Agent-facing contract suite tại Gateway: response schema + error envelope (400/401/403/404/409/503) cho API trong ma trận | READ / WRITE |
| C4 | STD-007 | TIMEOUT/UNKNOWN khi create: đọc trạng thái, tái dùng idempotency key, không tạo booking thứ hai | AI WRITE |
| C5 | STD-030 | Một lỗi correlated Gateway→Booking→Hotel (Gateway hiện không có tracing dependency) | AI WRITE |
| C6 | STD-012 | Rà soát legacy UI surfaces ngoài search/detail (frontend; không chặn Agent backend) | Frontend core |
| F1 (FIX, LOW, không chặn AI) | STD-026 | `lib/api/hotels.ts` `getHotelById` và owner/admin writers dùng `fetch` không deadline, lỗi chứa `statusText` → chuyển sang helper có deadline/typed error hiện có | Frontend core |

**D. Deferred** (không thuộc AI MVP): STD-002/007 provider refund/QueryDR/IPN (G1b); STD-007/008 crash/restart/multi-node; STD-021 backup/PITR/restore, Test-profile `ddl-auto: update`, legacy import/cutover (U03); STD-024 historical corpus; STD-025 round-trip floor/SLO (8D next); STD-027 log ingestion + backend cloud deployment; STD-029 operator replay/DLQ/retention, legacy cash reconciliation.

**E. Excluded — External Owner** (không tạo implementation task): `place-service/**`; phần Place/Nearby của STD-011/012/023/026/031/032 (NearbyPlacesSection, PlaceCard, PlaceReviewsModal, `lib/api/places.ts`, nearby origin trong `app/hotel/[id]/page.tsx`). Không coi là DONE, không là blocker của AI MVP không dùng Place.

#### Recommended next batches

| Batch | Scope / Issue IDs | Dependencies | Expected source changes | Tests | Acceptance | Risks | Complexity |
|---|---|---|---|---|---|---|---|
| **9B — AI READ contract verification** | C1 (public subset), C2 (search/availability), C3 (READ APIs) — STD-003/018/026/028 | Không; dùng Test mode (`docker/.env.test`, local PostgreSQL) hoặc native disposable; không hosted write | Chỉ test/harness + tài liệu tool contract; không đổi API | MockMvc/socket contract tests hotel-service; Gateway read smoke trên stack test; anonymous/cross-owner negatives | Mỗi API READ trong ma trận có schema snapshot + error envelope + public/private negative có output thực; không Place | Stack test cần seed approved inventory qua workflow thật, không SQL bypass | LOW–MEDIUM |
| **9C — AI WRITE pre-gate verification + F1** | C1 (route Agent dùng), C2 (create), C3 (WRITE APIs), C4, C5; F1 frontend | 9B harness | Tests; F1 sửa adapter frontend nhỏ; tracing Gateway chỉ khi được duyệt (dependency/config) | Create/cancel/status với CUSTOMER JWT qua Gateway; injected timeout → readback + same key; correlated trace một lỗi | Không duplicate booking dưới timeout; stale price 400; approval 409; một trace ID xuyên Gateway→Booking→Hotel | Thêm dependency tracing phải review; không tự retry write | MEDIUM |
| **10 — AI Agent READ MVP** (ngoài standardization) | Read tools trên public hotel API | 9B DONE | Agent code mới (không sửa API) | AI evaluation query set; tool schema validation | Agent không bịa facts; phân biệt no-result/lỗi/unapproved | LLM output untrusted | MEDIUM |
| **11 — AI Agent WRITE (booking assist)** | Draft → xác nhận → create unpaid; trao URL VNPay Sandbox | 9C + 10 | Agent code; draft/expiry/one-time confirmation | Confirmation required; timeout recovery | Không mutation nếu không xác nhận; không payment/refund tự động | Sandbox ≠ production | HIGH |

Không mở lại Batch 8E. Deferred workstreams (G1b provider refund, production ops, 8D perf follow-up, legacy cutover) cần quyết định riêng.

## 15. Unverified Claims

- Hosted/provider kết quả của 6B/8–8D được đối chiếu từ report/evidence, **không chạy lại** trong 9A.
- Production deployment, backup/PITR/restore, IPN, provider refund, multi-node, SLO, log ingestion: NOT_VERIFIED.
- Batch 7A: không có report độc lập; không suy diễn nội dung.
- Lint baseline so với origin/main toàn repo: NOT_VERIFIED (không checkout nhánh khác theo ràng buộc Git).
- Supabase-mode Spring profile lấy từ private `docker/.env`/launcher — không đọc file private; dựa trên evidence 6B (validate, SQL init never).
- Opt-in DB suites và browser suites: NOT_RUN trong 9A.
- Thời điểm/ý nghĩa đầy đủ của các nhật ký §14–§73 PLAN: đối chiếu qua headings/acceptance, không tái kiểm từng dòng.

## 16. Final Status

**Batch 9A: DONE (documentation reconciliation + read-only verification).** Acceptance 1–14 của batch: PLAN/AUDIT đọc và đối chiếu; source hiện tại kiểm cho 32 issue trong phạm vi phù hợp; evidence Batch 1–8D đối chiếu; current matrix cập nhật; PLAN/AUDIT nhất quán (kiểm tự động: 32 ID, không duplicate, status khớp, counts khớp); checkpoint lịch sử giữ nguyên; Place loại trừ; không có compiler error Place; AI READ/WRITE đánh giá; backlog và next batches dựa trên evidence; không sửa application source; không thay đổi DB/provider/runtime; không stage/commit/push/reset/restore/checkout/clean/stash.

Files chỉnh: `Docs/standardization/PROJECT_STANDARDIZATION_PLAN.md`, `Docs/standardization/PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md`; file mới: báo cáo này. **Dự án không production-ready; standardization tổng thể vẫn PARTIAL.**
