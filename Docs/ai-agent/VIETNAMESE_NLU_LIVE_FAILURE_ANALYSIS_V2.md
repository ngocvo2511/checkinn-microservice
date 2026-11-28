# Vietnamese NLU live failure analysis v2

24/11/2026. **LIVE_TECHNICAL_BLOCKED**;14 actual attempts,12 diagnostics+2 smoke;primary40 NOT_RUN. Không có independent holdout/generalization measurement. Các versions chỉ là local experimental prompt/schema-description variants.

## Actual failures

Mọi câu below schema-valid đều hiểu đúng city/amount/action/intent; những dòng FAIL bên dưới thiếu cờ exclusive và thêm `starRatings:{operation:CLEAR}` không có yêu cầu xóa sao. Chỉ v2 DIAG4D-01 còn thêm missingSlots checkIn/checkOut/adults không cần thiết cho undated reference browsing. Expected fields là city+maxPrice+exclusive true, missingSlots[]. Không sửa hậu kỳ response để tính PASS.

|Version/sample|Vietnamese utterance|Expected maxPrice|Actual problem|Model/backend|Cause/severity|
|---|---|---:|---|---|---|
|v2/DIAG4D-01|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|2000000.0|Missing exclusive; unsolicited starRatings CLEAR; extra missingSlots|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v2/DIAG4D-03|Tìm khách sạn Huế giá tham khảo dưới năm trăm nghìn|500000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v2/DIAG4D-04|Tìm khách sạn ở Huế giá tham khảo dưới 1,5 triệu|1500000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v3/DIAG4D-01|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|2000000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v3/DIAG4D-02|Khách sạn ở Huế giá tham khảo dưới một triệu|1000000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v3/DIAG4D-03|Tìm khách sạn Huế giá tham khảo dưới năm trăm nghìn|500000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v3/DIAG4D-04|Tìm khách sạn ở Huế giá tham khảo dưới 1,5 triệu|1500000.0|Missing exclusive; unsolicited starRatings CLEAR|INCORRECT/REJECTED|H2_H3_HYPOTHESIS_MISSING_EXCLUSIVE_AND_SPURIOUS_CLEAR/QUALITY|
|v3/VEX1-02|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|2000000.0|Provider non-200; status/usage not retained|NOT_EVALUATED/NOT_EVALUATED|H7_PROVIDER_NON_200_STATUS_NOT_RETAINED/BLOCKER|

## H1–H7 and evidence limits

- **H1 model misunderstanding:**0 city/amount/action/intent errors on13 valid outputs.7 output proposals semantically wrong in business representation. Không phân bổ chúng thành7 confirmed linguistic misunderstandings; prompt/schema/model-conditioned generation chưa được ablate.
- **H2 prompt limitation:**v1 không mô tả when/why exclusive;v2/v3 đã mô tả nhưng omission vẫn còn. V3 omit-absent-fields rule không loại được spurious CLEAR. Hypothesis có evidence, không causal proof.
- **H3 schema/representation:**schema biểu diễn được true và v2 có1 exact correct output. Descriptions chưa đủ; pattern exclusive→starRatings CLEAR cần điều tra. Không có bằng chứng provider schema incompatibility; không sửa authoritative fields/constraints hoặc globally require flag.
- **H4 deterministic parser:**0 correct proposals rejected;0 parser exceptions quan sát.7 incorrect proposals bị từ chối đúng. Không nên nới field-set equality hoặc để model tự xác nhận occupancy/provenance.
- **H5 backend state/policy:**không có defect mới được chứng minh. Actual multi-turn application/trusted selection/domain execution NOT_RUN; không suy diễn limitation của state từ single-utterance probes.
- **H6 oracle:**không có genuine oracle defect đã chứng minh;40 selected và expected outcomes giữ nguyên;9 pending+11 DENY không gửi. Expected diagnostics mới được định trước dispatch và có explicit per-night inclusive semantics.
- **H7 provider technical:**VEX1-02 UNAVAILABLE at GeminiHttpTransport.java:85, non-200 branch; exact status và usage UNKNOWN. Không thể biết invalid credentials/quota/transient provider status. Live đã dừng ngay. Không dùng lỗi này làm linguistic FAIL trong attribution, dù attempted accuracy denominator vẫn tính failure.

## Positive controls and regression

V2 DIAG4D-02 (`Khách sạn ở Huế giá tham khảo dưới một triệu`) trả đúng maxPrice1000000 + referencePriceExclusive REPLACE true và backend ACCEPTED. V3 cùng câu lại thiếu flag/thêm CLEAR: **PASS→FAIL regression**. Không claim fix ổn định.

Inclusive `Tìm khách sạn ở Huế giá tham khảo không quá 2 triệu mỗi đêm` đúng ở cả versions, không exclusive. `...tối đa 1 triệu mỗi đêm` đúng v2; `Tìm khách sạn ở Huế` đúng v3, không price/flag. Decimal `...giá tham khảo dưới 1,5 triệu` hiểu đúng1500000 nhưng sai flag. Không có missing-unit positive coverage, dates/occupancy/new references hoặc ranges live mới. Tránh đề xuất parser thay đổi cho các categories chưa có evidence.

## Fixes actually implemented

Local versioned prompt/schema descriptions; explicit selection preserves v1. Local continuation now allows ordinary semantic errors/isolated schema failures with known usage across smoke+main. Shared limiter local120/60min; no ordinary Agent/signed-mode expansion. Strict backend checks preserved. Offline continuation/unsafe-stop/version/constraint/budget tests PASS.

Sau technical failure, bổ sung observed HTTP status scalar ngay khi transport nhận headers và đọc trong local runner kể cả throw. Tests loopback200/redirect/429/500/503 kiểm chứng. Không log provider error body/headers hoặc gọi live lại. Historical status vẫn UNKNOWN; không tạo giả evidence.

## Priorities

1. Giữ campaign sealed tại14 attempts vì unknown usage. Một campaign sau cần dùng status observation đã sửa để biết provider outcome; không automatic retry.
2. Điều tra reference flag và spurious CLEAR bằng existing synthetic outputs/finite schema examples; không hardcode fixture IDs hoặc sửa proposal hậu kỳ.
3. Hoàn tất40 với frozen prompt/schema và original oracle khi technical pipeline cho phép, rồi phân loại failures thực tế trước khi thay parser/context architecture.
4. Chỉ ưu tiên10B4E context-aware khi single-utterance quality có evidence đủ tốt. Chưa đạt trong batch này.

[Full expected/actual failure matrix](nlu-quality-v1/failure-matrix-v1.json), [main report](BATCH10B4D_VIETNAMESE_NLU_QUALITY_IMPROVEMENT_REPORT.md). D07/D08 unaccepted; FULL_AGENT_E2E NOT_RUN; không production-readiness claim.
