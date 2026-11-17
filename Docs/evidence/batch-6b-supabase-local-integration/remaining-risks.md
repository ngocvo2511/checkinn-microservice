# Remaining risks

> **Provider closure 17/11/2026 — Batch6B: DONE cho phạm vi local development + VNPay Sandbox/Storage.** Actual NCB card/OTP → return00 → payment COMPLETED, booking CONFIRMED, một Revenue CREDIT; callback replay/negative checks PASS. Storage upload/private signing PASS; fixture đã dọn. [Evidence và giới hạn](../../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md). Các provider BLOCKED/PARTIAL ở checkpoint16/11 phía dưới là lịch sử; không tự đóng production/IPN/refund, batch khác hoặc parent standardization findings.

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
- Critical/BLOCKED: actual5 refs/org/Connect tuples/CA/credentials/access/version/security chưa cung cấp riêng cho6B; chưa có exact user migration approval.
- Critical/NOT VERIFIED: Data API exposure/default ACL/managed roles/HTTP negative tests; no security provisioning executed. Attestation của operator cần evidence độc lập, không tự đánh true.
- High/BLOCKED if present: active event triggers hoặc managed non-extension public objects chưa có reviewed allowlist; runner STOP, không bypass. PG ngoài14..16 yêu cầu runner dependency/support review.
- High/NOT VERIFIED: production-like real TLS CA/hostname negatives, pooler tenant/custom-role compatibility, Management API actual response shape, Flyway hosted session/DDL/lock behavior và SQL permissions.
- High/NOT VERIFIED: runtime grants/history/immutable restrictions/catalog/JPA/startup/core workflows/broker/Redis isolation. Guard readiness flags là operator attestations, không tự là evidence runtime PASS.
- High/NOT VERIFIED: provider sandbox/SMTP/Media dependency, backup/PITR/retention/RPO/RTO/restore drill trước traffic; local-only scope không cần deploy cloud.
- Retained: STD-006 stock provenance OPEN, không suy đoán stock10/import/repair. Không dùng local compatibility selector để khẳng định code UUID hiện tại chạy được schema demo cũ.
- Tool limitations: first-run migrate only, immediate no-op rerun same invocation; subsequent rerun stops at existing history. Dedicated migrator-as-owner only; no SET ROLE. OS lock local only; require approved exclusive DDL window externally. Full postmigration catalog/API/runtime acceptance remains separate. No hosted claims from local guard tests.


## Follow-up15/11/2026

Các facts bên trên là checkpoint14/11. Runner hiện1.1.0/Flyway10.22.0, major gate14..17; actual local PostgreSQL17.11/13 tests/5V1+rerun/scoped role SQL PASS. Version tooling blocker17 đã đóng local, không mở hosted/security/migration gates. SQL review package chưa apply; active roles/credentials chưa cấu hình. [Follow-up report](../../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../../tools/supabase-runner/README.md).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).

## Checkpoint đồng bộ và xét đóng Batch 6B — 16/11/2026

Core database/local acceptance đã đủ evidence; overall PARTIAL do phạm vi provider thực tế còn BLOCKED. Các checkpoint cũ NOT STARTED/BLOCKED/DONE phản ánh phạm vi và thời điểm trước đó, không được dùng thay current status. Cleanup fixtures và consolidation thay thế các ghi chú cũ về giữ synthetic rows, stack đang chạy và private `.local` fallback.

Giữ mở PUBLIC TEMP, STD-006 stock provenance, legacy cutover/recovery, real provider payment/refund/Storage, production backup/PITR/restore/RPO/RTO, broad outage/load và backend cloud deployment. Không tự đóng toàn bộ standardization hoặc mở Batch 7.
