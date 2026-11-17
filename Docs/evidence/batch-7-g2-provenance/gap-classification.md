# Batch 7 — Phân loại G2, 16/11/2026

Các kết luận Batch 3/4 là checkpoint lịch sử. Năm canonical V1 đã có local acceptance Batch 5B và hosted acceptance Batch 6B; không phân loại lại là chưa triển khai. Batch 6B database integration đã tạm đóng theo quyết định người dùng, không bị mở lại bởi VNPay/Storage.

| Finding / acceptance scope | Trước Batch 7 | Xử lý Batch 7 / giới hạn |
|---|---|---|
| STD-006 stock thiếu/nonpositive, counter âm/overcommitted, implicit default 10 trong canonical runtime | DONE theo source/tests trước | Regression lại; không đổi quantity/history |
| STD-006 OWNER proposal + ADMIN approval/rejection, revision/audit, room lock | DONE ở implementation | Bổ sung native review/hold race và kiểm tra provenance |
| STD-006 `verified` dựa trên quantity nhưng chưa kiểm đầy đủ approval/link/evidence | PARTIAL | Sửa service, thêm tests; không tạo approval mới |
| STD-006 phân loại stock lịch sử không suy đoán từ giá trị 10 | PARTIAL ở tài liệu | Thêm SQL metadata-only chỉ đọc; kiểm thử synthetic bốn nhóm |
| STD-006 physical count/source thực tế và danh sách từng bản ghi cần review | NOT VERIFIED | Không đọc dữ liệu hosted/legacy; không có corpus owner-confirmed. Không tạo danh sách giả |
| STD-006 bắt buộc approval với mọi stock unmanaged/room mới | NOT IMPLEMENTED ở contract hiện hành | Kế hoạch §56/56.1 đã duyệt workflow và giữ unmanaged legacy compatibility. Cần quyết định policy riêng trước đổi booking admission; không tuyên bố unverified stock đã bị chặn toàn bộ |
| STD-021 five V1 provisioning/history/Hibernate hosted | DONE trong evidence Batch 6B | Giữ acceptance, không migrate/rerun hosted |
| STD-021 clean provisioning/catalog/constraints/indexes/Hibernate | DONE ở Batch 5B | Verification mới trên disposable; phân biệt với kết quả lịch sử |
| STD-021 runtime canonical/supabase validate, SQL never, Flyway disabled | DONE theo source | Không đổi config đã verified; legacy defaults không phải profile hosted được chấp nhận |
| STD-021 service-specific current applied/source manifest | PARTIAL | Manifest hash-bound mới, tách canonical/legacy/test/security; không giả history legacy |
| STD-021 representative full legacy upgrade/schema diff | NOT VERIFIED | Không có representative release/data; synthetic cases không thay thế |
| STD-021 legacy cutover/import lên Supabase | DEFERRED | Quyết định U03 Batch 5A là fresh/no demo import; không tự mở cutover |
| STD-021 production backup/restore/financial reconciliation | DEFERRED | Ngoài Batch 7, không tự thực hiện G5 |
| STD-024 scalar descriptions/canonical text và regulation serialization | DONE trong selected contracts | Giữ literal/plain/null, giữ adapters; regression |
| STD-024 nullable room amenities SQL NULL → DTO | NOT IMPLEMENTED đúng schema | Sửa mapper và thêm SQL NULL/JSON null/native round-trip |
| STD-024 amenities sai shape/double-encoded thành List có vẻ hợp lệ | PARTIAL | Từ chối shape ngoài array-of-string/null; giữ nguyên dữ liệu gốc |
| STD-024 corpus encoding lịch sử thực tế | NOT VERIFIED | Không mass decode/normalize/rewrite; không đóng toàn finding |
| Historical receipts/refund/CREDIT-DEBIT/loyalty/snapshots schema compatibility | DONE trong canonical synthetic acceptance | Đối chiếu constraints/typed refs và focused regression; không chỉnh state machine/amount |
| Actual historical financial source/settlement evidence | NOT VERIFIED | Đưa vào owner review riêng, không suy từ projection/fixture |
| VNPay/Storage provider acceptance, Place, cloud deploy, G3–G5 | DEFERRED | Không triển khai trong Batch 7 |

## Quy trình review thực tế, không sửa dữ liệu tự động

1. Operator xác nhận target và quyền đọc của đúng Hotel database. Batch 7 **không** chạy query này trên Supabase.
2. Chạy [metadata classifier](../../../docker/batch7-inventory-provenance.sql) trong transaction repeatable-read/read-only. Output chỉ room/hotel UUID, quantities, revision và reason codes; không xuất source/reason text, actor/customer IDs.
3. `VERIFIED` chỉ có nghĩa có recorded approval khớp quantity và đầy đủ metadata, không chứng nhận số phòng vật lý ngoài thực địa. `UNKNOWN_SOURCE` gồm cả NULL và 10 khi thiếu nguồn; `INVALID_CONFIRMED` là lỗi quantity/counter xác định được trong snapshot, không kết luận ai gây ra.
4. OWNER kiểm đếm và gửi proposal/source qua workflow hiện có. ADMIN đối chiếu bằng chứng, committed/held load và revision trước approve/reject; không bulk approve hoặc ghi trực tiếp DB.
5. Bản ghi chưa đủ chứng cứ giữ `UNVERIFIED`/`UNKNOWN_SOURCE`; không sửa booking/hold, không tăng stock để booking thành công. Muốn áp dụng mandatory review cho mọi legacy/create path phải duyệt policy triển khai riêng.

## Migration boundary

Không cần V2 cho các sửa Java/mapper/tests/tooling của Batch 7. Không sửa V1. Nếu muốn database-enforced review-link/evidence constraints: proposal riêng phải kiểm tra orphan/cross-room rows và approved rows thiếu reviewer/time/reason bằng read-only inventory trước, chọn version additive V2 theo owner, thử disposable và validate/regression, xin duyệt target/version rồi mới hosted apply. Không backfill approval để hợp thức hóa constraint; không phát hành migration trong Batch 7.
