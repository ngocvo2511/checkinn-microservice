# Batch 6B — Báo cáo tổng kết và đối chiếu đóng batch

16/11/2026, Asia/Saigon. Rà soát documentation-only trên branch `feat/canonical-uuid-schema`, HEAD `909e73d6021d076479bb6533badb9a029c5a9c2a`. Repository backend nằm tại `DA1/checkinn`; `DA1` không phải Git repository. Working tree đã có nhiều thay đổi tracked/untracked trước phiên này; chúng được giữ nguyên. Không stage/commit/push.

## A. Executive Summary

**Final status cập nhật 17/11/2026: DONE trong phạm vi Batch6B local development + VNPay Sandbox/Storage thực tế.** Core database integration giữ evidence PASS; hai provider blockers đã được thay bằng actual acceptance: [VNPay NCB/OTP, callback, Booking/Revenue và replay](BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md), [Storage upload/private signing](evidence/batch-6b-supabase-local-integration/storage-live-acceptance-2026-11-16.json). Không suy backend cloud deployment, IPN webhook hoặc production stability. Các mục ghi documentation-only dưới đây mô tả lần rà soát 16/11 trước follow-up live này.

Kết luận dựa trên source/config và evidence có sẵn; phiên này không kết nối database, chạy service/test, migration hoặc DDL/DML. PASS dưới đây nghĩa là VERIFIED bằng evidence đã lưu, không phải tái xác minh live ngày giờ hiện tại.

## B. Initial State

Batch 5B đã có canonical V1 trên năm PostgreSQL local mới; Batch 6A chỉ audit/readiness. Đầu Batch 6B, identity, hosted JDBC, managed security, migration và startup chưa được xác minh. Các checkpoint BLOCKED/PARTIAL ngày 14–16/11 phản ánh đúng thời điểm của chúng. Không sửa kết luận lịch sử của Batch 5A/5B/6A hoặc các báo cáo 6B cũ.

[Báo cáo ban đầu](BATCH6B_SUPABASE_LOCAL_DATABASE_INTEGRATION_2026-11-14.md), [readiness audit](BATCH6A_SUPABASE_READINESS_SECURITY_AUDIT_2026-11-14.md), [Batch 5B](BATCH5B_CANONICAL_FLYWAY_IMPLEMENTATION_2026-11-14.md).

## C. Implementation Summary

Đã có owner-specific profile `supabase-dev`, environment guards, guarded migration runner, dedicated roles, TLS, isolated RabbitMQ/Redis, runtime validation, workflow acceptance và Docker runtime. Các phần này được triển khai ở các phiên trước, không triển khai lại trong phiên đồng bộ tài liệu.

Thứ tự evidence được ưu tiên theo phạm vi: preflight → hosted migration → core acceptance → provider recheck → Docker → fixture cleanup → configuration consolidation. Timestamp đầu của `final-verification` không phải thời điểm của tất cả các trường được bổ sung sau đó.

Evidence mới nhất: [cleanup](evidence/batch-6b-supabase-local-integration/fixture-cleanup-2026-11-16.json) ghi xóa có chọn lọc 192 fixture rows, giữ history/unselected rows; [consolidation](evidence/batch-6b-supabase-local-integration/config-consolidation-2026-11-16.json) ghi hai mode Supabase/Test, không chạy SQL hoặc start container. `final-verification.currentRuntime` ghi stopped trước configuration cleanup; không tuyên bố stack đang chạy. [Reference catalog](evidence/batch-6b-supabase-local-integration/vietnam-reference-2026-11-16.json) ghi 74 reference rows, reapply 0, không schema change; đây không phải số bảng và không khôi phục synthetic hotels.

## D. Five Supabase Projects

| Alias | Service/domain | Business tables | JDBC / V1 / Hibernate |
|---|---|---:|---|
| P_USER | User + Loyalty | 7 | PASS / PASS / 7 entities PASS |
| P_HOTEL | Hotel + Room/content/inventory | 24 | PASS / PASS / 17 entities PASS |
| P_BOOKING | Booking + Payment + Refund | 7 | PASS / PASS / 4 entities PASS |
| P_REVENUE | Revenue | 5 | PASS / PASS / 2 entities PASS |
| P_REGULATIONS | Regulations | 3 | PASS / PASS / 2 entities PASS |

Năm project độc lập, `postgres/public`, tổng **46 bảng nghiệp vụ + 5 bảng Flyway history**, 32 JPA entities. Auth không project thứ sáu; Media Storage là project riêng cũ, không đồng nhất với năm database projects. Không công khai refs, endpoints hoặc credentials thật.

[All-five preflight](BATCH6B_USER_HOSTED_PREFLIGHT_2026-11-16.md), [migration/catalog evidence](evidence/batch-6b-supabase-local-integration/five-project-migration-acceptance-2026-11-16.json).

