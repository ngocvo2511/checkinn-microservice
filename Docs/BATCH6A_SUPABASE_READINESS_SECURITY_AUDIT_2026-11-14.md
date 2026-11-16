# BATCH6A — Supabase readiness, security audit & deployment planning

14/11/2026, Asia/Saigon. **DESIGN/AUDIT DONE** trong phạm vi static; **Batch6B NOT READY** để thực thi. Không truy cập/thay đổi hosted resources. Không chuyển static facts thành hosted PASS.

## A. Overall Status

Đã đối chiếu current source/POM/YAML/SQL/deployment scripts với13 tài liệu đầu vào và evidence5B; xây dựng security/connection/pool/backup/cutover/runbook6B. [Initial state](evidence/batch-6a-supabase-readiness/initial-state.md), [input manifest](evidence/batch-6a-supabase-readiness/required-inputs.json), [source inventory](evidence/batch-6a-supabase-readiness/source-inventory.json). Không chạy lại regression suite vì batch chỉ thêm tài liệu và inspector;901 backend/211 frontend trong5B và214 frontend trong PR review là kết quả lịch sử, không test mới6A.

**STATIC VERIFIED:** năm V1 đúng SHA5B,46 bảng/458 cột theo canonical inventory, profile canonical an toàn khi được bật/resolved đúng, version/dependency scope, local-only runner limitation và deployment/security gaps. **HOSTED VERIFIED:** không có. **NOT VERIFIED:** toàn bộ project identity/config/version/grants/RLS/network/backup/connection thực tế. **BLOCKED:** các hành động6B phụ thuộc metadata/runner/overlay/security gate chưa đạt. **NEEDS USER INPUT:** metadata và quyết định vận hành bên dưới.

## B. Five-Project Architecture

Giữ nguyên năm projects đã tạo: User+Loyalty; Hotel; Booking+Payment/Refund; Revenue; Regulations. Năm database endpoint/credential/history độc lập là target; chưa có metadata an toàn để xác nhận actual bindings. Không project ref/endpoint giả, không gộp/tạo project. Source V1 FK đều trong owner, reviewed API/gRPC/events/outbox không cần cross-project SQL join/FK/transaction. Compose có năm input slots nhưng shared local defaults, không chứng minh hosted credential isolation. [Topology audit](evidence/batch-6a-supabase-readiness/five-project-topology-audit.md).

## C. Connection Strategy

Dedicated Flyway/admin ưu tiên direct TLS; session pooler là phương án IPv4 sau acceptance. Persistent Spring/Hikari direct hoặc shared session; không chọn transaction pooler cho current session-init/Flyway workload. `prepareThreshold=0` chỉ xử lý server preparation, không khắc phục session state; xact advisory lock khác session lock. SSL verify-full+CA/hostname, timeout/pool budgets riêng từng project. Hikari5.1.0 defaults max10/min=max/keepalive0; thực tế deployed UNKNOWN. Công thức `replicas × maxPool + migrationConnections + operationalHeadroom <= approvedBudget`, reserve migration2 provisional, replica/surge/plan chưa biết. Candidate max4/min1 chỉ đề xuất, chưa áp dụng. [Strategy/budget](evidence/batch-6a-supabase-readiness/connection-strategy.md).

## D. Security

V1 public không tự có RLS/API grants protection. Phải secure exposure và creator default ACL **trước** tạo tables, kiểm actual ACL/inherited roles/functions/views/sequences và negative SQL+Data API tests. Backend-only ưu tiên disable Data API hoặc bỏ public exposure khi không ảnh hưởng dependency đã kiểm; Media Storage mapping còn UNKNOWN. Không tự thay dashboard/RLS/grants. Service-role API key không phải migration password; JDBC không tự có per-user RLS. [Security model](evidence/batch-6a-supabase-readiness/supabase-security-model.md), [role plan](evidence/batch-6a-supabase-readiness/roles-and-grants-plan.md).

Dedicated runtime nonowner/no DDL/history/escalation; migrator/owner riêng, append-only restrictions theo source. Managed admin capability/SET ROLE/default privileges cần kiểm, không sao chép local maintenance DB revoke hoặc pg_read_all_settings guard. Ignored local env chứa material nhạy cảm; chỉ loại/vị trí được ghi, không giá trị, không thử kết nối; rotation nếu exposed/shared/committed. [Secrets/config contract](evidence/batch-6a-supabase-readiness/secrets-configuration-audit.md).

