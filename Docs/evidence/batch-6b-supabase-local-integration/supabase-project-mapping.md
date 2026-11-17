# Five-project mapping

> **Provider closure 17/11/2026 — Batch6B: DONE cho phạm vi local development + VNPay Sandbox/Storage.** Actual NCB card/OTP → return00 → payment COMPLETED, booking CONFIRMED, một Revenue CREDIT; callback replay/negative checks PASS. Storage upload/private signing PASS; fixture đã dọn. [Evidence và giới hạn](../../BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md). Các provider BLOCKED/PARTIAL ở checkpoint16/11 phía dưới là lịch sử; không tự đóng production/IPN/refund, batch khác hoặc parent standardization findings.

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
| Service owner | Logical project | Actual project/org/Connect | Status |
|---|---|---|---|
| user | Supabase User + Loyalty | Chưa cấu hình riêng cho6B | BLOCKED |
| hotel | Supabase Hotel | Chưa cấu hình riêng cho6B | BLOCKED |
| booking | Supabase Booking + Payment/Refund | Chưa cấu hình riêng cho6B | BLOCKED |
| revenue | Supabase Revenue | Chưa cấu hình riêng cho6B | BLOCKED |
| regulations | Supabase Regulations | Chưa cấu hình riêng cho6B | BLOCKED |

Không tạo/gộp project, FK hoặc join liên project. `config/supabase-dev/targets.properties.example` là template metadata, không chứa project refs thật; 5 SHA V1 lấy từ source. Actual mapping phải do người dùng review từ Dashboard → Connect và Management API của đúng organization. Không dùng alias làm ref. Runner buộc đủ5 refs/login khác nhau, owner cố định, direct hoặc session5432, database postgres/public. Runtime mỗi owner dùng DB_PROJECT_REF/DB_RUNTIME_ROLE/DB_URL/DB_USERNAME/DB_PASSWORD riêng; session login phải gắn đúng tenant ref.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
