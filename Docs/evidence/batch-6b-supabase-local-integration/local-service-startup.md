# Local service startup

> Trạng thái hiện hành 16/11/2026: **Batch 6B overall PARTIAL; core database/local integration VERIFIED/PASS, provider acceptance BLOCKED (VNPay code 71, Storage NXDOMAIN).** Năm projects / 46 business tables / 5 Flyway histories / 32 JPA entities; startup/restart và 117 workflow checks đã PASS theo evidence. Backend cloud deployment chưa thực hiện. Fixtures đã cleanup 192 rows; cấu hình daily Supabase=`docker/.env`, Test=`docker/.env.test`; private runtime fallback đã tắt. Quan sát runtime cuối ghi stopped trước configuration cleanup, không khẳng định đang chạy. [Báo cáo tổng kết](../../BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md). Các kết luận phía dưới giữ nguyên theo checkpoint lịch sử; trạng thái hiện hành ưu tiên ghi chú này.
**NOT RUN business stack** vì hosted migrations/security/acceptance chưa được phê duyệt và chưa hoàn tất. Không khởi động ứng dụng against DB demo cũ hoặc gửi event từ queues cũ.

**PASS local:** actual SpringApplication bootstrap guard tests5/5; profile thiếu config fail trước datasource/context. `docker compose ... config --quiet` PASS bằng synthetic env, chỉ resolve structure; không tạo/start container. Dedicated infra compose chỉ RabbitMQ/Redis local, loopback nondefault ports, named volumes theo unique SUPABASE_DEV_SESSION, dedicated vhost; không dùng definitions/pending events từ stack cũ. Redis database chọn rõ, fresh dedicated instance. Session name và empty volume/vhost/queues phải kiểm thực tế trước bật SUPABASE_DEV_INFRA_ISOLATED=true.

Sau approval/database validation: fresh local infra → Eureka → Regulations → User → Hotel → Booking → Revenue → Gateway → Next.js. Điều chỉnh dependency theo runtime health, không bật Config Server override cho5 DB owners. SMTP/OTP, Media Storage, SECRET_KEY/JWT, internal HTTP/gRPC, VNPay sandbox cần thiết lập riêng. Không giao dịch/refund thật. Profile supabase-dev dành host JVM; base compose app profile docker không được dùng để start5 owners vào hosted. Docker hỗ trợ ở batch này là infra local; muốn JVM trong container cần overlay/guard localhost mapping riêng, chưa được chứng nhận.

## Approved hosted migration checkpoint —16/11/2026

All five canonical V1 migrations + Flyway validation + immediate no-op reruns PASS after explicit five-target approval. Scoped runtime grants applied; hosted catalog/role/API-right checks and32 standalone Hibernate entity validations PASS.46 business tables plus5 history tables;30 actual expected permission-denied probes; no business rows read/modified by probes. No local service startup. Runtime PUBLIC TEMP and operator-confirmed Data API setting limitations remain; pool budgets, dedicated broker/cache isolation/startup and business workflows pending. Overall6B PARTIAL.

See [hosted migration acceptance](../../BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md) for actual evidence and scope; prior checkpoint statements remain historical as-of observations.

## Final16/11/2026 — DONE cho local development

Cả5 Supabase DB, independent security config (SSL enforced/exposed schemas rỗng), V1/history/catalog/Hibernate và actual startup/restart PASS.117 hosted workflow checks,5 cross-project negative logins,30 privilege denials,5 bounded pool exhaustion/recovery probes PASS. Pool max2/min1; fresh broker/cache session, Mailpit local; NextAuth end-to-end PASS.848 backend executed PASS/47 optional skip, frontend214 PASS và typecheck. Không business-rule/Place/frontend/base SQL/YAML changes, cloud backend hoặc destructive cleanup. Synthetic rows giữ lại.

VNPay provider thật/Storage uploads NOT RUN; callbacks chỉ HTTP local giả lập, SMTP chỉ Mailpit. PUBLIC TEMP/legacy STD-006 vẫn được ghi nhận; không chứng nhận production readiness. Các checkpoint PARTIAL/BLOCKED phía trên là lịch sử as-of.

[Final report](../../BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-16.md); [final evidence](final-verification-2026-11-16.json).
