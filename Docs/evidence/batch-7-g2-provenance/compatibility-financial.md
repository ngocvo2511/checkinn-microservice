# Batch 7 — STD-024 và giới hạn dữ liệu tài chính

Ngày: 16/11/2026 (Asia/Saigon). Kiểm tra source hiện tại, không đọc hoặc sửa bản ghi hosted. Synthetic tests không chứng minh nguồn gốc hoặc completeness của corpus lịch sử thực tế.

## Đối chiếu contract và thực trạng

| Field/luồng | Contract hiện tại | Kết luận |
|---|---|---|
| Hotel/Room description và scalar name/contact | TEXT, nullable theo entity; PLAIN_V1 trong DTO | Giữ nguyên Unicode, newline, dấu ngoặc kép, chuỗi literal `null`, JSON-looking text; không parse text như JSON hoặc tự decode hai lần. Mapper tests hiện có được giữ. |
| Hotel.address | JSONB NOT NULL; DTO object địa chỉ | Serializer chỉ áp dụng field structured. Không có chứng cứ phải nhận plain text/array/string double-encoded; không tự biến dữ liệu không rõ thành địa chỉ hợp lệ. |
| Room.capacity | JSONB NOT NULL; CapacityDto object | Không đổi schema/validation/policy sức chứa; không mở rộng support mọi JSON shape. |
| Room.amenities | JSONB nullable; DTO List<String> nullable | GAP thật: SQL NULL làm mapper throw; JSON null trả null; unchecked List.class cho phép số/object lọt ra DTO dù contract là chuỗi. Đã sửa tối thiểu bên dưới. |
| Hotel amenities/content | Catalog/category/item entities và TEXT titles, không phải mọi field là JSONB | Giữ reviewed catalog mapping, scalar encoding và structured content contracts. Không mass JSON conversion. |
| Regulation.snapshot_data | TEXT NOT NULL chứa JSON serialized từ map | ObjectMapper serializer giữ quotes/backslashes/newlines/Unicode và nested nullable values. DTO trả nguyên String lịch sử; không tự parse/rewrite snapshot cũ. |
| Revenue event receipt payload | JSONB NOT NULL, quarantine APPLIED/IGNORED/REVIEW_REQUIRED | Giữ raw invalid source identity và JSON null hợp lệ; không áp object-only constraint hoặc rewrite payload. |

## Implementation

`RoomTypeMapper.readAmenities` phân biệt SQL NULL/JSON null (unknown, trả null), `[]` (known empty) và array-of-strings (giữ literal values). Object, number, boolean, null item, malformed JSON và JSON string/double-encoded array không được biến thành label có vẻ hợp lệ. Lỗi mới không echo raw historical payload ra message. Không sửa entity hoặc database khi mapping.

Đây là sửa application adapter theo List<String> DTO contract và nullable schema, không đổi business rule hoặc DDL. Không thêm V2 cho sửa này; không sửa V1. Compatibility adapters hiện có ở frontend không bị xóa hoặc thay đổi.

Test mới `RoomAmenitiesCompatibilityTest`: SQL NULL/JSON null, known-empty array, Unicode/newline/quoted/literal JSON-looking labels, round-trip DTO serialization, malformed/structured/numeric/null-element/double-encoded values; kiểm tra input entity không bị đổi.

Native helper `verifyEncodingRoundTrips` trong canonical Hotel acceptance bổ sung TEXT/JPA round-trip và SQL-inserted JSONB shapes qua Hibernate String binding + actual generated RoomTypeMapper. Kiểm tra raw DB value trước/sau mapping không đổi. Đây chỉ là synthetic contract verification trên database disposable do runner kiểm soát, không representative historical upgrade.

## Historical financial boundary — source review

- `Payment.vnpayCreateDate` immutable, nullable với legacy; `transactionOccurredAt` tách khỏi receipt-processing `paidAt`. Không suy diễn timestamp/provider request từ thời gian nhận.
- `CashTransactionWriter` giữ `LEGACY_HISTORY_NOT_VERIFIED` trong cashflow gaps, phân biệt LEGACY_PROJECTION_ONLY/MISSING_TRANSACTION_FACTS/TRANSACTION_CONFLICT; CREDIT/DEBIT đòi hỏi source identity/transaction facts. `RevenueCashFlows` chặn báo cáo khi unresolved gaps, legacy versionless payment hoặc REVIEW_REQUIRED receipts còn tồn tại.
- `LoyaltyPointsService` từ chối legacy EARN/USE không có command/monetary receipt; giữ reconciliation boundary. Không cấp lại điểm hoặc sửa balance để hợp thức hóa lịch sử.
- Booking/payment/refund state machine, snapshot amounts, refund amounts, ledger và raw receipt không được sửa trong Batch 7 compatibility work. Không gọi VNPay, không triển khai provider refund/query.
- Actual historical receipts/refunds/cash/loyalty provenance: **NOT VERIFIED** do không có sanitized owner-confirmed corpus. Owner review cần source receipt/event identity/version, original transaction time, immutable snapshot và đối chiếu CREDIT/DEBIT/loyalty tương ứng; không xuất PII. Legacy cutover/import **DEFERRED**, không tự tạo dữ liệu rồi tuyên bố lịch sử PASS.

## Executed tests và giới hạn

- `mvn -f regulations-service/pom.xml -Dtest=RegulationConsistencyTest,RegulationServiceTest test -q`: **PASS 8**, failures/errors/skips 0.
- `mvn -f revenue-service/pom.xml -Dtest=UuidReceiptContractTest,CashTransactionIdentityTest test -q`: **PASS 4**, failures/errors/skips 0.
- `mvn -f user-service/pom.xml -Dtest=LoyaltyPointsServiceTest test -q`: **PASS 26**, failures/errors/skips 0.
- First native Hotel attempt: **ERROR** do standalone test mapper thiếu dependency `mediaAssetMapper`; empty Hibernate collection vẫn đánh giá method reference trên dependency null. Đã inject generated MediaAssetMapper trong test fixture; không sửa production logic cho lỗi wiring test. Kết quả rerun và cleanup được ghi trong schema/run evidence của Batch 7; không đổi lần lỗi đầu thành PASS.
- Hotel focused first attempt: **FAIL ở testCompile**, missing hàng loạt existing classes trong shared target khi có overlapping Hotel Maven jobs; không có test được thực thi ở attempt đó. Chạy tuần tự lại và native test được ghi riêng trong tổng kết tests của Batch 7; không coi attempt lỗi hoặc skip là PASS.
- Native JPA và unit focused kết quả cuối: xem regression/schema evidence của Batch 7; không dùng reports cũ làm fresh evidence.
- Không hosted write tests; không fixture Supabase để cleanup; không thay đổi dữ liệu lịch sử thật.

STD-024 selected application compatibility được triển khai; full historical-data acceptance vẫn **PARTIAL / NOT VERIFIED** cho actual historical corpus. Không đánh dấu toàn STD-024 FIXED_VERIFIED.
