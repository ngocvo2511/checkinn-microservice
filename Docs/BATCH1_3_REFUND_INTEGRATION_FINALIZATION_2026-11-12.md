# Batch 1.3 — Refund Integration Testing & Finalization

Ngày 12/11/2026, Asia/Saigon. Trạng thái chốt ở mục H sau khi chạy kiểm chứng.

## A. Initial State
Backend master HEAD 27f1d9817c7e50182c1102c5f8b3c5294d4851fd; frontend main HEAD d446b9808f8732c9982da2c156979e245742aa7f. Working tree chứa toàn bộ Batch 1–1.2 chưa commit và hai file IDE có sẵn. Giữ nguyên các thay đổi đó, không stage/commit/push/merge/PR. Đọc lại báo cáo Batch 1.2 và source PaymentService, refund/manual domain, outbox/delivery, consumer, cash/reporting cùng các SQL/test. Plan/policy/audit đã được khảo sát trong cùng chat; không thay cancellation policy.

Source xác nhận Batch 1.2 đã có internal refund transaction nhưng chưa có Java/PostgreSQL acceptance. Guard yêu cầu profile sandbox và ba điều kiện khác trong khi application demo hiện dùng default/local, VNPay pay URL mặc định sandbox. Các report hiện có đọc RevenueCashFlows từ immutable cash movements, không lấy duy nhất latest REFUNDED projection để tính âm tiền. Vì vậy không cần thiết kế lại dashboard.

## B. Test Infrastructure
- PostgreSQL image postgres:18-alpine; hai database mới refund_booking và refund_revenue.
- RabbitMQ image rabbitmq:3-management; exchange hotel.events, queue refund.integration, binding payment.*. Giới hạn scheduler Erlang trong container test.
- Mỗi lần harness tạo container tên ngẫu nhiên, publish cổng ngẫu nhiên trên 127.0.0.1; không dùng credential/database có sẵn. Test-only broker user/password cố định không phải credential VNPay.
- JUnit 5, Hibernate/JPA thật, JpaRepositoryFactory, SharedEntityManager và JpaTransactionManager; transaction advice giống annotation production. Không Mockito trong hai integration tests.
- Fresh test schema từ entities trong DB disposable, sau đó áp dụng SQL production trong docker/sql. Đây là clean-schema integration, không chứng nhận clone dữ liệu người dùng hoặc upgrade mọi legacy schema.
- Booking chạy hai lần Maven/JVM riêng trên cùng DB: initial và retry. Retry mở EntityManagerFactory mới với ddl none, giữ database đã commit. Harness xóa containers ở finally.
- Outbox publisher thật với RabbitTemplate và broker confirms. Consumer EventListener và EventIngestionService thật, gọi từ harness nhận AMQP với manual ACK; không mock consumer. Không khởi động toàn Spring Boot application/gateway hoặc @RabbitListener container.

Chạy lại từ checkinn:
```
python docker/refund-integration.py
mvn -o -B -ntp -pl booking-service,revenue-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
git diff --exit-code -- place-service
```
Integration opt-in CHECKINN_REFUND_TEST=true và loopback JDBC URL có tên DB test cố định được harness tự cấp. Không bật opt-in vào database khác. Các test có ddl create, chỉ dùng với harness disposable.

## C. Implementation & Fixes
Files trong batch này:
- booking-service/.../payment/service/PaymentService.java: đơn giản hóa cấu hình, kiểm isAuthenticated khi authorize execute và trả 404 khi payment không tồn tại.
- revenue-service/.../service/CashTransactionWriter.java: dùng cùng cờ cấu hình internal refund.
- booking-service/.../payment/service/SandboxRefundTest.java: cập nhật expectation disabled theo business decision mới, bỏ expectation production profile đã bị user bỏ yêu cầu.
- booking-service/.../payment/service/RefundPostgresTest.java: service transaction/rollback/concurrency/restart/recovery, publisher broker thật.
- revenue-service/.../service/RefundPipelineTest.java: migration, handler/ledger/report, duplicate/order/rollback/channel redelivery.
- booking-service/.../booking/controller/BookingAuthorizationTest.java: reject ADMIN/customer và forged header anonymous.
- docker/refund-integration.py: disposable DB/broker orchestration, fresh JVM retry và cleanup.
- Báo cáo này.

Không thêm migration mới: áp dụng và kiểm SQL Batch 1.1/1.2 và các migration outbox/revenue hiện hữu. Revenue migration execution_mode chạy hai lần, giữ nguyên CREDIT lịch sử test 5 VND và unique cash identities. Booking schema/generated checks được mở rộng bằng SQL Batch 1.2 trước execute.

