# Batch 1.2 — Sandbox Internal Refund

> Historical Batch 1.2 snapshot. **Configuration superseded by [Batch 1.3](BATCH1_3_REFUND_INTEGRATION_FINALIZATION_2026-11-12.md):** current `refund.internal.enabled` / `REFUND_INTERNAL_ENABLED` defaults false on Booking/Revenue; old sandbox profile/deployment/pay-URL gates below are not current setup instructions. Final Backend/API demo acceptance is [Batch 1.6](BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md); this report's original test results/limitations remain unchanged.

Ngày 12/11/2026 Asia/Saigon. **PARTIAL — chưa đạt tất cả acceptance criteria.**

## Git và baseline
Backend master HEAD 27f1d9817c7e50182c1102c5f8b3c5294d4851fd; frontend main HEAD d446b9808f8732c9982da2c156979e245742aa7f. Giữ các thay đổi chưa commit Batch 1/1.1 và hai file IDE có sẵn. Không stage/commit/push/merge/PR. Plan/audit và contracts được khảo sát trong cùng chat; đọc lại refund/payment/event/revenue source để xác định implementation thực tế. Không sửa tài liệu policy cũ theo giả định đã nghiệm thu production.

## Semantics và workflow
Theo source, booking.paidAmount là số tiền đã nhận đang được dùng cho eligibility/payment/reconciliation; Batch 1 policy dự kiến trừ sau refund. Với internal refund mới trừ đúng full amount một lần; Payment.amount, paidAt, vnpayCreateDate, original VNPay transaction/order/receipt giữ nguyên. Booking CANCELLED giữ nguyên. Request và Payment chuyển REFUNDED cùng local transaction. Đây là hoàn tiền **nội bộ demo**, không phải provider settlement.

REQUESTED -> REJECTED hoặc APPROVED_PENDING_EXECUTION -> REFUNDED. Một ADMIN có thể approve và execute. Rejection lưu actor/time qua các trường audit đang có (approvedBy/approvedAt trên record REJECTED); đây là naming debt cần chuẩn hóa sau, không phải approval hợp lệ vì status khác. Không có second-admin/evidence requirement trong đường execute mới.

Dùng API POST /api/payments/{id}/refund và RPC refund hiện hữu. Service kiểm ADMIN từ SecurityContext hoặc verified gRPC context khi internal mode enabled, không nhận role/actor từ request. Service giữ lock booking -> payment -> execution; validator Batch 1 vẫn kiểm binding/approval/amount/completed/cancelled. Replay REFUNDED kiểm mode/binding/amount/cancelled/payment status, không trừ tiền hoặc enqueue lại.

Tái sử dụng manual_refund_executions/audit. Pending manual chưa submit có thể được chuyển sang SANDBOX_INTERNAL/REFUNDED trong transaction execute. Record PENDING_VERIFICATION/NEEDS_MANUAL_REVIEW bị chặn 409 để review; không tự backfill hoặc coi evidence là tiền đã hoàn. Endpoint evidence/verify cũ giữ để không phá compatibility, verify vẫn fail closed; không cần gọi chúng để dùng demo refund. Recovery cũ chỉ chuẩn bị internal records, không settlement.

## Cấu hình và financial event
Mặc định internal mode disabled. Booking phải đồng thời có spring.profiles.active=sandbox (đúng một profile), refund.deployment-environment=SANDBOX, refund.sandbox-internal.enabled=true và VNPay pay URL chính xác https://sandbox.vnpayment.vn/paymentv2/vpcpay.html. Production/không rõ profile/URL/env bị chặn, không fallback provider -> internal. Không triển khai/gọi Refund API.

Consumer revenue cũng phải bật flag, environment SANDBOX và profile sandbox. Không cấu hình mặc định bật trong source/config chung. Operator chỉ bật trên stack/database demo riêng; không áp dụng vào dữ liệu production.

Event payment.refunded thêm executionMode=SANDBOX_INTERNAL, currency=VND, transactionId=sandbox-refund:<refund-request-id> ổn định và transactionAt là giờ hoàn tất nội bộ. Không giả provider refund reference/time, không dùng original transaction ID. Outbox enqueue cùng transaction nguồn; request/payment/booking/audit rollback cùng nhau nếu enqueue thất bại. Cash writer giữ original CREDIT, ghi DEBIT theo contract hiện tại, unique payment/kind và transaction identity để replay dedup. Column execution_mode phân loại demo debit; unknown mode/currency/non-refund/không đúng identity prefix hoặc consumer mode disabled đều bị review/quarantine thay vì ghi debit.

