# Batch 1.1 — Manual Refund Workflow

Ngày 12/11/2026 — Asia/Saigon. Trạng thái: **PARTIAL**.

## A. Initial State
Backend master, HEAD 27f1d9817c7e50182c1102c5f8b3c5294d4851fd. Frontend main, HEAD d446b9808f8732c9982da2c156979e245742aa7f. Giữ nguyên hai thay đổi IDE và toàn bộ source/test/SQL/report chưa commit từ Batch 1. Plan, audit, policy và contracts đã được khảo sát ở Batch 1 trong cùng chat; đối chiếu lại refund source hiện tại và báo cáo trước khi sửa. RefundRequest vẫn REQUESTED/APPROVED_PENDING_EXECUTION, execution 503. Không có trusted settlement verification source hoặc policy cho phép một ADMIN tự xác minh tiền đã hoàn.

## B. Architecture
Mở rộng bằng execution table riêng, không thay state request hiện hữu. Execution: PENDING_MANUAL_REFUND -> PENDING_VERIFICATION; NEEDS_MANUAL_REVIEW được schema dành cho review nhưng chưa có endpoint chuyển trạng thái. Chỉ ADMIN được start/submit/list/verify. Start yêu cầu cancellation, completed positive payment, đủ paidAmount và approval audit từ validator Batch 1. Approval không tự settlement.

Transaction start/submit giữ lock booking -> payment -> execution, không network I/O. UNIQUE payment_id và request_id bảo vệ một execution; start lặp trả record hiện hữu. Submit cùng reference/evidence lặp trả record hiện hữu; nội dung khác khi đã nộp trả conflict. Audit CREATED/EVIDENCE_SUBMITTED cùng transaction mutation, lấy actor từ Authentication. Audit chưa bao phủ request/approval/rejection lịch sử.

Recovery scheduler chỉ tạo internal record bị thiếu cho approved request, tối đa 20 mỗi scan. Nó gọi transactional start qua bean, không thực hiện giao dịch bên ngoài, không verify, không sửa paidAmount. Review queue giới hạn 100 record pending/review, tồn tại qua restart nhờ PostgreSQL. Chưa có paging đầy đủ, SLA aging/metrics hay acceptance restart service thực tế.

RefundProvider là interface domain độc lập SDK với execute/query và SUCCESS/FAILURE/UNKNOWN. Không production implementation, không bean mock, không automated invocation hoặc chuyển từ MANUAL sang automatic. Đây là contract chuẩn bị; chưa có orchestration timeout/retry/query hay adapter result verification.

## C. Changes
File mới:
- booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundService.java
- booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundController.java
- booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundRecovery.java
- booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundProvider.java
- booking-service/src/test/java/com/example/bookingservice/payment/refund/ManualRefundServiceTest.java
- docker/sql/manual-refund-v1-20261112.sql
- docker/manual-refund-migration-test.py
- Báo cáo này.

API ADMIN: GET /api/payments/manual-refunds; POST /api/payments/manual-refunds/{paymentId}/start, /evidence, /verify. Evidence body gồm externalReference và evidenceReference. Chỉ nhận identifier ký tự giới hạn/độ dài 200, không nhận file/public URL. Không có evidence upload/storage mới. Identifier là metadata tham chiếu riêng tư, chưa xác minh nó tồn tại hoặc thuộc tài liệu settlement nào. Operator phải tránh nhập dữ liệu nhạy cảm; service không log evidence.

Migration additive tạo execution/audit tables; FK và unique identity, amount positive, currency VND, method MANUAL, trạng thái giới hạn, evidence metadata bắt buộc khi pending verification. Không rewrite/backfill receipt hoặc trạng thái refund lịch sử. Booking không có migration runner: áp dụng SQL trước deploy ứng dụng/scheduler, sau migration Batch 1; không dựa ddl-auto. Rollback source giữ bảng/audit, không DROP dữ liệu. Chưa áp dụng production/clone hiện có.

## D. Verification
Commands từ checkinn:
```
mvn -o -B -ntp -pl booking-service,revenue-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
python docker/manual-refund-migration-test.py
git diff --check
git diff --exit-code -- place-service
```
Log regression: C:/Users/ACER/IdeaProjects/DA1/checkinn/.local/evidence/batch-1-refund/BATCH1_1_final-tests.log. Không dùng XML cũ để chứng nhận lần chạy mới.
PostgreSQL postgres:18-alpine dùng container riêng không host port: PASS migration replay; 4 concurrent INSERT với ON CONFLICT tạo đúng 1 execution; amount âm, REFUNDED và pending thiếu evidence bị constraint chặn; rollback giữ pending; evidence đầy đủ lưu PENDING_VERIFICATION. Xóa container test sau chạy. Đây là SQL/constraint tests trên schema tối giản, không phải concurrency service/JPA/gateway acceptance.
Ba test mới: verification fail closed không chạm database/payment, invalid evidence bị chặn trước state access, thiếu approval không tạo execution. Full regression gồm tests Batch 1, booking cancellation/late callback và revenue hiện hữu. Chưa kiểm service restart/crash thật, concurrent ADMIN HTTP actions, authorization endpoints mới bằng MockMvc, audit rollback failure injection, provider delayed/duplicate/timeout và query orchestration. Các test DB opt-in hiện hữu mặc định bị skip; không claim chúng đã chạy.

## E. Financial Safety
Không đường nào mới ghi REFUNDED, trừ paidAmount hoặc phát cash debit. /verify luôn 503 vì thiếu trusted verifier/policy; không coi ADMIN input là settlement. Original payment receipt và CANCELLED booking giữ nguyên. Do chưa cho settlement, không claim duplicate verified debit đã được kiểm chứng. Repeated start/submit không tạo external refund, operator cần tự kiểm giao dịch bên ngoài; không có exactly-once external execution guarantee.

## F. Protected Scope
Không sửa Place backend/frontend hoặc lỗi showDistance. Frontend sạch. Hai file IDE có tham chiếu Place là baseline được giữ nguyên. Diff chỉ bổ sung manual refund và giữ toàn bộ Batch 1. Không stage/commit/push/merge/PR, không gọi VNPay hoặc dùng credential thật.

## G. Remaining Work
Cần hoàn thiện reject/failed/review transitions và audit toàn request lifecycle; accepted evidence source, separation-of-duties policy, verifier/settlement atomic transaction và authoritative refund identity/time; provider adapter/quyền merchant; automated reconciliation và cash/outbox integration. Chưa có test mock provider scenarios vì interface chưa được nối vào production orchestration. Cần recovery failure metrics/logging, đầy đủ API/security/concurrency/restart tests trên PostgreSQL và gateway, clone upgrade trước production. Không tự đặt policy xác minh để làm PASS.

## H. Final Status
**PARTIAL**, không DONE cho toàn Manual Refund Infrastructure và không FIXED_VERIFIED STD-002/007. Đã có durable manual execution/evidence pending, limited internal recovery và SQL constraints được kiểm thử. Settlement verification bị chặn bởi thiếu nguồn/policy đáng tin cậy; các phần reject/audit/test/recovery chi tiết còn là implementation work, không chỉ environment blocker. Batch tiếp theo hoàn thiện các phần độc lập này và xác nhận nguồn evidence trước khi cho phép financial mutation.

Kết quả regression cuối: BUILD SUCCESS, booking 259 reported (258 PASS, 1 skipped), revenue 39 reported (38 PASS, 1 skipped): tổng 296 thực thi PASS, 2 skipped, 0 failure/error. git diff --check PASS.