Cấu hình demo mới: **refund.internal.enabled=true** trên cả booking-service và revenue-service (environment REFUND_INTERNAL_ENABLED=true). Mặc định false. Không còn yêu cầu profile sandbox/deployment-environment hoặc so khớp pay URL để refund nội bộ. Không đổi VNPay Payment configuration/behavior; không đọc secrets và không gọi Refund API. Client không thể chọn mode bằng query/body/header. Tắt cờ ở consumer thì event có execution mode bị quarantine theo contract hiện hữu. Cờ cũ refund.sandbox-internal.enabled không còn được dùng: cập nhật local launch configuration sang tên mới.

Hai thử nghiệm readiness Rabbit đầu thất bại trước khi chạy Java vì không tìm thấy cổng AMQP sau vòng diagnostics. Đổi harness sang polling TCP, dùng image RabbitMQ 3 với scheduler giới hạn; lần chạy có broker sau đó PASS. Đây là sửa harness, không phải bằng chứng production broker lỗi. Không suy đoán nguyên nhân hệ điều hành từ hiện tượng đó.

## D. Verification Matrix
| Scenario | Expected | Actual | Status | Evidence |
|---|---|---|---|---|
| Successful refund | Request/Payment REFUNDED, net paid 0, CANCELLED giữ nguyên | SQL trực tiếp xác nhận; original amount 10 và VNPay transaction 123456 giữ nguyên | PASS | RefundPostgresTest |
| Rollback trước commit | Không partial financial effect | Deferred PostgreSQL trigger ném lỗi tại commit; payment COMPLETED, request approved, paid 10, không execution | PASS | RefundPostgresTest |
| Concurrent execute | Một financial effect | Bốn execute đồng thời, thêm approve/reject race; final một completion audit và một refund outbox | PASS | RefundPostgresTest |
| Approve/reject race | Không downgrade/settle hai lần | Cạnh tranh trên approved source, conflict 409 được chấp nhận, state cuối REFUNDED | PASS | RefundPostgresTest |
| Retry sau commit | Không giảm thêm/không event mới | Gọi lại sau commit trả state cũ, kiểm SQL counts | PASS | RefundPostgresTest |
| Fresh JVM restart/retry | State bền vững | JVM Maven thứ hai mở DB hiện hữu, retry giữ một completion | PASS | booking-retry log |
| Recovery pending | Không tự refund | Scheduler/service thật tạo PENDING_MANUAL_REFUND cho approved request khác; qua JVM mới vẫn COMPLETED payment/pending execution | PASS | RefundPostgresTest |
| Outbox publisher | Broker confirm và DELIVERED | BookingEventDelivery thật publish hai event với Rabbit confirms; SQL DELIVERED=2 | PASS | booking-retry log |
| Revenue handler | Một DEBIT, giữ CREDIT | Persisted booking outbox JSON và AMQP messages đi qua EventListener/ingestion thật vào DB riêng | PASS | RefundPipelineTest |
| Reordered/duplicate delivery | Ledger không đổi | Refund trước credit, sau đó bốn thread lặp credit/debit; đúng hai cash rows | PASS | RefundPipelineTest |
| Consumer trước commit lỗi | Rollback debit/projection/receipt | Deferred receipt trigger làm commit lỗi; không cash row cho payment; retry sau bỏ trigger thành công | PASS | RefundPipelineTest |
| Commit trước ACK, channel mất | Broker redelivery không double debit | SQL commit handler xong, đóng channel trước ACK; channel mới nhận redeliver=true, xử lý/ACK lại | PASS | RefundPipelineTest |
| Revenue migration | Replay, giữ lịch sử/index uniqueness | SQL replay trên schema DB test, historical CREDIT 5 giữ nguyên; event duplicate còn 1 debit | PASS | RefundPipelineTest |
| Revenue aggregate | Net refund demo 0 | SQL signed sum theo payment=0; RevenueCashFlows read tổng scope=5 do giữ historical credit test 5 | PASS | RefundPipelineTest |
| API authorization | ADMIN được, customer/OWNER/anonymous bị chặn | Existing REST/gRPC suites và tests forged header/reject; service thật chặn OWNER | PASS | Regression + integration |
| Configuration | Một server flag, disabled fail closed | Default false và disabled case trong unit regression; integration inject cùng boolean enabled trên services thật | PASS trong service harness | Regression + integration |
| Regression | Các luồng booking/payment/revenue cũ giữ đúng | Kết quả cuối ghi tại H | Xem H | BATCH1_3_regression.log |

