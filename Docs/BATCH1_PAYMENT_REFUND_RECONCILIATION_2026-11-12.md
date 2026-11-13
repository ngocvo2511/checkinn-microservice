# Batch 1 — Payment Refund & Reconciliation

Ngày: 12/11/2026, Asia/Saigon. **Kết quả: PARTIAL. STD-002 và phần provider/refund của STD-007 vẫn IN_PROGRESS.**

## A. Initial State

- Backend: `master`, HEAD `27f1d9817c7e50182c1102c5f8b3c5294d4851fd`.
- Có sẵn hai file IDE chưa stage: `.idea/compiler.xml` (+2 dòng) và `.idea/encodings.xml` (+1 dòng), đều có tham chiếu Place. Batch không chỉnh các file này.
- Frontend: `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`, working tree sạch.
- Đọc plan, đặc biệt policy mục 18, và audit ngày 12/11; đối chiếu source hiện tại thay vì lấy status lịch sử làm trạng thái hôm nay. Không tìm thấy AGENTS.md trong workspace.
- Đã kiểm tra PaymentService, PaymentController, RefundRequestService/controller/entity/repository, PaymentGrpcServer, Payment/repository, BookingService cancellation, PaidReservationService/Recovery, HotelEventPublisher/outbox và CashTransactionWriter/EventIngestionService, các test và SQL liên quan.

### Các gap xác nhận từ source

1. Refund execution đang cố ý trả 503, không gọi provider, không thay paidAmount/status. Đây là guard an toàn nhưng chưa phải khả năng hoàn tiền.
2. REST/RPC đã yêu cầu ADMIN; tuy nhiên PaymentService.refundPayment chưa kiểm tra request đã duyệt/cancellation/approval audit. Guard 503 hiện chặn mọi execution nên chưa có bằng chứng exploit tài chính; cần giữ điều kiện này khi chuẩn bị adapter.
3. Provider hiện tại là VNPay, cấu hình ứng dụng có các property pay URL, return URL, version và tên property merchant/signing; chưa có cấu hình/adapter refund/query hoặc bằng chứng quyền được cấp cho merchant. Không đọc/in giá trị credential, không dùng merchant hiện có để thử API.
4. Payment chưa lưu chính xác vnp_CreateDate của request pay. createdAt dùng clock local lúc persist; paidAt là lúc xử lý receipt; transactionOccurredAt lấy vnp_PayDate. Các thời điểm này không thể thay thế ngày request gốc.
5. Chưa có durable refund command, provider outcome UNKNOWN, query reconciliation hoặc worker khôi phục refund sau crash. Không coi RefundRequest APPROVED_PENDING_EXECUTION là provider đã nhận hay đã hoàn tiền.
6. Callback thành công muộn đã giữ booking CANCELLED, ghi receipt một lần và chuyển inventory recovery theo intent hiện có; không tự cấp phòng hay hoàn tiền. Refund của trường hợp này vẫn cần request và ADMIN approval.
7. Outbox enqueue cùng transaction nguồn đã tồn tại; cash writer có identity/idempotency CREDIT/DEBIT. Chưa có refund producer mang provider refund identity/time đã xác minh; không dùng timestamp nhận callback hay transaction thanh toán gốc để tạo debit giả.

### Contract và giới hạn provider

[Tài liệu VNPay querydr/refund chính thức](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html) mô tả vnp_TransactionDate lấy vnp_CreateDate của giao dịch pay, định dạng GMT+7. Batch tra cứu tài liệu công khai; lần mở trực tiếp trang bị timeout. Chỉ áp dụng tiền đề ngày request có bằng chứng từ kết quả tài liệu chính thức, không triển khai signing/response semantics từ suy đoán.

Không có tài liệu merchant-specific về quyền refund/query, giao dịch test được chỉ định hoặc bằng chứng settlement trong đầu vào batch. Cấu hình sandbox không chứng minh quyền refund và không phải phép cho gọi giao dịch. Dừng phần phụ thuộc provider; vẫn triển khai các guard và dữ liệu nguồn có đủ căn cứ.

## B. Changes

