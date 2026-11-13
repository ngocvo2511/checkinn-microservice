# Batch 1.7 — Standardization Audit Reconciliation & Documentation Closure

Ngày 13/11/2026, Asia/Saigon. **Documentation reconciliation: DONE. Overall standardization: PARTIAL / IN_PROGRESS.**

## A. Initial State

- Backend `master`, HEAD `27f1d9817c7e50182c1102c5f8b3c5294d4851fd`; frontend `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`.
- Backend ban đầu có15 modified tracked paths (2 IDE, payment/refund/event/revenue source/tests và Compose), cùng các untracked Refund classes/tests/SQL/harness/reports Batch 1–1.6. Frontend clean, không staged changes. Không quy các thay đổi này cho Batch 1.7; đã lưu hash manifest tracked/untracked nonignored trước sửa để so sánh sau.
- PLAN/AUDIT nằm ở workspace root **ngoài cả hai Git repos**; phải được lưu/bàn giao cùng repo documentation. Không tạo bản sao tài liệu chính trong repo, không đổi tên audit ngày12/11.
- Đối chiếu toàn cấu trúc PLAN (inventory32 STD, S00–S24, gates, decisions và nhật ký14–73), toàn audit và các [Batch 1–1.6 reports](#e-related-documentation); kiểm source PaymentService/RefundRequestService/controller, PaymentController, Booking catalog/availability/cancellation, Gateway routing, Revenue cash ingestion/report và frontend SDK/placeholder.
- Mâu thuẫn ban đầu: audit/Booking README nói execution luôn503; audit đề xuất provider refund làm NEXT; PLAN chưa có closure Refund; Docker runbook thiếu cờ/mode hiện tại; Batch 1.2 chứa cấu hình profile/pay-URL đã bị thay thế. Git/test snapshot12/11 không còn là working tree hiện tại.

## B. Refund Status Reconciliation

**DONE — Refund Backend/API Demo Accepted** được giữ bằng evidence, không chỉ sao chép kết luận. Source hiện tại có transactional ADMIN internal execution, approved/cancelled/payment validators, replay trước completed-only validation, orphan-booking404, mode/currency/refund identity, outbox và immutable cash dedup. Gateway route payments/bookings đi Booking; reports đi Revenue.

Đã đọc [Gateway JSON](../../BATCH1_6_gateway_evidence.json), [post-fix log](../../BATCH1_6_gateway_postfix.log), [focused log](../../BATCH1_6_regression_final.log), ba integration logs và [disabled](../../BATCH1_6_disabled.log)/[lifecycle](../../BATCH1_6_lifecycle_final2.log), khớp [Batch 1.6](BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md):

- CUSTOMER request201 → cùng ADMIN approve200/execute200; REQUESTED→APPROVED_PENDING_EXECUTION→REFUNDED, reject→REJECTED. Payment/Request REFUNDED; Booking CANCELLED, paidAmount giảm một lần; receipt gốc giữ nguyên.
- Anonymous401, CUSTOMER/OWNER execute403, foreign CUSTOMER request403; invalid-state409, missing payment/booking404 sau fix.
- Barrier2 clients từ APPROVED đều200, overlap HTTP intervals. Test proxy mất response sau commit, client RemoteDisconnected rồi Gateway retry200. Một completed execution/completion audit/logical outbox/DEBIT, original CREDIT giữ nguyên.
- Outbox DELIVERED→RabbitMQ→Revenue; SQL theo payment net0; existing report HTTP200/amount0 theo hotel fixture/date riêng. Không lấy tổng database để so một payment.
- Disabled valid execution503; payment COMPLETED, paidAmount1000000.00, execution/outbox/DEBIT0. Application `refund.internal.enabled` / `REFUND_INTERNAL_ENABLED` mặc định false; cần Booking/Revenue cùng bật cho pipeline. Smoke-only `REFUND_SMOKE_INTERNAL_ENABLED` map vào application flag, overlay fallback true, không phải application default.
- Focused **96 executed PASS, 2 opt-in SKIPPED**, 0 failure/error, BUILD SUCCESS; 3 integration runs riêng mỗi lượt1 composite scenario PASS/no skip. Không cộng skipped vào pass; không tuyên bố full repo PASS. Integration trước orphan fix; focused/Gateway sau fix. Batch 1.7 không chạy lại test.

Không thực hiện real VNPay Refund API/money movement/provider reconciliation. Provider adapter NOT IMPLEMENTED; live Sandbox checkout, production settlement/migration/rollout, multi-instance failover NOT VERIFIED. Frontend Refund request/status/history/admin approve/reject/execute NOT IMPLEMENTED, browser Refund NOT VERIFIED. Current source chỉ có thông điệp payment/refund và admin placeholder, không phải UI actions/history.

## C. Audit Updates

[Audit chính](standardization/PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md) được chỉnh có chọn lọc:

- A/B: thêm Last reviewed, current dirty-tree note và phân biệt snapshot/test12/11 với source/evidence sau Batch 1.6; risk Refund chia internal demo/provider.
- C: cập nhật completed sub-scope STD-002/007/021/027/028/029; bổ sung ánh xạ taxonomy. Giữ nguyên32 IDs và parent totals **16 FIXED_VERIFIED, 12 IN_PROGRESS, 3 FIXED_UNVERIFIED, 1 NEEDS_ENVIRONMENT**; không đổi STD-002/007 sang FIXED_VERIFIED.
- D1/E/F: thêm closure/status/evidence matrix và giới hạn Booking/frontend/provider/production. Existing completed scopes/search/security/loyalty không bị mở lại hoặc đóng thêm bằng suy đoán.
- G0/G1: bảng ưu tiên toàn bộ16 parent findings còn mở, chọn một workstream Booking–Hotel/Availability Gateway lifecycle. G1b giữ nguyên yêu cầu provider-backed gốc nhưng dành riêng future real-money path. G2 giảm dependency giả vào việc phải implement provider trước khi làm schema manifest. G3 protected/deferred; G4 non-Place acceptance không phải đợi sửa Place.
- H/appendix: không nâng AI WRITE readiness; giữ nguyên kết quả861 backend/211 frontend ngày12/11 như lịch sử, không cộng với focused96 hoặc nhận là mới chạy.

## D. Plan Updates

[PLAN chính](standardization/PROJECT_STANDARDIZATION_PLAN.md) có cấu trúc checkpoint sẵn nên cần cập nhật: header/checkpoint13/11, chú thích current-vs-historical scope, scope notes ở STD-002/007/028, contextual note cho roadmap gốc và mục74 closure/dependencies/evidence.

Không rewrite inventory, không xóa acceptance provider/S02/S10/mục18 hoặc hạ yêu cầu production. Giữ policy cancellation/free24h/14:00, không thêm partial refund, second ADMIN hay evidence requirement vào demo. Không tick global Ready READ/WRITE từ một sub-scope. Không biến PLAN thành bản chép báo cáo1.6; trạng thái tổng hợp/priority ở audit, chi tiết test ở report tương ứng.

## E. Related Documentation

| Document | Action / rationale |
|---|---|
| [Booking README](../booking-service/README.md) | Sửa execution luôn503, mô tả outbox/recovery đã cũ và route callback sai thành GET /api/payments/vnpay/return theo controller; request/reject/approve/execute, mode/event/receipt/paidAmount, flag và demo/provider boundaries; link setup và acceptance |
| [Docker README](../docker/README.md#sandbox-internal-refund-demo-reviewed-2026-11-13) | Thêm cấu hình hai service, application default false vs smoke overlay true, migration/bootstrap/harness prerequisites; sửa câu execution unavailable thành provider adapter unavailable |
| [Batch 1.2](BATCH1_2_SANDBOX_REFUND_EXECUTION_2026-11-12.md) | Chỉ thêm notice cấu hình superseded, link1.3/1.6; không sửa lịch sử test/implementation |
| [Batch 1](BATCH1_PAYMENT_REFUND_RECONCILIATION_2026-11-12.md), [1.1](BATCH1_1_MANUAL_REFUND_WORKFLOW_2026-11-12.md), [1.3](BATCH1_3_REFUND_INTEGRATION_FINALIZATION_2026-11-12.md), [1.4](BATCH1_4_REFUND_FULLSTACK_SMOKE_2026-11-12.md), [1.5](BATCH1_5_REFUND_GATEWAY_ACCEPTANCE_2026-11-13.md), [1.6](BATCH1_6_REFUND_FINAL_ACCEPTANCE_2026-11-13.md) | Đọc/giữ nguyên dated evidence; PARTIAL ở report cũ không bị sửa thành retroactive PASS |
| [Event review](../docker/REVIEW_EVENTS_2026-11-07.md), [root README](../README.md), [frontend README](../../checkinn-frontend/README.md), UC05/UC15 business rules | Rà liên quan, không đổi: requirement/historical provider accounting không có claim internal demo đã settlement; cancellation policy không thay đổi |

## F. Remaining Workstreams

Bảng đầy đủ **Priority / Workstream / Current status / Evidence / Dependencies / Recommended next action** ở [audit G0](standardization/PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md#g0-remaining-workstream-priorities-13-november-review), tránh duy trì hai bảng dễ lệch. Bao phủ toàn16 open parent findings:

- STD-002/007: financial capability provider NOT IMPLEMENTED; Booking lifecycle integration/test gap; internal demo closed.
- STD-006/021/024: stock/cash/encoding provenance và clean+upgrade manifests; implementation từng phần, chưa có đại diện production evidence.
- STD-003/018: deployment actor/validation acceptance chưa consolidated; không suy missing guard từ permitAll.
- STD-011/012/023/032: source-confirmed nearby facts/error/provenance còn mở, protected; typecheck failure là evidence cũ, không rerun.
- STD-026: còn published client/RPC deadline/error contracts; Refund errors không chứng minh tất cả SDK.
- STD-027/028: full setup/lifecycle/browser/provider test gaps; Refund disposable environment chỉ đóng subset.
- STD-029/030: legacy cash/operator replay/DLQ/retention/trace/metrics; singleton internal debit không chứng minh universal exactly-once.

Documentation gap thuộc Refund đã sửa trong batch này. Workstream ngoài Refund không được coi đã kiểm runtime mới; nếu chưa có evidence thì giữ NOT VERIFIED/partial scope, không tự kết luận code vẫn lỗi. STD-025 baseline đã accepted; production performance/SLO là gap bổ sung, không mở lại baseline.

## G. Recommended Next Workstream

**Booking–Hotel/Availability Gateway lifecycle acceptance** (audit G1, PLAN S06/S08/S09/S10/S21/S23). Chọn vì có blocker môi trường cụ thể từ POST /api/bookings503/no hotel-service, ảnh hưởng giá/tồn/cancel/demo readiness và có thể kiểm độc lập Place/provider; không chọn chỉ vì số thứ tự.

Prerequisites: disposable ownership rõ; Hotel schema/bootstrap manifest tối thiểu, active approved hotel/room/capacity/price/stock; Auth/service identities, discovery/Gateway và Booking migrations. Scope dùng endpoints hiện có, create/cancel/retry, canonical quote/hold, policy snapshots và inventory/loyalty effects. Acceptance: Gateway create/cancel thành công, hold/counters và replay đúng, denial/tamper không mutation, record config/source/evidence; tách seeded payment khỏi live checkout. Nếu bootstrap vượt scope, ghi blocker cụ thể để làm G2.

Không triển khai trong Batch 1.7; không provider Refund/QueryDR, frontend Refund, Place/showDistance, kiến trúc mới, policy mới hoặc production/multi-instance certification. Yêu cầu real money vẫn giữ P0 nếu triển khai thật, deferred ngoài demo; không gọi nó DONE hoặc tự bắt đầu thêm Refund batch.

## H. Cross-Document Verification

Nguồn sự thật: current source cho behavior/config; execution logs/JSON theo từng batch cho claims PASS; dated reports giữ history; audit quyết định status tổng hợp; PLAN giữ requirements/dependencies. Current docs thống nhất internal demo DONE, full-chain E2E NOT VERIFIED, Booking lifecycle BLOCKED trong smoke thiếu Hotel, UI/provider NOT IMPLEMENTED, production NOT VERIFIED.

Đã kiểm relative links/anchors mới, Markdown tables/fences, issue counts/IDs và preservation diff. `git diff --check` hai repo PASS. Root PLAN/AUDIT kiểm whitespace/diff so với snapshot trước sửa vì nằm ngoài Git. Hash manifest đối chiếu toàn1169 file tracked/untracked nonignored ban đầu: chỉ5 Markdown hiện có thay đổi, cộng report Markdown mới; source/tests/SQL/Docker config/Place và frontend giữ nguyên. Root plan thay đổi nhỏ, không rewrite lịch sử. Không phát hiện secret/password/JWT trong added documentation.

Không chạy Docker/database/migrations/Maven/npm/browser trong batch documentation; kiểm retained logs không phải test mới. Branch/HEAD/staging giữ nguyên; không commit/push/merge/PR.

## I. Files Changed

| Exact workspace path | Attribution / purpose |
|---|---|
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/Docs/standardization/PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md` | Existing root doc; Batch1.7 current scope/status/evidence/priority reconciliation |
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/Docs/standardization/PROJECT_STANDARDIZATION_PLAN.md` | Existing root doc; scope/dependency/checkpoint74, requirements preserved |
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/booking-service/README.md` | Existing tracked doc; correct Refund API/opt-in semantics |
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/docker/README.md` | Existing tracked doc; setup/accounting/harness boundaries |
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/Docs/BATCH1_2_SANDBOX_REFUND_EXECUTION_2026-11-12.md` | Existing untracked report từ Batch1.2; chỉ superseded-config note mới |
| `C:/Users/ACER/IdeaProjects/DA1/checkinn/Docs/BATCH1_7_STANDARDIZATION_AUDIT_RECONCILIATION_2026-11-13.md` | New report trong Batch1.7 |

Tất cả code/test/SQL/Compose/untracked Refund artifacts khác đã tồn tại trước batch. PLAN/AUDIT không hiện trong `git -C checkinn diff`; không được bỏ qua khi bàn giao.

## J. Final Assessment

| Scope | Final status |
|---|---|
| Refund Backend/API Demo | **DONE — Refund Backend/API Demo Accepted** |
| Booking create/cancel Gateway trong isolated Refund stack | **BLOCKED**: thiếu Hotel Catalog/Availability; không áp cho toàn Booking |
| Full booking/payment/cancel/refund E2E | **NOT VERIFIED** |
| Frontend Refund request/status/admin actions | **NOT IMPLEMENTED**; browser E2E NOT VERIFIED |
| Provider-backed refund/query/reconciliation, actual money movement | **NOT IMPLEMENTED**; production settlement NOT VERIFIED |
| Overall project standardization | **PARTIAL / IN_PROGRESS**; parent issue counts unchanged, chưa Ready WRITE |

Đóng documentation reconciliation và nhóm Internal Refund Backend/API demo đúng scope. Workstream tiếp theo đã được xác định; chưa bắt đầu triển khai.
