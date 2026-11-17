# Batch 8B — Hosted E2E & performance, 17/11/2026

## 1. Executive Summary

**PARTIAL — scoped authenticated approval/booking E2E đã chạy thật; performance AFTER, query-count slope nhiều mức và full concurrency chưa đạt. Không DONE, không production-ready.**

Đã xác minh năm targets; ADMIN `admin` login và ADMIN queue qua Gateway HTTP200; tạo 2 OWNER, 2 CUSTOMER, 2 hotel, 4 room types; chạy proposal/approve/reject/authorization/booking/idempotency/cancel bằng API. Hotel publication được setup bằng exact-ID SQL synthetic, không phải acceptance upload giấy tờ/duyệt publication API.

Read optimization đã triển khai và qua **838 unit/service/security regression + 5 native canonical PostgreSQL tests**, không failure/error ở các lượt cuối. Hosted AFTER bị chặn vì runtime không sẵn sàng sau restart, có `too many connections` ở Hotel runtime role. Không có claim cải thiện latency hoặc N+1 resolved.

Cleanup xóa **47 rows**, verify **48 manifested IDs absent** (một OTP đã bị lifecycle resend xóa). Dữ liệu hiện hữu theo fingerprint khớp, schema/Flyway không đổi 5/5. ADMIN nguyên vẹn. Lượt HTTP login cuối503 do User runtime unavailable; credential BCrypt kiểm trực tiếp vẫn khớp secure environment.

## 2. Supabase Environment

User/Loyalty, Hotel/Inventory, Booking/Payment, Revenue, Regulations: control-plane identity, runtime mapping, TLS1.3 verify-full PASS ở preflight mới. Session pooler5432, guarded supabase-dev: Hibernate validate, SQL init never, Flyway false; không hosted DDL/migration/role change. Chi tiết tại `*-runtime-preflight.json`, `*-before.json`, `*-after.json`.

Năm columns/constraints/indexes/Flyway history khớp canonical và fingerprint trước/sau. Baseline:3 users,1 hotel,0 room/booking/hold; Regulations có4 snapshots/regulations/outbox hiện hữu, không khởi động worker này. Không khởi động Revenue/Media. Hikari max2/minIdle1, acquisition timeout10000ms. Windows/Java21, local Gateway/services đi Supabase; region mô tả là pooler hostname codes, không bằng chứng độc lập vị trí PostgreSQL.

## 3. ADMIN Authentication

Username `admin`, role ADMIN, active, email verified. Credentials đọc từ `docker/.env` ADMIN_PASSWORD trong memory, không hardcode hoặc xuất password/JWT/hash. Login200, role từ auth response ADMIN, JWT thật truy cập `/api/hotel/admin/inventory-reviews`200. `/admin/...` trực tiếp trên Gateway404 là sai route của runner và đã sửa; không phải authorization failure.

Protected ADMIN ID nằm riêng trong manifest; không là fixture, không dùng destructive scenario, không sửa role/password/status. Credential/token files của runner nằm private/ignored và được dọn sau phiên; evidence không chứa chúng.

## 4. Fixture Scope

Run `batch8b-20261117T015015Z-e3b481f8`. Small2×2, 2OWNER/2CUSTOMER,5 review requests,3 booking/hold,2 daily counters,6 outbox events và dependent profiles/OTP/zero-point loyalty side effects. Không ADMIN fixture. Medium3×5/Larger5×10 NOT RUN vì pressure/readiness stop; không tạo hàng nghìn records.

Tạo accounts/OTP/login/hotel/room/proposal/review/booking qua Gateway. SMTP chỉ Mailpit riêng local; reverse DNS gây hai OTP attempts thất bại, sửa local Mailpit bằng `--smtp-disable-rdns`, resend/verify/login sau đó PASS. Hotel API publication yêu cầu Storage documents+thumbnail; chỉ cập nhật publication của exact synthetic hotel IDs để kiểm inventory, không giả nhận provider/publication acceptance. Không sở hữu/sửa hotel thật.

Manifest được tạo trước writes, liệt kê owner/table/PK/dependencies/scenario/side effects/cleanup order/verification. Có scoped cleanup gate trước writes. Cancel unpaid booking còn tạo2 zero-point command receipts và1 zero-point loyalty account; đã kiểm user/booking đúng fixtures, điểm0, không có points transaction/payment/refund/Revenue ledger. Không xóa audit/ledger thật.

## 5. OWNER → ADMIN → CUSTOMER E2E

OWNER tạo room và PENDING proposal; CUSTOMER booking trước approval trả409, không hold/counter write. ADMIN queue/approve200, available quantity2, CUSTOMER booking201. Duplicate booking key trả cùng ID201; đổi payload cùng key409.

ADMIN stale review409; quantity proposal giảm2→1 khi occupied2 bị409; reject200, approval cũ vẫn verified/currentQuantity2. Cancel2 unpaid bookings204 và occupied trở về0. Các checks là hosted HTTP thật, không lấy mocks làm acceptance. Có54 PASS/4 FAIL logged checks theo từng attempt, gồm setup/duplicate labels; **không phải54 unique business scenarios**. OTP failures/expiry request400/concurrency failure được giữ tại `e2e-results.json`.

