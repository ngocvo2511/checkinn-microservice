# BATCH 5A — UUID contract implementation & regression verification

Ngày13/11/2026, Asia/Saigon. **PARTIAL**: source UUID và backend gates đạt; frontend typecheck còn lỗi baseline thuộc Place được bảo vệ. Không chuyển sang Batch5B/6.

## A. Initial State

Backend `test/booking-hotel-gateway-acceptance` tại `8ee230b250863164dd47f83af037711a0ea4a0ef`; frontend `main` tại `d446b9808f8732c9982da2c156979e245742aa7f`. Các thay đổi IDE, tài liệu, deletion BATCH2 và schema harness có sẵn được giữ nguyên. [Trạng thái ban đầu](evidence/batch-5a-uuid/initial-state.md) và JSON ghi đầy đủ. Không stage/commit/push/merge/reset.

## B. Implementation Summary

Triển khai38/38 approved persisted columns: User3, Booking21, Revenue14. Bốn entity PK Booking/Item/Payment/Refund dùng UUID; association BookingItem.booking được giữ; repositories/services/controllers/jobs/JDBC và adapters đồng bộ. [Danh sách38 field và source paths](evidence/batch-5a-uuid/implementation-inventory.md), [machine inventory](evidence/batch-5a-uuid/implementation-inventory.json). Không đếm DTO/signature như field chuyển đổi mới. Native tests xác minh tập thao tác đại diện, không phải catalog V1 đầy đủ38 field.

## C. Preserved Exceptions

String: ratePlanId; loyalty command key UUID|TYPE; hash/idempotency/business/provider/VNPay/evidence/document references; audit actor SYSTEM_RECOVERY/source_user system; raw revenue_event_receipts.booking_id TEXT. Regulations và snapshot IDs vẫn BIGINT. Không thêm RatePlan domain, physical cross-owner FK hoặc sửa deletion/stock/financial policy.

## D. Cross-Service Changes

Booking cập nhật domain, strict boundary, outbox và Hotel/Loyalty adapters; User cập nhật ba booking refs, ledger/reservation/command/recovery; Revenue cập nhật14 refs, native binds/readback/projections/report/cash và giữ raw receipt. Hotel/Auth/Media/Notification/Regulations không cần thay production source; protobuf clients/test liên quan build PASS. Frontend không thay đổi, IDs tiếp tục là string. [Compatibility](evidence/batch-5a-uuid/api-grpc-event-compatibility.md).

## E. Contract Verification

UUIDv3/v4, uppercase, malformed/missing/null và serialization có tests; malformed REST400 và gRPC INVALID_ARGUMENT được kiểm tra cùng authorization regression. Spring converter fallback từng cho short UUID đi qua: bổ sung strict binding editor và regression; User JSON parse400 thay vì generic401. So sánh Hotel catalog dùng UUID identity để tránh String.equals(UUID). Scheduler giữ text lexical ordering.

Golden test chạy PASS trước và sau conversion; expected vectors độc lập chứng minh UTF-8/prefix/Booking/hold UUIDv3 và request/key hash giữ nguyên. Command key canonical UUID|TYPE giữ cấu trúc; retry/idempotency regressions PASS. Protobuf bytes/tag/string contract không đổi. Event routing/version/hash/dedup/raw evidence được giữ; không regenerate pending events. [Golden vectors](evidence/batch-5a-uuid/golden-vector-results.md), [contract gates](evidence/batch-5a-uuid/id-contract-verification.md).

## F. Regression Results

8/8 backend module BUILD SUCCESS:901 executed PASS,39 existing opt-in skipped,0 failures/errors. Ba native PostgreSQL tests chạy riêng PASS,0 skip. Frontend regression211 PASS; focused booking lint PASS. Frontend tsc FAIL giống baseline: `NearbyPlacesSection.tsx(161,15)` showDistance không thuộc PlaceCardProps. Không sửa hoặc suppress lỗi này. [Commands/counts/limits](evidence/batch-5a-uuid/regression-test-results.md), [class-level results](evidence/batch-5a-uuid/backend-unit-results.json).

Mock/loopback/native subsets không được coi là toàn bộ Gateway/RabbitMQ/multi-node/restart workflows PASS. ApplicationTests và shared-DB/Redis opt-in suites không chạy vì an toàn DB; không tính skipped là PASS. Các lần compile/test trung gian thất bại giữ log; kết quả cuối cùng ở trên. Không gọi VNPay thật hay refund provider API; REFUND_INTERNAL_ENABLED không đổi.

## G. Database Boundary

Docker daemon không có; dùng PostgreSQL18 binaries để tạo riêng cluster loopback55485, ba DB mới batch5a_uuid_booking/user/revenue, owner/credential riêng. Guard kiểm tra database và data_directory trước mỗi schema mới ngẫu nhiên. Tests dùng create-only synthetic mappings; không ddl-auto=update/create-drop. JPA UUIDv3 association/generated UUIDv4, repository/advisory lock, JDBC UUID, nullable refs, loyalty ledger và Revenue raw receipt/CREDIT-DEBIT/dedup PASS. Harness đầu thiếu Boot naming strategy, đã sửa riêng harness và chạy lại thành công; không xóa schema thất bại.

Cluster riêng đã dừng, data/log giữ lại; không drop/truncate/reset/import demo, không kết nối/sửa DB hiện tại hay Supabase. Chưa có canonical Flyway V1, all-owner migration/JPA validate/role/security/runtime acceptance. Tuyệt đối không chạy source mới trên demo VARCHAR cũ. [Native results](evidence/batch-5a-uuid/native-postgres-results.json).

## H. Protected Scope

SHA-256 so với inventory trước batch: toàn bộ frontend, place-service, proto copies, historical migrations/runtime resources/POM/docker harness không đổi. [Bằng chứng hash](evidence/batch-5a-uuid/protected-scope-verification.json). Lỗi showDistance có trước và sau batch; không phải sửa Place gián tiếp. Không đổi Hotel City identity.

## I. Remaining Risks

TypeScript blocker cần scope Place được cho phép riêng rồi rerun. Full application/broker/concurrency/restart và canonical schema acceptance còn NOT VERIFIED. U01/U03/U04 đã phê duyệt, không yêu cầu phê duyệt lại. STD-021 IN_PROGRESS, STD-006 OPEN; các quyết định/gates còn lại trong design vẫn giữ. Kiến trúc năm Supabase projects độc lập không đổi. [Remaining risks](evidence/batch-5a-uuid/remaining-risks.md).

## J. Final Status

**PARTIAL** theo completion criterion10: affected backend PASS nhưng frontend typecheck FAIL baseline protected Place.38 source conversions và test evidence đã hoàn tất trong phạm vi mô tả; không tuyên bố migration/deployment DONE. PLAN/AUDIT/design/model đã cập nhật checkpoint, giữ nguyên provenance lịch sử. Batch5B/6 chưa bắt đầu.
