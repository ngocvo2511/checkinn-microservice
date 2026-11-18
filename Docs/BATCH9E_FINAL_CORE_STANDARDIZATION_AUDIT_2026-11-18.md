# Batch 9E — Final Core Standardization Audit & Closure (18/11/2026)

Đối chiếu 32 issue STD với source hiện tại và bằng chứng kiểm thử mới nhất (gồm Batch 9B–9D), rà soát frontend core còn tồn đọng (không gồm Place), xác định điều kiện còn thiếu trước Batch 10. Batch này là **audit**: không sửa application source; chỉ thêm báo cáo và cập nhật PLAN/AUDIT. Không stage/commit/push/reset/restore/checkout/clean/stash; không hosted/provider/migration.

Git (cả hai repo đều **chưa commit** phần 9A–9D): backend `feat/inventory-approval-read-batching` @ `96bfc4f` (41 đường dẫn thay đổi/mới, gồm hai IDE edit có sẵn); frontend `fix/inventory-approval-workflow` @ `6942747` (4 file sửa + 1 test mới).

## 1. Kết luận

- **Parent status không đổi: FIXED_VERIFIED 17 · IN_PROGRESS 12 · FIXED_UNVERIFIED 3 · NEEDS_ENVIRONMENT 0.** Open theo priority: P0 3/7 (STD-002/003/007), P1 7/17 (STD-011/012/018/021/023/026/028), P2 4/7 (STD-024/027/029/030), P3 1/1 (STD-032).
- **In-scope (không gồm Place): VERIFIED 17 · PARTIAL 11 · IN_SCOPE_DONE / PLACE_SCOPE_EXCLUDED 3 · DEFERRED 1 · NOT_VERIFIED 0 · BLOCKED 0.**
- Không issue nào đủ điều kiện đóng thêm: 9B–9D thu hẹp đáng kể STD-018/026/030/028 nhưng mỗi issue còn phần acceptance gốc chưa có bằng chứng (chi tiết §3). Không nới acceptance để đóng.
- **Core standardization: CLOSED FOR AI READ MVP (Batch 10) với điều kiện §5; KHÔNG đóng cho AI WRITE và KHÔNG production-ready.**
- Frontend core có **một defect mới đáng kể (F-9E-1, P1)**: trang đặt phòng/thanh toán hiển thị dữ kiện bịa (khan hiếm phòng, giường, bữa sáng, Wi-Fi, giờ trả phòng, "Không hoàn tiền") — vi phạm STD-012 và mâu thuẫn chính sách hủy thật.

## 2. Kiểm tra đã chạy (18/11, fresh)

| Kiểm tra | Kết quả |
|---|---|
| Backend `mvn -pl '!place-service' -Dtest='!*ApplicationTests' test` (12 module) | **1054 run / 0 fail / 0 error / 48 skipped (opt-in)**, BUILD SUCCESS |
| Backend `mvn compile` toàn reactor 13/13 (gồm Place) | PASS, 0 diagnostic (Place không có lỗi compile để báo cáo) |
| `*ApplicationTests.contextLoads` | ERROR do môi trường (F-9E-5): kết nối `localhost:5432` (PostgreSQL cài trên máy) bị từ chối mật khẩu — đã quan sát ở user/hotel/revenue; không đụng DB đó |
| FE `npx tsc --noEmit --incremental false` | PASS |
| FE `npm run test:regression` | 218/218 |
| FE `npm run build` | PASS (Compiled successfully) |
| FE `npx eslint .` | FAIL baseline: **133 errors / 151 warnings**, 29 file (112 `no-explicit-any`, 15 `no-unescaped-entities`, 4 `set-state-in-effect`); 1 lỗi thuộc Place. 9A: 133/152 |
| FE `npm run test:browser` (default config) | 105 passed / **45 failed** — 45 = đúng 7 spec thuộc area config (F-9E-3), tất cả `page.goto ERR_FAILED` vì chạy sai server |
| FE area configs | amenities 7/7, content-webpack 8/8, media-write 13/13, regulations 3/3, revenue 6/6; **content 0/8, documents 0/4, reviews 1/4** (Turbopack dev, `ERR_ABORTED`) |
| FE area specs trên Webpack dev (config tạm, đã xoá) | documents 4/4, reviews 4/4, content 8/8 → mọi 150 browser case PASS về chức năng; lỗi là cấu hình Turbopack (F-9E-4) |
| Gateway canonical smoke (9D, stack disposable) | 104/0 (`results-9f.json`) — không chạy lại trong 9E |

Browser cases đều intercept backend bằng dữ liệu synthetic — không phải full-stack evidence.

## 3. Đối chiếu 32 issue

