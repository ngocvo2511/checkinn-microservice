# Batch 7 — G2 remaining gaps & historical-data provenance implementation

Ngày: **16/11/2026, Asia/Saigon**. **Final Status: PARTIAL** cho toàn acceptance Batch 7/G2; source fixes đã triển khai và kết quả kiểm thử ghi bên dưới. Không đánh dấu toàn STD-006/021/024 là FIXED_VERIFIED. Batch 6B database integration tiếp tục được tạm đóng; provider VNPay/Storage là workstream riêng.

## 1. Executive Summary

Đã tìm và sửa hai khoảng trống application: inventory `verified` chưa kiểm đầy đủ bằng chứng phê duyệt/link đúng room–hotel; mapper amenities không đọc được SQL NULL mặc dù canonical schema cho phép, và unchecked List làm dữ liệu sai shape lọt vào DTO. Bổ sung authorization/provenance/round-trip/concurrency tests, danh sách provenance chỉ đọc và migration manifests theo service. Không chỉ audit; không sửa schema hoặc dữ liệu lịch sử.

Canonical V1 đã được triển khai thành công trong Batch 5B/6B. Batch 7 kiểm chứng lại bằng local disposable và giữ nguyên acceptance hosted trước đó. Không import legacy, không sửa checksum/history, không dùng Supabase làm test database.

Phần còn mở: physical stock/source thực tế, corpus encoding/financial records lịch sử và representative legacy upgrade chưa có evidence owner-confirmed. Policy đã duyệt giữ unmanaged legacy compatibility; verified-only booking trên toàn inventory cần quyết định riêng. Vì vậy chưa đủ điều kiện đóng đầy đủ Batch 7/G2.

## 2. Initial State

Backend `feat/supabase-dev-runtime`, HEAD `ea46f3cb7168039b0160d05a05822aa906a81a17`; frontend `fix/place-card-distance-visibility`, HEAD `ae40b4e64dfcce6b19a042a954909d9d148646d6`. Backend có sẵn `.idea/compiler.xml`, `.idea/encodings.xml` unstaged, không staged/untracked; frontend sạch. Giữ nguyên IDE/index/HEAD; không reset/checkout/restore/stage/commit/push. [Initial state/hash](evidence/batch-7-g2-provenance/initial-state.json).

Đối chiếu PLAN/AUDIT, Batch 3/4/5B/6B và canonical database-design/evidence với source thực tế. Các checkpoint cũ còn giữ nguyên, không dùng kết luận migration-only FAIL của Batch 3 để phủ định canonical V1 đã được nghiệm thu. [Gap matrix](evidence/batch-7-g2-provenance/gap-classification.md).

## 3. STD-006 Findings & Implementation

OWNER nhập quantity/source và tạo proposal; ADMIN duyệt/reject theo revision; review-managed edits dùng chung room `FOR UPDATE` lock với hold. Missing/nonpositive stock và counters không hợp lệ fail closed, không default 10 hoặc tăng stock để booking thành công. Approval kiểm peak held+booked trên các ngày đã ghi nhận, không giảm booked/held để làm cho quantity hợp lệ.

Sửa `RoomInventoryReviewService`: chỉ `verified=true` khi approval đúng trạng thái, room/hotel, positive quantity và đầy đủ source/requester/reviewer/time/reason; link sang room/hotel khác trả conflict, không lộ source của đối tượng khác. Thêm guard tại review để từ chối request có hotel link không khớp trước mutation. Không tạo approval/backfill hoặc thay audit trail.

Thêm SQL metadata-only `docker/batch7-inventory-provenance.sql`, transaction read-only/repeatable-read: VERIFIED / UNVERIFIED / INVALID_CONFIRMED / UNKNOWN_SOURCE, kèm reason codes. Không xuất source/reason text hoặc actor/customer IDs. Quantity 10 và NULL thiếu evidence đều không trở thành VERIFIED; 10 cũng không bị coi là sai. `VERIFIED` là recorded approval khớp, không phải chứng nhận physical count ngoài thực địa. Chỉ chạy classifier trên synthetic fixtures của disposable database.

Kế hoạch §56/56.1 đã duyệt compatibility cho unmanaged legacy API. Không tự đổi sang verified-only booking; nhánh unmanaged có positive stock vẫn có thể booking theo contract cũ. Đây là giới hạn acceptance còn mở, không được tuyên bố đã chặn toàn bộ stock chưa xác minh. [10 câu hỏi source flow, tests và policy boundary](evidence/batch-7-g2-provenance/inventory.md).

**STD-006: PARTIAL / IN_PROGRESS**; integrity và regression scope có implementation, actual historical stock/source **NOT VERIFIED**.

## 4. STD-021 Findings & Implementation

Năm độc lập User/Hotel/Booking/Revenue/Regulations, 46 business tables/458 columns/5 Flyway histories/32 entities. Canonical và supabase-dev runtime dùng Hibernate validate, SQL init never, Flyway disabled; migration là guarded external job. Không sửa config đã được Batch 6B kiểm chứng. Historical defaults/profile SQL cũ không được đem áp dụng lên canonical hoặc dùng làm hosted startup contract.