## 6. Authorization & Booking Lifecycle

OWNER/CUSTOMER approve403; OWNER B sửa hotel OWNER A403; forged ADMIN header trên CUSTOMER JWT403; stale revision409. Direct hold endpoints theo contract chỉ nhận booking-service authority; CUSTOMER đi qua Booking API, không ký machine/ADMIN token giả.

Concurrency: tạo được đúng2 booking/hold, occupied2 với stock2, không thấy oversell trong trạng thái đối soát. Tuy nhiên lượt recovery race trả409/500/409; log có Hikari acquisition timeout, max2/10s. **PARTIAL, không concurrency PASS**; dừng tăng tải. Một probe SQL08006 đã được giữ và đối soát lại exact IDs, không replay writes mù.

Booking thứ3 trên tháng3 được tạo để chờ natural15-minute hold expiry, khác ngày search benchmark. Lượt đầu400 do unitPrice fixture không khớp catalog, sửa request rồi201. Runtime Hotel/User không ready sau đó nên **expiry/release end-to-end NOT VERIFIED**, không sửa expires_at để tạo PASS. Không payment/onsite confirm/refund/provider call; paid-confirm lifecycle ngoài phạm vi đã kiểm.

## 7. Performance Baseline

Small2 hotels×2 room types, Gateway8080→Hotel8083→Supabase. Warmup3,30 attempted samples, concurrency1. **29 successful/1 timeout**, timeout45s, error/timeout3.33%. Chưa đạt30 successful samples; không retry để che lỗi. Sau cell lỗi dừng, không tăng concurrency/dataset. Direct comparison và các cells khác NOT RUN. Percentile linear interpolation trên successful samples, không tính timeout thành success.

| Endpoint | n success/attempt | p50 ms | p95 ms | p99 ms | Mean ms | Min–max ms | attempted req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| search undated C1 | 29/30 | 5875.8 | 15758.2 | 19907.9 | 7541.6 | 5335.1–21236.0 | 0.114 |

Raw có timeout45s riêng. Đây là development diagnostic; một functional expiry setup khác ngày có overlap cửa sổ trước, không benchmark isolated/production hoặc saturation capacity. Không gán giá trị thiếu cho C3/C5/C10/direct/detail/availability benchmark.

## 8. N+1 Analysis

Instrumentation riêng, warmup1/endpoint +3 serial samples, không gộp với benchmark:

| Endpoint | JDBC statements/request | Mean JDBC ms, gồm network | Mean HTTP ms |
|---|---|---:|---:|
| search_undated | 31, 30, 30 | 4652.2 | 6159.9 |
| search_dated | 38, 38, 38 | 5802.0 | 6920.5 |
| public_catalog | 26, 27, 26 | 4051.7 | 4770.9 |
| hotel_detail | 14, 14, 14 | 2030.3 | 2457.6 |
| room_availability | 4, 4, 4 | 568.6 | 1294.5 |

Sai lệch1statement ở một số sample có thể gồm scheduled session; Hibernate session metrics không trace-isolated và không bao gồm transaction-control/JdbcTemplate ngoài Hibernate. Source xác nhận hai per-room approval loops ở public room mapping/lowest price, thêm checks ở search và batch availability. **Chỉ có một populated size: chưa đo runtime slope2×2→3×5→5×10; không tuyên bố N+1 resolved.** Lazy room-media và association/amenity reads theo hotel còn mở.

SELECT-only EXPLAIN ANALYZE trên Small: single approval0.825ms, batch candidate1.087ms server execution; không phải HTTP/JDBC time. Persistent TLS libpq SELECT1 mean342.3ms, SELECT1+rollback mean495.6ms,10 samples; driver/protocol/network cộng chung, không phải pure RTT hay Hikari. Network/transaction overhead rất khác server execution; Hikari wait và app processing/Gateway chưa tách đầy đủ.

## 9. Optimizations

`RoomTypeRepository.findEffectivelyApprovedRoomIds`: set-based native predicate giữ đủ room/hotel/quantity/status/provenance/reviewer/time/effective pointer/latest-approval constraints. `HotelMapper` lấy approved IDs theo batch500 và dùng chung cho public room list+lowestPrice, không cache xuyên request. `AvailabilityService` batch approval trong batch availability; `HotelOfferSearchService` batch eligible IDs/dated availability, không gọi single availability mỗi room.

Giữ nguyên authoritative `hasEffectiveInventoryApproval` sau room FOR UPDATE ở new hold, transaction boundaries, authorization, idempotency, confirm/release và provenance. Không schema/index/V1/financial/frontend change. Native test thêm đối chiếu batch predicate khi approval valid và khi corruption metadata. Duplicate reads theo hotel và lazy associations chưa tối ưu toàn bộ.

## 10. Before/After

