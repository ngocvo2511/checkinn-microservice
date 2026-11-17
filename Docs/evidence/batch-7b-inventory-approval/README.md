# Batch 7B — mandatory inventory approval

Ngày 16/11/2026 (Asia/Saigon). Tất cả fixtures chỉ ở PostgreSQL18 disposable loopback; hosted reads/writes/fixtures = 0.

## Initial Git

Backend `feat/supabase-dev-runtime` / `ea46f3cb7168039b0160d05a05822aa906a81a17`; frontend `main` / `91a2751cf500c38b95a68e354374f63eea9d5113`, frontend sạch ban đầu. Backend có staged tài liệu/evidence/tooling Batch7 và unstaged IDE/docs/provenance fixes; giữ nguyên. `initial-git-state.json` lưu originalStatus tái lập từ initial tool observation, status/hash snapshot sau lượt edit đầu được ghi rõ thời điểm tương đối. Không coi hash của file Batch7B trong snapshot này là hash trước sửa. Không stage/commit/push/reset/checkout/restore.

## Review và policy

Đã đối chiếu PLAN/AUDIT, báo cáo Batch7, canonical schema và migration plan, evidence inventory/schema/regression Batch7. Review source thực tế: RoomInventoryReviewService, RoomType/RoomAvailability, repositories/controllers/DTO, AvailabilityService, HotelOfferSearchService, public mapper/catalog REST/gRPC, BookingService/HotelCatalogClient/HotelAvailabilityClient, confirm/release/cancel/expiry, OWNER edit/new room và ADMIN queue.

Canonical không có `approved_revision` riêng. `revision` là token optimistic concurrency của workflow: mỗi propose/review tăng một lần; không thể yêu cầu approval revision bằng revision của proposal PENDING mới. Hiệu lực xác định bằng pointer approved_request_id hiện hành + record APPROVED đúng room/hotel + positive quantity trùng total_rooms + source/requester/requested_at/reviewer/reviewed_at/reason đầy đủ + thời gian review không trước request + không có approval mới hơn bị bỏ qua; revision phải đạt lifecycle tối thiểu 2. Không tin boolean từ client. `State.verified` cũng dùng predicate database này.

Room mới lưu declared quantity nhưng chưa có approval nên không bookable. OWNER submit source/quantity trong workflow hiện có. Mọi thay đổi quantity sau tạo phòng phải qua proposal/review, kể cả unmanaged legacy. Unchanged quantity/unrelated edits giữ nguyên. Pending/rejected proposal tiếp theo không áp dụng quantity và không thu hồi approval cũ; nếu effective approval thiếu/bị vô hiệu thì chặn admission mới. Không thêm endpoint revoke hoặc sửa state machine giao dịch.

## Authoritative boundaries

- `RoomTypeRepository.hasEffectiveInventoryApproval`: một predicate dùng chung, query PostgreSQL trên dữ liệu approval thực.
- Availability đơn: từ chối 409/code INVENTORY_APPROVAL_REQUIRED; batch: trả 0 cho room chưa approved, room hợp lệ giữ stock theo peak held+booked.
- New hold: sau room FOR UPDATE, kiểm approval trước đọc/tạo counters/hold. Command retry trả đúng hold cũ trước admission gate; không tăng counters/deadline.
- Search có/không ngày: loại unapproved trước khi tạo offer; không fallback quantity thành availability. Public Hotel detail loại unapproved rooms, giữ publication của hotel; public room REST/gRPC cũng chặn/loại. OWNER/ADMIN preview không bị lọc.
- BookingService luôn lấy catalog thật rồi gọi Hotel hold; catalog 409 nay giữ business conflict thay vì 503. Hold 409 dùng error convention CONFLICT hiện có ở Booking. Không đổi financial/booking transitions.
- gRPC catalog dùng public service, unapproved room trả FAILED_PRECONDITION. Không có RPC tạo inventory hold riêng; grep tất cả RoomHold/counter writers chỉ về AvailabilityService. Scheduled cleanup chỉ giải phóng hold cũ.