Thêm reproducible `tools/batch7-schema-verify.py`: tạo cluster local riêng theo testRunId, xác nhận data directory/owned database/loopback target, chạy canonical Flyway/JPA/repository/constraints/permission tests, so normalized columns/constraints/indexes/sequences/history với catalog Batch 5B, dừng cluster trong finally. Giữ private synthetic data để review, không đụng development DB.

Manifest từng owner ghi SHA256, exclusive canonical location, version 1 đã được Batch 6B xác nhận, historical prerequisites/conflicts và external role grants tách khỏi Flyway. Legacy SQL không được replay trên canonical; không sửa V1, baseline/repair/clean hoặc migration history. [Manifest](evidence/batch-7-g2-provenance/migration-manifests.json), [schema reproduction/results](evidence/batch-7-g2-provenance/schema.md).

Representative full legacy upgrade/diff **NOT VERIFIED**, không dùng synthetic rows để chứng nhận upgrade thật. Legacy cutover/import **DEFERRED** theo U03 fresh/no-import Batch 5A. Production backup/recovery nằm ngoài Batch 7.

**STD-021: canonical scoped gate DONE; parent PARTIAL / IN_PROGRESS** do historical acceptance còn thiếu.

## 5. STD-024 Findings & Implementation

`RoomTypeMapper.readAmenities` giữ SQL NULL/JSON null là unknown, `[]` là known empty, string array giữ literal Unicode/newline/quoted/JSON-looking labels. Object/numeric/boolean/null-element/malformed/double-encoded shapes bị từ chối; không tự decode thành dữ liệu có vẻ hợp lệ, không mutate entity/JSONB.

Description Hotel/Room là TEXT và giữ nguyên literal/plain/null; address/capacity là structured JSONB theo DTO, không giả định mọi field hỗ trợ mọi JSON shape. Regulation snapshots dùng serializer hiện hành, giữ adapters frontend. Tests mới kiểm DTO serialization và native Hibernate/JSONB raw-value fingerprint trước/sau mapping. [Field matrix/financial review](evidence/batch-7-g2-provenance/compatibility-financial.md).

**STD-024: selected compatibility implemented/tested; parent PARTIAL / FIXED_UNVERIFIED** cho actual historical corpus. Không mass rewrite JSON hoặc xóa adapter.

## 6. Historical Financial Boundary

Đối chiếu payment receipt/transaction time, refund execution/audit, immutable CREDIT/DEBIT source identity, raw/quarantined receipts, loyalty command/ledger và booking snapshots. Canonical schema/typed refs/constraints được giữ, financial review gates tiếp tục từ chối dữ liệu legacy không đủ chứng cứ. Không thay paid/refund amounts, financial snapshots, revenue transactions, loyalty balances hoặc payment/refund state machine; không gọi VNPay provider.

Actual historical financial provenance **NOT VERIFIED**; owner cần review receipt/event/version/original transaction time và reconciliation riêng. Không suy từ projection hoặc timestamp xử lý thành settlement fact.

## 7. Database/Test Environment

Unit tests không cần DB; native tests dùng PostgreSQL 18.0 loopback `127.0.0.1:55486` trong cluster `.local/batch7-schema-<testRunId>` mới. Target identities/ownership và cleanup ghi trong schema evidence, không credentials. Không copy hosted data, không dùng database development/demo hoặc năm projects Supabase cho test.

Không khởi động backend cloud, VNPay, email thật, Storage hoặc Place. Frontend không sửa nên không chạy frontend/TypeScript trong Batch 7.

## 8. Test Results

| Final suite | Executed PASS | FAIL/ERROR | SKIPPED |
|---|---:|---:|---:|
| Hotel full unit/regression, loại default ApplicationTests thiếu môi trường | 390 | 0 | 33 opt-in |
| Booking lifecycle/payment/refund focused regression | 135 | 0 | 0 |
| Five canonical native PostgreSQL/Flyway/Hibernate tests | 5 | 0 | 0 |
| Regulations consistency/service | 8 | 0 | 0 |
| Revenue UUID receipt/cash identity | 4 | 0 | 0 |
| Loyalty service | 26 | 0 | 0 |
| Standalone migration runner unit | 11 | 0 | 0 |
| Runtime/provider configuration unit, mocks only | 10 | 0 | 0 |

Catalog **5/5 PASS**, 458 columns và exact constraints/indexes/sequences/history **zero diff**. Review suite 13 và amenities compatibility 12 nằm trong Hotel390, không cộng lại; initial focused Hotel70 và runtime-config2 cũng không cộng trùng. Các 33 opt-in skips không phải PASS; ApplicationTests bị loại không phải test đã chạy. Không provider/network acceptance từ configuration unit tests.