BEFORE artifact/hash + raw/query counts có evidence. AFTER JAR build PASS sau khi dừng process Windows đang giữ JAR;838+5 regressions PASS. Tuy nhiên hosted restart không đạt readiness, Hotel báo runtime role quá nhiều connections; User/Hotel vẫn không sẵn sàng sau bounded quiesce/restart. Migration-role read-only probe thấy4 Supavisor sessions/role ở một số targets, không đủ quyền xem owner/state để terminate an toàn. Không tăng pool/role cap hoặc kill backend session không xác định.

**AFTER benchmark BLOCKED, không có % improvement, không có measured SQL reduction claim.** Không dùng native/mock/empty-dataset thay comparison. Implementation chưa đạt hosted performance acceptance.

## 11. Cleanup Verification

Dừng clients và toàn bộ owned JVM; không Revenue/Regulations consumer. Ack đúng6 fixture booking events và2 fixture OTP DLQ payloads, không purge; final queues ready/unacked0. Redis chỉ kiểm/xóa4 exact fixture session-generation keys (0 tồn tại), loại ADMIN. Xóa2 container Mailpit riêng của batch,4 captured messages synthetic; original infra dừng/retained, không xóa dữ liệu hạ tầng có trước.

Database cleanup qua TLS verify-full migration-role transaction, exact PK lists + expected row counts; không prefix DELETE/TRUNCATE/DROP/sequence reset. Một OTP đã lifecycle-delete nên lần guard mismatch rollback; đối soát actual exact-ID counts trước khi cleanup lại. Xóa User15 +Booking12 +Hotel20 =47 rows;48 manifested IDs verified absent. Outbox/loyalty/account/dependent rows đều nằm manifest; không residue.

Schema/history/counts unchanged5/5, non-fixture complete-row salted fingerprints match5/5. Fingerprint baseline được lấy sau registration, exclude exact manifested IDs; bảng baseline0 được chuẩn hóa rỗng theo snapshot pre-write. Vì vậy không tuyên bố có full-content snapshot trước mọi write. Counters sequence có thể tiến do synthetic events, không reset; schema/history không đổi.

## 12. ADMIN Preservation

Final `admin` tồn tại, ADMIN, active/email verified, same ID hash/baseline fields, complete user row fingerprint unchanged, BCrypt match với secure environment PASS. Không lưu password hash/JWT. Initial Gateway login/authorization200; **final actual HTTP login503/BLOCKED do User unavailable**, không đánh đồng BCrypt check với login E2E. ADMIN không ở cleanup fixture IDs và được giữ cho batch sau.

## 13. Regression

| Suite cuối | Executed PASS | FAIL/error | Opt-in skipped |
|---|---:|---:|---:|
| User |110|0|4|
| Auth |47|0|0|
| Hotel |396|0|33|
| Booking |285|0|4|
| Native PostgreSQL18 canonical (5 owners) |5|0|0|

**843 unique executed PASS,41 opt-in skips.** Chỉ cộng final full suite/module và5 native executions riêng; không cộng focused/failed attempts. Default ApplicationTests không chạy lại vì thiếu environment context, explicit excluded. Initial default User context error và intermediate Mockito setup failure được giữ riêng; final suites không che chúng. Frontend không đổi bởi Batch8B nên SKIPPED; không dùng kết quả Batch7B làm test mới.

## 14. Remaining Risks

Connection budget/role cap/Supavisor session lifecycle sau restart chưa được giải quyết. Không terminate backend không xác định ownership hoặc chỉnh hosted role/DDL để vượt gate. Cần operator xác minh pools/active clients/idle session behavior, restart procedure và connection headroom trước load tiếp.

Medium/Larger,30 successful samples/cell,Gateway/direct overhead,C3/5/10 read benchmark,AFTER comparison,runtime room-count slope,steady expiry/release/paid-confirm và final HTTP ADMIN re-login còn thiếu. Hotel publication từ documents chưa acceptance vì Storage thật bị cấm. N+1 room-media/per-hotel association còn mở. Không production-ready.

## 15. Final Status

**PARTIAL / HOSTED PERFORMANCE AFTER & FULL LIFECYCLE ACCEPTANCE BLOCKED.** Scoped initial auth/approval/booking/idempotency/authorization/cancel PASS; concurrency/pool/readiness và performance gates chưa đủ. Cleanup DONE, permanent ADMIN preserved, schema/Flyway unchanged. Backend/frontend HEAD và staged index giữ nguyên; frontend pre-existing diff byte-for-byte preserved, không stage/commit/push/reset/checkout/restore.

## 16. Recommended Next Workstream

Giải quyết runtime-role/Supavisor connection lifecycle với operator, trong budget được duyệt và không tự apply DDL. Sau readiness ổn định, tạo run mới/exact-ID manifest mới; hoàn tất hosted AFTER & same-dataset BEFORE có đủ successful samples, direct/Gateway metrics,3 dataset sizes/query slope,concurrency/expiry/final HTTP admin login. Kiểm room-media/association fetching bằng SQL patterns; chỉ tối ưu tiếp khi có populated evidence. Giữ ADMIN hiện hữu, không tạo ADMIN khác. Batch8 lịch sử được giữ nguyên.

Evidence: [Batch8B directory](evidence/batch-8b-hosted-e2e-performance/README.md).
