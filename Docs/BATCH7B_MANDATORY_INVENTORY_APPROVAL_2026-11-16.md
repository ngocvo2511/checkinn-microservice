# Batch 7B — Mandatory Admin Inventory Approval Enforcement

Ngày **16/11/2026, Asia/Saigon**. Trạng thái phạm vi: **LOCAL IMPLEMENTATION DONE / HOSTED ACCEPTANCE NOT VERIFIED**. Không production-ready; acceptance qua hosted/full running Gateway chưa thực hiện.

## 1. Executive Summary

Đã triển khai verified-only inventory admission thực sự trên backend, thay thế compatibility cho positive unmanaged inventory theo quyết định mới của người dùng. Inventory chưa có approval hợp lệ không tạo hold/booking mới, không trở thành offer hay public room có thể chọn; hotel publication giữ riêng. Giữ nguyên idempotency và lifecycle các hold/booking đã tạo hợp lệ. Không thay canonical V1, không cần migration và không đọc/ghi Supabase.

## 2. Business Policy

OWNER khai báo room, submit quantity/source; proposal PENDING chỉ lưu audit. ADMIN duyệt/reject với revision/reason; approved quantity chỉ áp dụng trong transaction kiểm capacity. Unapproved/rejected không có effective approval thì không nhận booking. Proposal mới không tự áp quantity, previous approved quantity tiếp tục hiệu lực nếu còn hợp lệ; mất approval chỉ chặn admission mới, không tự hủy hold/booking hay tác động tài chính.

Không kiểm một boolean đơn lẻ. Predicate truy vấn record thực: approved_request_id thuộc đúng room/hotel; APPROVED; positive proposed_quantity khớp total_rooms; source/requester/requested_at/reviewer/reviewed_at/reason đầy đủ; reviewed_at không trước requested_at; không bỏ qua approval mới hơn và workflow revision >=2. `revision` trong schema là concurrency token tăng khi propose/review, không phải immutable approval-version. Vì vậy không so revision của latest PENDING với approval cũ: pointer và quantity là effective approval identity, revision input/latest pointer/previous_quantity được kiểm sau room lock khi review.

## 3. Existing Implementation

Workflow OWNER proposal / ADMIN queue+history / revision+ownership guard / peak stock check và room FOR UPDATE đã tồn tại. Batch7 bổ sung provenance/source linkage; Batch7B giữ các thay đổi có trước. Khoảng trống cũ: AvailabilityService chỉ kiểm positive quantity, undated search dùng configured stock, direct unmanaged updates cho tăng quantity, public catalog và giá thấp nhất chưa kiểm approval.

Đã đọc PLAN/AUDIT, Batch7 report, canonical schema/migration plan và evidence inventory/schema; kiểm source RoomType/RoomAvailability, review/controller/repository/DTO, booking create/retry/hold/confirm/release/expiry, public search/detail/REST/gRPC và OWNER/ADMIN UI. [Chi tiết review và authorization](evidence/batch-7b-inventory-approval/README.md).

## 4. Backend Changes

- `RoomTypeRepository.hasEffectiveInventoryApproval`: predicate dùng chung trên approval record thật, không tin client.
- `AvailabilityService`: single read trả business409 khi chưa approved; batch trả0 cho unapproved; new hold kiểm approval sau room lock, trước counters/hold writes. Existing command retry trả original hold/deadline trước gate, không reserve lại.
- `InventoryApprovalRequiredException`, controller và exception advice: stable code **INVENTORY_APPROVAL_REQUIRED**, HTTP409, không source/reviewer/reason nội bộ.
- `HotelOfferSearchService`: kiểm approval cả dated/undated offers. Public Hotel mapper lọc unapproved rooms và chỉ tính lowestPrice từ approved inventory; hotel không có approved room vẫn theo publication policy, room list rỗng/lowestPrice null. Không fabricated price/stock.
- `RoomTypeServiceImpl`: public room list/single REST và gRPC chỉ cung cấp approved room; owner/admin preview giữ nguyên. gRPC unapproved trả FAILED_PRECONDITION, không INTERNAL.
- `RoomInventoryReviewService`: `State.verified` dùng cùng predicate; review kiểm positive quantity/source/request metadata trước áp dụng; mọi changed quantity qua legacy update bị409, kể cả chưa managed. Unchanged count/unrelated edits vẫn cho phép.
- Booking `HotelCatalogClient`: catalog409 giữ business conflict thay vì bị coi dependency503; không lộ raw upstream body. Booking hold409 giữ convention CONFLICT hiện hành. Không đổi booking/payment/refund semantics.

