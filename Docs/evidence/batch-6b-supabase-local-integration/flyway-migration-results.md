# Flyway results

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
| Gate | Local tooling | Hosted5/5 |
|---|---|---|
| Runner compile/package and negative guards | PASS | NOT VERIFIED |
| Canonical V1 hashes preserved | PASS | NOT VERIFIED |
| V1 apply/history/checksum | NOT RUN in6B local harness | BLOCKED |
| Immediate rerun no-op | Runner asserts0 after validate | BLOCKED |

Runner riêng `tools/supabase-runner`, Java21/Flyway core+PostgreSQL10.10.0/pgJDBC42.7.4; không sửa harness5B/historical/V1. Only canonical directory containing exactly V1; schemas/default public/createSchemas false/baseline false/clean disabled/outOfOrder false/validate true. Không clean/repair. Fresh-first guard không tự baseline history cũ. Migrate requires approval file: exact manifest SHA+service/ref+V1 SHA+inventory SHA, approver, expiry<=24h và các security/exclusive-DDL/app-stopped gates. Preflight/catalog/control-plane được kiểm lại trước Flyway. Mỗi connection kiểm identity trước trả cho Flyway; local OS file lock không thay external exclusive DDL window.

Chưa có project được xác nhận và chưa được duyệt DDL; không tạo approval thay người dùng. **CONFIGURATION/TOOLING PREPARED; READY FOR MIGRATION APPROVAL chỉ sau khi mapping/preflight/security thực tế PASS**, hiện **BLOCKED** ở các prerequisites đó. Migration approval per-service, thứ tự User→Hotel→Booking→Revenue→Regulations, STOP ở lỗi đầu. Rerun trong cùng approved run chỉ sau history exactV1+Flyway validate; standalone chạy lại migrate gặp existing history sẽ STOP. Catalog acceptance đầy đủ sau migrate vẫn là gate riêng.


## Follow-up15/11/2026

Các facts bên trên là checkpoint14/11. Runner hiện1.1.0/Flyway10.22.0, major gate14..17; actual local PostgreSQL17.11/13 tests/5V1+rerun/scoped role SQL PASS. Version tooling blocker17 đã đóng local, không mở hosted/security/migration gates. SQL review package chưa apply; active roles/credentials chưa cấu hình. [Follow-up report](../../BATCH6B_POSTGRES17_RUNNER_ROLE_REVIEW_2026-11-15.md), [runner runbook](../../../tools/supabase-runner/README.md).

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
