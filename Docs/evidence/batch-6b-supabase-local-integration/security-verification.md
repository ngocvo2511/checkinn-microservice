# Security verification

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
**PASS local:** negative URL/option/host/project/credential override guards; every physical connection checked (mock second connection wrong-role closes); real PostgreSQL default ACL/runtime CREATE/unexpected object probes. Xem [local catalog](local-catalog-test.json), [verification](verification.json).

**NOT VERIFIED hosted:** Data API enable/exposed schemas, anon/authenticated/service_role/PUBLIC effective privileges, column grants, function/view/RPC paths, inherited roles, runtime nonownership/escalation, managed role provisioning, TLS wrong CA/hostname, real HTTP denials và cross-project credential negative tests. Không thay dashboard/RLS/roles/grants.

Runner kiểm migrator USAGE/CREATE, runtime không CREATE public/member migrator/privileged inherited roles, PUBLIC không CREATE schema; creator global và schema defaults không cấp business object rights cho PUBLIC/API roles kể cả inheritance. Function EXECUTE default cũng phải revoke đúng creator trước tạo bảng. Không tự áp SQL grants. Mode được hỗ trợ hiện tại: dedicated migrator là object owner; runtime riêng, không SET ROLE/NOLOGIN owner workflow. Nếu cần model khác phải review code trước dùng, không nới quyền runtime.

Data API disable hoặc bỏ public exposure cần xác minh độc lập từ Dashboard/control-plane và dependency Media/Auth/Storage; runner chỉ chấp nhận operator attestation gắn exact approved manifest, không tự chứng minh HTTP security bằng SQL. Approval false/missing/expired => STOP. Post-migration vẫn cần actual API/SQL negative tests46 tables, runtime grants theo allowlist/immutable rules/history denial; chưa thực hiện. RLS không tự bảo vệ JDBC/owner/BYPASSRLS. Không dùng service-role API key làm DB password.


## Follow-up15/11/2026

Các facts bên trên là checkpoint14/11. Runner hiện1.1.0/Flyway10.22.0, major gate14..17; actual local PostgreSQL17.11/13 tests/5V1+rerun/scoped role SQL PASS. Version tooling blocker17 đã đóng local, không mở hosted/security/migration gates. SQL review package chưa apply; active roles/credentials chưa cấu hình. [Follow-up report](../../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../../tools/supabase-runner/README.md).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