Grep toàn main source cho thấy chỉ AvailabilityService tạo RoomHold/tăng held; confirm chuyển held→booked, cancel/release/expiry giảm commitment. Booking create luôn gọi authoritative hold, retry accepted booking không tạo reservation mới. gRPC catalog đi qua public service; không có RPC hold writer riêng hay scheduled bypass.

## 5. Frontend Changes

Tái dùng RoomInventoryReview hiện có. OWNER edit luôn khóa direct quantity field và hướng dẫn proposal/review; hiện thông báo phòng chưa thể nhận booking mới khi `verified=false`. Form/source/revision/PENDING/REJECTED/reason/resubmit có sẵn và được browser kiểm chứng. New-room success thông báo chưa bookable, cung cấp link đến cùng workflow review; không xây workflow thứ hai.

ADMIN queue/reason/approve/reject/history giữ logic hiện có; browser bổ sung successful approve/reject và empty refresh, conflict không giả success, outage không giả empty. Public detail nhận filtered rooms từ backend nên không có CTA chọn unapproved room. Không sửa Place.

## 6. Database/Migration Changes

**Không thay schema, không V2 cần thiết, không sửa applied canonical V1/history.** Native harness mới `tools/batch7b-schema-verify.py` kế thừa loopback/owned-database guards, tạo run directory mới, ghi riêng evidence Batch7B. PostgreSQL18 disposable, five independent owner databases/public histories; Flyway migrate/validate/no-op, Hibernate/repositories/permission và exact catalog comparison. V1 hashes giữ nguyên baseline5B.

## 7. Authorization & Concurrency

Backend JWT role/principal + ownership/service checks: chỉ OWNER đúng hotel propose, chỉ ADMIN review/queue; OWNER không tự duyệt dù forge X-Is-Admin. booking-service token mới gọi hold và vẫn phải qua approval. Unit HTTP security filter kiểm401/403/ADMIN200; native kiểm workflow thực.

Tất cả propose/review/room update/new hold dùng chung room FOR UPDATE. Review kiểm latest PENDING/revision/previous_quantity sau lock; quantity giảm phải >= peak nightly held+booked, không sửa counters để hợp thức hóa. Hai ADMIN cùng proposal chỉ một decision; hai OWNER cùng revision chỉ một proposal; stale/superseded không approve; concurrent OWNER replacement-vs-ADMIN review chỉ một winner. Approval reduction-vs-booking không oversell.

Native kiểm retry hold sau approval bị đổi REJECTED vẫn trả original, new command bị từ chối, existing hold vẫn confirm được; confirm/release/cancel/expiration/idempotency regression giữ nguyên. Không auto-release/refund khi kết quả giao dịch chưa xác định. [Evidence concurrency](evidence/batch-7b-inventory-approval/README.md).

## 8. Tests & Results

Kết quả cuối được ghi tại [regression-results.json](evidence/batch-7b-inventory-approval/regression-results.json); [danh sách file sửa](evidence/batch-7b-inventory-approval/files-changed.json). Hotel full suite bao gồm inventory/search/catalog/security/gRPC, không cộng lại focused subsets. Booking full suite bao gồm payment/refund/lifecycle/recovery; native5 tests tách khỏi opt-in skips trong unit runs. Browser mock API không được coi provider acceptance.