| File | Thay đổi |
|---|---|
| booking-service/src/main/java/com/example/bookingservice/payment/entity/Payment.java | Thêm vnpayCreateDate nullable, varchar(14), JPA updatable=false. |
| booking-service/src/main/java/com/example/bookingservice/payment/service/PaymentService.java | Ghi ngày request trước INSERT đầu tiên; dùng cùng giá trị để ký pay, GMT+7 rõ ràng; refund gọi validator chung trước guard 503. |
| booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequestService.java | Kiểm binding payment/booking/amount khi approve; chặn thiếu approval actor/trạng thái không hợp lệ; thêm validateExecution yêu cầu transaction đang mở, khóa booking rồi payment, completed/positive/đủ paidAmount, CANCELLED và approval audit đầy đủ. |
| booking-service/src/test/java/com/example/bookingservice/payment/service/PaymentServiceTest.java | Kiểm ngày có từ INSERT đầu tiên và trùng URL; không phụ thuộc timezone máy chủ; kiểm service chuyển tiếp approval denial và vẫn trả 503 khi provider chưa xác minh. |
| booking-service/src/test/java/com/example/bookingservice/payment/refund/RefundRequestServiceTest.java | Kiểm identity/approval/amount/financial preconditions, lặp validation và giữ audit approval gốc. |
| docker/sql/booking-vnpay-create-date-v1-20261112.sql | Additive ALTER TABLE payments ADD COLUMN IF NOT EXISTS, không backfill lịch sử. |
| docker/refund-prerequisites-migration-test.py | Harness PostgreSQL tạm riêng, không host port, tự xóa container sau test. |
| Docs/BATCH1_PAYMENT_REFUND_RECONCILIATION_2026-11-12.md | Báo cáo này. |

Giữ nguyên chính sách: customer/OWNER request, ADMIN approve/execute; không partial refund, không thay deadline/phí cancellation. Không thay paidAmount, payment receipt, cash ledger hoặc phát refund-success event trong batch.

Migration phải được áp dụng vào database booking **trước deploy source mới**. POM booking hiện không có Flyway runner; file SQL này là migration triển khai thủ công theo cấu trúc docker/sql hiện có, không tự chạy chỉ vì nằm trong repo. Không dựa vào ddl-auto=update. Kiểm thử disposable database chưa thay thế clone upgrade/schema validation và rollout thật. JPA updatable=false ngăn ORM UPDATE cột, không phải trigger bất biến cho mọi SQL client. Giữ cột khi rollback ứng dụng, không xóa receipt/ngày request đã ghi.

## C. Verification

Chạy từ repository checkinn:

```powershell
$env:CHECKINN_RANGE_TEST='false'
$env:CHECKINN_SESSION_TEST='false'
mvn -o -B -ntp -pl booking-service,revenue-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
python docker/refund-prerequisites-migration-test.py
mvn -o -B -ntp -pl booking-service '-Dtest=PaymentServiceTest#initVnPayPaymentCreatesPaymentThroughPolicyFactory' '-Duser.timezone=UTC' test
git diff --check
```

| Check | Kết quả |
|---|---|
| Booking regression | BUILD SUCCESS: 256 reported, 255 thực thi PASS, 1 skip; 0 failure/error. |
| Revenue regression | BUILD SUCCESS: 39 reported, 38 thực thi PASS, 1 skip; 0 failure/error. |
| Tổng regression | 293 thực thi PASS, 2 skip. ApplicationTests bị loại rõ ràng. |
| PostgreSQL migration | PASS trên postgres:18-alpine tạm riêng: legacy nguyên vẹn/NULL, replay, round-trip ngày mới, varchar(14), rollback DML, upgrade bảng rỗng. Container được xóa. |
| Test timezone UTC | BUILD SUCCESS: 1 test PASS, 0 failure/error/skip; ngày pay vẫn GMT+7 khi JVM chạy UTC. |
| Backend/frontend diff whitespace | PASS. |
| Frontend typecheck/build | Không chạy, không phải gate batch backend. |
| Provider/gateway/production DB | Không chạy. |

Log bên ngoài repository: `C:/Users/ACER/IdeaProjects/DA1/checkinn/.local/evidence/batch-1-refund/BATCH1_2026-11-12_backend-tests.log` và `BATCH1_2026-11-12_utc-test.log`. Kết quả reactor là nguồn tổng hợp cho lần chạy full regression; không cộng report ApplicationTests cũ hoặc nhầm report test chọn lọc với full suite. Test timezone riêng có thể ghi đè XML cùng class.

### Ma trận yêu cầu