## Authorization matrix

| Actor | Xem private state | Propose | Review/queue | Tạo hold |
|---|---|---|---|---|
| Anonymous/customer | Không | Không | Không | Không |
| OWNER đúng hotel | Có | Có | Không | Không |
| OWNER khác hotel | Không | Không | Không | Không |
| ADMIN | Có | Không | Có | Không |
| booking-service token | Không | Không | Không | Có, approval bắt buộc |

JWT principal/role ở controller, service ownership/admin checks và SecurityConfig; không tin X-Is-Admin/X-OWNER-ID. Test real HTTP security filter xác nhận anonymous401/OWNER403/ADMIN200 cho review; native workflow kiểm quyền/revision/stock thật. Gateway là pass-through, service enforcement vẫn bắt buộc khi truy cập trực tiếp.

## Concurrency và lịch sử

Proposal, review, direct room updates và new hold dùng cùng room FOR UPDATE. Hold creation command advisory lock giữ idempotency/cross-room command contract. Review kiểm latest pending pointer, input revision và previous_quantity sau lock; giảm quantity phải >= peak(held+booked), không clamp/repair counters. Confirm chỉ chuyển held→booked; release/expire/cancel chỉ giảm commitment, giữ nguyên cơ chế lock hold/counters.

Native canonical Hotel test: chưa duyệt bị chặn và batch0; approve thật rồi hold thành công; metadata/wrong room/hotel/mismatched quantity/revision denial; retry hold giữ nguyên sau approval bị đổi REJECTED; confirm hold đó vẫn thành công; held capacity/race hai bookings; approve reduction-vs-booking chỉ một kết quả tương thích; hai OWNER cùng revision một winner; hai ADMIN cùng proposal một decision; stale superseded proposal không duyệt; OWNER replacement-vs-ADMIN review cùng revision chỉ một winner; pending/rejected giữ effective approval trước.

Không tự hủy/release/refund booking vì mất approval. Không sửa snapshot/receipt/payment/refund/loyalty/revenue records. Native corruption probes là synthetic fixture trong owned database, không phải tác vụ trên dữ liệu thật.

## Database và reproduction

`tools/batch7b-schema-verify.py` tái sử dụng guard harness Batch7 nhưng ghi riêng evidence Batch7B và run directory mới `.local/batch7b-schema-<testRunId>`. Chỉ `127.0.0.1:55486`, đúng data_directory/database ownership, năm owner DB riêng, canonical V1/Flyway validate/no-op/JPA/native checks/catalog diff; stop cluster trong finally, giữ local synthetic files để review. Không đọc docker/.env hay nhận target Supabase. Không V2 cần thiết, không sửa V1/clean/repair/baseline.

Raw logs nằm trong `.local/batch7b-*.log` và private run directory (không publish credentials). Kết quả redacted machine-readable: `schema-verification.json`, `regression-results.json`, `final-verification.json`. Catalog/manifests mới nằm trong evidence này, không ghi đè Batch7.

Gateway không có `src/test`; lệnh test có pattern thất bại vì không tìm thấy test. Ghi NOT RUN/SKIPPED, không dùng failIfNoTests để giả PASS. Service HTTP/JWT và gRPC authorization regression là bằng chứng local; full running Gateway-to-services chưa chạy.

## Giới hạn

Không chứng nhận physical count ngoài thực địa; ADMIN phải thực sự kiểm tra source. Không hosted/provider acceptance, production restart/load/multi-node hoặc restore drill. Predicate hiện đọc theo room, có thêm SQL cho catalog/search; broad load/query-budget acceptance chưa chạy. Opt-in range/candidate integration cũ không thuộc canonical harness được ghi SKIPPED, không tuyên bố toàn bộ opt-ins đã PASS. UI browser tests dùng synthetic API, không chứng nhận hệ thống provider thật. VNPay/Storage deferred không đổi.