Classification tồn tại trong cash table và event receipt payload; Payment REST response có refundExecutionMode và refund request có executionMode/refundedAt. **Chưa bổ sung nhãn mode vào mọi aggregate report DTO/UI hoặc gRPC response.** Không coi báo cáo tổng hợp hiện hữu đã phân biệt demo/provider đầy đủ. Demo cần database riêng.

## Files và migration
Thay đổi thêm trên Batch 1/1.1:
- PaymentService: execute transaction, ADMIN/environment gates, replay và callback sau refund không ghi receipt/credit lại.
- RefundRequest và RefundRequestService/Controller: REJECTED/REFUNDED, reject endpoint, mode/completion time.
- PaymentResponse: refundExecutionMode.
- Booking PaymentEvent: mode/currency, toBuilder để tạo refund facts riêng.
- Revenue PaymentEvent: mode/currency nullable, giữ constructor cũ cho compatibility.
- CashTransactionWriter: consumer gate, mode validation/persistence và replay comparison.
- SandboxRefundTest: execute/replay, receipt/amount/cancellation/outbox và production gate.
- docker/sql/sandbox-internal-refund-v1-20261112.sql (booking).
- docker/sql/revenue-sandbox-refund-v1-20261112.sql (revenue).
- docker/sandbox-refund-migration-test.py và báo cáo này.

Apply migrations Batch 1 rồi 1.1, sau đó booking/revenue Batch 1.2 trước deploy producer/consumer. Booking SQL mở rộng status/checks và cột request, giữ tables/records/history; không backfill REFUNDED. Revenue thêm execution_mode nullable. Không migration runner mới, không dựa ddl-auto. Rollback application giữ các bảng/cột/audit; tắt internal execution trước rollback, không xóa receipts. SQL booking kiểm replay trên PostgreSQL tạm; revenue migration chưa có database acceptance riêng.

## Verification
Commands:
```
python docker/sandbox-refund-migration-test.py
mvn -o -B -ntp -pl booking-service,revenue-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
git diff --exit-code -- place-service
git -C ../checkinn-frontend status --short
```
Log cuối: C:/Users/ACER/IdeaProjects/DA1/BATCH1_2_final-tests.log.

PostgreSQL migration harness PASS: replay; bốn concurrent INSERT chỉ một execution; amount âm, REFUNDED ở method MANUAL và PENDING_VERIFICATION thiếu evidence bị chặn; rollback DML; chuyển execution SANDBOX_INTERNAL/REFUNDED hợp lệ. Các lần chạy harness đầu phát hiện conflict từ unique request_id và thiếu evidence check sau mở rộng constraint; sửa harness ON CONFLICT và phục hồi evidence constraint, lần chạy cuối PASS. Container test riêng không host port, được xóa; không chạm database hiện có.

Unit execute/retry dùng repositories/JDBC mocks, kiểm một event, paidAmount chỉ giảm một lần, receipt và CANCELLED giữ nguyên; production environment bị chặn. Full regression giữ payment, late callback, cancellation, paid reservation và revenue tests hiện hữu. Không dùng mocks để chứng nhận locks/commit/crash/restart thực tế.

**Chưa kiểm chứng:** Java service transaction/concurrency với PostgreSQL; fault ngay trước/sau commit và restart/HTTP retry; debit demo qua outbox -> actual consumer -> PostgreSQL; duplicate demo event/consumer delivery end-to-end; mọi cấu hình thiếu/disabled/profile combination bằng automated test; gateway authorization và gRPC metadata readback; report UI classification; migration revenue trên schema clone; VNPay Sandbox payment thật. Hai opt-in integration suites hiện hữu bị skip khi không bật môi trường.

## Protected scope và trạng thái
Không thay đổi Place backend/frontend hoặc lỗi showDistance. git diff --check và place-service diff check PASS, frontend sạch. Hai file IDE chứa Place là baseline giữ nguyên. Không frontend build, không provider call, không production data mutation.

**PARTIAL**: có implementation internal execute và unit evidence, migration booking PASS; chưa đủ service-level database/concurrency, ledger/report/environment acceptance để tuyên bố DONE. STD-002/007 provider-backed production refund vẫn IN_PROGRESS. Batch tiếp theo cần kiểm Java service/consumer trên clone PostgreSQL, bổ sung missing failure/security/config cases và expose classification xuyên báo cáo/gRPC trước nghiệm thu Sandbox Internal Refund. Không cần merchant Refund API để hoàn thành các phần demo còn lại.

Full regression BUILD SUCCESS: booking 260 reported/259 PASS/1 skip; revenue 39 reported/38 PASS/1 skip. Tổng 297 thực thi PASS, 2 skipped, 0 failure/error. Sau review, execution_mode được ghi ngay trong INSERT cash transaction; replay không UPDATE/reclassify lịch sử. Rerun revenue riêng ghi tại BATCH1_2_revenue-final.log.
