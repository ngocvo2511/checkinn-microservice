# Batch6B follow-up — PostgreSQL17 runner và role review

15/11/2026, Asia/Saigon. **Local preparation PASS; overall6B PARTIAL; hosted BLOCKED.** Không Supabase SQL/DDL, role provisioning, password changes, Dashboard settings hoặc application startup. Không Git stage/commit/push. Metadata đã được người dùng bổ sung ở ignored `.local`; không đưa actual refs/credentials vào report này.

## Thay đổi

- Standalone runner1.1.0 nâng Flyway core+PostgreSQL từ10.10.0 lên10.22.0, Java21/pgJDBC42.7.4 giữ nguyên. Không đổi Spring Boot/service POMs, V1/historical migrations hoặc nghiệp vụ. PostgreSQL17 được Flyway hỗ trợ từ10.20 theo [release notes chính thức](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine). Đây là targeted compatibility upgrade, không tuyên bố phiên bản mới nhất hoặc security certification toàn dependency tree.
- Guard cho phép major14..17, vẫn chặn18+. Mỗi actual connection vẫn phải khớp exact approved major, database, role, schema/TLS. Không relax identity/empty/history/security/approval gates để test PASS.
- Dependency output mới `target/dependency-10.22.0`, artifact `supabase-runner-1.1.0.jar`; không dùng wildcard folder cũ có thể chứa10.10 JARs. Unit test xác nhận actual loaded core/PG module đều10.22.0.
- Tách canonical Flyway configuration thành helper chung để local integration dùng đúng settings của production runner. Không thêm local URL override/bypass vào production CLI.
- `prepare-role-review.py` chỉ render five private review packages từ V1 + supplied target metadata; validates distinct refs/CA/V1 hashes, không kết nối/tạo secrets/đổi active env. Proposed migrator-as-owner/app roles riêng cho từng service; actual target role/login fields vẫn để trống vì roles chưa được tạo.
- `admin-preflight-readonly.sql` xuất một JSON cell chứa catalog/ACL/role/event-trigger facts, read-only/rollback, không business rows/password columns. Có test parse JSON trên PostgreSQL17.11; actual hosted/catalog permissions chưa verified.

## SQL review package

Private output: `.local/batch6b/role-review-2026-11-15-runner1.1.0/`, có README/mapping/hash manifest và subfolder cho user/hotel/booking/revenue/regulations. Actual project refs chỉ ở ignored private package. Source/generator hướng dẫn tại [runner README](../tools/supabase-runner/README.md).

| File mỗi owner | Mục đích | Hosted status |
|---|---|---|
| 00-admin-readonly.sql | Lấy metadata/security baseline trước approval | NOT RUN |
| 01-provision-roles.sql | New NOLOGIN migrator/app; narrow schema rights/creator defaults | DRAFT_NOT_APPLIED |
| 02-enable-logins.psql | Interactive SCRAM password prompts, rồi LOGIN | NOT RUN; psql-only |
| 03-postmigration-grants.sql | Exact canonical table/sequence allowlist, immutable/history/API denial | DRAFT_NOT_APPLIED |
| 04-readonly-verification.sql | Effective grants sau actual provisioning/migration | NOT RUN |
| runtime.env.example / migration.env.example | Proposed separate process config, passwords/budgets blank | NOT LOADED |

No broad ON ALL TABLES/SEQUENCES, app owner/migrator membership, schema ownership change, maintenance database-wide CONNECT/TEMP revoke hoặc guessed password. Runtime immutable model giữ nguyên: User loyalty_commands/points_transactions; Booking manual_refund_audit; Revenue cash/event receipts; Regulations snapshots SELECT+INSERT; Hotel feature-migration table SELECT-only. History denied. Creator defaults hardened globally và schema-specific trước V1; no automatic app DML defaults.

