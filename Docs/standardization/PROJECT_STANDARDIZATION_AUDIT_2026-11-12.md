# Project standardization audit and execution recommendation

> **Current state — Batch 9A, 17/11/2026: xem [§0](#0-current-state--batch-9a-reconciliation-17112026).** 17 FIXED_VERIFIED / 12 IN_PROGRESS / 3 FIXED_UNVERIFIED / 0 NEEDS_ENVIRONMENT; Place scope EXCLUDED — EXTERNAL OWNER; không production-ready. Các ghi chú blockquote, mục A–H và checkpoint dưới đây là lịch sử có ngày; mọi tổng hợp trạng thái trong đó là HISTORICAL — SUPERSEDED BY BATCH 9A khi mâu thuẫn với §0.

> **Provider closure 17/11/2026 — Batch6B: DONE cho phạm vi local development + VNPay Sandbox/Storage.** Actual NCB card/OTP → return00 → payment COMPLETED, booking CONFIRMED, một Revenue CREDIT; callback replay/negative checks PASS. Storage upload/private signing PASS; fixture đã dọn. [Evidence và giới hạn](../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md). Các provider BLOCKED/PARTIAL ở checkpoint16/11 phía dưới là lịch sử; không tự đóng production/IPN/refund, batch khác hoặc parent standardization findings.

> Checkpoint Batch 7 — 16/11/2026: **G2 PARTIAL; implementation source/tests/tooling đã thực hiện.** Inventory approval/link integrity và nullable amenities compatibility đã sửa; fresh local canonical provisioning/catalog/Hibernate 5/5 PASS, zero schema diff. Không sửa V1, không truy cập Supabase hoặc dữ liệu lịch sử. STD-006/021 vẫn IN_PROGRESS; STD-024 full historical scope vẫn FIXED_UNVERIFIED. Actual provenance NOT VERIFIED, legacy import DEFERRED; unmanaged compatibility theo §56 giữ nguyên, verified-only admission cần policy riêng. Batch 6B database integration tạm đóng theo quyết định người dùng; không mở lại bởi VNPay/Storage. [Báo cáo Batch 7](../BATCH7_G2_PROVENANCE_IMPLEMENTATION_2026-11-16.md).

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.

> Historical status (superseded by checkpoint 16/11/2026): Current: Batch5B local canonical acceptance DONE (14/11/2026); see appended checkpoint. Older statuses are dated history. Supabase/Batch6 not executed.

Audit date: 12 November 2026, Asia/Saigon. Scope: the two local repositories and root planning documents. This is an audit, not authorization to implement the roadmap. The original 12 November audit did not edit the plan or application source.

**HISTORICAL — SUPERSEDED BY BATCH 9A — Last reviewed: 2026-11-13 — Batch 2 execution.** [Vietnamese acceptance report](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md): isolated unpaid Booking–Hotel/Availability Gateway lifecycle accepted, 57 Gateway/SQL/setup assertions PASS; focused Booking/Hotel 627 PASS +34 skipped; separate fresh PostgreSQL2 PASS. No production source changes. Parent status counts below unchanged. Canonical PLAN/AUDIT are now here under `Docs/standardization`; DA1 originals are pointers, not independent copies. The initial execution left new files unstaged; the subsequent [PR review #31](https://github.com/ngocvo2511/checkinn-microservice/pull/31) prepares them for version control. **Recommended next: G2 schema/applied-history/provenance.** Provider/paid/positive-points Gateway, UNKNOWN boundary, multi-node and production rollout remain unverified.

**Historical review: 2026-11-13 — Batch 1.7 documentation reconciliation.** Current Refund scope/status below incorporates [Batch 1.6](../BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md); original 12 November Git/test snapshots remain dated history. No application/configuration/test/SQL changes, runtime tests, Docker operations or database access were performed in Batch 1.7. The plan receives only scope/dependency/evidence updates.


## 0. Current state — Batch 9A reconciliation (17/11/2026)

**Đây là trạng thái hiện hành duy nhất của 32 finding.** Các mục A–H, appendix và checkpoint phía dưới là lịch sử có ngày; phần nào mâu thuẫn với mục này được đánh dấu `HISTORICAL — SUPERSEDED BY BATCH 9A`. Bảng trạng thái hiện hành trong PLAN (§0) được sinh từ cùng dữ liệu. [Báo cáo Batch 9A](../BATCH9A_STANDARDIZATION_RECONCILIATION_2026-11-17.md).

**Batch 9E (18/11) — Final core audit & closure:** đối chiếu lại 32 issue với source + evidence 9B–9D và lần chạy fresh 18/11 (backend 12 module 1054 run/0 fail/48 skipped, compile 13/13; FE Node 218, tsc, build PASS; browser 150 case PASS về chức năng). **Counts không đổi** (17/12/3/0; in-scope VERIFIED 17, PARTIAL 11, IN_SCOPE_DONE 3, DEFERRED 1). Core standardization **đóng cho AI READ MVP (Batch 10) với điều kiện R1–R5**, không đóng cho AI WRITE, không production-ready. Finding mới F-9E-1…5. Bảng §0.2 dưới đây đã cập nhật tới 9E; các dòng tóm tắt 9A trong §0.1 là lịch sử. [Báo cáo 9E](../BATCH9E_FINAL_CORE_STANDARDIZATION_AUDIT_2026-11-18.md).

**Batch 9F (20/11) — core follow-up fixes:** F-9E-1, F-9E-3/4, STD-030 client REST còn lại, STD-026 DB outage ở user/revenue/regulations → FIXED_VERIFIED; smoke canonical 106/0. Parent counts không đổi (17/12/3/0); **in-scope STD-012 → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED** (VERIFIED 17, PARTIAL 10, IN_SCOPE_DONE 4, DEFERRED 1). Finding mới F-9F-1. [Báo cáo 9F](../BATCH9F_CORE_FOLLOWUP_FIXES_2026-11-20.md).

**Batch 9G (21/11) — remaining core gaps:** F-9F-1, F-9E-2, F-9E-5, Gateway 504/503 envelope, Gateway tracing bridge + access log, log ingestion (Promtail→Loki, project configs), E2E booking→payment→cancel→refund (VNPay return ký cục bộ, Sandbox Internal refund), browser không intercept, multi-node 2 replica. **STD-028 → FIXED_VERIFIED; STD-030 in-scope → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED.** Counts: FIXED_VERIFIED 18 · IN_PROGRESS 11 · FIXED_UNVERIFIED 3; in-scope VERIFIED 18 · PARTIAL 8 · IN_SCOPE_DONE 5 · DEFERRED 1. Canonical smoke 135/0 (1 replica + live browser), 138/0 (2 replica). Finding mới F-9G-1 (CORS). [Báo cáo 9G](../BATCH9G_REMAINING_CORE_GAPS_2026-11-21.md).

**Batch 9H (21/11) — CORS + error contract:** F-9G-1 sửa (CORS booking/regulations = Gateway, cấu hình được); envelope `{timestamp,status,error,code,message,path,requestId}` + `X-Request-ID` ở mọi service có REST qua Gateway, 18 lỗi không body có envelope. **STD-026 in-scope → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED.** Counts parent 18/11/3; in-scope VERIFIED 18 · PARTIAL 7 · IN_SCOPE_DONE 6 · DEFERRED 1. Smoke 144/0. [Báo cáo 9H](../BATCH9H_CORS_AND_ERROR_CONTRACT_2026-11-21.md).

### 0.1 Executive summary

- Batch 9A là documentation reconciliation + read-only verification: không sửa application source, database, provider hoặc runtime. Git: backend `feat/inventory-approval-read-batching`/`96bfc4f`, frontend `fix/inventory-approval-workflow`/`6942747`; chỉ hai IDE edit có sẵn.
- **Parent status:** FIXED_VERIFIED **17**, IN_PROGRESS **12**, FIXED_UNVERIFIED **3**, NEEDS_ENVIRONMENT **0** (13/11: 16/12/3/1). Open (≠ FIXED_VERIFIED): **P0 3/7** (STD-002/003/007), **P1 7/17** (STD-011/012/018/021/023/026/028), **P2 4/7** (STD-024/027/029/030), **P3 1/1** (STD-032). Priority gốc không đổi: P0 7, P1 17, P2 7, P3 1.
- **In-scope (không gồm Place):** VERIFIED 17, PARTIAL 9, `IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED` 3 (STD-011/023/032), NOT_VERIFIED 0, DEFERRED 1 (STD-024), BLOCKED 0. *(Batch 9B: STD-003/018 NOT_VERIFIED → PARTIAL; PARTIAL nay 11.)* `IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED` không đóng parent issue.
- **Thay đổi:** STD-006 IN_PROGRESS → FIXED_VERIFIED (acceptance gốc về admission đạt bởi 5B/7B/8B/8D; legacy physical-stock provenance DEFERRED theo U03); STD-027 NEEDS_ENVIRONMENT → IN_PROGRESS (local Docker/production frontend có evidence; log ingestion/cloud còn thiếu).
- **Verification 9A:** backend 8 module 1021 reported / 973 PASS / 48 opt-in skipped / 0 fail; reactor compile 13/13 (gồm Place, không diagnostic); frontend 214/214, `tsc` PASS, production Webpack build PASS; ESLint baseline FAIL 133 errors/152 warnings (không phải acceptance gate). Blocker `showDistance` lịch sử không còn.
- Không production-ready; overall standardization PARTIAL. Không mở lại Batch 8E.

### 0.2 Master issue status (current)

| Issue | Pri | Historical (pre-9A) | **Current (9A–9H)** | In-scope (không gồm Place) | Latest evidence | Remaining work | AI | Recommendation |
|---|---|---|---|---|---|---|---|---|
| STD-001 | P0 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | BookingAuthorizationTest/GrpcBookingAuthorizationTest (9A rerun PASS); hosted Gateway authz 8B/8D (forged header/cross-owner 403) | External RPC caller inventory NOT_VERIFIED (G4); không phải code fix | WRITE | VERIFY (preserve) |
| STD-002 | P0 | IN_PROGRESS | **IN_PROGRESS** | PARTIAL | Batch 1.6 internal refund demo; [6B VNPay Sandbox 17/11](../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md) return00/HMAC/replay/amount-mismatch 400; 9A grep: 0 class `implements RefundProvider` | Provider refund/QueryDR/IPN reconciliation NOT IMPLEMENTED → DEFERRED (G1b). Sub-scope live Sandbox checkout nay VERIFIED | WRITE | DEFER provider refund; Agent không thực thi payment/refund |
| STD-003 | P0 | FIXED_UNVERIFIED | **FIXED_UNVERIFIED** | PARTIAL | Catalog/Media/Review authorization tests; Storage live (6B); hosted 8B/8D 403; 9B `AgentReadContractTest` (14 public anonymous, 20 private 401, 8 admin/internal 403); 9C `AgentWriteContractTest`; **Gateway thật trên stack canonical 87→104/0 (9C–9D)** | Consolidated REST/RPC actor matrix cho owner/admin writes + external RPC caller inventory (G4) | BOTH | VERIFY |
| STD-004 | P0 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | LoyaltyAuthorizationTest/ReportAuthorizationTest (9A rerun PASS); historical native | Caller/deployment recheck (G4) | NONE (WRITE nếu Agent dùng điểm) | — |
| STD-005 | P0 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | [Batch 2](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); hosted booking 201 server price 8B/8D; VNPay signed amount mismatch 400 (6B) | Không hỗ trợ rate plan/tax/partial payment — không được ngụ ý | WRITE | — |
| STD-006 | P0 | IN_PROGRESS | **FIXED_VERIFIED** | VERIFIED | [7B](../BATCH7B_MANDATORY_INVENTORY_APPROVAL_2026-11-16.md) native race/contract; hosted 8B/8D: room PENDING 409 + không hold/counter, không xuất hiện trong search; sau ADMIN approve available=2 | Legacy physical-stock provenance → DEFERRED (canonical DB fresh, không import legacy theo U03). Physical count thật thuộc OWNER/ADMIN workflow, không phải code | BOTH | Preserve; DEFER legacy provenance |
| STD-007 | P0 | IN_PROGRESS | **IN_PROGRESS** | PARTIAL | Batch 2 unpaid lifecycle; 8C natural expiry; VNPay Sandbox paid → CONFIRMED + replay (6B); **9C Gateway lost-response + retry cùng key → 1 booking/1 hold; cancel lặp idempotent 204** | Provider refund/QueryDR DEFERRED; crash/restart, multi-node NOT_VERIFIED | WRITE | DEFER provider; VERIFY crash trước AI WRITE |
| STD-008 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | AvailabilityServiceTest (9A rerun PASS); hosted stock2 race 201/201/409 (8C/8D); natural expiry (8C) | Multi-node throughput NOT_VERIFIED (D) | WRITE | — |
| STD-009 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | BookingIdempotencyTest/BookingCreationCompensationTest (9A rerun PASS); hosted same-key concurrent → 1 booking/1 hold (8C/8D) | Prolonged orphan monitoring khi deploy | WRITE | — |
| STD-010 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | HotelOfferSearchServiceTest (9A rerun PASS); 8D response BEFORE/AFTER tương đương 15/15 | Một room type/base price; availability là quan sát, không bảo đảm | READ | — |
| STD-011 | P1 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Node search-errors/api-errors (9A regression 214 PASS) | Phần còn lại chỉ là Nearby/Place → EXCLUDED — EXTERNAL OWNER; không coi là DONE toàn issue | READ | EXCLUDE (Place) |
| STD-012 | P1 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Node room-facts/inventory PASS; 9E rà soát hệ thống (`FeaturedHotels`/`GuestReviews`/`HotelCard` dùng dữ liệu nguồn); **9F F-9E-1 sửa: trang đặt phòng/thanh toán không còn dữ kiện cứng, giờ/điều khoản hủy từ cancellation-policy API (`tests/booking-facts-source.test.mjs`)** | Chỉ còn PlaceCard/PlaceReviewsModal → EXCLUDED — EXTERNAL OWNER; không coi là DONE toàn issue | READ (frontend) | EXCLUDE (Place) |
| STD-013 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | Node search-filters (9A PASS) | Giữ default/unit/AND-OR trong tool schema | READ | — |
| STD-014 | P2 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | HotelOfferPagingTest (9A PASS); Node search-pagination | Thứ tự collection lồng (media/policies/FAQ/amenities) không có ORDER BY — không thuộc pagination contract (8D risk) | READ | — |
| STD-015 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | AccommodationTypeServiceTest (9A PASS) | — | READ | — |
| STD-016 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | AmenityCatalogServiceTest (9A PASS) | Mapping completeness là data review | READ | — |
| STD-017 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | LocationSearchIntegrationTest; tsc PASS (9A) | — | READ | — |
| STD-018 | P1 | FIXED_UNVERIFIED | **FIXED_UNVERIFIED** | PARTIAL | Validation tests (9A PASS); search/availability 400 (9B); booking-create fieldErrors (9C); **9D giới hạn F-9B-3 (không quá khứ, ≤30 đêm, rooms 1–20, adults 1–40, children 0–40) ở Hotel + Booking, unit + Gateway** | Inventory hợp nhất cho các REST/RPC write còn lại (content/media/regulations/owner-admin); Place EXCLUDED | BOTH | VERIFY |
| STD-019 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | RoomDescriptionTest (9A PASS) | — | READ | — |
| STD-020 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | Session/Revocation tests (9A PASS); NextAuth hosted E2E (6B); ADMIN Gateway login (8C/8D) | Google vẫn unsupported (không phải verified exchange) | WRITE | — |
| STD-021 | P1 | IN_PROGRESS | **IN_PROGRESS** | PARTIAL | 5B; hosted V1 5/5 + Hibernate 32 entities (6B); zero drift (7); canonical `validate` + `hikari.connection-timeout` 10 s (9D) | Profile mặc định/`docker` vẫn `ddl-auto: update` (booking/hotel/regulations/revenue); backup/PITR/restore NOT_VERIFIED; legacy cutover DEFERRED (U03) | NONE (runtime canonical) | DEFER (production ops) |
| STD-022 | P1 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | LoyaltyPointsServiceTest (9A PASS); §24 | — | WRITE (nếu bật điểm) | — |
| STD-023 | P1 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | HotelGrpcPublicationAuthorizationTest; hosted 8D PENDING room không lộ; **9D public `HotelResponse.ownerId` = null (mapper test + Gateway)** | Chỉ còn nearby/tọa độ thô → Place, EXCLUDED — EXTERNAL OWNER | READ | EXCLUDE (Place) |
| STD-024 | P2 | FIXED_UNVERIFIED | **FIXED_UNVERIFIED** | DEFERRED | HotelMapperTextTest (9A PASS); Batch 3/7 synthetic shapes | Historical corpus chỉ tồn tại ở legacy DB không import (U03) → DEFERRED cùng legacy cutover | NONE | DEFER |
| STD-025 | P2 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | [8D](../BATCH8D_READ_OPTIMIZATION_2026-11-17.md) SQL 96→12/111→15/91→11/19→11, slope 0; native 2/2 | 12–15 round trip cố định, p50 ~1,5–2,5 s hosted; không SLO → DEFERRED | READ (latency) | DEFER further perf |
| STD-026 | P1 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Envelope Hotel/Booking (9B–9D), DB outage 503 mọi service (9D/9F), F-9F-1 + Gateway envelope + F-9E-2 (9G); **9H: cùng envelope + `X-Request-ID` ở auth/user/revenue/regulations, 18 lỗi không body → envelope; runtime sweep 6 service qua Gateway; CORS F-9G-1** | Chỉ còn Place SDK → EXCLUDED — EXTERNAL OWNER | BOTH | EXCLUDE (Place) |
| STD-027 | P2 | NEEDS_ENVIRONMENT | **IN_PROGRESS** | PARTIAL | 6B Docker runtime; VNPay run qua `stack.ps1`; FE production build PASS (9E); **topology canonical tái lập từ source nhiều lần 9C–9D (13 container healthy, V1 5/5)** | Log ingestion Loki/Promtail NOT_VERIFIED; backend cloud deployment DEFERRED | NONE | DEFER |
| STD-028 | P1 | IN_PROGRESS | **FIXED_VERIFIED** | VERIFIED | Contract suites (9B/9C/9G), native DB, hosted 8B–8D; **9G canonical stack: E2E booking→VNPay (return ký cục bộ)→CONFIRMED→cancel→refund Sandbox Internal→Revenue CREDIT+DEBIT net 0 (135/0); browser không intercept 3 luồng; multi-node 2 replica: không oversell, cùng key 1 booking (138/0)**; backend 1078/0 fail; FE 222 + browser 105 + area | Phạm vi: refund Sandbox Internal (provider refund DEFERRED STD-002), VNPay thật đã nghiệm thu riêng (6B), stack local; browser live chỉ 3 luồng; crash/restart thuộc STD-007 | BOTH | — |
| STD-029 | P2 | IN_PROGRESS | **IN_PROGRESS** | PARTIAL | Batch 1.6 DEBIT/net0; VNPay → 1 CREDIT + replay (6B) | Operator replay/DLQ/retention, legacy cash reconciliation DEFERRED; SMTP không exactly-once | NONE | DEFER |
| STD-030 | P2 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Booking→Hotel (9D), auth/revenue/user client (9F); **9G: Gateway tracing bridge (tiếp nối/tạo W3C trace, inject `traceparent`), `[GATEWAY_ACCESS] traceId=…` (không query/token), log ingestion Promtail→Loki bằng config `monitoring/` trên stack canonical: một trace có trong log Gateway/Booking/Hotel, 0 dòng giống JWT** | Chỉ còn place type blocker → EXCLUDED; trace exporter/collector và log pipeline hosted là phạm vi triển khai (STD-027) | BOTH (vận hành Agent) | EXCLUDE (Place) |
| STD-031 | P2 | FIXED_VERIFIED | **FIXED_VERIFIED** | VERIFIED | Historical browser; Node calendar/criteria (9A PASS) | Nearby fetch lifecycle là Place → EXCLUDED | READ (frontend) | — |
| STD-032 | P3 | IN_PROGRESS | **IN_PROGRESS** | IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED | Node canonical/location regressions (9A PASS); §35 | Caller Nearby/PlaceCard/PlaceReviewsModal → EXCLUDED — EXTERNAL OWNER; Agent MVP không lọc địa lý | NONE (city-only READ) | EXCLUDE (Place) |

### 0.3 Remaining risks

1. Provider refund/QueryDR/IPN chưa có implementation (`RefundProvider` không có class triển khai); Sandbox Internal Refund không phải hoàn tiền thật; VNPay chỉ được nghiệm thu ở **Sandbox**.
2. Chưa có consolidated route/RPC actor matrix (STD-003) và negative-contract inventory cho write còn lại (STD-018); api-gateway không có test và không có tracing bridge (response-timeout 20 s đã cấu hình 9D).
3. UNKNOWN commit boundary, crash/restart, multi-node chưa kiểm (STD-007/008/009 vận hành).
4. Test-mode profile `docker` vẫn kế thừa `ddl-auto: update` (hotel/booking/revenue/regulations); backup/PITR/restore và log ingestion chưa kiểm.
5. Hosted latency p50 ~1,5–2,5 s với 12–15 round trip cố định; không có SLO.
6. Frontend (9E): trang đặt phòng/thanh toán hiển thị dữ kiện/chính sách bịa (F-9E-1, STD-012); 27 `fetch` thô không deadline (F-9E-2, STD-026); browser default config đỏ 45 case do lẫn spec area + 3 area config Turbopack abort (F-9E-3/4); ESLint 133/151.
7. Place/Nearby: EXCLUDED — EXTERNAL OWNER; không đánh giá, không phải DONE.

### 0.4 Latest evidence

[Batch 9E](../BATCH9E_FINAL_CORE_STANDARDIZATION_AUDIT_2026-11-18.md) (fresh 18/11); [9D](../BATCH9D_FINDINGS_REMEDIATION_2026-11-18.md); [9C](../BATCH9C_AI_WRITE_PREGATE_VERIFICATION_2026-11-18.md); [9B](../BATCH9B_AI_READ_CONTRACT_VERIFICATION_2026-11-17.md); [canonical smoke evidence](../evidence/batch-9c-canonical-smoke/README.md). Trước đó: [Batch 9A](../BATCH9A_STANDARDIZATION_RECONCILIATION_2026-11-17.md) (fresh local commands); [8D](../BATCH8D_READ_OPTIMIZATION_2026-11-17.md); [PR review 17/11](../PR_REVIEW_2026-11-17.md); [8C](../BATCH8C_RUNTIME_STABILITY_PERFORMANCE_2026-11-17.md); [8B](../BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md); [7B](../BATCH7B_MANDATORY_INVENTORY_APPROVAL_2026-11-16.md); [VNPay Sandbox 17/11](../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md); [6B final](../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Hosted/provider kết quả không chạy lại trong 9A.

### 0.5 AI Agent readiness (supersedes §H)

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
| Public/private data boundary | Tất cả public GET ở trên | — | Publication/preview authz tests; hosted 8B/8D; 9B `AgentReadContractTest`; **Gateway canonical 104/0; `ownerId` public = null (9D)** | Contact khách sạn vẫn public — Agent chỉ đưa vào context khi cần | READY_WITH_CONSTRAINTS |
| API errors / timeouts | Backend `ApiErrorSupport` (code/status/requestId) | — | 9B/9D: 400 envelope, 404, 409 approval, **DB outage 503 trong 10 s qua Gateway**; Gateway response-timeout 20 s (504, không envelope) | Agent đặt deadline ≤10 s và tự giới hạn tần suất (Gateway không rate limit) | READY_WITH_CONSTRAINTS |
| Nearby Places / POI / road distance | `/api/places/**` | — | — | — | EXCLUDED — EXTERNAL OWNER (không thuộc AI MVP) |

**Cập nhật Batch 9B (17/11):** C1/C2/C3 phần READ đã có evidence ở service boundary; Gateway-level READ smoke NOT_RUN (chuyển 9C). Tool contract: [READ_TOOL_CONTRACT](../ai-agent/READ_TOOL_CONTRACT.md).

**Kết luận READ (9A):** không có issue nào cần **sửa code** trước một Agent READ MVP chỉ dùng public hotel API; còn **verification gates** C1–C3 (STD-003 public-read subset, STD-018 negative contract cho tham số search/availability, STD-028 agent-facing contract suite). Đây là READY_WITH_CONSTRAINTS trên local development runtime, không phải deployed/production READ.

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
| Error recovery / correlation | requestId trong lỗi; idempotency key | — | ApiErrorSupport; JwtMdcFilterTest; **9D: trace client → Booking → Hotel (test + runtime)** | Trace chỉ có khi client gửi `traceparent` (Gateway không sinh); auth/revenue/user client chưa instrument (STD-030) | READY_WITH_CONSTRAINTS (Booking/Hotel) |

**Kết luận WRITE:** backend có đủ principal binding, server pricing (giá cũ → 400), mandatory inventory approval, idempotency và hold lifecycle đã kiểm thử hosted. Không có issue nào đã chứng minh cần sửa code trước MVP "Agent tạo booking unpaid sau xác nhận"; còn gates C2/C4/C5 và phần xác nhận user-bound phía Agent. Payment chỉ ở dạng trao URL Sandbox; refund bị loại khỏi Agent.

### 0.6 Backlog and recommended next workstream (supersedes §G0 "NEXT")

#### Remaining backlog (Batch 9A)

**A. Must Fix Before AI READ — không có issue nào.** Không tìm thấy defect code đã chứng minh chặn search correctness, public data, authorization, API errors hoặc read contract cho Agent READ MVP (public hotel API, không Place). Điều kiện còn lại là verification gates ở nhóm C.

**B. Must Fix Before AI WRITE — không có issue nào.** Principal binding (STD-001), server pricing + stale-price 400 (STD-005), mandatory inventory approval (STD-006), hold expiry/concurrency (STD-008), idempotency (STD-009) đều VERIFIED kể cả hosted Gateway 8B–8D. Phần còn thiếu là verification gates C2/C4/C5 và cơ chế draft/xác nhận user-bound thuộc phạm vi Agent.

**C. Verification Only** (implementation có, thiếu evidence):

| Gate | Issue | Phạm vi | Trước |
|---|---|---|---|
| C1 | STD-003 | Public-read route matrix (anonymous/CUSTOMER/OWNER khác hotel) cho mọi GET Agent dùng; full REST/RPC actor matrix giữ cho G4. **9B: READ subset DONE; 9C: Agent WRITE routes DONE (service boundary); Gateway-level còn thiếu** | AI READ (subset) / AI WRITE (đầy đủ cho route Agent dùng) |
| C2 | STD-018 | Negative contract: search criteria, availability check, booking create → 4xx fieldErrors, không side effect. **9B: search + availability DONE; 9C: booking create HTTP DONE** | READ (search/availability), WRITE (create) |
| C3 | STD-028, STD-026 | Agent-facing contract suite tại Gateway: response schema + error envelope (400/401/403/404/409/503) cho API trong ma trận. **9B/9C: READ + WRITE service-level DONE; Gateway-level DONE 18/11 trên stack canonical disposable (87/87, [evidence](../evidence/batch-9c-canonical-smoke/README.md))** | READ / WRITE |
| C4 | STD-007 | TIMEOUT/UNKNOWN khi create: đọc trạng thái, tái dùng idempotency key, không tạo booking thứ hai. **9C: service + HTTP DONE** (key forward nguyên vẹn, replay trả cùng booking; service tests replay/409/hold-timeout/compensation); Gateway lost-response (socket đóng trước khi đọc) + retry cùng key → đúng 1 booking/1 hold trên stack canonical | AI WRITE |
| C5 | STD-030 | Một lỗi correlated Gateway→Booking→Hotel (Gateway hiện không có tracing dependency). **9C: Booking `RestClientConfig` dùng `new RestTemplate(factory)` → Booking→Hotel không propagate trace; runtime-confirmed trên stack canonical (Gateway→Booking OK); cần fix nhỏ được duyệt** | AI WRITE |
| C6 | STD-012 | Rà soát legacy UI surfaces ngoài search/detail (frontend; không chặn Agent backend) | Frontend core |
| F1 (FIX, LOW, không chặn AI) | STD-026 | `lib/api/hotels.ts` `getHotelById` và owner/admin writers dùng `fetch` không deadline, lỗi chứa `statusText` → chuyển sang helper có deadline/typed error hiện có. **DONE 9C** (`tests/hotel-adapter-deadline.test.mjs`) | Frontend core |

**D. Deferred** (không thuộc AI MVP): STD-002/007 provider refund/QueryDR/IPN (G1b); STD-007/008 crash/restart/multi-node; STD-021 backup/PITR/restore, Test-profile `ddl-auto: update`, legacy import/cutover (U03); STD-024 historical corpus; STD-025 round-trip floor/SLO (8D next); STD-027 log ingestion + backend cloud deployment; STD-029 operator replay/DLQ/retention, legacy cash reconciliation.

**E. Excluded — External Owner** (không tạo implementation task): `place-service/**`; phần Place/Nearby của STD-011/012/023/026/031/032 (NearbyPlacesSection, PlaceCard, PlaceReviewsModal, `lib/api/places.ts`, nearby origin trong `app/hotel/[id]/page.tsx`). Không coi là DONE, không là blocker của AI MVP không dùng Place.

#### Recommended next batches

| Batch | Scope / Issue IDs | Dependencies | Expected source changes | Tests | Acceptance | Risks | Complexity |
|---|---|---|---|---|---|---|---|
| **9B — AI READ contract verification** — **DONE (service boundary) 17/11, [report](../BATCH9B_AI_READ_CONTRACT_VERIFICATION_2026-11-17.md)** | C1 (public subset), C2 (search/availability), C3 (READ APIs) — STD-003/018/026/028 | Không; dùng Test mode (`docker/.env.test`, local PostgreSQL) hoặc native disposable; không hosted write | Chỉ test/harness + tài liệu tool contract; không đổi API | MockMvc/socket contract tests hotel-service; Gateway read smoke trên stack test; anonymous/cross-owner negatives | Mỗi API READ trong ma trận có schema snapshot + error envelope + public/private negative có output thực; không Place | Stack test cần seed approved inventory qua workflow thật, không SQL bypass | LOW–MEDIUM |
| **9C — AI WRITE pre-gate verification + F1** — **DONE (service boundary + F1) 18/11; Gateway smoke NOT_RUN, [report](../BATCH9C_AI_WRITE_PREGATE_VERIFICATION_2026-11-18.md)** | C1 (route Agent dùng), C2 (create), C3 (WRITE APIs + Gateway-level READ smoke từ 9B), C4, C5; F1 frontend | 9B harness | Tests; F1 sửa adapter frontend nhỏ; tracing Gateway chỉ khi được duyệt (dependency/config) | Create/cancel/status với CUSTOMER JWT qua Gateway; injected timeout → readback + same key; correlated trace một lỗi | Không duplicate booking dưới timeout; stale price 400; approval 409; một trace ID xuyên Gateway→Booking→Hotel | Thêm dependency tracing phải review; không tự retry write | MEDIUM |
| **10 — AI Agent READ MVP** (ngoài standardization) | Read tools trên public hotel API | 9B DONE | Agent code mới (không sửa API) | AI evaluation query set; tool schema validation | Agent không bịa facts; phân biệt no-result/lỗi/unapproved | LLM output untrusted | MEDIUM |
| **11 — AI Agent WRITE (booking assist)** | Draft → xác nhận → create unpaid; trao URL VNPay Sandbox | 9C + 10 | Agent code; draft/expiry/one-time confirmation | Confirmation required; timeout recovery | Không mutation nếu không xác nhận; không payment/refund tự động | Sandbox ≠ production | HIGH |

Không mở lại Batch 8E. Deferred workstreams (G1b provider refund, production ops, 8D perf follow-up, legacy cutover) cần quyết định riêng.

#### Batch 9E — kết quả gate và điều kiện trước Batch 10

| Gate | Kết quả 9E |
|---|---|
| C1 STD-003 | READ subset + Agent WRITE routes VERIFIED (service + Gateway canonical); full actor matrix còn G4 |
| C2 STD-018 | Search/availability/booking-create VERIFIED + giới hạn F-9B-3 |
| C3 STD-028/026 | READ + WRITE tại Gateway canonical 104/0 |
| C4 STD-007 | Lost response + retry cùng key VERIFIED (Gateway); crash/multi-node còn |
| C5 STD-030 | Booking→Hotel VERIFIED; auth/revenue/user + Gateway bridge còn → gate trước AI WRITE |
| C6 STD-012 | Rà soát xong; **F-9E-1** cần sửa trước AI WRITE |
| F1 STD-026 | DONE (9C); phần FE còn lại là F-9E-2 |

**Điều kiện trước Batch 10 (Agent READ MVP):** R1 review + commit thay đổi 9A–9E ở cả hai repo (hiện chưa commit); R2 bộ query/tiêu chí đánh giá AI; R3 Agent tự đặt deadline ≤10 s và giới hạn tần suất; R4 nếu chạy trên hosted Supabase-dev: deploy bản 9D và chạy lại READ smoke ở đó; R5 chỉ dùng route trong [READ_TOOL_CONTRACT](../ai-agent/READ_TOOL_CONTRACT.md), anonymous, không Place. Không có defect code nào chặn Agent READ.

**Gate trước AI WRITE (Batch 11):** ~~F-9E-1~~ (9F); STD-030 Gateway bridge (client auth/revenue/user xong 9F); ~~STD-026 DB outage 503 ở service còn lại~~ (9F); F-9F-1 nếu Agent dùng user-service write; STD-007 crash/multi-node; draft user-bound + xác nhận một lần; refund không cho Agent.

**Đề xuất batch:** 9F nhỏ (F-9E-1, F-9E-3/4, STD-030 còn lại, STD-026 503 còn lại) — có thể song song Batch 10.

## A. Executive summary

> HISTORICAL — SUPERSEDED BY BATCH 9A (§0). Counts, risks và readiness dưới đây là trạng thái tổng hợp 13/11. Ví dụ: rủi ro 3 về TypeScript `showDistance` không còn tái hiện (`tsc`/build PASS ở 5B, PR review 17/11 và Batch 9A); STD-006 hiện FIXED_VERIFIED; G2 không còn là “NEXT”.

The implementation is substantially stronger than the initial inventory: principal-bound booking operations, server pricing, fail-closed inventory, idempotent creation, durable reservation/loyalty recovery, published search offers, canonical catalogs, pagination, and a bounded media client exist. This is not evidence that the deployed system is ready for unrestricted AI operations.

The consolidated assessment is **16 FIXED_VERIFIED, 12 IN_PROGRESS, 3 FIXED_UNVERIFIED, and 1 NEEDS_ENVIRONMENT**. NOT_STARTED and BLOCKED: zero. Open means any status other than FIXED_VERIFIED: **P0 4/7; P1 7/17; P2 4/7; P3 1/1**. These are issue counts, not completion percentages. Verification is against each issue's defined acceptance scope; verification of a local contract does not certify deployment or every neighboring feature.

Most important remaining risks:

1. **DONE — Refund Backend/API Demo Accepted:** opt-in Sandbox Internal Refund completes locally with Gateway/PostgreSQL/RabbitMQ/Revenue evidence. It does not move real money. Provider refund/query reconciliation remains unimplemented, not merely missing credentials; STD-002/007 remain IN_PROGRESS. Disabled internal execution returns 503 without financial mutation. See D1 for scope and evidence.
2. Schema/application history is not reproducible across every database. Hotel/booking still use `ddl-auto: update`; root SQL, `docker/sql`, and user Flyway migrations coexist. Historical physical inventory and cash provenance require review, not automatic correction.
3. Current nearby-place source contradicts broad claims that all public location facts are provenance-safe: raw coordinates reach a nearby request, road labels are unconditional, and failures become an empty result. The existing `showDistance` prop mismatch also prevents a clean standard TypeScript check.
4. Full-lifecycle/production gateway and provider verification, multi-node concurrency, clean provisioning, and cross-service tracing remain incomplete. Historical local PostgreSQL tests and intercepted browser tests are useful but have narrower meaning.

**AI READ:** suitable for further work on an isolated, explicitly scoped hotel-search tool; not approved for deployed unrestricted READ, nearby recommendations, or availability guarantees. **AI WRITE:** not ready. G1 isolated unpaid Booking–Hotel/Availability Gateway lifecycle is now accepted by Batch 2; G2 schema/applied-history/provenance is the single recommended next workstream. Provider-backed refund remains a preserved future requirement (G1b), outside the accepted demo scope; Place work is excluded from the next workstream.

Evidence policy: “fresh” in the original sections/appendix means executed on 12 November; Batch 1.7 only inspects retained evidence and current source. Batch 1–1.6 evidence is attributed to its own report/run, never a fresh Batch 1.7 test. “Historical” below retains its original dated meaning. Originally, “fresh” meant executed in this audit. “Historical” means a checkpoint or retained local log, checked against current source where indicated, not a fresh runtime certification. No live database, provider, Docker mutation, browser server, migration, dependency installation, staging, commit, push, or branch creation was performed. Browser suites were not rerun because their checked-in configurations start services, explicitly prohibited for this audit. Existing backend tests use mock application contexts/loopback fixtures; database opt-in flags were explicitly disabled for the fresh Maven command.

## B. Git and working tree status

**Batch 2 initial state:** backend `feat/refund-reconciliation-workflow`/`332f97a66c02b89abfd04720f73c9cc6d6cca47a`, only two pre-existing unstaged IDE edits; frontend `main`/`d446b9808f8732c9982da2c156979e245742aa7f` clean. No staged/nonignored untracked files at start. [Inventory and traceability](../evidence/batch-2-booking/README.md). Historical branch/root-location statements below retain their dated meaning.

**13 November Batch 1.7 historical review:** backend remains master/27f1d9817c7e50182c1102c5f8b3c5294d4851fd, but now contains unstaged/untracked Refund implementation, tests, SQL, Docker harnesses and reports from Batch 1–1.6 in addition to the two IDE edits. Frontend remains main/d446b9808f8732c9982da2c156979e245742aa7f and clean. These are pre-existing Batch 1.7 inputs, not documentation-batch changes. The detailed snapshots below describe **12 November before Refund work**, not the current working tree. Root PLAN/AUDIT remain outside both repositories.

### Backend: `C:/Users/ACER/IdeaProjects/DA1/checkinn`

- Branch: `master`; HEAD: `27f1d9817c7e50182c1102c5f8b3c5294d4851fd` (merge PR #28, media metadata). Local `origin/master` points to the same commit. No fetch was performed; this is not a claim about current remote server state.
- Modified, unstaged: `.idea/compiler.xml` (+2 lines adding place-service compiler entries) and `.idea/encodings.xml` (+1 place-service UTF-8 entry).
- Staged: none. Untracked nonignored files: none. Application-source working-tree differences: none. `git diff --check`: fresh exit 0.
- The two paths match the pre-73 IDE-edit description. Their exact current bytes cannot be proved identical to the start of checkpoint 73 without its original hashes/snapshot. Attribution: consistent with pre-existing IDE changes, not conclusively dated by Git.
- `22627ba` exists and is an ancestor of HEAD. Historical “backend HEAD 22627ba” is therefore valid as a checkpoint base, not as today's HEAD.
- `fix/media-client-write-state` points to `7dffb65e966216b226d1019f9fb213e5bafad9ce`, a documentation commit dated 11 November. It is **not an ancestor of current master**. Its checkpoint follow-up in `hotel-service/MEDIA_METADATA_2026-11-10.md` is absent from master.
- Direction matters: `git diff HEAD fix/media-client-write-state --stat` shows 74 files, +337/-2489. This is branch-tree divergence, **not uncommitted deletions**. Current master additionally contains `ad58020` event/cash reporting and `9a27cee` notification work merged after the branch base. Do not replace master files wholesale from the older branch to recover its note.

### Frontend: `C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend`

- Branch: `main`; HEAD: `d446b9808f8732c9982da2c156979e245742aa7f` (merge PR #22, media-client write state). Local `origin/main` agrees; remote server was not queried.
- Modified, untracked nonignored, and staged files: none. HEAD versus working tree: clean. `git diff --check`: fresh exit 0.
- `b4e5bf04128898ba1d786737bb9ccd9e89ec0db7` exists as the metadata/content checkpoint base.
- `fix/media-client-write-state` points to `f158225` (`fix(media): serialize client writes and reconcile unknown outcomes`), now merged. `git diff f158225 HEAD --name-status` is empty: the current frontend tree is exactly the checkpoint implementation commit tree.
- The documented 16 pending frontend paths are now committed, not missing. `git diff b4e5bf0 f158225 --stat` identifies those 16 paths and +779/-455 lines.

### Attribution and preservation

| Category requested | Backend | Frontend |
|---|---|---|
| Before checkpoint 73 | 22627ba metadata base; two IDE edits are consistent with the documented pre-existing paths | b4e5bf0 content/media base; other already-existing merged work must be distinguished by ancestry |
| Introduced by checkpoint 73 | Documentation follow-up 7dffb65, available on branch but not master | 16-path media-client commit f158225, tree present on main |
| After checkpoint 73 | Current branch changed to master; merge ancestry includes older event/notification work absent from the checkpoint branch | Merge commits, including d446b98; no tree delta from f158225 |
| Unknown origin | Exact timing/content identity of unstaged IDE edits; no original pre-checkpoint byte snapshot | None among current nonignored working-tree changes |

Commit author dates alone cannot establish when another user changed branches, staged, committed, pushed, or merged. The checkpoint's “no commit/push in that session” cannot be independently proved from today's Git tree. Later commits prove that the changes were subsequently recorded. The root DA1 directory is outside both Git repositories; the plan and this report need separate preservation. No additional repository was identified in the inspected project layout; no claim is made about unrelated directories elsewhere on the machine.

## C. Master issue status table

> HISTORICAL — SUPERSEDED BY BATCH 9A: bảng hiện hành là [§0.2](#02-master-issue-status-current). Bảng dưới là trạng thái 13/11, giữ làm evidence lịch sử.

Evidence keys in the table are defined immediately below it. Each row separates implemented behavior from acceptance/verification still missing. Status mapping for this review: FIXED_VERIFIED means DONE only for the accepted issue scope; IN_PROGRESS means PARTIAL; FIXED_UNVERIFIED means implementation exists but acceptance is NOT VERIFIED; NEEDS_ENVIRONMENT identifies missing environment evidence. BLOCKED/NOT IMPLEMENTED/NOT VERIFIED in D1 and G are sub-scope labels, not silent changes to all parent issues.

Historical criteria are taken from the original inventory and acceptance sections, interpreted with subsequent checkpoint decisions; an old intermediate IN_PROGRESS note does not override later acceptance.

| Issue | Priority | Current status | Implementation progress and acceptance supported | Verification evidence | Remaining work / next action |
|---|---|---|---|---|---|
| STD-001 | P0 | FIXED_VERIFIED | Original cross-resource booking access replaced by principal/resource checks; create rejects another user; status/count operations restricted; REST and gRPC share access policy. | AUTH: BookingAccess, controller annotations, JWT interceptor; BookingAuthorizationTest and GrpcBookingAuthorizationTest; historical direct/gateway 20-case matrix. | Preserve matrix on deployed gateway; external RPC callers remain unverified. Lifecycle is STD-007, not an unclosed authorization criterion. |
| STD-002 | P0 | IN_PROGRESS | Payment reads/create/onsite/refund roles guarded; REST verifies signed fields; insufficient legacy callback RPC denied. Request/approval/rejection and opt-in SANDBOX_INTERNAL execution are DONE for Backend/API demo; disabled execution remains 503. | PAY: current controller/service guards; [Batch 1.3](../BATCH1_3_REFUND_INTEGRATION_FINALIZATION_2026-11-12.md), [Batch 1.5](../BATCH1_5_REFUND_GATEWAY_ACCEPTANCE_2026-11-13.md), [Batch 1.6](../BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md). | Implement provider query/refund, durable unknown-outcome reconciliation and verify real designated provider transactions. No provider settlement evidence. |
| STD-003 | P0 | FIXED_UNVERIFIED | Hotel/room/city/catalog/media/review authorization and JWT service identities exist, including owner/admin preview and parent-locked media writes. | AUTH/CATALOG/MEDIA; CatalogAuthorizationTest, MediaAuthorizationTest, ReviewAuthorizationTest, ReviewGrpcAuthorizationTest, MediaAuthenticationInterceptorTest. Historical denied native/socket checks. | Need consolidated current REST/RPC route-by-route deployment matrix, including authorized storage/private signing. No missing guard inferred merely from a broad permitAll fallback. |
| STD-004 | P0 | FIXED_VERIFIED | Points mutation requires signed booking-service identity; reads self/admin/service. Reports bind actor to hotel; regulation snapshots ADMIN-only. | LoyaltyPointsController explicit requireService/requireRead despite user SecurityConfig permitAll; LoyaltyAuthorizationTest, ReportAuthorizationTest, RegulationBoundaryTest; historical native authorization. | Recheck deployment/callers; financial formula/history gaps belong to STD-021/029, not authorization. |
| STD-005 | P0 | FIXED_VERIFIED | Booking validates canonical room/hotel/capacity/basePrice before hold, computes server totals and snapshots; payment derives booking amount; provider merchant/amount verified. | BOOK/PAY; BookingService catalog checks and immutable quote use; HotelCatalogClientTest, BookingServiceTest, PaymentServiceTest and signed controller tamper tests. Historical native price/relationship tests.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Preserve base-price/full-payment scope; do not imply support for unimplemented rate plans, taxes or partial payments. |
| STD-006 | P0 | IN_PROGRESS | Missing/nonpositive stock fails closed; no implicit ten-room creation; negative/overcommitted counters reject. Owner proposal/admin review workflow exists. | INV: requireTotalRooms, InventoryCounterSafetyTest, RoomInventoryReviewServiceTest; historical 48-case review and 25-case counter tests.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Review legacy physical-stock evidence. Existing value 10 is not proof of corruption. No fresh data provenance audit or production rollout verification.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-007 | P0 | IN_PROGRESS | Onsite and paid V1 durable intents/retry/review prevent false CONFIRMED; original hold only; cancellation/release is replayable; cash receipt is separate from reservation. Internal refund is demo-accepted, not provider settlement; Refund-only stack lacked Hotel; Batch 2 separate full dependency stack now accepts unpaid create/cancel Gateway. | BOOK/PAY/INV: PaidReservationService/Recovery, OnsiteReservationService/Recovery, scheduler, unit suites; historical paid44/cancel76/onsite38 and related native evidence.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Provider settlement/refund/query, unresolved paid/no-stock review and real service crash/multi-node recovery still need closure; Batch 2 closes the isolated unpaid G1 integration gap; paid/provider/crash/multi-node scope remains open. Do not allocate replacement rooms or invent refunds.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-008 | P1 | FIXED_VERIFIED | Confirmation checks absolute expiry inside locked transition; room/hold locks and counter guards serialize creation/confirm/release/cleanup. | INV repositories and AvailabilityService; AvailabilityServiceTest, HoldCleanupSchedulerTest; historical real PostgreSQL/native hold-concurrency 11 cases.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Re-run database suite for release; multi-node throughput/deadlock testing is additional operational assurance, not evidence already obtained. |
| STD-009 | P1 | FIXED_VERIFIED | Command hash/input binding and idempotent hold command prevent duplicate booking; unknown hold errors remain unavailable; known rollback resources compensated, including after transaction completion. | BOOK: BookingIdempotencyTest, BookingCreationCompensationTest, BookingCreationCompensation; historical native idempotency/fault tests and later commit-failure coverage.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Preserve UNKNOWN-commit safety; no blind release on ambiguous commit. Prolonged orphan cleanup relies on expiry/recovery and requires deployment monitoring. |
| STD-010 | P1 | FIXED_VERIFIED | Each result's price, capacity and dated available count come from the same active room offer and requested quantity. | SEARCH: HotelOfferSearchService eligibleRooms/match/availabilityFor; HotelOfferSearchServiceTest, offer controller tests, frontend search-inventory tests; historical native matching. | Contract remains one room type/base price; availability is observation, never reservation guarantee. |
| STD-011 | P1 | IN_PROGRESS | Hotel search/availability and media preview distinguish empty success from errors, with typed safe failures. | SEARCH/MEDIA; fresh Node search-errors/api-errors/media suites pass. Counterevidence: NearbyPlacesSection catch resets places to []. | Preserve completed hotel-search scope; fix nearby error-vs-empty state and SDK deadline/error contract. Current broad issue cannot be called universally complete. |
| STD-012 | P1 | IN_PROGRESS | Hotel search uses source-backed review/amenity/price/dated stock; unknown room facts remain unknown. Galleries preserve ACK/partial failure and freeze controls. | SEARCH/MEDIA/UI; fresh Node room-facts, inventory, media-state suites. Counterevidence: PlaceCard and PlaceReviewsModal always label distance as road/time without checking optional provenance. | Correct nearby fact rendering and raw-coordinate flow; verify other visible legacy surfaces before claiming every public fact is sourced. |
| STD-013 | P1 | FIXED_VERIFIED | Shared criteria round-trip and budget/filter patches avoid resetting unrelated filters; URL is authoritative. | Frontend lib/search/criteria.ts, SearchFilters and SearchPage; fresh search-filters/accommodation tests; historical controlled browser navigation. | Keep default/unit/AND-OR contract in future tool schema; deployed browser verification pending separately. |
| STD-014 | P2 | FIXED_VERIFIED | Global filter/sort before paging, stable identity ties, totals and bounded page contract implemented; no client-only global sorting claim. | SEARCH: HotelOfferPagingTest, HotelOfferControllerTest; fresh search-pagination tests; historical actual 84-case paged API regression. | Candidate population still evaluated in memory; optimize only against workload evidence without changing global semantics. |
| STD-015 | P1 | FIXED_VERIFIED | Accommodation type is model/DTO/catalog/query data, with explicit unknown handling and reviewed changes; UI filters real codes. | CATALOG: AccommodationTypeServiceTest, entity/request/response and catalog service; fresh accommodation-type tests; historical migration/native evidence. | Deployment schema/data manifest remains STD-021; no automatic hotel classification. |
| STD-016 | P1 | FIXED_VERIFIED | Canonical amenity codes and ADMIN-managed reviewed property mapping; active literal legacy labels remain compatible; renaming label does not redefine canonical meaning. | CATALOG: AmenityCatalogService/Repository, AmenityCatalogServiceTest, opt-in query test; fresh amenity-write and filter regressions; historical admin/mapping native tests. | Mapping completeness is a data-review task; never guess aliases or auto-backfill. |
| STD-017 | P1 | FIXED_VERIFIED | City/province typed IDs propagate to backend relational expansion; DTO and selected search contracts match. | SEARCH: LocationSearchIntegrationTest, location criteria and city contracts; historical native location29 and chapter29/38 evidence; fresh search criteria tests. | Nearby route props/type error is real but does not demonstrate wrong city/province identity resolution; report it separately. |
| STD-018 | P1 | FIXED_UNVERIFIED | Bean/manual validation, cross-field stay/capacity/range checks, safe field errors, content/amenity/media wire ACK checks exist. | DTO validators, HotelOfferCriteria, BookingValidationControllerTest, CatalogValidationTest, HotelContentValidationTest, AmenityWriteValidationTest; fresh Node invalid-input tests. | No consolidated current negative-contract inventory proves every REST/RPC/write has no pre-validation side effect. Keep scoped verified cases; verify remaining user/place/storage boundaries explicitly. |
| STD-019 | P1 | FIXED_VERIFIED | Room description request/entity/response round-trip uses canonical text without success on ignored input. | RoomType DTO/entity/service, RoomDescriptionTest; canonical-text Node tests; historical actual API/DB/UI round-trip in checkpoint33/45. | Preserve plain-string/null compatibility; production historical values need provenance review under STD-021. |
| STD-020 | P1 | FIXED_VERIFIED | Supported credentials map to internal identity/token; unsupported Google disabled; password reset proof and account-wide session generation enforced including RPC validation. | AUTH: SessionTokenIssuanceTest, AuthGrpcRevocationTest, UserRpcAccessTest, PasswordResetProofTest; opt-in Redis tests; fresh auth-session Node tests; historical real session/reset acceptance. | Real deployment revocation store availability/callers must be checked. Google is unsupported, not a verified external exchange. |
| STD-021 | P1 | IN_PROGRESS | Reviewed additive SQL and test-clone replay evidence exist; user Flyway V3-V5 protects points contracts; inventory review records evidence. | DATA: docker/sql, root SQL, user migration tree; hotel/booking application ddl-auto update; historical clone-only migration reports.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Build applied-history manifest and clean+upgrade schema diff for all services. Audit physical stock, historical cash, JSONB provenance without rewriting records. Batch 1.5/1.6 clean disposable Refund bootstrap is verified for that harness only (D1); production applied-history/representative legacy upgrade is NOT VERIFIED.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-022 | P1 | FIXED_VERIFIED | Immutable reservation quote/rate/cap, serialized command receipts and durable USE/EARN/CANCEL recovery meet original ledger criteria. | LoyaltyPointsService/CommandLock, LoyaltySettlementService/Recovery, LoyaltyPointsServiceTest; historical PostgreSQL concurrent reserve/spend/reversal and rollback acceptance in checkpoint24. | Do not reopen for independent provider defects. Legacy ambiguous ledger/history still requires reviewed data handling, not automatic correction. |
| STD-023 | P1 | IN_PROGRESS | Public hotel/room/review publication and private preview authorization implemented across relevant read boundaries. | CATALOG: HotelGrpcPublicationAuthorizationTest, hotel publication tests and historical chapter27 native. Counterevidence: hotel/[id] nearby section passes raw address coords despite hotelCoordinates helper. | Preserve verified publication sub-scope; close public coordinate-provenance dependency for nearby requests before declaring all original criteria met. |
| STD-024 | P2 | FIXED_UNVERIFIED | Explicit canonical text codecs/mappers, frontend compatibility adapters, structured content/media ACK guards and serializer-based regulation snapshots exist. | HotelMapperTextTest, RoomDescriptionTest, fresh canonical-text/content/media tests; historical canonical-text native33 and regulation round-trips. | Historical runtime encoding/migration completeness not established. Verify representative raw/JSON-string/null/structured rows and document which adapters remain necessary. Do not remove adapters or rewrite legacy JSON blindly.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-025 | P2 | FIXED_VERIFIED | Bulk inventory, selective location, room/facet candidate batches, review summaries and post-page full mapping bound measured query fan-out; workload baseline exists. | SEARCH/PERF: opt-in query tests; retained target-search-read-baseline.log: 24 hotels/48 rooms, 18 requests, concurrency4, CLI-inclusive p50 713.8ms/p95 837.1ms. Historical, not fresh. | No SLO defined and no production-scale benchmark. In-memory all-candidate work remains a scaling risk. Original baseline criterion met, production scalability not certified. |
| STD-026 | P1 | IN_PROGRESS | Hotel/booking error envelopes and selected content/review/report/regulation/media clients have bounded safe contracts. | API helpers and typed SDKs; fresh api-errors/content/media regressions; historical MVC/socket/native fault tests. Counterevidence: nearby direct-route fallback and unbounded fetches; other hotel writers retain legacy errors. | Extend stable error/deadline including body to remaining published clients/RPCs; trace errors without raw response leakage. No universal timeout claim. |
| STD-027 | P2 | NEEDS_ENVIRONMENT | Dockerfile, Compose app/web/config profiles, healthy dependencies, test overlay and operational runbook exist. | OPS: Dockerfile, docker/docker-compose*.yml, docker/README.md. Historical built local test images and clone runtime.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Verify clean provisioning/config/database and frontend production build on designated environment. Existing schema update mode and remote config dependencies are not clean deployment evidence. Batch 1.5/1.6 isolated Refund stack is accepted (D1); no full clean deployment, frontend build or production migration certification. No Docker operations in Batch 1.7.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-028 | P1 | IN_PROGRESS | Large unit/MVC/socket suites, opt-in PostgreSQL suites and real native smoke harnesses exist; browser suites render real components but intercept APIs. | TEST evidence below; fresh Node and backend test results; historical clone concurrency/security evidence.  Batch 2: [fresh scoped HTTP/SQL/test evidence](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md); disposable unpaid lifecycle only. | Refund Gateway/API with real JWTs and PostgreSQL/RabbitMQ is DONE (D1); full booking→payment→cancellation→refund E2E, browser/provider, migration-upgrade and multi-node matrices remain NOT VERIFIED. Tests named IntegrationTest may be disabled or simulated; count executed separately.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-029 | P2 | IN_PROGRESS | Booking/regulation transactional outboxes, publisher confirms/returns, monotonic receipts/cash ledger, durable retry/DLQ and history gaps implemented on current master. | EVENTS: BookingEventDelivery, EventIngestionService, CashTransactionWriter, NotificationDeliveryTest, opt-in outbox/ordering tests; historical broker/native33 evidence. | Internal refund outbox→broker→Revenue singleton DEBIT and SQL/report net=0 verified in Batch 1.6 (D1). Real legacy cash reconciliation, operator replay/retention and deployment broker policies remain unverified. SMTP accept/ACK loss can duplicate email; exactly-once delivery not claimed.  Batch3: [bounded schema/history evidence](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md); parent/historical acceptance unchanged. |
| STD-030 | P2 | IN_PROGRESS | Safe selected auth/OTP/broker/report/media logs, request IDs and documentation improvements; signed media log removed. | AUTH/API/MEDIA; AuthLogPrivacyTest, JwtMdcFilterTest, RevenueBrokerPrivacyTest; fresh Node safe-error cases, historical browser console-argument test. | No demonstrated correlated error across current deployed services; trace IDs can be independently generated. Global legacy logs/metrics/ingestion and place type blocker remain. |
| STD-031 | P2 | FIXED_VERIFIED | SearchPage effect-local current guard blocks stale writes/errors/redirects; URL/form lifecycle and civil dates tested historically. | SearchPage current checks/cleanup, HotelSearchBar criteria sync; historical adversarial search-lifecycle browser tests; fresh Node calendar/criteria regression. | NearbyPlacesSection has a separate unguarded fetch lifecycle; fix with nearby boundary batch. This does not negate the specifically defined SearchPage acceptance. |
| STD-032 | P3 | IN_PROGRESS | hotelCoordinates correctly accepts only bounded OWNER_PROVIDED/GEOCODED source for its callers, but not every public caller uses it. | GEO: hotels.ts helper vs hotel/[id]/page.tsx:1394 and unconditional PlaceCard:67 / PlaceReviewsModal:76. Git blame attributes road card to ae85b2e, 4 Nov. | Route nearby request/map origins through verified coordinates; require matching origin/road-provider metadata, finite distances and durations. No claim of confirmed corrupt stored coordinates or provider accuracy. |

### Evidence index and exact module anchors

All source paths below are current local files. Class/function names are durable anchors; line numbers in discussion were verified during inspection and may change later.

- **AUTH:** [BookingAccess](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/security/BookingAccess.java), [BookingController](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/controller/BookingController.java), [hotel SecurityConfig](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/config/SecurityConfig.java), [LoyaltyPointsController](C:/Users/ACER/IdeaProjects/DA1/checkinn/user-service/src/main/java/com/example/userservice/controller/LoyaltyPointsController.java), [UserRpcAccess](C:/Users/ACER/IdeaProjects/DA1/checkinn/user-service/src/main/java/com/example/userservice/security/UserRpcAccess.java), [media RPC interceptor](C:/Users/ACER/IdeaProjects/DA1/checkinn/media-service/src/main/java/com/example/mediaservice/security/MediaAuthenticationInterceptor.java).
- **BOOK:** [BookingService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/BookingService.java), [BookingCreationCompensation](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/BookingCreationCompensation.java), [PaidReservationService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/PaidReservationService.java), [BookingScheduledTask](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/BookingScheduledTask.java).
- **PAY:** [PaymentController](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/controller/PaymentController.java), [PaymentService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/service/PaymentService.java), [RefundRequestService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequestService.java), [PaymentGrpcServer](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/grpc/server/PaymentGrpcServer.java).
- **INV:** [AvailabilityService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Room/availability/service/AvailabilityService.java), [RoomAvailabilityRepository](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Room/availability/repository/RoomAvailabilityRepository.java), [RoomInventoryReviewService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Room/service/RoomInventoryReviewService.java).
- **SEARCH:** [HotelOfferSearchService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Hotel/service/HotelOfferSearchService.java), [HotelOfferCandidates](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Hotel/service/HotelOfferCandidates.java), [SearchPage](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/app/search/page.tsx), [criteria](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/search/criteria.ts), [room facts](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/search/room-facts.ts).
- **CATALOG:** [HotelServiceImpl](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Hotel/service/HotelServiceImpl.java), [AccommodationTypeService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Hotel/service/AccommodationTypeService.java), [AmenityCatalogService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/Amenity/service/AmenityCatalogService.java).
- **MEDIA/UI:** [media-write.ts](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/media-write.ts), [media-state.ts](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/media-state.ts), [media-upload-batch.ts](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/media-upload-batch.ts), [MediaMutationService](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/src/main/java/com/example/hotelservice/MediaAsset/service/MediaMutationService.java), [checkpoint73 test report](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/tests/MEDIA_CLIENT_2026-11-11.md).
- **GEO:** [hotel coordinates helper](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/hotels.ts:858), [raw nearby origin](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/app/hotel/[id]/page.tsx:1394), [NearbyPlacesSection](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/components/NearbyPlacesSection.tsx:65), [PlaceCard](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/components/PlaceCard.tsx:67), [PlaceReviewsModal](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/components/PlaceReviewsModal.tsx:76), [places SDK](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/places.ts).
- **EVENTS:** [BookingEventDelivery](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/messaging/BookingEventDelivery.java), [EventIngestionService](C:/Users/ACER/IdeaProjects/DA1/checkinn/revenue-service/src/main/java/com/example/revenueservice/service/EventIngestionService.java), [CashTransactionWriter](C:/Users/ACER/IdeaProjects/DA1/checkinn/revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java).
- **DATA/OPS/PERF:** [Docker runbook](C:/Users/ACER/IdeaProjects/DA1/checkinn/docker/README.md), [Dockerfile](C:/Users/ACER/IdeaProjects/DA1/checkinn/Dockerfile), [Compose](C:/Users/ACER/IdeaProjects/DA1/checkinn/docker/docker-compose.yml), [test overlay](C:/Users/ACER/IdeaProjects/DA1/checkinn/docker/docker-compose.test.yml), [retained search baseline](C:/Users/ACER/IdeaProjects/DA1/checkinn/hotel-service/target-search-read-baseline.log), [original plan](C:/Users/ACER/IdeaProjects/DA1/PROJECT_STANDARDIZATION_PLAN.md).

## D. Completed work and checkpoint 73 verification

> HISTORICAL — SUPERSEDED BY BATCH 9A: danh sách accepted hiện hành gồm thêm STD-006 (xem §0.2). Kết quả `tsc` exit1 bên dưới là lịch sử 12/11; 9A `tsc`/build PASS.

Fully accepted issues under their defined local scope: STD-001,004,005,008,009,010,013,014,015,016,017,019,020,022,025,031. Historical PostgreSQL/native evidence is explicitly distinct from freshly rerun unit/contract tests. For all other issues, the table identifies completed sub-scopes without promoting the parent issue.

Checkpoint 73 source is preserved exactly in the current frontend tree. The following behaviors were inspected and their Node cases freshly passed:

| Requirement | Current source behavior | Evidence / limits |
|---|---|---|
| Shared 15-second timeout including body | write() creates one AbortController/timer and clears it after awaited decode finishes | media-write.ts; fake-timer connection and slow-body Node tests. Real network/provider timeout not measured. |
| Typed safe failures | MediaWriteError exposes status and uncertain; no raw response text/statusText in messages | Node 400/401/403/404/409/503 and malformed/transport tests. |
| No mutation retries | One fetch per mutation; draft404 no alternate DELETE fallback | Fresh exact-call-count regression. |
| Strict ACK | Validates UUID/target/type, URL scheme/no embedded credentials, thumbnail/order; DELETE requires Deleted; draft structured message | Fresh malformed/cross-ID/target/null/false thumbnail/order/URL tests. SDK checks are not server authorization. |
| Unknown/pending state | Synchronous refs block overlap; uncertain/conflict state requires explicit preview GET; failed GET stays blocked | Four host routes inspected; Node state checks pass; rendered interaction evidence remains historical. |
| Authoritative GET recovery | Validates complete array, target ownership shape and duplicate IDs; empty array differs from outage | Fresh preview Node tests; actual server authorization independently belongs to backend tests. |
| Partial upload/delete | Delete ACK applied before follow-up thumbnail; MediaUploadBatch stops on failure, blocks replay and suppresses approval after partial upload | Fresh state/batch tests; historical rendered wizard browser case. |
| Multiple files/frozen controls | newHotel gallery input has multiple; media fieldsets and wizard controls gated while submitting/recovering | Source inspection; historical browser actually selects two files. Not freshly exercised in a browser. |
| Immutable sort | orderMedia sorts a copied array | Fresh frozen/source-array state test. |
| Signed URL logging removed | Selected gallery no longer logs hero media object; broader legacy console logs remain elsewhere | Source inspection; historical test captures console argument JSON, not merely console.text. |
| Browser coverage | media-write.spec.ts intercepts HTTP and renders actual Next/Edge UI | Historical 13 media +8 content +7 amenities =28 total. **Not 28 compatibility tests in addition to media**; compatibility alone is15. No real provider E2E. |

Fresh checks:

- `npm run test:regression`: **211 tests, 211 pass, 0 fail/skipped**, exit0. The 47 media additions are part of this total, not additional tests.
- `node node_modules/typescript/bin/tsc --noEmit --incremental false`: **exit1, one TS2322 diagnostic** at `components/NearbyPlacesSection.tsx(161,15)`, showDistance absent from PlaceCardProps. No tsbuildinfo write was requested. Standard typecheck is failing; a clean production build cannot be claimed. No build was started.
- `git diff --check`: exit0 in both repositories.
- Backend offline test execution and per-module results: see final verification appendix below.
- Browser media13/content8/amenities7: **not freshly rerun**. Checked-in Playwright webServer launches `npm run dev -- --webpack`; no existing listeners were found on3314–3316 during inspection. Starting them would violate this audit's constraint. Stored results in checkpoint73 are historical synthetic/intercepted browser evidence.

### D1. Refund demo closure (reviewed 2026-11-13)

Parent issue counts/taxonomy in C remain unchanged: STD-002/007/021/027/028/029 have newly accepted **sub-scopes**, not whole-issue closure. Overall standardization remains **PARTIAL / IN_PROGRESS**, not Ready WRITE.

| Scope | Status | Evidence and boundary |
|---|---|---|
| Sandbox Internal Refund Backend/API | **DONE — Refund Backend/API Demo Accepted** | [Batch 1.6](../BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md), corroborated by retained Gateway JSON/logs and current source |
| Request/approval/execution/rejection | DONE within demo | CUSTOMER request 201; same ADMIN approve 200 then execute 200; REQUESTED → APPROVED_PENDING_EXECUTION → REFUNDED, or REQUESTED → REJECTED. Payment/Request REFUNDED; Booking stays CANCELLED, paidAmount reduced once; original receipt/transaction metadata retained. [Batch 1.5](../BATCH1_5_REFUND_GATEWAY_ACCEPTANCE_2026-11-13.md), Batch 1.6 |
| Authorization/invalid states | DONE within tested API matrix | Anonymous 401, CUSTOMER/OWNER execute 403, ADMIN 200; foreign CUSTOMER request 403; unapproved/rejected/PENDING/mismatched binding/ineligible booking 409; missing payment/booking 404 after orphan-booking fix. Batch 1.6 |
| Retry/concurrency/lost response | DONE within tested scenarios | Two barrier clients from APPROVED_PENDING_EXECUTION return 200/200; intervals overlap. Test-only proxy discards response **after** commit; client RemoteDisconnected then Gateway retry 200. One completed execution, completion audit, logical outbox and DEBIT; no extra paidAmount reduction. Not all interleavings/distributed failover. Batch 1.6; restart/rollback harness in [Batch 1.3](../BATCH1_3_REFUND_INTEGRATION_FINALIZATION_2026-11-12.md) |
| Outbox/Revenue net | DONE within isolated fixture scope | payment.refunded DELIVERED through RabbitMQ/consumer; one original CREDIT + one SANDBOX_INTERNAL DEBIT, signed SQL net=0. Existing GET /api/reports/revenue returns 200/amount=0 for each unique fixture hotel/date scope. Batch 1.6 |
| Disabled mode | DONE within tested matrix | Valid approved ADMIN execute 503; payment COMPLETED, paidAmount unchanged, execution/outbox/DEBIT all 0. Application refund.internal.enabled / REFUND_INTERNAL_ENABLED defaults false on both producer/consumer. Smoke-only REFUND_SMOKE_INTERNAL_ENABLED maps into that flag; its dedicated overlay defaults true, not the application default. [Setup](../../docker/README.md#sandbox-internal-refund-demo-reviewed-2026-11-13) |
| Seeded completed-payment fixture | DONE / VERIFIED for Refund smoke | Real Auth/Gateway JWTs; booking/payment/original CREDIT seeded in disposable DB. Not a live checkout or full booking lifecycle. Batch 1.5/1.6 |
| Booking create/cancel Gateway E2E | BLOCKED in isolated Refund stack | POST /api/bookings 503 DEPENDENCY_UNAVAILABLE; no hotel-service registered. Catalog/availability/valid hold prerequisites missing. Not a production defect or a BLOCKED status for all Booking. G1 |
| Full booking→payment→cancel→refund E2E | NOT VERIFIED | Seeded completed payment and CANCELLED booking do not satisfy this scope |
| Frontend Refund request/status/history; ADMIN approve/reject/execute | NOT IMPLEMENTED | Current app/components/lib search finds messaging and admin placeholder, no Refund API client/actions; [booking SDK](../../../checkinn-frontend/lib/api/booking.ts). Browser Refund E2E NOT VERIFIED; existing Booking/Payment UI not reclassified |
| VNPay Refund API / provider query reconciliation / actual money movement | NOT IMPLEMENTED | RefundProvider is an interface without a production adapter; internal completion identity/time is not provider settlement. Preserved requirement G1b |
| Live VNPay Sandbox checkout; production settlement/migration/rollout; multi-instance failover | NOT VERIFIED | Not claimed by demo acceptance; no provider API called in these Refund batches |

Retained execution evidence: [focused log](../../.local/evidence/batch-1-refund/BATCH1_6_regression_final.log) **96 executed PASS, 2 opt-in SKIPPED, 0 failure/error, BUILD SUCCESS**, not the whole repository suite. Separate disposable integration runs each executed one composite JUnit scenario: [Booking initial](../../.local/evidence/batch-1-refund/BATCH1_6_booking-initial.log), [fresh JVM retry](../../.local/evidence/batch-1-refund/BATCH1_6_booking-retry.log), [Revenue pipeline](../../.local/evidence/batch-1-refund/BATCH1_6_revenue.log), all PASS/no skip. Integration ran before the small orphan-404 fix; focused and Gateway acceptance ran again after it. [Gateway JSON](../../.local/evidence/batch-1-refund/BATCH1_6_gateway_evidence.json), [post-fix log](../../.local/evidence/batch-1-refund/BATCH1_6_gateway_postfix.log), [disabled log](../../.local/evidence/batch-1-refund/BATCH1_6_disabled.log), [lifecycle log](../../.local/evidence/batch-1-refund/BATCH1_6_lifecycle_final2.log) match the report. No suite rerun in this documentation batch. Earlier Batch 1.2 profile/pay-URL gates were superseded by Batch 1.3; use the current opt-in flag, not those historical instructions.

## E. Remaining work by area

### Security and authorization

Build an explicit route/action inventory across public, owner/admin, service REST, and every RPC; map each to identity, ownership, failure policy and direct/gateway tests. Current booking and loyalty guards are substantive. Do not infer user-service compromise from its permitAll config without accounting for controller/RPC checks. Authorized storage success/private signing and callers outside the repos still need designated environment evidence. Preserve HMAC-only provider callback authority and refusal of the insufficient legacy callback RPC.

### Booking, payment and inventory consistency

| Scenario | What currently happens | Strongest available evidence and remaining gap |
|---|---|---|
| Expired hold/payment | Hold confirmation checks expiry under lock; scheduler locks booking before cancelling pending attempts | Unit and historical real PostgreSQL/native expiry/concurrency; no current deployed clock/multi-node validation. |
| Duplicate callback | Booking then payment locks; terminal replay validates facts and does not add money again | PaymentServiceTest and historical native signed callback/outbox smoke; provider notification delivery not tested. |
| Late success after cancellation | Records verified cash receipt once without reopening booking; durable paid recovery releases original reservation | Named lateSuccessRecordsCancelledPaymentOnceWithoutReopeningReservation and cancelledSourceWins tests; historical native paid/cancel tests. No completed provider-backed refund. |
| Success but expired/missing inventory | Paid recovery enters V1_REVIEW_REQUIRED for 400/404/409; does not buy replacement stock or claim CONFIRMED | PaidReservationServiceTest, historical native tests; reviewed provider resolution remains incomplete. |
| Remote unknown result | Retry durable original reference; malformed/unknown hold response is unavailable, not expired | Booking idempotency/paid recovery mocks and historical fault injection; no real provider lost-response experiment. |
| Concurrent last room | Parent/hold locks and persisted counter constraints serialize decisions | Historical real PostgreSQL/native11; current Maven default skips opt-in DB work; no multi-node load proof. |
| Local rollback after remote success | Transaction synchronization compensates known rollback resources; UNKNOWN does not automatically release | BookingCreationCompensationTest; historical deferred-commit evidence. Very long outages/restarts need operational reconciliation evidence. |
| Internal demo Refund | Opt-in request/approve/execute completes locally, replay safe; disabled execution 503 | DONE within Backend/API scope; Batch 1.6 and D1. No real money movement. |
| Provider refund/query | No provider adapter or settlement/reconciliation | NOT IMPLEMENTED capability; original financial requirement remains in G1b. |
| Booking lifecycle in Refund smoke | Catalog/availability absent; create returns503, cancel cannot be meaningfully accepted without a valid hold | BLOCKED environment/integration sub-scope; G1. Full booking/payment/refund E2E NOT VERIFIED. |

### Database and data integrity

Inventory10 can be legitimate; no evidence today establishes that particular historical records are corrupt. The review workflow is not retroactive proof of every count. Financial history with unknown timestamps/receipts remains a reconciliation gap; do not infer transaction day from receive time. Compare clean and upgrade schema to entities/DTOs, maintain service-specific applied history and explain legacy foreign keys rather than copying user tables between services. Inspect JSONB types/encoding through read-only queries in an approved environment before removing compatibility decoders. No SQL was executed today.

### Search and frontend correctness

Preserve same-offer matching, global paging and filter/URL controls. Place-related remediation remains protected/deferred and requires a separate authorized scope; do not change showDistance or shared contracts in Batch 1.7 or G1. The historical recommendation remains to close nearby failure/empty, provenance, road/time labeling and stale-response gaps together; do not silently substitute city origins or hide provider errors. Correct the explicit PlaceCardProps mismatch through a reviewed feature contract, not ignoreBuildErrors. No provider-specific coordinates or existing historical rows were changed in this audit.

### Infrastructure and deployment

Compose includes health checks and core dependencies, and a test overlay; an image build deliberately skips tests, so image success is not test evidence. Database provisioning/config-server profile, app discovery readiness, schema deployment order and production frontend compilation need reproducible acceptance. Test clone startup evidence does not prove a clean production setup or healthy OSRM. Monitoring log ingestion was not verified.

### Testing and observability

Keep separate ledgers for pure unit, MVC/filter, loopback socket, real PostgreSQL, native gateway/API, intercepted browser and real provider tests. Add a correlated failure across gateway→booking→hotel and async outbox/consumer with safe IDs; generated request IDs alone are not distributed tracing. Verify retries/DLQ operator permissions, retention and replay. Never call SMTP exactly-once across accept/ACK ambiguity.

## F. Verification gaps

1. Existing unit tests demonstrate branch logic, not SQL row locks or provider behavior. The opt-in tests explicitly require CHECKINN_RANGE_TEST/CHECKINN_SESSION_TEST; disabled containers are reported as skips, not executed scenarios.
2. Historical PostgreSQL tests have genuine transactions/rollback/concurrency, but test-clone data/configuration and earlier source snapshots limit inference to production. No live DB was contacted during this audit.
3. Native signed VNPay callbacks are generated using test configuration. Creating a URL or accepting a locally signed callback is not a VNPay transaction/provider refund/settlement. Separately, internal demo Refund is accepted via D1; seeded payment does not prove live checkout.
4. Browser tests intercept HTTP. They validate real rendering/event state under controlled responses; they do not prove browser→gateway→database→storage consistency. The documented transient content hydration timeout was preserved in checkpoint73; an unchanged rerun passed, root cause unestablished.
5. Authorized live storage upload/delete/signing, bucket ACL/RLS behavior and provider commit/database rollback reconciliation remain environment-dependent. Metadata transaction atomicity cannot roll back a cloud write.
6. Clean migration provisioning, representative legacy upgrade and backup/restore rehearsal lack consolidated acceptance. Stored script replay on a pre-existing clone is narrower evidence.
7. No current multiple-instance concurrency/partition/restart testing, production-scale search baseline, agreed latency SLO, or log ingestion/cross-service trace evidence.
8. Source-confirmed nearby boundary gaps are not environment blockers. The TypeScript failure is a source contract error. Fixing it is outside this audit, not impossible without Docker.
9. A complete endpoint-level audit of every implementation line and external caller is not certified by this report. The table provides inspected anchors and explicit missing evidence; uninspected/externally configured behavior must not be treated as secure or verified by implication.

## G. Ordered execution roadmap

> HISTORICAL — SUPERSEDED BY BATCH 9A: G0 “NEXT”/G2 không còn là workstream kế tiếp; backlog và batch đề xuất hiện hành ở [§0.6](#06-backlog-and-recommended-next-workstream-supersedes-g0-next). G1b, G3 (Place, EXCLUDED — EXTERNAL OWNER), G4, G5 giữ làm requirement gốc.

The original roadmap below was not executed in Batch 1.7. Batch 2 has now accepted G1 isolated unpaid lifecycle; G2 owner-confirmed historical-baseline follow-up is the **single recommended next workstream**; G1b preserves the original provider requirement without reopening accepted internal Refund. G2 may supply the minimal schema manifest prerequisites for G1; it does not require a provider adapter to start. Place-dependent G3 is excluded from the next scope.

### G0. Remaining workstream priorities (13 November review)

All 16 open parent findings are retained below; the other 16 stay FIXED_VERIFIED under their original scope. Priority is by correctness/risk and available prerequisites, not plan numbering. A real-money requirement can remain P0 while being excluded from the chosen internal demo.

| Priority | Workstream | Current status | Evidence | Dependencies | Recommended next action |
|---|---|---|---|---|---|
| Accepted demo subset | Booking–Hotel/Availability lifecycle (STD-007; STD-027/028) | DONE unpaid isolated Gateway; broader scope NOT VERIFIED | Batch 2: 57 assertions, SQL/expiry/fault/concurrency evidence | Test-only schema and project, not production/legacy certification | Preserve G1 acceptance; paid/provider/UNKNOWN/multi-node remain separately scoped |
| P0 for real money, deferred outside demo | Provider refund/query (STD-002/007) | NOT IMPLEMENTED capability; parent IN_PROGRESS | Current PaymentService/RefundProvider; D1 | Merchant-specific contract/permissions/test transaction; durable reconciliation implementation | Preserve G1b; no automatic next Refund batch or real-money claim |
| **NEXT** P0 stock / P1 schema / P2 encoding | Schema, inventory and historical provenance (STD-006/021/024) | IN_PROGRESS / FIXED_UNVERIFIED | Source ddl-auto update, split SQL/Flyway; D1 clean test bootstrap only | Service manifests, representative legacy fixtures and reviewed stock/receipt sources | Batch 2 minimal G1 bootstrap complete; next G2 clean+upgrade/restore evidence, no guessed backfills |
| P0 / P1 | Deployed catalog/media authorization and validation (STD-003/018) | FIXED_UNVERIFIED at full-scope acceptance | C source/test anchors; no new universal route-matrix evidence | Isolated service/storage environment, caller inventory | G4 non-Place actor/invalid-side-effect matrix; preserve accepted guards |
| P1 | Remaining HTTP/RPC/SDK error/deadline contracts (STD-026) | IN_PROGRESS; core scopes preserved | C/E contract suites; current Refund 404/409/503 evidence | Explicit published client inventory, scoped dependency faults | G1 lifecycle errors first; G4 remaining non-Place boundaries; do not call all clients fixed |
| P1 / P3, protected | Nearby truth/provenance/public boundary (STD-011/012/023/032) | IN_PROGRESS; historical typecheck failure, no new build run | Source still clears places on error and labels road time; C/G3 | Separately authorized Place/frontend task and provenance contract | Defer G3 under protected scope; no showDistance/shared-contract bypass |
| P2 / P1 acceptance | Clean deployment and cross-service/browser coverage (STD-027/028) | NEEDS_ENVIRONMENT / IN_PROGRESS | D1 Refund subset accepted; original deployment/browser gaps remain | Current schema/config, selected services; Place only for whole frontend build | Scope G1 Gateway now; G4 broader routes later, split actual provider/browser checks |
| P2 | Event operations, historical cash and observability (STD-029/030) | IN_PROGRESS | Refund debit/net accepted D1; durable receipts/outbox source exists | Permissioned replay/DLQ/retention, safe trace environment, legacy receipts | G5 runbook/metrics/trace and recovery; no global exactly-once claim |
| Demo product follow-up | Refund frontend | NOT IMPLEMENTED; browser NOT VERIFIED | Current SDK has no Refund actions, admin bookings placeholder | Accepted API/error/mode contract, UI scope decision | Separate future UI task if demo requires it; not a Backend/API closure gate |

STD-025 local measured baseline remains FIXED_VERIFIED; production-scale/SLO measurement is an additional G5 gap, not a newly reopened baseline finding. No unchanged finding is assumed re-tested today: uninspected runtime/legacy/deployment assertions remain NOT VERIFIED; no evidence means no promotion to DONE.

### G1. NEXT: Booking Hotel Availability Gateway lifecycle acceptance

**Batch 2 update: this heading/anchor is retained for historical links; G1 is no longer NEXT.** Unpaid create/hold/cancel/retry/auth/pricing/stock/concurrency and selected fault recovery accepted in project checkinn-booking-smoke. [Report](../BATCH2_BOOKING_HOTEL_GATEWAY_ACCEPTANCE_2026-11-13.md), [reviewed evidence](../evidence/batch-2-booking/README.md). Refund-only missing-Hotel environment gap resolved without changing that retained stack. Known-hold persistence compensation, availability rollback, release failure/retry, lost response and actual expiry scheduler verified; UNKNOWN boundary/crash/double-fault/paid/provider/multi-node not verified. No production source/policy change. The bullets below preserve original G1 requirements and exclusions, not claims that all broader gates are complete.

- **Issues/tasks:** STD-007/027/028; preserve STD-001/005/008/009 and scoped dependencies STD-006/021/026. PLAN S06/S08/S09/S10/S21/S23. This is an integration/test gap after internal Refund closure, not a source-confirmed production Booking bug.
- **Why now:** the nearest demonstrated demo boundary is missing Hotel catalog/availability in the Refund stack. Closing it exercises authoritative pricing, inventory and cancellation across services without provider access or Place changes. Financial correctness already accepted for internal Refund must be preserved; provider/legacy money risks stay open outside demo.
- **Prerequisites:** verify isolated resource ownership; fresh Hotel schema/bootstrap manifest, active/approved hotel and room, known positive inventory and future dates; current Auth/service identities, Gateway/discovery readiness and compatible Booking/Hotel migrations. Reuse source baselines only, never developer/shared data. If bootstrap expands into general migration work, stop at a concrete blocker and move that portion to G2.
- **Scope:** use existing services/endpoints; register/login real actors; seed catalog/stock only; create booking through Gateway, retain canonical quote/hold, cancel under unchanged accepted policy and prove inventory/points/status effects and safe replay. A follow-on internal Refund may use an explicitly labelled seeded completed payment; this does not certify live VNPay checkout.
- **Acceptance:** record pinned source/config and resource ownership; Gateway create succeeds with canonical room/hotel/amount and one hold; cancel succeeds with policy/status evidence and inventory release/intents completing exactly once; unauthorized/tampered requests and retries have no duplicate side effects; existing accepted Refund effects remain singleton if linked. Separate statuses for lifecycle, payment fixture, Refund and provider checkout. No Place dependency.
- **Exclude:** provider Refund/QueryDR, live payment unless separately scoped, frontend Refund UI, Place/showDistance, new services, Booking/Hotel architecture refactor, cancellation policy changes, production migration or multi-node certification. Propose fixes only for demonstrated defects within the authorized batch.

### G1b. Preserved backlog: provider-backed full-refund execution and reconciliation

The following original 12 November requirement remains unimplemented for provider-backed money; it is **not** the next demo workstream and does not invalidate D1. Its acceptance applies to a future provider-backed path, not the intentionally local SANDBOX_INTERNAL path.

- **Issues:** STD-002/007; scoped dependencies STD-021/028/029. Existing policy already separates customer/owner request from ADMIN approval/execution and requires reviewed cancellation. Do not introduce partial-refund policy.
- **Original rationale (12 November; now deferred outside demo):** this is a directly evidenced P0 capability gap: paid/no-room and cancelled-paid states have safe detection but no verified money resolution. More gallery polish, query optimization or extra mocks cannot resolve it. The nearby build/data boundary is important for READ but has less direct financial impact; keep that route excluded until repaired.
- **Dependencies:** confirm current approved cancellation/full-payment policy in plan18; obtain the applicable provider merchant contract, original transaction identity/time and a designated test account/transaction. Provider permission and settlement evidence must be supplied before enabling actual execution; do not treat sandbox configuration as permission/success. Reconcile backend branch ancestry first without replacing master source from the older checkpoint branch.
- **Concrete work:** add bounded query/refund adapter and request/response verification; persist a durable immutable refund command tied to payment, amount and approval; serialize execution; classify PENDING/UNKNOWN/verified success/failure with provider reference/evidence; query before any resend after ambiguity; preserve original payment receipt and record compensating financial event only after verified provider result; expose ADMIN review/retry and safe customer state; outbox the verified debit once. Review ambiguous legacy payment dates, never fabricate them.
- **Inspect/modify existing exact files:** [PaymentService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/service/PaymentService.java), [PaymentController](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/controller/PaymentController.java), [Payment entity](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/entity/Payment.java), [RefundRequest](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java), [RefundRequestService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequestService.java), [PaidReservationService](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/PaidReservationService.java), [PaidReservationRecovery](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/booking/service/PaidReservationRecovery.java), [HotelEventPublisher](C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/src/main/java/com/example/bookingservice/messaging/HotelEventPublisher.java), [CashTransactionWriter](C:/Users/ACER/IdeaProjects/DA1/checkinn/revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java), [EventIngestionService](C:/Users/ACER/IdeaProjects/DA1/checkinn/revenue-service/src/main/java/com/example/revenueservice/service/EventIngestionService.java), [payment SDK](C:/Users/ACER/IdeaProjects/DA1/checkinn-frontend/lib/api/booking.ts).
- **New code locations proposed, not existing artifacts:** provider adapter and refund worker/repository under booking-service payment/refund; reviewed additive SQL under checkinn/docker/sql; provider/native fixtures alongside checkinn/docker/booking-events-smoke.py. Choose final class/script names during implementation.
- **Required tests:** anonymous/cross-owner/customer execution denial; immutable amount/approval; duplicate/concurrent ADMIN execution; late success and cancelled booking; expired/missing hold; provider response lost after success; local commit failure after remote success; restart; query mismatch/timeout; conflicting reference; exactly one verified financial debit/outbox and inventory/loyalty unchanged by unverified outcome. Use real PostgreSQL for receipt/command locks, actual gateway for auth, and a designated real provider test transaction for final acceptance.
- **Acceptance:** no local REFUNDED/paidAmount reduction without verified provider evidence; no duplicate refund after retry/restart; UNKNOWN stays explicit and queryable; original receipt remains immutable; reports reflect one compensating transaction on authoritative transaction day; unresolved legacy records stay review-required. Mock green alone is insufficient.
- **Risks/rollback:** provider success cannot be rolled back by application rollback. Feature-gate execution; keep additive tables and receipts across rollback; disable new execution while continuing read/query reconciliation; never delete a pending command or replay blindly.

### G2. Reproducible schema and historical-data provenance

**Batch3 audit/verification DONE; G2 historical-baseline follow-up remains NEXT.** Use Batch 2 schema manifest as disposable evidence only. JPA baseline, non-idempotent Hotel V5 and accommodation compatibility SQL demonstrate the remaining applied-history gap; no production/legacy certification or financial backfill was performed. Parent STD-006/021/027 remain open.

- **Issues:** STD-006/021/024, dependent financial/reporting history.
- **Reason/dependencies:** reliable rollout and trustworthy inventory/cash facts require explicit applied schema and evidence; use current internal Refund/receipt schema as one input; provider-specific additions wait for G1b. General applied-history work does not depend on implementing provider refund. No speculative financial backfills.
- **Tasks:** inventory every service migration/root SQL dependency; service-specific manifest; clean vs legacy-upgrade schema diff; identify representative JSONB encodings; owner/admin stock review queue; provider-receipt-based cash gap review. Classify unknown, valid and confirmed invalid data separately.
- **Tests/acceptance:** isolated clean and representative upgrade databases, migration replay/constraints/indexes, unchanged baseline fingerprints, DTO/entity round-trips and restore rehearsal; all differences explained and no guessed historical rewrites.
- **Risk/rollback:** legacy DDL can be destructive; expand/validate first, backup/rehearse, retain evidence and compatible readers, no automatic cleanup.

### G3. Truthful nearby-place boundary and clean frontend compilation

- **Issues:** STD-011/012/023/026/032 and related UI lifecycle/testing.
- **Reason/dependencies:** concrete current public-source defects and build failure; provider-backed routing semantics and geo provenance need an explicit contract. Keep feature unadvertised in READ tools until accepted.
- **Protected/deferred:** not authorized in Batch 1.7 or the recommended G1; requires a separately authorized Place-related task. Original tasks retained for tracking: inspect hotel/[id]/page.tsx, NearbyPlacesSection.tsx, PlaceCard.tsx, PlaceReviewsModal.tsx, lib/api/places.ts, types/place.ts and hotelCoordinates; pass validated source coordinates; check provider/origin/finite metrics; distinguish error/empty; bound response reading; ignore stale nearby results; reconcile showDistance prop with actual intended rendering.
- **Tests/acceptance:** no request/pin/distance guarantee for centroid/unknown coords; false/missing road provenance never gets road/time label; API outage visible; deferred A/B cannot overwrite; tsc and production build clean; browser route cases plus actual API contract test, without replacing production data with fixtures.
- **Risk/rollback:** do not relabel straight-line as road distance; safe rollback disables nearby enhancements while preserving hotel search and source coordinates.

### G4. Deployed authorization and failure-path acceptance

- **Issues:** STD-003/018/026/027/028; preserve accepted STD-001/004/020.
- **Reason/dependencies:** accept the selected non-Place routes in a designated provisioned environment and close deployment uncertainty. Full nearby/build gates still depend on separately authorized G3; non-Place acceptance need not wait for it.
- **Tasks:** complete every route/RPC actor matrix, actual authorized storage lifecycle, gateway/direct consistency, input-side-effect checks and external-caller inventory; clean provisioning, health/discovery and bounded dependency outages; re-run browser suites sequentially on supported Webpack and standard production build.
- **Tests/acceptance:** current hashes/configuration/commands/output recorded; valid own-resource succeeds, guest/cross-owner/forged service fails; no invalid side effects; real provider tests separated from fixtures; all declared READ endpoints meet explicit contract.
- **Risk/rollback:** coordinated caller/security deployment and schema ordering; staged environment and old compatible readers; no bypass to make tests pass.

### G5. Operational recovery and measurable service behavior

- **Issues:** STD-029/030 and scaling follow-up to STD-025.
- **Reason/dependencies:** after correctness contracts stabilize, prove recoverability and monitor unresolved states instead of optimizing guessed bottlenecks.
- **Tasks:** outbox/DLQ/review queue runbooks and metrics; permissioned replay/retention; a correlated sync+async failure; workload-sized search/candidate measurement; multiple-instance contention/restart tests.
- **Tests/acceptance:** crash before/after broker ACK and SQL commit; no duplicate ledger impact; SMTP duplication limitation documented; trace one failure end-to-end; benchmark states data size/config and measured statistics with an agreed target rather than invented SLO.
- **Risk/rollback:** replay duplicates and private payload retention; use idempotent consumers, access control and retain immutable receipts. Performance changes must preserve offer and pagination invariants.

### Commands after the recommended batch

The commands below are retained 12 November recommendations, **not executed in Batch 1.7** and not an unconditional startup instruction. For G1, choose only the relevant booking/hotel authorization/lifecycle suites and a verified fresh disposable Gateway harness; the Refund runners do not provision Hotel. New provider tests apply only to future G1b. Existing scripts may write clone fixtures: verify their DB ownership/data policy before use; do not substitute a developer/shared database.

```powershell
# checkinn, using already installed dependencies
mvn -o -B -ntp -pl booking-service,revenue-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
python -X utf8 docker/booking-security-smoke.py
python -X utf8 docker/booking-events-smoke.py
python -X utf8 docker/availability-range-integration.py refund-review-regression revenue
# checkinn-frontend
npm run test:regression
node node_modules/typescript/bin/tsc --noEmit --incremental false
# Each repository
git diff --check
```

The integration command's existing `revenue` suite checks current ordering/cash behavior; it is **not a new refund-provider test**. Run it only on the designated clone after checking the harness safeguards and with service startup separately authorized. Add a dedicated real-provider acceptance procedure once the adapter/merchant contract exists. Existing typecheck is expected to fail until the separate G3 contract issue is fixed; report that baseline honestly rather than bypassing it. The same applies to `npm run build` after G3.

## H. AI Agent readiness assessment

> HISTORICAL — SUPERSEDED BY BATCH 9A: readiness hiện hành ở [§0.5](#05-ai-agent-readiness-supersedes-h).

| Operation group | Current assessment | Prerequisites before enabling |
|---|---|---|
| READ search/filter/hotel details | Core domain contracts largely accepted locally; deployed gate not certified | Current deployment route security, schema/data fixture provenance, truthful unknown/error handling, supported typed filter schema and bounded pages; actual API/gateway smoke at pinned hashes; quarantine/disable unsafe nearby scope. |
| READ availability | Dated observations and fail-closed unknowns exist; not a reservation | Verified runtime stock provenance/counters/expiry; expose dates, quantity, checkedAt, price unit and unknown state; do not promise inventory from a prior read. |
| READ recommendations/nearby | Not ready for location/road-time claims | G3 provenance/error/type contract plus provider-origin evidence; no invented amenities/ratings/discounts/distance, no fallback city coordinate presented as hotel fact. |
| WRITE creation/modification | Not ready for autonomous use | G1/G2/G4 relevant gates; current principal/resource auth, server quote and version/stale policy; booking command idempotency; real DB fault/concurrency recovery; define supported modification transitions rather than generic field writes. |
| WRITE payment initiation | URL creation and signed callback checks do not certify provider lifecycle | Provider test transactions, durable outcome/query reconciliation, cancellation/refund route, safe status readback; no blind retry or success inferred from model text. |
| WRITE cancellation | Internal Refund Backend/API demo DONE; production/provider financial closure and full lifecycle E2E remain incomplete | G1 lifecycle evidence; for real money G1b provider reconciliation, deployed replay/recovery and truthful cash/reservation distinction. Demo acceptance does not enable autonomous production WRITE. |

For all future WRITE tools, add a user-bound draft, server-produced quote/status, expiry/version and explicit one-time confirmation before a mutation. LLM output remains untrusted and must pass the same schema/resource policy; it must never synthesize a payment/booking success. Implementing AI tooling is not part of this audit.

## Final verification appendix (original run: 12 November 2026)

Fresh Maven command, completed 12 November at 11:19 Asia/Saigon, elapsed 6m45s, exit0 / BUILD SUCCESS:

```powershell
$env:CHECKINN_RANGE_TEST='false'
$env:CHECKINN_SESSION_TEST='false'
mvn -o -B -ntp -pl auth-service,user-service,hotel-service,booking-service,revenue-service,regulations-service,notification-service,media-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

| Module | Reported tests | Executed, passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| user-service | 103 | 101 | 2 | 0 |
| auth-service | 47 | 47 | 0 | 0 |
| hotel-service | 396 | 364 | 32 | 0 |
| media-service | 28 | 28 | 0 | 0 |
| booking-service | 241 | 240 | 1 | 0 |
| revenue-service | 39 | 38 | 1 | 0 |
| notification-service | 13 | 13 | 0 | 0 |
| regulations-service | 31 | 30 | 1 | 0 |
| **Total** | **898** | **861** | **37** | **0** |

Counts were read from freshly written Surefire XML and checked against the reactor log. Disabled parameterized containers may count as one skipped item: 37 skips are not proof of 37 executed database scenarios. The audit excludes Spring Boot ApplicationTests and explicitly disables opt-in persistent-database suites. Some executed tests exercise MockMvc/security contexts and loopback sockets; calling every executed test a pure unit test would also be inaccurate. No test-server processes were intentionally launched outside these test harnesses.

[Fresh backend execution log](C:/Users/ACER/IdeaProjects/DA1/checkinn/.local/evidence/batch-1-refund/AUDIT_2026-11-12_backend-tests.log) is an audit artifact outside the repositories; generated test security-password output is redacted. Existing Surefire files under each module's ignored target directory contain per-class detail. Frontend211 and backend861 are different suites; neither replaces historical PostgreSQL/native/provider acceptance.

Original 12 November preservation check (superseded by B for today): backend still had exactly the two unstaged IDE files; frontend remains clean; no staged/untracked nonignored repository files appeared. Both `git diff --check` commands exit0. Only this separate report, its execution log and normal ignored test/build output were created. The original standardization plan remains untouched. No implementation batch was started.


## Batch 3 checkpoint — 13/11/2026: G2 audit/verification closure

- **Audit/verification DONE; overall database standardization PARTIAL / IN_PROGRESS.** [Báo cáo Batch3](../BATCH3_DATABASE_SCHEMA_MIGRATION_STANDARDIZATION_2026-11-13.md), [schema/history/catalog evidence](../evidence/batch-3-schema/README.md). Production applied history NOT VERIFIED; không production/developer/shared DB access.
- **STD-021:** source/ownership/SHA inventory, fresh SQL-only failures của 5 services, test-only JPA+explicit SQL startup5 PASS; cash source-derived subset clean/upgrade MATCH. Full representative legacy upgrade NOT VERIFIED; User missing V1, Hotel V2 syntax/V5 duplicate, Booking baseline users FK/UUID mismatch. Existing migration history không rewrite; không đổi global ddl-auto.
- **STD-006:** review rules/counter constraints được kiểm thử, review migration không mutate synthetic NULL/10/invalid stock. Root historical SQL actively backfills10 là risk, application history UNKNOWN; actual physical provenance cần OWNER source/ADMIN review. Không auto-backfill/approve/repair.
- **STD-024:** fresh mapper/regulation serializer + PostgreSQL text/JSON shapes synthetic PASS; real historical corpus/native JPA all shapes/media provider NOT VERIFIED. Giữ selected fixes và adapters, không historical JSON conversion hoặc tự đánh dấu historical-data acceptance DONE.
- **STD-007/029:** cash/refund/loyalty uniqueness/FK/check/rollback probes PASS trong disposable; không historical cash/status repair, provider settlement hoặc fresh broker recovery claim. **STD-027/028:** fresh5 startup checks +26 Maven tests +24 PostgreSQL assertions; no full deployment/Gateway/frontend/Place suite acceptance; migration-only5 FAIL giữ riêng.
- **Next workstream:** G2 follow-up, owner-confirmed sanitized applied history + full source/release baseline per service, clean-vs-upgrade diff và versioned remediation; không guessed historical stock/cash repair. G4 broader deployment và G1b provider giữ scope riêng. Batch1–2 history và parent status/counts không thay bởi bounded tests.
- Place/backend/frontend/showDistance không đổi; Booking/Refund semantics giữ nguyên. No stage/commit/push/PR; existing IDE edits và deleted Batch2 review document giữ nguyên.


## Batch 4 checkpoint — 13/11/2026: Canonical Database Design

- **Design DONE; migration/deployment NOT DONE.** [Batch4 report](../BATCH4_CANONICAL_DATABASE_DESIGN_2026-11-13.md), [canonical schema V1](../database-design/CANONICAL_DATABASE_SCHEMA_V1.md), [ERD](../database-design/ERD.md), [Supabase architecture](../database-design/SUPABASE_DATABASE_ARCHITECTURE.md), [Batch5 implementation plan](../database-design/MIGRATION_IMPLEMENTATION_PLAN.md), [reviewed evidence](../evidence/batch-4-database-design/README.md).
- Năm supplied pgAdmin/pg_dump18 snapshots: 29 tables (including User Flyway history), 269 columns, 7 explicit secondary indexes; no COPY/INSERT. Source/SQL design: 46 app tables / 458 columns, 32 non-Place JPA entities, 32 repository interfaces, SQL/JDBC/outbox/audit coverage. Dump export database identity/applied history UNKNOWN; no Place snapshot, protected read-only.
- **Topology recommendation:** one Supabase project/database + user_service/hotel_service/booking_service/revenue_service/regulations_service, per-service roles/history. Auth no own database; Media metadata stays Hotel; no cross-service FK; Place outside migration scope. Environment/network/role/pool acceptance pending, not deployed.
- **STD-021 IN_PROGRESS:** complete design and discrepancy review exist; new exclusive canonical V1 chain proposed, original migrations/history preserved. No SQL-only V1/JPA validate/runtime acceptance executed in Batch4. D01–D06/D09 type/rate/time/delete/ID/target/seed decisions must resolve before Batch5 SQL freeze; retention/recovery D07 and cutover D08 before live rollout.
- **STD-006 OPEN:** explicit nullable stock/no default10 and OWNER-source/ADMIN-review audit design kept. Physical stock provenance is not supplied by schema dump, no automatic backfill/approve/counter repair. Booking/Refund/Revenue/Loyalty accepted semantics unchanged; no provider refund design added.
- **Next scope Batch5:** resolve design decisions, implement five fresh canonical Flyway chains/profiles/roles in isolated new target, clean/replay/catalog/JPA/query/guard/role/workflow/restore gates, then review concrete cutover. No legacy DB deletion or silent migration of old histories.
- No production source/config/migration/credentials/DB changes; Place backend/frontend/shared contracts unchanged; no stage/commit/push/merge/PR. Pre-existing Batch3/IDE/deletion changes retained. Earlier acceptance/status history remains scoped to its original checkpoint.


## Batch 4.1 checkpoint — 13/11/2026: UUID contracts / Five Supabase projects

- **DESIGN REVISION DONE; UUID migration/SQL/Supabase deployment NOT DONE.** [Report](../BATCH4_1_UUID_AND_SUPABASE_ARCHITECTURE_REVISION_2026-11-13.md), [ID standard](../database-design/ID_STANDARDIZATION_CONTRACT.md), [167 persisted contracts](../evidence/batch-4-1-uuid-topology/id-contract-inventory.md), [38-column impact matrix](../evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md).
- **Current topology decision, confirmed by user:** five existing independent Supabase projects/owner databases for User+Loyalty, Hotel, Booking+Payment/Refund, Revenue, Regulations. **Supersedes Batch4 one-project/five-schema recommendation.** Namespace proposal public per owner database, gated by Data API/grants/RLS/app-role security tests; no connection/config/deployment assumption, no Auth project, Place excluded.
- Four Booking/Item/Payment/Refund String entity PKs +34 typed references proposed UUID, pending U01 user design approval before implementation. User/Hotel/event UUIDs kept; Booking/hold deterministic UUIDv3 generators kept, v4 default only for random entity IDs. Loyalty/Revenue/HotelReview target booking UUID consistent; JSON/protobuf strings and tag numbers preserved.
- Exceptions kept: Regulations/snapshot BIGINT identities, structured loyalty command/hash/business/provider/catalog/composite keys, mixed SYSTEM_RECOVERY/system audit actors and raw invalid-event receipt booking TEXT. ratePlanId String alias U04; no false all-identifiers-UUID claim or automatic legacy casts.
- **STD-021 IN_PROGRESS:** canonical model/spec/ERD/architecture/plan revised with current-versus-target provenance, proposal statuses and independent histories. Next **Batch5A** only after design approval (code/contracts/tests), **Batch5B** fresh five local DBs/canonical Flyway V1/JPA validate/runtime gates, **Batch6** five-project staging/deployment/cutover only after concrete acceptance. Historical migrations never scanned alongside fresh V1 or rewritten.
- **STD-006 OPEN:** stock provenance/counter/inventory policy unchanged; no NULL→10/default/repair. Money/rate/time/delete/seed/backup/cutover gates remain; no policy/provider refund expansion. Place/city identity dataset compatibility flagged without modifying Place.
- Static checks/evidence scope in report; no runtime UUID implementation, migration, Supabase access or application test execution. Java/frontend/proto/runtime config/legacy SQL/credentials/DBs unchanged; existing changes preserved; no stage/commit/push/merge/PR.


## Batch 5A implementation checkpoint — 13/11/2026

U01 approved38 conversions implemented in source (User3 / Booking21 / Revenue14); U03 approved fresh databases without demo import; U04 approved ratePlanId String. U02 evidence: golden legacy/new PASS, eight backend builds PASS (901 tests,39 existing skips), three isolated PostgreSQL contract tests PASS, frontend regression211 PASS. JSON/protobuf strings, Booking/hold UUIDv3 recipes, hashes, provider refs, raw Revenue receipts and domain policies retained.

**Batch5A PARTIAL:** frontend tsc retains baseline protected Place showDistance error; no Place/frontend edits allowed. Full broker/Gateway/multi-node/restart and canonical migration acceptance NOT RUN. Current source mapping is UUID; actual supplied snapshot types and historical migrations remain unchanged. Never start new code against old VARCHAR schema. Native create-only synthetic tests are not canonical Flyway V1/JPA validate acceptance.

Current model preserves batch4_1_* provenance; current source/binding status supersedes earlier pending-U01 language. Historical reports/inventories remain as-of Batch4.1; this checkpoint records the approved implementation. Five independent Supabase projects unchanged. STD-021 IN_PROGRESS, STD-006 OPEN; Batch5B/6 not started. No current DB/Supabase/runtime-config/history/Place or Git index/commit mutations. [Batch5A report](../BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md), [evidence](../evidence/batch-5a-uuid/implementation-inventory.md).


## Batch 5B implementation checkpoint — 14/11/2026

**DONE for local canonical acceptance; Supabase/Batch6 NOT STARTED.** Five standalone canonical V1 chains/service db/canonical,46 tables/458 columns, five independent PostgreSQL databases/public histories. Bootstrap/rerun/catalog/Hibernate5/5 PASS;32 repositories/guards; runtime roles deny DDL/history/cross-service CONNECT. Guarded external job, opt-in runtime canonical validates/disables Flyway/SQL init; legacy defaults/migrations retained.

D01 historical unbounded Loyalty input/result/reservation total/discount and Revenue cash NUMERIC retained; bounded money38,2 unchanged. D02 exact rate; D03 Instant/timestamptz vs LocalDateTime/timestamp/date retained, JVM/JDBC/sessionUTC tested; D04 logical Room–Hotel/no cascade; D09 Java sole seed owner. Earlier proposal/model provenance stays historical design, executed local facts are recorded separately in5B evidence.

901 backend regressions executed/47 opt-in skips,8 packages PASS;5 canonical native tests PASS. Actual5 apps UP/restart5/5 with owned RabbitMQ/Redis; positive-points Booking/Hotel/User/Payment/refund/Revenue/concurrency/broker-outage/outbox/replay PASS. Place showDistance fixed minimally, tsc PASS,3 component+211 regressions PASS, lint0 errors/2 existing warnings. Minimal repository parameter correction findByRoomTypeId(UUID).

STD-021 local canonical gate complete; hosted deployment/managed security/backup/cutover and vendor PG/Flyway version certification future gates. STD-006 stock provenance OPEN/no default10/import. Full Gateway/provider/SMTP/Media/multi-node/all skipped opt-ins NOT VERIFIED. No Supabase connection/automatic Batch6. [Report](../BATCH5B_CANONICAL_FLYWAY_IMPLEMENTATION_2026-11-14.md), [evidence](../evidence/batch-5b-canonical-flyway/REPRODUCE.md).

## Batch6A checkpoint — 14/11/2026: Supabase readiness/security

**DESIGN/AUDIT DONE (static); Batch6B NOT READY for execution.** [Report](../BATCH6A_SUPABASE_READINESS_SECURITY_AUDIT_2026-11-14.md), [6B execution plan](../evidence/batch-6a-supabase-readiness/batch-6b-execution-plan.md), [risk matrix](../evidence/batch-6a-supabase-readiness/remaining-risks.md). This current checkpoint supersedes earlier “Batch6 not started” statements only for static6A; historical5A/5B results above remain unchanged.

Five existing independent projects retained; actual identity/endpoints/credentials/server versions/plans/API/RLS/grants/backups all NOT VERIFIED, no HOSTED VERIFIED items. Current V1 hashes match5B; canonical runtime validates/disables Flyway/SQL init when correctly selected. Compose currently selects docker only; base four ddl-auto=update and User baseline=true are deployment blockers until reviewed canonical overlay/resolved configuration. Existing5B runner is loopback-only, not hosted deploy tooling.

Direct TLS or accepted session pooler for persistent JDBC; dedicated guarded Flyway runner, verify-full/CA/hostname; transaction mode not selected for current session-init workload. Hikari5.1.0 default max10, each project budget replicas*max+migration+headroom; plan/replica allocation unknown, candidate lower pools not applied. Flyway10.10 major16 threshold requires actual server-version support review; no dependency upgrade performed.

Public Data API/default ACL/managed-role exposure requires pre-creation hardening and SQL/API negative tests, runtime/migrator separation, owner-specific grants; local5B permission PASS is not Supabase acceptance. Backup/PITR/RPO/RTO/restore drill and fresh broker/Redis/cutover/financial reconciliation gates documented. No demo import/cache/old event replay; STD-006 remains OPEN, no guessed stock10.

No hosted query/migration/DDL/DML/role/RLS/dashboard/backup/cutover, no Place/history/source/config/real.env changes or Git stage/commit/push.6B requires metadata/hosting/recovery inputs and explicit execution authorization after blockers close, not topology reapproval.


## Batch6B checkpoint — 14/11/2026: local backend → five Supabase databases

**PARTIAL; hosted execution BLOCKED, chưa DONE.** Đã thêm opt-in supabase-dev/local profiles, per-owner environment contract/startup guards, standalone guarded Flyway preflight/migrate runner, dedicated local RabbitMQ/Redis compose và local tests. Legacy defaults/V1/history/Place/business rules không sửa. Runtime validate/SQL never/Flyway false, fail-closed/no fallback; secrets không lưu report.

Actual five refs/org/Connect/TLS/version/budget/roles/Data API chưa cấu hình/xác minh riêng cho6B; không hosted SQL/DDL/dashboard/grants/fixtures. Phải có preflight/security PASS và user approval chính xác project/service/migration trước DDL. Active event triggers hoặc managed non-extension public objects cần review guard allowlist; không bypass. Hibernate/startup/business hosted đều BLOCKED; local regression không thay hosted acceptance. Backend/frontend/Gateway/Eureka/RabbitMQ/Redis tiếp tục chạy local, không cloud deployment.

[Báo cáo6B](../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-14.md), [hướng dẫn/evidence](../evidence/batch-6b-supabase-local-integration/reproduction-guide.md). Historical5A/5B/6A giữ nguyên as-of scope; STD-021 hosted IN_PROGRESS, STD-006 provenance OPEN.


## Batch6B PostgreSQL17 follow-up — 15/11/2026

Standalone runner1.1.0/Flyway10.22.0 đã kiểm trên new isolated PostgreSQL17.11:13 tests PASS,5 V1+validate+rerun no-op và scoped role/grant SQL local PASS. Service POMs/Boot/V1/history/business/Place/frontend không đổi. Package role/security review và admin read-only inspector đã chuẩn bị; chưa apply hosted, chưa tạo roles/passwords hoặc bật runtime. PostgreSQL17 version blocker đã đóng ở tooling/local; actual identity/TLS/Supavisor/Data API/managed permissions/event-trigger/security/approval vẫn BLOCKED. Overall6B PARTIAL, không hosted PASS từ local tests.

[Follow-up report](../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../tools/supabase-runner/README.md). Không stage/commit/push/cloud deployment/Batch7.


## Batch6B User hosted preflight —16/11/2026

Actual User-only Management API identity + migrator/runtime JDBC + client TLSv1.3/CA/hostname negatives + empty/history/default ACL/event-policy checks PASS in read-only scope. Runner1.3.0 now separates client TLS from pooler backend. Observed backend pg_stat_ssl=false, so migration remains BLOCKED; no TLS downgrade or backend-TLS exception for migrate. PUBLIC TEMP inheritance and Data API401 limitation retained.18 local tests PASS; other4 hosted preflights/roles/migrations/JPA/workflows NOT RUN. Overall6B PARTIAL, no DDL/DML/settings/app startup.

[16/11 report](../BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md), [redacted evidence](../evidence/batch-6b-supabase-local-integration/user-hosted-preflight-summary-2026-11-16.json).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](../evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json).

## Checkpoint đồng bộ và xét đóng Batch 6B — 16/11/2026

Core database/local acceptance đã đủ evidence; overall PARTIAL do phạm vi provider thực tế còn BLOCKED. Các checkpoint cũ NOT STARTED/BLOCKED/DONE phản ánh phạm vi và thời điểm trước đó, không được dùng thay current status. Cleanup fixtures và consolidation thay thế các ghi chú cũ về giữ synthetic rows, stack đang chạy và private `.local` fallback.

Giữ mở PUBLIC TEMP, STD-006 stock provenance, legacy cutover/recovery, real provider payment/refund/Storage, production backup/PITR/restore/RPO/RTO, broad outage/load và backend cloud deployment. Không tự đóng toàn bộ standardization hoặc mở Batch 7.

### Checklist và dependencies hiện hành

- [x] Xác minh năm project identities và owner-specific JDBC/TLS.
- [x] Apply canonical V1 5/5, history một successful V1/project, Flyway validate/checksum và immediate no-op 5/5.
- [x] Hosted catalog 46 business tables +5 history; Hibernate 32 entities và full startup/restart 5/5.
- [x] Runtime/migrator separation, scoped grants, independent security settings, 30 denials/5 cross-project pairs.
- [x] Isolated broker/cache, bounded pools, 117 core workflow checks và scoped regression acceptance.
- [x] Fixture cleanup và daily configuration consolidation theo evidence; không replay acceptance.
- [x] Real VNPay Sandbox payment — 17/11/2026: NCB test card/OTP, return00/HMAC, payment COMPLETED, booking CONFIRMED, Revenue một CREDIT; replay/negative checks PASS. [Evidence](../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md). *(Đồng bộ với PLAN ở Batch 9A; ghi chú gốc 16/11: merchant code71.)*
- [x] Supabase Storage bucket/visibility/upload/private-preview — actual public/private upload, signed download/batch signing và anonymous denial PASS; probe objects đã xóa. *(Đồng bộ Batch 9A; ghi chú gốc 16/11: NXDOMAIN.)*
- [ ] Production backup/restore/PITR/RPO/RTO, legacy-data cutover/reconciliation, broader outage/load và backend cloud deployment.

Dependencies: Batch5B local canonical → Batch6A static readiness → Batch6B hosted database/core local acceptance đã xác minh. Provider follow-up độc lập còn BLOCKED; deployment tương lai phải có acceptance riêng. STD-021 local/hosted integration subgates được giải quyết, parent migration/operational readiness vẫn IN_PROGRESS; STD-006 OPEN. Không đổi các checkbox lịch sử thành DONE ngoài phạm vi.

Evidence: [migration](../evidence/batch-6b-supabase-local-integration/five-project-migration-acceptance-2026-11-16.json), [core/regression](../evidence/batch-6b-supabase-local-integration/final-verification-2026-11-16.json), [provider](../evidence/batch-6b-supabase-local-integration/real-provider-acceptance-results-2026-11-16.json), [cleanup](../evidence/batch-6b-supabase-local-integration/fixture-cleanup-2026-11-16.json), [consolidation](../evidence/batch-6b-supabase-local-integration/config-consolidation-2026-11-16.json).


## Batch 7 — G2 provenance implementation checkpoint, 16/11/2026

Source fixes: `RoomInventoryReviewService` xác nhận approval/source/reviewer và đúng room/hotel, chặn foreign-link reads/review/supersede; `RoomTypeMapper` giữ SQL NULL/JSON null amenities và từ chối unsupported shapes. Thêm unit/native round-trip/authorization/approval-vs-hold race và metadata-only read-only classifier. Không thay stock admission policy: §56/56.1 giữ unmanaged legacy compatibility; không gọi mọi positive stock là VERIFIED.

Fresh disposable PostgreSQL18: five canonical tests/Flyway validate/no-op/Hibernate/repositories/permission checks PASS; exact catalog columns/constraints/indexes/sequences/history 5/5 MATCH baseline5B, không drift. Manifest hash-bound theo owner phân biệt canonical V1, legacy/test SQL, external grants. Không sửa applied V1 hoặc history, không V2 cần thiết cho fixes hiện tại, không đọc/ghi Supabase. Local owned cluster dừng finally; synthetic data riêng được giữ để review.

Actual physical stock, representative historical encoding/financial corpus và full legacy upgrade NOT VERIFIED; legacy cutover/import DEFERRED theo fresh/no-import U03. Không guessed backfill, approve, historical booking/financial mutation. **Batch7/G2 PARTIAL**; không đóng toàn STD-006/021/024 hoặc chuyển G3/G4/G5. Batch6B core database/local integration được tạm đóng; provider workstream riêng.

[Báo cáo và regression chính xác](../BATCH7_G2_PROVENANCE_IMPLEMENTATION_2026-11-16.md); [gap classification](../evidence/batch-7-g2-provenance/gap-classification.md); [fresh results](../evidence/batch-7-g2-provenance/schema-verification.json). Checkpoint lịch sử phía trên giữ nguyên, không xem Batch3 legacy defects là canonical V1 chưa triển khai.


## Batch 7B — mandatory inventory approval, 16/11/2026

Quyết định mới của người dùng thay thế unmanaged legacy admission compatibility cho booking mới; các checkpoint Batch7/7A giữ nguyên lịch sử. Đã triển khai database approval predicate thực (room/hotel/status/quantity/source/reviewer/time/effective pointer), gate sau room FOR UPDATE ở new hold, availability/search cả không ngày và public catalog/lowestPrice. OWNER changed quantity luôn qua proposal; ADMIN review revision/latest request/stock constraints. Pending/rejected proposal mới giữ effective approved quantity cũ; idempotent retries và confirm/release/expiry của hold cũ giữ nguyên. Frontend thông báo chờ duyệt và dẫn từ tạo phòng sang workflow hiện có. REST409/gRPC FAILED_PRECONDITION; không sửa financial state machine.

**LOCAL IMPLEMENTATION DONE / HOSTED ACCEPTANCE NOT VERIFIED.** Final suite counts/skip/fail attempts nằm trong [báo cáo Batch7B](../BATCH7B_MANDATORY_INVENTORY_APPROVAL_2026-11-16.md) và [evidence](../evidence/batch-7b-inventory-approval/README.md). Five canonical PostgreSQL18 native tests/catalog5/5 PASS, V1 unchanged, không V2; cluster stopped finally. Hosted reads/writes/fixtures0, cleanup N/A. Gateway không có suite riêng/full running acceptance chưa chạy; provider/physical-stock/load/operational gates không được coi verified. Parent provenance/historical gaps STD-006/021/024 không tự đóng; VNPay/Storage/Place/Supabase data giữ nguyên. Không stage/commit/push.


## Batch 8 — hosted acceptance/performance checkpoint, 17/11/2026

**PARTIAL / HOSTED APPROVAL & BOOKING E2E BLOCKED**: năm project identity/runtime TLS1.3/read-only catalog+Flyway baseline khớp; Gateway read benchmark480/480 tại concurrency1/3/5/10, không error/timeout. Twelve HTTP scenarios: một transient booking503 lúc startup, recheck401; không successful approval/booking acceptance vì không có ADMIN test hợp lệ (existing users ADMIN0). Không tạo hosted fixtures (0created/0deleted); sau stop row counts/schema/history năm projects không đổi. Empty public catalog/0room: runtime room N+1 chưa xác minh; source có per-room/duplicate approval reads. Không Java optimization/index/schema mutation hoặc production-ready claim. Native five-owner disposable results và limits tại [báo cáo Batch8](../BATCH8_HOSTED_E2E_PERFORMANCE_2026-11-17.md); evidence mới tách riêng, checkpoint6B/7/7B giữ nguyên. Next: provision synthetic ADMIN qua security contract rồi controlled populated E2E+write benchmark+exact-ID cleanup.


## Batch 8B — hosted authenticated E2E & performance follow-up, 17/11/2026

**PARTIAL / PERFORMANCE AFTER & FULL CONCURRENCY BLOCKED.** Permanent `admin` login200/ADMIN queue200; real Gateway OWNER→proposal→ADMIN approve/reject→CUSTOMER booking/idempotency/cancel and ownership/header/stale guards exercised. Synthetic hotel publication SQL setup is separate from document/provider acceptance. Small2×2 populated SQL counts/search measured; benchmark29/30 C1 with timeout, stopped. Read batching implemented; final843 executed local regression/native PASS,41 opt-in skips. Hosted AFTER/multi-size slope/expiry/final HTTP ADMIN login not verified because role-connection exhaustion and failed User/Hotel readiness after restart; no latency improvement/N+1-resolved/production-ready claim.

Exact-ID cleanup47 rows,48 manifested IDs absent, fixture broker/DLQ payloads removed without purge; existing row digests and schema/Flyway/counts5/5 match. ADMIN complete row unchanged,active/ADMIN and BCrypt credentials match; final runtime login503 is explicitly BLOCKED. No role/password change,extra ADMIN,hosted DDL/V1/provider/frontend/index/HEAD change or Git stage/commit/push. Prior Batch8 conclusions remain historical. Next: operator-reviewed Supavisor/runtime-role connection lifecycle and readiness, then new controlled populated BEFORE/AFTER+concurrency/expiry/query slope run preserving permanent ADMIN.

[Batch8B report](../BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md), [evidence](../evidence/batch-8b-hosted-e2e-performance/README.md). Parent standardization/production gates remain open.


## Batch 8C — runtime stability & performance checkpoint, 17/11/2026

**PARTIAL; scoped runtime/hosted acceptance PASS, historical ownership RCA và remaining read amplification OPEN.** Thêm opt-in guarded Windows launcher (operation lock, JVM/port/PID ownership, live role-headroom gate, local JMX graceful close, bounded full-chain readiness). Không tăng pool/role cap hoặc đổi hosted settings. Hikari max2/min1/timeout10s; trong run không role-limit error/Hikari timeout. Final post-cleanup ADMIN login/queue HTTP200 sau discovery/route readiness polls; cuối cùng owned JVM/backend rows về0, dependencies dừng/retained.

AFTER thực tế: Gateway1170/1170 + direct150/150 measured samples PASS; datasets2×2,3×5,5×10. Trace-scoped search undated statements22→41→92, dated28→50→107, catalog20→38→87, detail11→14→19, availability4: remaining N+1/read amplification được xác nhận, không claim resolved hoặc % improvement; controlled BEFORE artifact không có. Medium/Larger có per-cell warmup, unsafe concurrency cells được skip rõ. Stock2 race201/201/409, concurrent same-key một booking/hold, cancellation counters0; natural15-minute expiry CANCELLED/RELEASED và replay PASS.

Cleanup194 exact rows/IDs, broker8 exact fixture payloads, Redis4 exact keys absent; schema/Flyway/counts/full-row HMAC5/5 và permanent ADMIN preserved.843 Java/native +6 guard tests PASS,41 opt-in skips; frontend unchanged. Không hosted DDL/role/grant/index/provider call hoặc Git stage/commit/push/reset/checkout/restore. Parent STD-006/021/024 và production/provider/physical-stock gates không tự đóng.

Next: source-level lazy room-media/per-hotel fetch optimization trong run mới, giữ authoritative approval sau lock/idempotency/financial boundaries; current AFTER JAR đã giữ private để làm baseline tương thích. Historical Batch8/8B giữ nguyên. [Báo cáo8C](../BATCH8C_RUNTIME_STABILITY_PERFORMANCE_2026-11-17.md), [evidence](../evidence/batch-8c-runtime-stability/README.md).

## Batch 8D — read amplification & hotel search optimization, 17/11/2026

**DONE (scoped hosted development acceptance); không production-ready.** SQL attribution theo W3C trace ID xác nhận N+1 cấp room (lazy media) và cấp hotel (rooms/policies/FAQ/amenity categories/items/effective codes, approval lặp mapper+matching, dated availability snapshot mỗi hotel). Sửa cục bộ `hotel-service`: `@BatchSize` cho `Hotel.roomTypes`, `HotelMapper.toPublicHotelResponses` đọc facts một lần/response (chunk 500, request-local, không cache), media bulk theo `target_type` (vì `@BatchSize` không batch collection có `@Where` — native evidence), `HotelOfferSearchService.approvalFor` + availability snapshot một lần/request. Không đổi schema/Flyway/API/authorization/approval predicate/booking transaction.

Controlled BEFORE/AFTER trên cùng hosted fixture IDs (AFTER 5×10 rồi exact-ID shrink về đúng tập 3×5, 2×2): SQL 5×10 search undated 96→12, dated 111→15, catalog 91→11, detail 19→11, availability 4→4; slope/room 1,587/1,783/1,522/0,174 → 0. Gateway C1 p50 5×10: search 12022→1919 ms, catalog 11535→1944, detail 2360→1821; dated cell gốc 13291→4701 (nhiễu cửa sổ; đo lặp 2327). Response tương đương 15/15. Gateway 2760/2760 + direct 600/600 thành công; 0 Hikari timeout/role-limit/HTTP500. E2E approval/booking/idempotency/race/cancel/authz PASS. Cleanup 202 rows exact-ID + 14 broker messages + 4 Redis keys; schema/Flyway/counts/full-row HMAC 5/5 và permanent ADMIN preserved. Regression hotel 401 + booking 285 PASS (38 opt-in skips), native PostgreSQL18 2/2 PASS (SQL count hằng số 12/15/11/11 ở 2×2–5×10). Không hosted DDL/index/role/grant/provider call; không Git stage/commit/push/reset/checkout/restore.

Next: giảm 12–15 round trip cố định còn lại hoặc latency floor network (đo RTT/region), hosted rich-content dataset, thứ tự ổn định cho collection public nếu được phê duyệt. Parent STD/production gates không tự đóng. [Báo cáo 8D](../BATCH8D_READ_OPTIMIZATION_2026-11-17.md), [evidence](../evidence/batch-8d-read-optimization/README.md).

## Batch 9A — Current-State Reconciliation, 17/11/2026

**DONE (documentation reconciliation + read-only verification); không production-ready.** Current matrix, counts, risks, AI READ/WRITE readiness và backlog được đưa lên [§0](#0-current-state--batch-9a-reconciliation-17112026) và đồng bộ với PLAN §0/§77. Thay đổi: STD-006 → FIXED_VERIFIED (scoped admission; legacy provenance DEFERRED), STD-027 → IN_PROGRESS; STD-011/023/032 in-scope `IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED`. Fresh: backend 973 PASS/48 skipped/0 fail (8 module), reactor compile 13/13, frontend 214 PASS, `tsc` và production build PASS, lint baseline FAIL. Không sửa source/DB/provider/runtime; không stage/commit/push/reset/restore/checkout/clean/stash. Checkpoint lịch sử phía trên giữ nguyên. Next: Batch 9B — AI READ contract verification. [Báo cáo 9A](../BATCH9A_STANDARDIZATION_RECONCILIATION_2026-11-17.md).

## Batch 9B — AI READ contract verification, 17/11/2026

**DONE (service-boundary scope); Gateway-level NOT_RUN; không production-ready.** Thêm test-only `hotel-service` `AgentReadContractTest` (11) và `AvailabilityReadValidationTest` (1): 14 public GET Agent dùng là anonymous và dùng public policy kể cả dưới owner token/forged header; 20 private GET lân cận trả 401 envelope, 8 admin/internal/hold GET trả 403 cho CUSTOMER; 19 search + 6 availability input sai → 400 trước mọi query; 404/409 `INVENTORY_APPROVAL_REQUIRED`/5xx sanitized envelope có `requestId`. Hotel full suite: 447 reported / 413 PASS / 34 opt-in skipped / 0 fail, BUILD SUCCESS. Không đổi application source/API/schema/runtime. In-scope STD-003/018 NOT_VERIFIED → PARTIAL; parent statuses/counts không đổi. Findings: DB outage → 500 `INTERNAL_ERROR` (không 503); public `HotelResponse` có `ownerId`/contact; search không chặn ngày quá khứ/không có cận trên rooms/adults; Gateway không cấu hình response timeout. Tool contract: [READ_TOOL_CONTRACT](../ai-agent/READ_TOOL_CONTRACT.md). Next: **9C — AI WRITE pre-gate verification** (gồm Gateway-level READ smoke). [Báo cáo 9B](../BATCH9B_AI_READ_CONTRACT_VERIFICATION_2026-11-17.md).

## Batch 9C — AI WRITE pre-gate verification + F1, 18/11/2026

- **DONE ở service boundary + F1; Gateway-level smoke NOT_RUN; không production-ready.** [Báo cáo](../BATCH9C_AI_WRITE_PREGATE_VERIFICATION_2026-11-18.md).
- **Test mới:** booking-service `AgentWriteContractTest` (9): 7 route Agent WRITE (create/read/list/cancel booking, read payment, VNPay URL, refund request) trả 401 envelope khi không có token và bỏ qua `X-User-Id`/`X-Is-Admin`/`X-Role`; CUSTOMER khác 403; 10 route ngoài phạm vi (status, cancel-reviewed, count, hotel list, refund execute/approve/reject, onsite confirm, manual-refunds) 403; create gắn principal và forward nguyên idempotency key, replay trả cùng booking; 6 body sai → 400 `fieldErrors`; giá cũ 400, key reuse 409, 503 `DEPENDENCY_UNAVAILABLE`, 500 sanitized; cancellation 204/409. Booking full suite: 298 reported / 294 PASS / 4 opt-in skipped / 0 fail, BUILD SUCCESS.
- **F1 (frontend, source change):** `lib/api/hotels.ts` — detail/cities/pending/room reads dùng `readJson` (10 s); owner/admin writers dùng `bookingFetch` (15 s, deadline cả body) + `responseError` (typed, có `requestId`, không echo raw body/`statusText`); một lần gọi, không retry; fallback submit-approval chỉ khi 404; preview giữ message có status (test cũ không bị nới). Bỏ `extractErrorMessage` (echo raw text). Node `tests/hotel-adapter-deadline.test.mjs` (4) thêm vào `test:regression`. Frontend: regression 218/218, tsc PASS, production Webpack build PASS.
- **Không chạy Gateway smoke:** volume `checkinn-test_postgres-test-data` là clone DB developer cũ (trước UUID) và Test profile `docker` dùng `ddl-auto: update` — khởi động source hiện tại trên đó vi phạm AGENTS.md (không chạy UUID mapping trên legacy VARCHAR schema). Cần stack canonical disposable mới (quyết định của người dùng).
- **Finding C5:** Booking→Hotel REST không propagate trace do RestTemplate tạo thủ công (source-confirmed, runtime NOT_VERIFIED). Statuses/counts không đổi (17/12/3; in-scope VERIFIED 17, PARTIAL 11, IN_SCOPE_DONE 3, DEFERRED 1).

## Batch 9C addendum — canonical disposable Gateway smoke, 18/11/2026

- **Gateway READ/WRITE smoke 87/87 PASS trên stack canonical disposable mới** (`docker/canonical-smoke.py`, `docker/docker-compose.canonical-smoke.yml`, `docker/CanonicalSmokeMigrate.java`; test-only). PostgreSQL 18 fresh, 5 V1 qua Flyway 10.22 (46 bảng, 1 history row/owner, rerun 0), app role chỉ DML; 9 service profile `canonical` (`validate`) + infra healthy lần đầu; tmpfs, không named volume; loopback-only; không Supabase/`.env`/legacy `checkinn-test`/Storage/VNPay provider. [Evidence](../evidence/batch-9c-canonical-smoke/README.md).
- **Chạy:** run 1 82/5 — cả 5 là lỗi định nghĩa check (cộng counter theo đêm, tên field `redirectUrl`, giả định cancel lặp không idempotent), giữ `results-run1.json`; run 2 dừng do trùng tên city fixture; run 3 87/0 sau khi sửa check (không sửa source).
- **Kết luận mới:** lost response + retry cùng key → 1 booking/1 hold qua Gateway thật; cancel idempotent 204; unapproved room 409 không hold; principal/ownership/out-of-scope 401/403 qua Gateway. **F-9C-2 runtime-confirmed:** trace client tới Booking (requestId = trace) nhưng không tới Hotel.
- **Cleanup:** project down — 0 container/network/volume còn lại; stack khác không bị đụng; 9 image `:canonical-smoke` giữ để chạy lại. Statuses/counts không đổi (17/12/3; in-scope VERIFIED 17, PARTIAL 11, IN_SCOPE_DONE 3, DEFERRED 1).

## Batch 9D checkpoint — xử lý finding 9B/9C, 18/11/2026

- **Xử lý finding 9B/9C** ([report](../BATCH9D_FINDINGS_REMEDIATION_2026-11-18.md)): F-9C-2 trace Booking→Hotel (RestTemplateBuilder), F-9C-3/4 envelope cho 400 rỗng, F-9B-1 DB outage → 503 `DEPENDENCY_UNAVAILABLE` (Hotel/Booking), F-9B-5 `checkedAt` + Gateway timeout 20 s, F-9B-2 `ownerId` bỏ khỏi public hotel DTO, F-9C-5 bỏ fallback submit-approval → FIXED_VERIFIED. F-9B-3 OPEN (cần quyết định nghiệp vụ cận trên/ngày quá khứ); F-9B-4 giữ by design.
- **Mới F-9D-1:** Hikari chờ 30 s > Gateway 20 s → outage DB qua Gateway là 504 (service vẫn map 503).
- **Kiểm tra:** Booking 301/0/4, Hotel 447/0/34 (`contextLoads` lỗi môi trường: PostgreSQL local ở 5432 từ chối mật khẩu), FE tsc PASS, regression 218/218; canonical Gateway smoke 94/0 trên stack mới ([evidence](../evidence/batch-9c-canonical-smoke/README.md)), down sạch.
- Statuses/counts ở bảng §0 không đổi trong checkpoint này (cập nhật trạng thái STD-026/STD-030 thuộc lần reconcile kế tiếp).

## Batch 9D addendum — F-9B-3, F-9B-4, F-9D-1, 18/11/2026

- F-9B-3 FIXED_VERIFIED (giới hạn tìm kiếm/availability theo quyết định người dùng: từ hôm nay `Asia/Ho_Chi_Minh`, ≤30 đêm, rooms 1–20, adults 1–40, children 0–40; hold/booking không đổi). F-9B-4 KEPT với bằng chứng (Booking `HotelCatalogClient` phụ thuộc 409; thử 404 gây regression, đã hoàn tác). F-9D-1 FIXED_VERIFIED (canonical `hikari.connection-timeout` 10 s → outage DB là 503 qua Gateway sau 10 s). Smoke canonical 101/0. Chi tiết: [9D §4](../BATCH9D_FINDINGS_REMEDIATION_2026-11-18.md).

## Batch 9D addendum — giới hạn lưu trú cho booking create, 18/11/2026

- Giới hạn F-9B-3 áp thêm cho booking create (≤30 đêm, ≤20 phòng, adults ≤40, children ≤40; ngày đến quá khứ vốn đã bị chặn). Booking 306/0/4; smoke canonical 104/0. Chi tiết: [9D §5](../BATCH9D_FINDINGS_REMEDIATION_2026-11-18.md).

## Batch 9E — Final core standardization audit & closure, 18/11/2026

**DONE (audit; không sửa application source).** 32 issue đối chiếu lại với source + evidence 9B–9D: counts không đổi (17/12/3/0; in-scope VERIFIED 17, PARTIAL 11, IN_SCOPE_DONE 3, DEFERRED 1). Fresh: backend 12 module 1054 run/0 fail/48 skipped, compile 13/13 (Place 0 diagnostic); FE Node 218, tsc + build PASS, ESLint 133/151; browser default 105 PASS + 45 fail do lẫn spec area (F-9E-3); area specs 45/45 PASS trên Webpack, 3 config Turbopack abort (F-9E-4); `contextLoads` lỗi môi trường (F-9E-5). Finding chính **F-9E-1** (P1): dữ kiện/chính sách bịa trên trang đặt phòng/thanh toán. Core standardization đóng cho Agent READ MVP với điều kiện R1–R5; không đóng cho AI WRITE; không production-ready. [Báo cáo 9E](../BATCH9E_FINAL_CORE_STANDARDIZATION_AUDIT_2026-11-18.md).

## Batch 9F — Core follow-up fixes, 20/11/2026

**DONE.** F-9E-1 (dữ kiện bịa trang đặt phòng/thanh toán), F-9E-3/4 (Playwright), STD-030 client REST auth/revenue/user, STD-026 `CannotCreateTransactionException` → 503 ở user/revenue/regulations → FIXED_VERIFIED. Kiểm tra: user 116/auth 48/revenue 55/regulations 41 run 0 fail; negative control user 401→503; FE regression 221, tsc/build, browser 105/105 + area 8/4/4; canonical Gateway smoke 106/0, down sạch. In-scope STD-012 → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED; parent counts không đổi. Finding mới F-9F-1 (user-service generic 401/400 kèm message). Đính chính 9E về DB outage. [Báo cáo 9F](../BATCH9F_CORE_FOLLOWUP_FIXES_2026-11-20.md).

## Batch 9G — Remaining core gaps (STD-026/028/030), 21/11/2026

**DONE.** F-9F-1, F-9E-2, F-9E-5, Gateway 504/503 envelope, Gateway tracing bridge + access log, log ingestion local, E2E paid lifecycle (Sandbox Internal refund, VNPay return ký cục bộ), browser không intercept, multi-node 2 replica → VERIFIED. STD-028 → FIXED_VERIFIED; STD-030 in-scope → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED; STD-026 vẫn PARTIAL (body legacy user-service). Backend 1078/0 fail/51 skipped (1 flaky rerun PASS), gateway 5/5, FE 222 + browser 105 + area; canonical 135/0 và 138/0, down sạch. Mới: F-9G-1 (booking-service CORS không cho `127.0.0.1:3000`). Counts 18/11/3. [Báo cáo 9G](../BATCH9G_REMAINING_CORE_GAPS_2026-11-21.md).

## Batch 9H — CORS (F-9G-1) + error contract STD-026, 21/11/2026

**DONE.** CORS booking/regulations đồng bộ với Gateway (`cors.allowed-origins`), browser không intercept PASS ở `127.0.0.1:3000`. Envelope chuẩn + `X-Request-ID` ở auth/user/revenue/regulations; 18 lỗi không body có envelope, status giữ nguyên. Đính chính 9G: `UserRpcClientTest` là test-order coupling (resolver gRPC mặc định), đã sửa test. Backend 1084/0, FE 222, smoke 144/0. STD-026 in-scope → IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED; parent counts không đổi. [Báo cáo 9H](../BATCH9H_CORS_AND_ERROR_CONTRACT_2026-11-21.md).