| Suite | Executed PASS | FAIL/ERROR cuối | SKIPPED |
|---|---:|---:|---:|
| Hotel full regression, loại HotelServiceApplicationTests thiếu môi trường | 396 | 0 | 33 opt-in |
| Booking full regression, loại BookingServiceApplicationTests thiếu môi trường | 285 | 0 | 4 opt-in |
| Five canonical PostgreSQL/Flyway/JPA tests | 5 | 0 | 0 |
| Frontend regression | 214 | 0 | 0 |
| Browser inventory/new-room/facts/search | 28 | 0 | 0 |
| TypeScript --noEmit / Next production build | PASS | 0 | 0 |
| Gateway standalone authorization suite | NOT RUN | Không có suite | Không có src/test |

Canonical catalogs **5/5 MATCH**, zero drift, clusterStopped=true. Các ApplicationTests bị loại không tính PASS. Không cộng focused/repeated attempts. Gateway pattern command đã FAIL vì không có test matching; không dùng workaround để đổi thành PASS. Authorization tại Hotel service có tests, full running Gateway-to-service chưa chạy.

Failed attempts giữ minh bạch: lần compile test mới gặp AssertJ generic inference ambiguity; lần full Hotel có hai verifyNoInteractions cũ chưa tính approval read và một fixture public room thiếu approval. Sửa type inference/fixture/kỳ vọng theo policy, chạy lại final suites. Các shell-edit/build reruns không được đếm thêm tests.

## 9. Supabase Safety/Cleanup

**Hosted reads=0, writes=0, fixtures=0; cleanup NOT APPLICABLE.** Không reset/xóa bảng/dữ liệu, không import legacy, không đọc docker/.env cho tests hoặc khởi động service chống hosted. User xác nhận chưa có hotel không được dùng làm quyền reset. Local run-owned cluster dừng trong finally; synthetic local files giữ để review, không development DB touched. Không thay booking/payment/loyalty/revenue thật, V1 hay history. VNPay/Storage deferred status giữ nguyên.

## 10. Remaining Risks

Hosted/runtime/provider acceptance, full running Gateway, physical stock/source thực tế, multi-node/load/restart và recovery/restore chưa kiểm. Gateway không có suite riêng; service boundary enforcement và security tests mới là bằng chứng local. Opt-in range/candidate integration cũ ngoài canonical harness chưa chạy và không được báo PASS. Predicate tăng số read SQL theo room ở catalog/search; cần query-budget/load review trước rollout lớn. Không có API revoke mới; nếu effective approval bị vô hiệu ở dữ liệu thì admission fail closed, existing lifecycle giữ nguyên.

Giữ nguyên kết luận lịch sử Batch7/7A và parent STD-006 provenance: chính sách admission đã triển khai không đồng nghĩa kiểm kê thực địa/historical corpus đã được chứng minh. Production readiness không được nâng trạng thái.

## 11. Final Status

**LOCAL IMPLEMENTATION DONE / HOSTED ACCEPTANCE NOT VERIFIED.** Source enforcement, local native race/contract tests, frontend workflows và scoped regression hoàn tất theo kết quả cuối; hosted deployment/business acceptance không thực hiện. Local acceptance không bao gồm Gateway integration không tồn tại suite, broader opt-ins hay provider acceptance.

Không stage/commit/push/reset/checkout/restore; giữ HEAD/index và các thay đổi trước. [Initial Git/provenance](evidence/batch-7b-inventory-approval/initial-git-state.json), [final boundaries/files](evidence/batch-7b-inventory-approval/final-verification.json).

## 12. Recommended Next Workstream

OWNER khai báo source/physical count thật và ADMIN review qua workflow; sau đó thực hiện acceptance runtime ở đúng môi trường được cho phép riêng, gồm Gateway→Booking→Hotel và query/load budget. Hosted write/fixtures chỉ sau phê duyệt riêng, có manifest/cleanup; không dùng Supabase làm test DB mặc định. Provider VNPay/Storage vẫn là workstream độc lập.