Chỉ liệt kê issue có thay đổi evidence/remaining từ 9B–9E; các issue còn lại giữ nguyên dòng ở AUDIT §0.2 (đã kiểm source không thay đổi kết luận).

| Issue | Parent / In-scope | Evidence mới (9B–9E) | Còn thiếu (lý do chưa đóng) |
|---|---|---|---|
| STD-003 | FIXED_UNVERIFIED / PARTIAL | `AgentReadContractTest`, `AgentWriteContractTest`; Gateway thật trên stack canonical 87→104/0 (401 forged header, 403 cross-owner/out-of-scope) | Consolidated REST/RPC actor matrix cho owner/admin writes + external RPC caller inventory (G4) |
| STD-007 | IN_PROGRESS / PARTIAL | Lost response qua Gateway + retry cùng key → 1 booking/1 hold; cancel lặp idempotent | Provider refund/QueryDR/IPN DEFERRED; crash/restart/multi-node NOT_VERIFIED |
| STD-012 | IN_PROGRESS / PARTIAL | 9E rà soát hệ thống: `FeaturedHotels`, `GuestReviews`, `HotelCard` dùng dữ liệu nguồn (review stats, `lowestPrice`, `availableRooms`) | **F-9E-1**: `app/booking/page.tsx` "Chỉ còn 8 phòng…"/"Chỉ còn 1 phòng"; `app/booking/payment/page.tsx` "1 giường", "Bữa sáng cho 2 người", "WiFi miễn phí", "Từ 14:00"/"Trước 12:00", "Không hoàn tiền", "Không đổi lịch" (cứng, mâu thuẫn `GET /api/bookings/cancellation-policy`). PlaceCard/PlaceReviewsModal EXCLUDED |
| STD-018 | FIXED_UNVERIFIED / PARTIAL | Search/availability/booking-create negative contract; giới hạn F-9B-3 (≤30 đêm, rooms 1–20, adults 1–40, children 0–40, không quá khứ) ở Hotel + Booking, unit + Gateway | Inventory hợp nhất cho các write REST/RPC còn lại (owner/admin content, media, regulations…); Place boundary EXCLUDED |
| STD-023 | IN_PROGRESS / IN_SCOPE_DONE | 9D: public `HotelResponse.ownerId` = null (mapper + Gateway smoke) | Chỉ còn Place (tọa độ thô) — EXCLUDED |
| STD-026 | IN_PROGRESS / PARTIAL | Hotel/Booking: DB outage → 503 `DEPENDENCY_UNAVAILABLE` trong 10 s qua Gateway; 400 rỗng → envelope; Gateway response-timeout 20 s; FE F1 + bỏ fallback submit-approval | auth/user/revenue/regulations chưa map DB outage → 503 (generic 500); FE core còn **27 lời gọi `fetch` thô không deadline** (`lib/api/auth.ts` 9, `admin.ts` 4, `users.ts` 4, `cities.ts` 3, `components/PersonalDataForm.tsx` 2, `app/host/hotels/newHotel/page.tsx` 2, `app/personal-data/my-bookings/page.tsx` 1, `app/api/loyalty-points/[userId]/route.ts` 1, `lib/mapUtils.ts` 1 — geocode bên thứ ba) (F-9E-2); `FeaturedHotels`/`GuestReviews` fan-out tuần tự và nuốt lỗi từng phần; Gateway 504 không có envelope; Place SDK EXCLUDED |
| STD-027 | IN_PROGRESS / PARTIAL | Topology canonical dựng lại từ source nhiều lần trong 9C–9D (mỗi lần 13 container healthy, V1 5/5) | Log ingestion Loki/Promtail, backend cloud deployment DEFERRED |
| STD-028 | IN_PROGRESS / PARTIAL | §2 fresh; Gateway smoke 104/0; browser 150/150 về chức năng | Full booking→payment→cancel→refund E2E; browser non-intercepted; multi-node; hạ tầng test F-9E-3/4/5 |
| STD-030 | IN_PROGRESS / PARTIAL | 9D: Booking→Hotel trace propagate (test + runtime: Hotel log 6 lần trace client, trước 0) | `auth-service` (`AuthServiceApplication`) và `revenue-service` (`RestClientConfig`) vẫn `new RestTemplate(factory)`; `user-service` `RegulationClient` dùng `new RestTemplateBuilder()` (không qua bean Boot → không observation); Gateway không có tracing bridge (không sinh trace khi client không gửi; log Gateway không có trace id); log ingestion NOT_VERIFIED |
| STD-021 | IN_PROGRESS / PARTIAL | Canonical `validate` + `hikari.connection-timeout` 10 s (9D) | Profile mặc định/`docker` vẫn `ddl-auto: update` ở booking/hotel/regulations/revenue; backup/PITR/restore NOT_VERIFIED; legacy cutover DEFERRED |

