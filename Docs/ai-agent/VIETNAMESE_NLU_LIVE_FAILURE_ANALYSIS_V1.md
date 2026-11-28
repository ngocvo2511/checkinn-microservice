# Vietnamese NLU live failure analysis v1

24/11/2026. **COMPATIBILITY_FIXED_SMOKE_FAILED**. Chỉ có initial smoke2 và supplemental3 để phân tích linguistic output; primary40 **NOT_RUN**. Hai compatibility diagnostic tách riêng. Model `gemini-3.5-flash-lite`, prompt/schema `vietnamese-extraction-compact-v1` không đổi. TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION; không independent human evaluation hoặc blinded holdout.

## Hai vấn đề khác nhau

**Provider compatibility, đã sửa (P1):** raw response HTTP200 chứa extraction đúng Đà Lạt nhưng usage có `promptTokensDetails`/`serviceTier`. Closed usage allowlist từ chối trước SDK. Recorded-response test và diagnostic live sau sửa chứng minh full pipeline thành công. Không phải NLU model failure; không thể khôi phục raw response lịch sử của batch trước. [Bằng chứng](BATCH10B4C_LIVE_COMPATIBILITY_FIX_AND_EVALUATION_REPORT.md), [recorded response](../../ai-agent-service/src/test/resources/gemini/compatibility/gemini-3.5-usage-metadata.json).

**Reference-price semantic omission, chưa sửa prompt (P1 smoke blocker):** model đúng action/intent/city/amount nhưng bỏ cờ nghiệp vụ `referencePriceExclusive`. Strict schema cho phép field vắng mặt, backend/oracle yêu cầu field theo literal phrase. Backend từ chối đúng; không nhận giá inclusive thay exclusive. Không chấp nhận một model proposal sai để nâng tỷ lệ PASS.

## Từng quan sát thất bại có ý nghĩa

Trong bốn trường hợp dưới đây, phần chung expected và actual là `action=CRITERIA`, `intent=SEARCH`, `reference=""`, `missingSlots=[]`, `inferredFields=[]`. Mọi city/price dùng `operation=REPLACE`. Expected bổ sung `referencePriceExclusive={"operation":"REPLACE","value":true}`; actual **không có field này**, không phải value=false.

|Fixture/sample|Exact synthetic utterance|Expected fields|Actual fields|Schema / backend|
|---|---|---|---|---|
|VEX1-02|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|cityName=Nha Trang; maxPrice=2000000; referencePriceExclusive=true|cityName=Nha Trang; maxPrice=2000000; exclusive absent|PASS /REJECTED|
|SUP1-REF-01 (source VNE1-002)|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|cityName=Nha Trang; maxPrice=2000000; referencePriceExclusive=true|cityName=Nha Trang; maxPrice=2000000; exclusive absent|PASS /REJECTED|
|SUP1-REF-02 (source VNE1-020)|Khách sạn ở Huế giá tham khảo dưới một triệu|cityName=Huế; maxPrice=1000000; referencePriceExclusive=true|cityName=Huế; maxPrice=1000000; exclusive absent|PASS /REJECTED|
|SUP1-REF-03 (source VNE1-050)|Tìm khách sạn Huế giá tham khảo dưới năm trăm nghìn|cityName=Huế; maxPrice=500000; referencePriceExclusive=true|cityName=Huế; maxPrice=500000; exclusive absent|PASS /REJECTED|

Cả bốn dừng ở `VietnameseExtraction.corroborate` với `Uncorroborated extraction`: proposed field keys thiếu một key so với independent literal parser. Không parser exception, không tool call. Severity mỗi trường hợp **P1 đối với smoke/evaluation admission**, tác động người dùng dự kiến là không nhận được browse hợp lệ cho yêu cầu giá tham khảo này. Không quan sát data corruption hoặc xác nhận giá/availability sai vì backend chặn.

Expected/actual JSON đầy đủ và từng pipeline/usage/latency: [observations JSON](live-compatibility-v1/observations-v1.json), [CSV](live-compatibility-v1/observations-v1.csv). VEX1-02 response được lưu thành [regression fixture](../../ai-agent-service/src/test/resources/gemini/compatibility/gemini-reference-price-missing-exclusive.json); test chứng minh schema PASS nhưng backend vẫn REJECTED. SUP1-REF-01 là diagnostic lặp câu có ID riêng; không ghi đè smoke và không phải automatic retry.

## Attribution dựa trên bằng chứng