| Kịch bản | Bằng chứng/giới hạn |
|---|---|
| Duplicate refund request | Test service hiện có trả lại request, không save thêm; unique payment constraint đã có trong schema. Không claim concurrency DB từ Mockito. |
| Approval/authorization | Regression REST/gRPC hiện có và test service mới kiểm request/identity/amount/approval; không có gateway thật trong batch. |
| Duplicate validation/approval | Không tạo financial effects, giữ actor/time approval gốc trong test. |
| Provider chưa xác minh | Execution vẫn 503, paidAmount/payment không đổi, không publish refund event. |
| Late success sau cancellation | Regression PaymentServiceTest kiểm callback/replay ghi tiền một lần, CANCELLED giữ nguyên, không inventory confirmation/award points. |
| Onsite/paid reservation/cancellation recovery | Các suite hiện có chạy trong booking regression, không sửa workflow. |
| Cash identity/outbox | Các test không cần DB trong regression PASS. BookingEventOutboxIntegrationTest và RevenueEventOrderingIntegrationTest bị skip do opt-in false; không tuyên bố kiểm chứng cash concurrency thật. |
| Refund SUCCESS/provider reject/duplicate response | Chưa triển khai adapter; chưa kiểm chứng. Không tạo fake success bằng mock để thay bằng chứng còn thiếu. |
| Timeout/UNKNOWN/query mismatch | Chưa có durable outcome/query, chưa kiểm chứng. |
| Crash trước/sau execution, remote success/local rollback, restart | Chưa có refund worker/command nên chưa kiểm chứng. |
| Concurrent refund execution | Chưa kiểm chứng bằng DB/provider; validator dùng khóa hiện có, không có remote execution trong batch. |

PostgreSQL test chỉ kiểm migration trên bảng payments tối giản trong DB dùng một lần; không xác minh toàn schema production, ORM round-trip, service row locks, clean provisioning hay dữ liệu clone đại diện. Mock service/security tests không chứng minh provider settlement hoặc exactly-once financial effect sau restart.

## D. Protected Scope Verification

- Không chỉnh file trong place-service hoặc frontend. Frontend giữ working tree sạch.
- Hai file .idea có tham chiếu Place đã modified trước batch; không phải thay đổi batch này. Không format/refactor/sửa config Place để chạy test.
- Git diff mới giới hạn payment/refund, test, migration/harness và báo cáo. Không stage/commit/push/merge/PR; giữ branch/HEAD.
- Audit ghi baseline PlaceCardProps/showDistance ngăn frontend typecheck/build. Batch không chạy lại frontend nên chỉ ghi **external blocker theo audit**, không tuyên bố tái hiện mới. Nó không ảnh hưởng backend regression lần này; không có regression Place được quan sát vì không kiểm frontend.

## E. Remaining Work

1. Cung cấp xác nhận merchant được quyền query/refund, phiên bản contract áp dụng, endpoint môi trường test, giao dịch test được chỉ định và bằng chứng/provider procedure phân biệt accepted request với hoàn tất settlement. Không gửi credential vào báo cáo/chat.
2. Hoàn thiện adapter request signing, giới hạn network, verify response signature/merchant/amount/identity và phân loại SUCCESS/FAILURE/UNKNOWN theo contract đã xác nhận.
3. Thêm durable immutable command gắn approval/payment/amount và original identity; serialization, state machine, query trước resend khi kết quả không rõ, scheduler restart-safe và ADMIN manual review. Không tự suy ra timeout=FAILURE.
4. Chỉ sau verified refund mới ghi outcome, trừ paidAmount và outbox debit cùng transaction local; giữ original payment receipt, authoritative refund reference/time, consumer dedup. Không dùng builder payment-completed hiện có cho refund khi chưa bổ sung identity/time đúng.
5. Dữ liệu cũ thiếu vnp_CreateDate/receipt/merchant identity cần reconciliation từ nguồn xác minh; không backfill từ createdAt/paidAt. Merchant config rotation và identity snapshot chưa được giải quyết bởi một cột ngày.
6. Bổ sung deterministic adapter tests đầy đủ; real PostgreSQL service concurrency/deferred commit faults/restart/cash-outbox tests; gateway actor matrix và giao dịch provider test có phép. Kiểm clone migration đại diện trước rollout.

Không yêu cầu thay đổi Place cho các bước này. Đối với phần merchant/provider chưa đủ bằng chứng, giữ execution disabled là lựa chọn an toàn trong phạm vi đã được user chốt.

## F. Final Status

**PARTIAL**: đã triển khai và kiểm tra guard execution/approval, chuẩn bị ngày request VNPay gốc và additive SQL trên PostgreSQL tạm; regression backend PASS. **Không đánh dấu toàn STD-002/007 FIXED_VERIFIED.**

Blocker cho provider execution/reconciliation: chưa có bằng chứng quyền merchant/test transaction và contract vận hành tương ứng. Đồng thời adapter/command/UNKNOWN/recovery vẫn là phần implementation chưa hoàn thành, không gọi toàn bộ gap này là thiếu credentials.

Batch tiếp theo: hoàn thiện provider-backed refund/query và durable reconciliation sau khi xác nhận các đầu vào merchant; dùng các kịch bản còn mở ở mục C làm acceptance. Không ưu tiên sửa Place hoặc UI ngoài phạm vi để thay thế nghiệm thu tiền.