Kết quả cuối chi tiết được đồng bộ tại [schema results](evidence/batch-7-g2-provenance/schema-verification.json), [inventory regression](evidence/batch-7-g2-provenance/inventory.md), [compatibility regression](evidence/batch-7-g2-provenance/compatibility-financial.md) và [regression summary](evidence/batch-7-g2-provenance/regression-results.json). Không cộng overlapping focused/full suites thành một tổng độc lập.

Lần compile Hotel chồng target, lần native fixture thiếu inject MediaAssetMapper và lần full Hotel có assertion mới kỳ vọng sai exception contract được giữ là failed attempts; không coi là PASS hoặc lỗi production đã xác nhận. Sửa test wiring/assertion và chạy lại đầy đủ; native dùng cluster mới. Skipped opt-in tests tách khỏi executed PASS. Actual historical corpus/upgrade và frontend không chạy không được ghi PASS.

## 9. Supabase Cleanup Verification

**NOT APPLICABLE: hosted reads 0, writes 0, hosted fixtures 0 trong Batch 7.** Không có fixture Supabase cần xóa; không dùng cleanup evidence Batch 6B để giả kết quả mới. Không sửa schema/history hoặc dữ liệu tồn phòng/tài chính hosted. Local cluster dừng trong finally và dữ liệu synthetic được giữ trong run-owned directory để review; không reset database cũ.

## 10. Files Changed

- Java source: `RoomTypeMapper.java`, `RoomInventoryReviewService.java` (Hotel service).
- Java tests: `RoomAmenitiesCompatibilityTest.java` mới, `RoomInventoryReviewServiceTest.java`, Hotel `canonical/CanonicalPostgresAcceptanceTest.java`.
- Tooling: `docker/batch7-inventory-provenance.sql`, `tools/batch7-schema-verify.py`.
- Evidence: `Docs/evidence/batch-7-g2-provenance/`, gồm initial state, gap matrix, inventory/compatibility/schema notes, manifests, catalogs, run results và verification.
- Documentation: báo cáo này; checkpoint có chọn lọc trong PLAN/AUDIT và migration implementation plan, giữ lịch sử.

Danh sách chính xác/hashes/boundary checks tại `final-verification.json`. Không frontend, Place, payment/refund business source, V1 hay runtime config changes; hai `.idea` modifications là thay đổi có trước.

## 11. Remaining Risks

Actual physical-stock review và historical text/financial corpus chưa có nguồn xác minh. Legacy applied-history/release baseline ngoài canonical còn UNKNOWN. Muốn áp verified-only admission cho unmanaged stock phải duyệt policy và rollout riêng, xử lý hold/booking hiện hữu; không tự chặn/repair hồi tố. Hosted schema constraint hardening nếu cần phải có additive V2 proposal/read-only precheck/disposable tests và phê duyệt riêng, không sửa V1.

Không cần V2 cho những fixes đã triển khai. Không mở lại provider acceptance, production recovery hoặc G3/G4/G5 trong batch này.

## 12. Final Status

**PARTIAL — chưa đủ điều kiện đóng đầy đủ Batch 7/G2.** Có technical implementation và bounded verification, không phải audit-only. Canonical acceptance được bảo toàn; phần thiếu evidence/policy đã tách rõ NOT VERIFIED/DEFERRED. Không nâng toàn STD-006/021/024 thành FIXED_VERIFIED.

| Acceptance Batch 7 | Kết luận |
|---|---|
| 1. Phân loại G2 gaps | DONE, gap matrix từng scope |
| 2. Inventory provenance/safe review | PARTIAL: workflow và integrity tests PASS; actual counts/source chưa xác minh, universal verified-only gate chưa được duyệt |
| 3. Không implicit stock=10 canonical runtime | DONE, source/constraint/tests |
| 4. Safe source/config fixes cần thiết | DONE, hai Java source fixes; không cần thay runtime config |
| 5. Applied V1 không đổi | DONE, SHA256 và Git boundary verification |
| 6. Clean provisioning | PASS5/5 disposable |
| 7. Schema/history manifest | DONE68 SQL sources, five independent histories; legacy applied UNKNOWN được ghi rõ |
| 8. Text/JSONB compatibility | PASS cho tested contracts; actual historical corpus NOT VERIFIED |
| 9. Không tự sửa historical data | DONE, hosted/legacy access0 |
| 10. Relevant regression | Final suites PASS;33 opt-in SKIPPED và excluded tests được tách |
| 11. Không còn Batch7 fixtures trên Supabase | NOT APPLICABLE, không tạo hosted fixtures |
| 12. Owner review/legacy decisions ghi rõ | DONE, NOT VERIFIED/DEFERRED không đổi thành PASS |

## 13. Recommended Next Workstream

Tiếp tục **G2 owner-confirmed provenance review**: cung cấp sanitized physical-count/source và historical encoding/receipt evidence theo workflow hiện hành; quyết định có mở rộng approval bắt buộc cho unmanaged inventory hay giữ compatibility. Không cần import legacy vào Supabase để thực hiện review này. Nếu không có kế hoạch cutover, giữ legacy upgrade DEFERRED/NOT VERIFIED theo scope; không tự chuyển sang G3–G5.
