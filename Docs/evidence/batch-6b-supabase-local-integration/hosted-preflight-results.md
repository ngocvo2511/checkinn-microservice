# Hosted preflight

> **Provider closure 17/11/2026 — Batch6B: DONE cho phạm vi local development + VNPay Sandbox/Storage.** Actual NCB card/OTP → return00 → payment COMPLETED, booking CONFIRMED, một Revenue CREDIT; callback replay/negative checks PASS. Storage upload/private signing PASS; fixture đã dọn. [Evidence và giới hạn](../../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md). Các provider BLOCKED/PARTIAL ở checkpoint16/11 phía dưới là lịch sử; không tự đóng production/IPN/refund, batch khác hoặc parent standardization findings.

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
**BLOCKED 5/5**: chưa có actual approved mapping, endpoint/TLS CA, dedicated credentials hoặc Management API access được người dùng cấu hình cho6B. Không thử credentials tìm thấy trong file cũ. Không có hosted SQL/control-plane requests được runner thực hiện.

Runner có `preflight OWNER`: Management API GET kiểm project/org/database host; strict connection/CA/version/role guard; explicit read-only transaction, rollback; business objects/history guard, catalogs/ACL/default privileges/API-role inheritance/runtime permissions. Chỉ in alias và inventory hash, không in full catalogs/SQL exceptions/URL/password. File mutex ở .local, không DB lock/DDL/DML trong preflight.

Existing Flyway history hoặc unexpected non-extension public relation/function/type => STOP. Extension members được phân biệt bằng pg_depend; không yêu cầu toàn database rỗng. Catalog gồm public relations, columns/defaults/identity/ACL, functions/bodies, types, triggers/policies, constraints/indexes/sequences, extensions, roles/memberships/default ACL, connection limits, event triggers. Inventory hash không thay thế review security/control-plane.

Giới hạn có chủ ý: event triggers đang bật luôn STOP để review; không tự coi trigger của Supabase là an toàn. Public object không có extension-membership cũng STOP dù có thể là managed; hiện chưa có approved per-project allowlist. Nếu gặp, cần phân loại và bổ sung exact reviewed guard trước migration, không xóa object/bỏ qua preflight. Read-only preflight có thể BLOCKED bởi quyền đọc catalog; không cấp quyền rộng tự động. PostgreSQL14..16 là conservative gate của runner Flyway10.10 hiện tại; major khác STOP để review version.

`PREFLIGHT_SQL_PASS` chỉ là SQL/identity gate; dòng tiếp theo vẫn yêu cầu hosted Data API review và exact migration approval. Chưa có full hosted PASS.


## Follow-up15/11/2026

Các facts bên trên là checkpoint14/11. Runner hiện1.1.0/Flyway10.22.0, major gate14..17; actual local PostgreSQL17.11/13 tests/5V1+rerun/scoped role SQL PASS. Version tooling blocker17 đã đóng local, không mở hosted/security/migration gates. SQL review package chưa apply; active roles/credentials chưa cấu hình. [Follow-up report](../../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../../tools/supabase-runner/README.md).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