Mutation scripts check exact database/current-role/major, stop if roles/history/canonical objects already exist, require explicit operator project/service/security settings. Settings là attestations, không bằng chứng authenticated project identity hoặc authorization tự sinh. Role/security approval phải tách canonical migration approval; không set flags để vượt gate.

## Verification mới

Portable PostgreSQL17.11 binaries từ [EDB official distribution](https://www.enterprisedb.com/download-postgresql-binaries), tải/extract trong ignored `.local/tools`, không installer/service/existing-data changes. Archive nguồn/digest được ghi private; computed digest không phải vendor signature verification. Hai new owned local cluster runs được dừng, giữ files/logs.

Final run **13 tests PASS,0 failure/error/skip**:7 unit safety/version/approval tests,1 real catalog/admin-inspection test,5 service-specific integration cases. Integration cases trên5 fresh independent local DBs:

- Role SQL chưa approved bị từ chối trước CREATE ROLE; duplicate-role provisioning bị từ chối, không sửa/reuse roles; roles ban đầu NOLOGIN.
- Synthetic role passwords được đặt trong local harness, actual dedicated-role connections thành công. Interactive psql password prompts không được tự động hóa/chứng nhận.
- V1 apply5/5, Flyway validate5/5, rerun no-op5/5, one history row perDB; expected7/24/7/5/3 business tables.
- Creator defaults deny API-role table access ngay sau V1, trước postmigration revokes. Actual scoped grant SQL chạy được; app required zero-row SQL permissions PASS, immutable UPDATE/DELETE và Hotel marker INSERT denied; app persistent CREATE/ALTER public/history access denied SQLSTATE42501; no owner membership.
- API table/column permissions denied cho anon/authenticated/service_role trên46 tables; sequence USAGE allowed, UPDATE denied. Đây là SQL role tests local, không HTTP Data API/Supavisor acceptance.

[Machine-readable result](evidence/batch-6b-supabase-local-integration/pg17-role-validation-2026-11-15.json). Không rerun frontend/backend business regression vì không sửa app/frontend/business code; kết quả843/47+214/tsc/35 guard tests của14/11 giữ historical scope, không đếm như test mới15/11. Các SQL/V1/base config hashes vẫn khớp initial snapshot.

## Chưa xác minh và bước tiếp theo

Hosted identity/org ID/Connect/TLS/custom-role/session-pooler permission/API exposure/default ACL/event-trigger/managed object baseline chưa có authenticated results. SQL00 không tự chứng minh identity hoặc Data API exposure. Người dùng ghi Data API đang bật; exposed schemas/dependencies cần kiểm trước tạo bảng. Active event triggers/unexpected non-extension public objects vẫn STOP để review exact allowlist, không tự delete/disable hoặc bỏ guard.

Managed postgres capability/role membership/default privileges cần xác minh trước chạy01; test trên local admin không chứng nhận Supabase admin capability. PUBLIC TEMP hoặc managed-schema rights có thể inherited, scripts không revoke rộng; hosted least-privilege acceptance chưa đạt. Role connection caps2/4 là đề xuất cần review actual budget/Supavisor limits, chưa áp Hikari pool bằng giả định.

**Next concrete step:** người dùng chạy riêng `user/00-admin-readonly.sql` trong đúng User project SQL Editor và lưu ô JSON vào `.local/batch6b/user.admin-preflight.json`; có thể lặp cho4 owner còn lại. Assistant sẽ kiểm private snapshots và dependency/security gaps trước trình exact per-project role/security changes để approval. Không cần password/token trong chat. Chưa chạy01/02/03; migration V1 chỉ sau preflight/security PASS và exact project/service approval. Runtime/Hibernate/business hosted vẫn BLOCKED.


## Subsequent User event review

[Runner1.2.0/User trigger review](BATCH6B_USER_EVENT_TRIGGER_REVIEW_2026-11-15.md) supersedes the blanket-trigger-stop limitation only for exact reviewed per-project policies. Latest local suite15 PASS; the13-test facts above remain historical. No hosted mutation/security/migration approval.