STD-001/004/005/006/008/009/010/013/014/015/016/017/019/020/022/025/031: FIXED_VERIFIED, test liên quan PASS trong lần chạy 1054 ở §2. STD-002/029: DEFERRED (provider refund, operator replay). STD-024: DEFERRED (legacy corpus). STD-011/032: chỉ còn Place.

## 4. Frontend core còn tồn đọng (không gồm Place)

| ID | Pri | Mô tả | Issue | Chặn |
|---|---|---|---|---|
| F-9E-1 | P1 | Dữ kiện/chính sách bịa trên trang đặt phòng & thanh toán (xem §3 STD-012). "Không hoàn tiền" sai với chính sách 24 h miễn phí cho VNPay | STD-012 | Trước AI WRITE (Agent trao người dùng sang các trang này) và trước demo khách hàng; không chặn Agent READ |
| F-9E-2 | P2 | 27 `fetch` thô không deadline trong auth/admin/users/cities/profile/my-bookings/newHotel/loyalty proxy | STD-026 | Không chặn Agent (Agent gọi backend) |
| F-9E-3 | P3 | `playwright.config.ts` không loại 7 spec area → `npm run test:browser` luôn đỏ 45 case | STD-028 | Không |
| F-9E-4 | P3 | `playwright.{content,documents,reviews}.config.ts` dùng Turbopack dev → điều hướng `ERR_ABORTED`; cùng spec PASS trên Webpack | STD-028 | Không |
| — | P3 | ESLint 133 errors/151 warnings (không phải acceptance gate) | — | Không |
| — | Low | `lib/api/hotel-documents.ts` fallback GET sang route trực tiếp khi 404 (chỉ đọc, có deadline, upload không replay) | STD-026 | Không |
| — | Deferred | Refund UI cho CUSTOMER chưa có | STD-002 | — |

Backend: F-9E-5 (P3) — `*ApplicationTests.contextLoads` phụ thuộc PostgreSQL ở `localhost:5432` mà không có guard opt-in như các suite khác.

## 5. Điều kiện trước Batch 10 (AI Agent READ MVP)

Đã đạt: C1 (public-read subset), C2 (search/availability), C3 READ tại Gateway (stack canonical), contract [READ_TOOL_CONTRACT](ai-agent/READ_TOOL_CONTRACT.md) cập nhật theo 9D (`checkedAt`, giới hạn F-9B-3, 409 approval, 503/504, `ownerId` null).

| # | Điều kiện | Loại | Trạng thái |
|---|---|---|---|
| R1 | Review + commit thay đổi 9A–9D ở cả hai repo (hiện chưa commit) để Batch 10 dựa trên baseline đã duyệt | Quy trình (người dùng quyết định) | OPEN |
| R2 | Bộ query đánh giá AI + tiêu chí kỳ vọng (§13.1 hàng cuối) | Batch 10 DoR | NOT STARTED |
| R3 | Agent tự giới hạn tần suất/ngân sách gọi tool (Gateway không có rate limiter; `RATE_LIMITED` không bao giờ phát sinh) và đặt deadline ≤10 s/lời gọi | Thiết kế Batch 10 | OPEN |
| R4 | Môi trường chạy Agent: nếu dùng hosted Supabase-dev thay vì stack canonical local, cần deploy bản 9D và chạy lại READ smoke trên hosted (bằng chứng hiện tại là local canonical) | Môi trường | OPEN (tuỳ lựa chọn) |
| R5 | Agent chỉ dùng route trong READ_TOOL_CONTRACT, anonymous, không Place, không truyền contact vào context khi không cần | Thiết kế Batch 10 | Đã ghi trong contract |

Không phải điều kiện cho Batch 10 nhưng là **gate trước AI WRITE (Batch 11)**: F-9E-1; STD-030 phần auth/revenue/user + tracing Gateway; STD-026 DB-outage 503 ở các service còn lại; STD-007 crash/multi-node; draft user-bound + xác nhận một lần (phạm vi Agent); provider refund vẫn DEFERRED và refund không cho Agent.

## 6. Đề xuất

1. Người dùng review/commit 9A–9E (R1).
2. **Batch 9F (nhỏ, frontend + backend vận hành, không chặn Batch 10):** F-9E-1 (lấy dữ kiện từ room/policy API hoặc bỏ), F-9E-3/F-9E-4 (cấu hình Playwright), tracing auth/revenue/user (STD-030), DB-outage 503 cho auth/user/revenue/regulations (STD-026). Có thể chạy song song Batch 10.
3. Batch 10 — Agent READ MVP theo §5.