## E. Flyway Migration

Năm canonical `V1__canonical_schema.sql` đã migrate; mỗi project có một history row version 1 thành công, Flyway validate PASS, immediate rerun NOOP, invalid constraints 0. Checksum validity được hỗ trợ bởi validate/hash-bound review; public summary không liệt kê giá trị checksum từng row nên không tự tạo số checksum. Canonical SQL/history không được sửa trong phiên này. Không baseline/repair/clean, import demo hoặc replay migration. Approval đã hết hạn/disabled; runner first-run chặn history hiện hữu.

[Hosted migration acceptance](BATCH6B_HOSTED_MIGRATION_ACCEPTANCE_2026-11-16.md), [catalog results](evidence/batch-6b-supabase-local-integration/catalog-verification.md).

## F. Spring Boot Integration

Source năm profile `application-supabase-dev.yml` dùng credentials riêng theo owner, Hibernate `validate`, schema `public`, UTC, SQL init `never`, Flyway `enabled=false`, baseline false/clean disabled. Environment guards fail closed trước fallback sai target/TLS/roles/readiness. Runtime không tự mở rộng schema.

Kết nối accepted là session pooler với `verify-full`, CA/hostname checks và hai TLS legs được kiểm. Hikari max 2/min idle 1 mỗi service, timeout 10 giây; năm bounded exhaustion/recovery probes PASS, không phải kiểm load/Internet outage toàn provider.

Standalone Hibernate 5/5 và full startup/restart 5/5 PASS; [Docker evidence](evidence/batch-6b-supabase-local-integration/docker-runtime-2026-11-16.json) ghi 11 application containers healthy, 0 restart và JAR hashes khớp. Đây là quan sát lịch sử trước khi stack dừng.

Daily entry point hiện hành: `docker/stack.ps1 -Environment Supabase` đọc private `docker/.env`; `-Environment Test` đọc private `docker/.env.test` và dùng PostgreSQL local test. `local` là selector/compatibility lịch sử, không phải hosted acceptance hay bảo đảm schema demo cũ tương thích UUID. [Runbook hiện hành](../docker/README.md).

## G. Security

Dedicated migrator và runtime tách biệt; không dùng management/migration credentials trong app containers. Runtime không owner/migrator member, không persistent public CREATE hoặc history access; immutable tables có scoped allowlist. 30 expected SQLSTATE 42501 probes PASS. [Independent settings](evidence/batch-6b-supabase-local-integration/independent-security-settings-results.json) xác nhận SSL enforced và exposed schemas rỗng 5/5; catalog kiểm API/PUBLIC business table/column/sequence rights denied. Không dùng HTTP 401 làm bằng chứng Data API disabled.

Managed PUBLIC database TEMP vẫn được kế thừa; không chứng nhận chặn mọi managed schema/API operation. Năm cross-project login negatives PASS là năm cặp đã thử, không phải exhaustive 20 cặp. Source giữ giao tiếp API/gRPC/events; không thêm cross-service FK/join/database access. Public-artifact secret scans trước đây PASS; phiên này không đưa secret vào docs. Không thể suy chưa từng có secret trong toàn bộ Git history từ scan này.

## H. Business Acceptance

117 hosted checks PASS: register/OTP qua Mailpit/login/NextAuth; Hotel/Room ownership và lifecycle; Booking idempotency, holds/no oversell/compensation; Loyalty commit/reversal; onsite payment/internal refund/duplicate execution; Revenue CREDIT/DEBIT/outbox/replay; Regulations snapshots/stale updates/broker outage/restart.

Checkpoint 16/11 dùng callback HTTP/HMAC giả lập, VNPay còn code71 và Storage còn NXDOMAIN. Follow-up live 17/11 đã supersede hai blocker đó: NCB card/OTP → provider return00 → payment COMPLETED / booking CONFIRMED / Revenue một CREDIT, replay/negative checks PASS; Storage actual upload/signing PASS. SMTP chỉ Mailpit. [Provider report](BATCH6B_REAL_PROVIDER_ACCEPTANCE_2026-11-16.md), [VNPay actual evidence](evidence/batch-6b-supabase-local-integration/vnpay-sandbox-acceptance-2026-11-17.json).

Fixtures sau đó đã cleanup theo yêu cầu người dùng; không tự replay để tái lập kết quả. Search trả 2 synthetic hotels là lịch sử; cleanup ghi synthetic search results 0.

## I. Regression Results

[Core regression](evidence/batch-6b-supabase-local-integration/final-regression-results.json): 895 backend tests gồm **848 executed PASS / 47 skipped**, 0 failures/errors; frontend 214 PASS và typecheck PASS. Default unconfigured ApplicationTests bị loại khỏi final suite; lần chạy ban đầu authentication failure được giữ riêng, không chuyển thành PASS.