## E. Compatibility / migration safety

Boot3.3.4/Java21/Flyway10.10.0/pgJDBC42.7.4/Hibernate6.5.3.Final/Hikari5.1.0. Flyway PG module có sẵn; User runtime, bốn service khác test scoped. Exact version theo POM+BOM+existing JAR; release cần rebuild. Flyway10.10 source threshold major16; localPG18 PASS không là vendor certification, current Redgate page không retroactively certify10.10. Actual năm hosted versions UNKNOWN; minimal release-specific runner upgrade chỉ khi cần, không framework major upgrade. [Compatibility](evidence/batch-6a-supabase-readiness/dependency-compatibility.md).

Quan trọng: Compose chỉ bật docker, base bốn service Hibernate update và User baseline true; canonical override chưa tự thành deployed setting. Runner5B hardcodes loopback/data_directory, không dùng hosted. Cần runner mới guard mọi connection bằng private project/control-plane+TLS/tuple+SQL identity; check public managed allowlist/business/history, fail closed; không baseline/clean/repair. [Preflight](evidence/batch-6a-supabase-readiness/flyway-deployment-preflight.md), [read-only SQL template](evidence/batch-6a-supabase-readiness/preflight-readonly.sql).

## F. Backup & Cutover

Plan/retention/PITR/RPO/RTO UNKNOWN, không mặc định entitlement. Logical+schema/security/sequence manifests, encrypted storage, isolated restore drill, Media bytes riêng. Fresh/no demo import/cache/old events; broker/cache mới trước startup. Go/no-go trước DDL/startup/traffic. Sau writes phải freeze/quarantine/reconcile payment/refund/loyalty/inventory/revenue/events, không blind config rollback hay Flyway clean/drop. [Backup](evidence/batch-6a-supabase-readiness/backup-restore-plan.md), [cutover/rollback](evidence/batch-6a-supabase-readiness/cutover-rollback-plan.md).

## G. External Dependencies

RabbitMQ/Redis/Boot/Next.js/Eureka cần hosting decision; Gateway/SMTP/Media/VNPay sandbox/HTTP-gRPC cần environment configuration và actual acceptance. Monitoring local scaffolding có nhưng alert delivery/business counters chưa verified. Place/OSRM modifications ngoài phạm vi. Supabase không tự host các dependency này. [External matrix](evidence/batch-6a-supabase-readiness/external-dependencies.md), [operational gates](evidence/batch-6a-supabase-readiness/operational-readiness.md).

## H. Required User Inputs cho6B

- Metadata riêng tư/approved access cho năm project aliases: owner mapping, Connect mode/tuple/login format, version/plan/compute/limits/API settings/admin capability và **secret-manager references**, không password trong chat.
- Deployment hosts/network/DNS/TLS, replicas/surge và pool budget; broker/Redis fresh boundary, Media project/bucket mapping; SMTP/VNPay sandbox/frontend endpoints.
- Backup retention/PITR thực tế, approved isolated restore destination, RPO/RTO/cutover window, incident/financial owner và monitoring/alert destination.
- Phê duyệt phạm vi thực thi6B sau khi blockers được đóng; không cần duyệt lại topology hay fresh-no-import.

## I. Batch6B Readiness

**NOT READY.** Blockers: target identity/metadata/access absent; hosted guarded runner và canonical overlay chưa implement/review; API/grants/managed roles/network/version/pool/backup/external gates chưa verified. [Execution plan](evidence/batch-6a-supabase-readiness/batch-6b-execution-plan.md) có exact order, environment contract, runner implementation requirements/commands, identity/empty/rerun guards, catalog/Hibernate/startup/negative tests/monitoring/rollback/evidence. Artifact commands là specification cho runner cần xây trong6B, không giả có sẵn tool deploy. [Risk matrix](evidence/batch-6a-supabase-readiness/remaining-risks.md).

## J. Safety / verification

Chỉ thêm report/evidence/inspector và append checkpoint vào bốn tài liệu được yêu cầu. Không sửa source/runtime config/POM/V1/history/Place/backend/frontend/real.env, không dùng discovered credentials, không truy cập DB/provider, không stage/commit/push/merge/reset/checkout. Hash preservation, document/link/JSON/Python checks và Git diff check được ghi trong [verification](evidence/batch-6a-supabase-readiness/verification.json). Không chạy migration/backup/restore/cutover hay tự thực hiện6B. Kết quả lịch sử5A/5B giữ nguyên.
