# Batch6B — User event-trigger review và pinned guard

15/11/2026, Asia/Saigon. **Static User function review và local guard validation PASS; hosted integration vẫn PARTIAL/BLOCKED.** Source là hai snapshot SQL Editor do người dùng cung cấp, không phải direct authenticated query của assistant. Không hosted SQL/DDL/settings/roles/password changes, migration hoặc startup.

## Kết quả review

User snapshot ghi PostgreSQL17.11, postgres/public, không public business objects/history; six enabled triggers và functions thuộc supabase_admin trong extensions. Tất cả là invoker functions, PL/pgSQL, zero arguments, `search_path=''`. User xác nhận Data API đang expose public và graphql_public; notes ghi không dùng project này cho Auth/Storage/ứng dụng khác.

| Trigger | Phạm vi body đã đọc | Đánh giá cho pinned canonical V1 |
|---|---|---|
| issue_graphql_placeholder | DROP EXTENSION, dựng GraphQL placeholder | V1 không DROP EXTENSION |
| issue_pg_cron_access | CREATE EXTENSION pg_cron, cấp quyền cron | V1 không CREATE EXTENSION |
| issue_pg_graphql_access | CREATE EXTENSION pg_graphql, wrapper/grants GraphQL | V1 không CREATE EXTENSION |
| issue_pg_net_access | CREATE EXTENSION pg_net, role/grants/legacy function security changes | V1 không CREATE EXTENSION |
| pgrst_ddl_watch | Duyệt DDL catalog events, NOTIFY pgrst reload schema | Không thấy sửa table/API ACL trong body này |
| pgrst_drop_watch | Duyệt dropped-object catalog, NOTIFY pgrst reload schema | Không thấy sửa table/API ACL trong body này |

Đây là kết luận từ supplied function definitions, không phải exemption chung cho các tên trên. Extension hooks có thể cấp quyền rộng/tạo role trong extension lifecycle; chỉ reviewed trong phạm vi User V1 checksum không có CREATE/DROP/ALTER EXTENSION. Không approve future migrations/extensions, không disable/drop/re-own provider triggers. Full definitions/ACL/private refs chỉ lưu ignored .local, không đưa vào report.

## Runner1.2.0

- Flyway10.22.0 và major14..17 giữ nguyên; app/source/schema/business không đổi.
- Optional per-owner `eventTriggerReviewFile` + `eventTriggerReviewSha256`. Chỉ User được cấu hình private static-review policy, không auto-copy sang4 projects khác.
- Policy bind exact owner/project/major/V1 SHA và static-review status; requires exact six known descriptors với expected tags/events/owners/schema/language/invoker/search-path shape. File hash không khớp hoặc scope khác => STOP.
- Live comparison gồm toàn bộ function definition, ACL, config, owner, tags, event, enabled state, signature và extension membership; object-key/row ordering không tạo exemption. Extra/missing/changed event/function => STOP.
- Kiểm trên mọi physical connection trong read-only identity transaction trước giao cho Flyway, và khi preflight/recheck. Target không có policy vẫn chặn unreviewed active triggers. Fresh public/history/security/Data API/CA/Management API/explicit migration approval gates giữ nguyên.
- Policy là static review, không chứng minh project identity và không phải approval tạo roles/chạy migration. Actual first authenticated connection còn phải khớp byte-for-byte với policy; chưa được thực thi hosted.

Private policy: `.local/batch6b/user.event-trigger-review.json`; user target có thêm path/hash, có backup. Role/login/password/budget/readiness/approval fields không tự bật; role proposal SQL không đổi.

## Local validation

**16 tests PASS,0 failure/error/skip**, PostgreSQL17.11 trên fresh owned cluster, stopped/preserved:

- 9 unit tests: existing URL/identity/major/approval/project-by-project mapping gates và policy hash/owner/project/V1/status/SECURITY DEFINER rejection.
- 5 canonical role/grant integration cases như follow-up trước; one real JSON inspector case.
- 1 new six-trigger fixture integration: no policy => STOP; correct pinned descriptors => PASS; extra trigger, changed body, owner, enabled state, search_path hoặc ACL => drift STOP. Mỗi mutation thử nghiệm nằm trong rollback transaction ở synthetic local DB, không chạm provider objects.
- Canonical User V1/validate/rerun thực thi thành công cùng synthetic notification watchers bằng dedicated NOSUPERUSER/NOCREATEROLE/NOCREATEDB migrator, không dùng local superuser để che thiếu quyền callback. Đây không phải execution certification cho actual Supabase functions.

Artifact `supabase-runner-1.2.0.jar` và versioned dependency-10.22.0; không trộn folder dependency cũ. One-project setup vẫn yêu cầu5 distinct refs và chặn duplicate configured logins; chỉ cần credentials cho selected owner, không bắt người dùng cấp credentials4 project khác để kiểm riêng User.

Full machine evidence riêng [event-trigger-review-validation](evidence/batch-6b-supabase-local-integration/event-trigger-review-validation-2026-11-15.json). Earlier13-test checkpoints giữ scope lịch sử.

## Security còn lại

Data API bật + public exposed, default postgres/public cấp API-role quyền, dedicated roles chưa tạo. Không migrate bằng postgres, không sửa default ACL của managed creators rộng để vượt gate. Proposed migrator defaults/table grants vẫn cần actual managed capability/security verification.

Vì User project theo notes chỉ dành backend JDBC và không dùng Supabase data endpoints, đề xuất tắt **Enable Data API riêng User** trước schema creation. [Supabase hướng dẫn disable Data API khi app không dùng REST/GraphQL/client libraries](https://supabase.com/docs/guides/api/securing-your-api). Đây là đề xuất concrete settings change cho user thực hiện/phê duyệt; assistant chưa thay Dashboard. Disable API không thay SQL privilege/HTTP denial/role tests còn cần kiểm thực tế. Không coi SQL `pgrst.db_schemas` null là API disabled.

Sau khi xác nhận trạng thái API và authenticated identity/capabilities, mới trình exact User role/security SQL để phê duyệt; canonical V1 có approval riêng sau preflight/security PASS. PUBLIC database ACL còn CONNECT/TEMP, cần scoped managed review; không revoke toàn database mù quáng. Four other owner snapshots/roles/security/Hibernate/workflows chưa được xác minh hosted. Không stage/commit/push/cloud deployment/Batch7.
