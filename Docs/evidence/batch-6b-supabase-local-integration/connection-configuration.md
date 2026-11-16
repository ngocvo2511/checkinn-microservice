# Connection configuration

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
**PASS (local/static):** 5 profile `supabase-dev` và `local`, environment guard, `.env.example`; base YAML và canonical profile cũ giữ nguyên. Không có default hosted endpoint hoặc fallback. Runtime: Hibernate validate, SQL init never, Flyway false/baseline false/clean disabled; UTC JDBC/session; SQL/bind/Hikari logs tắt; Config Server tắt. Guard kiểm effective config để chặn CLI/environment override không an toàn trước datasource.

URL chỉ cho direct/session pooler5432, postgres/public, `sslmode=verify-full`, CA path riêng, connectTimeout10; không credential trong URL, SSL factory/options/duplicate params/multi-host/transaction6543. Endpoint và username thực tế lấy riêng từ Connect; không suy luận tất cả projects cùng host. Runtime role không nhận postgres/anon/authenticated/service_role. CA/URL thật và TLS network **NOT VERIFIED**. Runner kiểm CA SHA, exact host/login/SQL role/major/schema trên mọi physical connection và Management API project/org/database host.

Connection pool không mặc định áp4. Mỗi owner bắt buộc DB_POOL_MAX (1..4) và DB_CONNECTION_BUDGET đã kiểm thực tế; yêu cầu budget>=max+2 migration reserve+3 operational headroom, một instance local. Min idle1, connection timeout10s. Đây là guard/candidate, không phải đo limit hosted. Giới hạn Supavisor/client, replica/concurrency và headroom thực tế phải review trước startup.

Nguồn đối chiếu14/11/2026: [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [API security](https://supabase.com/docs/guides/api/securing-your-api), [PostgreSQL privilege functions](https://www.postgresql.org/docs/16/functions-info.html). Hướng dẫn chạy: [reproduction](reproduction-guide.md).

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