- **H2 — prompt/instruction limitation, giả thuyết chính có bằng chứng:** instruction nói literal criteria và VND nhưng không định nghĩa khi nào tạo `referencePriceExclusive`. Ba cách biểu đạt số đều đúng amount nhưng đều bỏ cùng field nghiệp vụ. Giải thích bằng prompt thiếu ánh xạ hợp lý hơn kết luận model không hiểu “một triệu”/“năm trăm nghìn”. Chưa có A/B thay prompt nên chưa khẳng định causal proof hay bảo đảm bản prompt mới sẽ chữa được.
- **H3 — schema/protocol documentation gap góp phần:** schema có field với operation const REPLACE và value const true, nhưng không có mô tả phrase trigger; field optional. Schema có thể biểu diễn kết quả đúng nên đây không phải provider wire/schema dialect incompatibility. Không làm field bắt buộc trên mọi request vì nhiều request không phải reference price.
- **H1 chưa chứng minh:** không có wrong city/amount ở các requests quan sát. Omission không tự chứng minh hiểu nhầm ngôn ngữ; model vẫn phải đáp ứng contract, nên các dòng này giữ semantic FAIL.
- **H4 không quan sát trong các proposal đã validate:** không có model-correct/backend-rejected. Bốn proposal thiếu field được reject là đúng. Chưa có live bằng chứng để sửa finite grammar.
- **H5:** backend policy đang bảo vệ đúng sự khác biệt exclusive/inclusive. Không thử actual state application, occupancy confirmation hoặc reference resolution.
- **H6:** expected outcomes của bốn câu trên không có ambiguity mới được phát hiện; giữ oracle nguyên vẹn. Chín unresolved product cases vẫn excluded trước dispatch và không cản trở riêng smoke này.

Không invent thêm lỗi dates/occupancy/CLEAR/REPLACE/informal/unaccented/reference; những nhóm đó trong bộ chính chưa chạy. Các proposal giá đã gửi không chứa model-created trusted provenance, IDs, tool requests, dates, occupancy hoặc facts.

## Số liệu và giới hạn

Initial smoke: action2/2, intent2/2, missingSlots2/2, exact criteria1/2, exact semantics1/2, backend1/2; VEX1-03 NOT_RUN. Model/backend matrix: correct+accepted1, correct+rejected0, incorrect+accepted0, incorrect+rejected1. Supplemental: action/intent/city/amount3/3, exclusive0/3, exact semantics0/3; incorrect+rejected3. Primary40: attempted0, schema-valid0, semantic0, backend0, **NOT_RUN40; accuracy N/A**. Không gộp diagnostic/supplemental thành primary score.

Raw amounts được canonicalize thành scientific notation trong semantic JSON không tạo lỗi numeric: 2E+6 bằng2000000. Strict extraction JSON gốc không bị repair. Missing/exclusive là lỗi thực, không phải formatting mismatch. Generic runner falseRejection theo expectedBackend không phải tỷ lệ backend từ chối model đúng; semantic false rejection quan sát0.

Không có reference/clarification positive case, dates/occupancy evaluation hoặc adversarial injection response trong các request này. Không claim broad Vietnamese NLU accuracy hoặc production readiness. D07/D08 chưa accepted, E30 và FULL_AGENT_E2E NOT_RUN. Provider failures/invalid schema vẫn thuộc attempted denominator; NOT_RUN tách riêng.

## Batch cải thiện được đề xuất

**BATCH10B4D-VIETNAMESE-NLU-QUALITY-IMPROVEMENT**, tập trung semantic contract:

1. Tạo prompt/schema-description version mới giải thích rõ “giá tham khảo dưới X”: maxPrice=X và referencePriceExclusive=true; phân biệt “không quá/tối đa” inclusive, ngân sách chưa rõ đơn vị và giá thực tế. Không thêm mặc định stay/occupancy hoặc quyền xác nhận.
2. Kiểm thử offline phrase mapping/boundaries và strict rejection của omission, unknown fields, duplicate keys. Giữ MODEL_PROPOSED, independent literal corroboration và backend-authoritative validation.
3. Diagnostic live có giới hạn cho prompt mới, lưu old/new digests và không pool với v1. Chạy final smoke mới3/3 trên cùng version rồi mới chạy frozen40 unchanged oracle. Quan sát cũ tiếp tục immutable.
4. Sau khi có40 actual observations, ưu tiên grammar/prompt/state cải thiện bằng đúng failure matrix. Không thực hiện broad NLU rewrite hoặc infrastructure/authorization batch từ những nhóm chưa đo.

Không tự làm post-processing để thêm cờ rồi tính strict semantic PASS. Không dùng số supplemental nhỏ như một holdout khách quan. Batch hiện tại giữ prompt v1 và ghi nhận failure, thay vì làm mất baseline bằng prompt tuning giữa chiến dịch.