Giới hạn: race test bắt đầu từ APPROVED_PENDING_EXECUTION; không claim tất cả interleaving từ REQUESTED. Fresh JVM chứng minh process test restart, không phải rolling restart một cluster hay browser HTTP server. Outbox/handler/broker đều thật nhưng không nghiệm thu annotation-driven listener container, gateway deployment hoặc broker failover. Historical fixture đại diện nhỏ, không phải audit dữ liệu đang dùng.

## E. Financial Consistency
Booking paidAmount từ 10 xuống 0 đúng một lần; booking vẫn CANCELLED. Payment.amount/receipt gốc giữ nguyên, Payment và RefundRequest cùng REFUNDED. Internal completion identity sandbox-refund:<request-id> ổn định, khác original VNPay transaction ID; mode SANDBOX_INTERNAL và currency VND theo event/cash row. Timestamp là giờ Checkinn hoàn tất nội bộ, không tuyên bố VNPay settlement. Outbox cùng SQL transaction nguồn; failure lúc commit không để lại partial refund. Replay/restart không thêm outbox/audit. Revenue CREDIT/DEBIT là immutable movements, dedup payment/kind và event/transaction identities. Không UPDATE lịch sử để giả refund. Report hiện hữu đọc cash adapter nên không mất original credit khi projection chuyển REFUNDED. Demo data không cần redesign aggregate dashboard; không claim báo cáo này phản ánh tiền VNPay đã hoàn thật.

## F. Protected Scope
Không sửa backend Place hoặc bất kỳ frontend file nào; không sửa showDistance. git diff protected scope và frontend status kiểm trước/sau. Hai file .idea tham chiếu Place là baseline, không phải thay đổi batch. Chỉ chỉnh refund/config/tests/harness/report liên quan; không tự commit/push hoặc thay đổi database hiện có.

## G. Remaining Issues / Limits
Không tích hợp/gọi provider refund, không real settlement/manual verification/second ADMIN. Không có gateway/native HTTP deployment acceptance hoặc browser test trong batch; authorization được xác minh qua MockMvc/filter suite và service thật. Không kiểm live VNPay Sandbox payment; callback sử dụng dữ liệu test trong service regression. Không chứng nhận production rollout/multi-instance cluster/broker failover. Không rewrite historical manual evidence pending; verify cũ vẫn 503, recovery không financial mutation. Migration phải áp dụng trước deploy service theo thứ tự các batch; không dựa ddl-auto cho database thật.

## H. Final Status
**DONE cho phạm vi Sandbox Internal Refund đã kiểm chứng bằng Java service/PostgreSQL/RabbitMQ trong harness này.** Integration cuối PASS ba lượt: booking initial, booking retry trong JVM mới và revenue pipeline. Mỗi lượt là 1 JUnit scenario tổng hợp nhiều assertions, 0 skip/failure/error.

Full regression BUILD SUCCESS: booking 264 reported/262 PASS/2 skipped; revenue 40 reported/38 PASS/2 skipped. Tổng **300 thực thi PASS, 4 skipped, 0 failure/error**. Hai integration scenarios mới skip trong full regression vì được chạy riêng bằng disposable harness; hai DB opt-in cũ cũng skip. Không cộng skipped vào PASS. ApplicationTests bị loại rõ ràng.

Logs ngoài repo tại C:/Users/ACER/IdeaProjects/DA1: BATCH1_3_booking-initial.log, BATCH1_3_booking-retry.log, BATCH1_3_revenue.log, BATCH1_3_regression.log. Các lượt skip trong regression ghi đè Surefire XML cùng class; execution logs riêng giữ bằng chứng lượt integration thực thi. Containers cuối đã xóa; protected scope/frontend/whitespace checks PASS.

Có thể kết thúc nhóm correctness Sandbox Internal Refund trong phạm vi harness. Khi chạy demo toàn stack, áp dụng SQL các batch trước, bật REFUND_INTERNAL_ENABLED=true trên cả hai service và smoke HTTP/gateway tại môi trường demo chỉ định. Các giới hạn ở G vẫn giữ nguyên. Đề xuất chuyển sang batch standardization tiếp theo; provider-backed refund chỉ mở lại với mục tiêu/contract riêng. Không nâng toàn STD-002/007 provider-backed refund thành FIXED_VERIFIED từ nghiệm thu demo này.