[Provider follow-up](evidence/batch-6b-supabase-local-integration/real-provider-acceptance-results-2026-11-16.json): 8 provider config tests, 30 Media tests PASS. [Docker follow-up](evidence/batch-6b-supabase-local-integration/docker-runtime-2026-11-16.json): 40 guard tests, 33 search tests, production Webpack/TypeScript build PASS. Không cộng các suite có thể trùng nhau thành một tổng mới; không coi regression là provider acceptance.

## J. Documentation Updates

Danh sách chính xác các file chỉnh sửa trong phiên này và kết quả consistency checks được ghi tại [documentation verification](evidence/batch-6b-supabase-local-integration/documentation-closure-verification-2026-11-16.json).

Architecture bổ sung mapping/deployed state, profiles/TLS/roles/pool và giới hạn. Migration plan bổ sung checklist theo evidence và dependency còn lại. Canonical schema, ID contract và ERD chỉ thêm deployment/status notes, giữ specification. Standardization plan/audit thêm checkpoint, không xóa lịch sử hoặc đóng parent findings quá phạm vi. ADD/SAD/ToDo, README và evidence guides được thêm current pointers để phân biệt historical statements với runtime/config/fixtures hiện hành.

## K. Remaining Risks

- RESOLVED 17/11: VNPay Sandbox actual NCB card/OTP → signed return 00 → payment/booking/Revenue PASS; duplicate and malformed callbacks checked.
- RESOLVED: Storage public/private buckets verified; real Media upload/download/signing/denial and probe cleanup PASS.
- NOT RUN: external IPN delivery, real provider refund/query/reconciliation, external SMTP, provider-wide outage/load, production backup/PITR/restore drill/RPO/RTO, backend cloud deployment.
- NOT VERIFIED: exhaustive cross-project login matrix, toàn bộ managed-schema/API surfaces, toàn bộ Git history secret absence, demo schema UUID compatibility. Không phải lời khẳng định các mục này thất bại.
- Retained: PUBLIC TEMP; STD-006 physical stock provenance OPEN, không default stock10/backfill; legacy migration/data cutover và financial recovery/reconciliation là scope tương lai.
- Cần cập nhật evidence mới sau source/provider/runtime thay đổi; 30 protected hashes và historical frontend-clean claim không đồng nghĩa toàn bộ working tree hiện tại sạch. Follow-up trước phiên này có Media/frontend tooling changes; phiên documentation này không sửa chúng.

## L. Final Status

| # | Tiêu chí | Kết luận theo evidence |
|---|---|---|
| 1 | Đúng năm projects | VERIFIED/PASS — all-five identity preflight |
| 2 | Năm JDBC connections | VERIFIED/PASS — ten dedicated-role logins |
| 3 | V1 cả năm | VERIFIED/PASS — migration acceptance |
| 4 | 46 bảng đúng thiết kế | VERIFIED/PASS — hosted catalog; history đếm riêng |
| 5 | History/checksums | VERIFIED/PASS — V1 success/validate/no-op/hash review |
| 6 | Hibernate năm services | VERIFIED/PASS — 32 entities + startup validation |
| 7 | Local Spring Boot → Supabase | VERIFIED/PASS — recorded core startup/restart; Docker live recheck 17/11 with 11 healthy application containers |
| 8 | Runtime không tự đổi schema | VERIFIED/PASS — profiles/guards + catalog/protected hashes |
| 9 | Security/permissions | VERIFIED/PASS trong scoped checks; PUBLIC TEMP/managed-surface limits giữ mở |
| 10 | Core workflows | VERIFIED/PASS — 117 historical checks; VNPay Sandbox/Storage actual follow-up PASS |
| 11 | Demo PostgreSQL không bị ảnh hưởng | VERIFIED/PASS trong evidence no legacy DDL/DML/protected hashes; compatibility NOT VERIFIED |
| 12 | Không cross-service DB access | VERIFIED/PASS trong source boundaries và 5 negative pairs; exhaustive matrix NOT RUN |
| 13 | Credentials/secrets | VERIFIED/PASS cho public-artifact scans trong phạm vi; all-history absence NOT VERIFIED |
| 14 | Regression liên quan | VERIFIED/PASS trong các suite nêu trên; skipped/default-context không PASS |
| 15 | Remaining risks | VERIFIED/PASS — liệt kê ở mục K, không xóa risk để đóng batch |

**Overall Batch6B: DONE cho phạm vi local-development và hai provider follow-up đã yêu cầu**, cập nhật 17/11/2026 bằng actual acceptance evidence. Không đóng các scope chưa kiểm: IPN delivery, real refund/query/reconciliation, outage/load, production readiness. Standardization và các batch khác giữ trạng thái riêng; không tự triển khai giai đoạn tiếp theo.
