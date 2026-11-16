> Historical Batch4 report. **Topology/ID target recommendations superseded by Batch4.1**, 13/11/2026. Current authority: [revision report](BATCH4_1_UUID_AND_SUPABASE_ARCHITECTURE_REVISION_2026-11-13.md) and [ID contract](database-design/ID_STANDARDIZATION_CONTRACT.md). Original snapshot findings/tests below remain as-of Batch4.

# Batch 4 — Canonical Database Design & Supabase Architecture

Ngày: **13/11/2026, Asia/Saigon**. **DONE cho thiết kế; executable SQL freeze còn điều kiện, migrations/Supabase deployment NOT DONE.** Không chạy database hoặc đổi production source/config.

## A. Initial State

Backend branch `test/booking-hotel-gateway-acceptance`, HEAD `8ee230b250863164dd47f83af037711a0ea4a0ef`. Frontend `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`, sạch. [Initial state](evidence/batch-4-database-design/initial-state.json).

Có sẵn IDE compiler/encodings modified, Batch2 PR review deleted, canonical PLAN/AUDIT modified; Batch3 report/evidence và tám Docker schema test/harness files untracked. Giữ nguyên tất cả, không đổi branch/index hoặc overwrite/reset. PLAN/AUDIT chỉ append checkpoint Batch4, giữ nguyên bytes có trước.

Đã đối chiếu canonical PLAN/AUDIT, Batch2 acceptance, Batch3 schema/ownership/history/financial/diff evidence; historical acceptance giữ đúng scope, không tính lại thành tests của Batch4.

## B. Schema Dumps

Năm file thực tế tại `C:/Users/ACER/IdeaProjects/DA1/database-schema-snapshots/`. Đọc toàn bộ nội dung, pg_dump18 plain SQL, người dùng cung cấp nguồn pgAdmin. SHA-256 đầy đủ, path, constraints và limitations: [inventory](evidence/batch-4-database-design/schema-snapshot-inventory.md); [catalog](evidence/batch-4-database-design/snapshot-catalog.json).

| File | Service khớp source/config | Tables | Columns | Explicit indexes |
|---|---|---:|---:|---:|
| checkinn_user_schema.sql | User/Loyalty | 6 | 48 | 1 |
| checkinn_hotel_schema.sql | Hotel/Room/Inventory/content | 16 | 129 | 6 |
| checkinn_booking_schema.sql | Booking/Payment | 3 | 50 | 0 |
| checkinn_revenue_schema.sql | Revenue projections | 2 | 26 | 0 |
| checkinn_regulations_schema.sql | Regulations/snapshots | 2 | 16 | 0 |
| **Tổng** | | **29** | **269** | **7** |

Index count không bao gồm implicit PK/UNIQUE backing indexes. Tables bao gồm User Flyway history structure. No COPY/INSERT, không thấy credential theo heuristic; OWNER TO postgres có mặt, không grants/revokes. Regulations có hai identity sequences. Không user-defined enum/extension/function/trigger/view/materialized view trong supplied dump scope.

Không CREATE DATABASE/\connect identity trong dumps: tên DB nguồn/endpoints không được chứng nhận từ filename. Ownership dựa table contract + datasource default; mọi dump table ở public, không chứng minh deployed isolation. Flyway history không có rows, applied versions/checksums UNKNOWN. Không có Place dump; protected read-only, không claim đối chiếu pgAdmin Place. Auth không có separate datasource theo source, không yêu cầu dump giả.

68 SQL files nhìn thấy trong workspace đã được [phân loại theo provenance](evidence/batch-4-database-design/sql-source-classification.json): dumps, Flyway resources, manual migrations, Docker init, fixture/test-only, legacy/unknown. Không chọn một SQL bất kỳ làm snapshot; không copy dumps vào repository.

## C. Current Schema Analysis

[Full catalog](evidence/batch-4-database-design/snapshot-catalog.json) giữ columns/types/NULL/defaults, inline và ALTER constraints, explicit indexes, TOC objects và identity sequence options. Snapshot là cấu trúc exported, không live/production applied history.

User thiếu commands/reservations so với current code; Hotel thiếu accommodation/revision/audit/catalog/inventory-review/hold command và room description; Booking chỉ ba core tables, thiếu refund/outbox/intents/policy/idempotency metadata; Revenue chỉ hai projections, thiếu immutable cash/receipt/gap infrastructure; Regulations thiếu outbox. Dump-only bootstrap không đủ cho current business contract.

## D. Source Comparison

32 non-Place JPA entities, 32 repository interfaces (ba native-query repositories), 56 query/transaction/JDBC/scheduler/guard source files và 52 business/validation marker files được lập references + hashes. [Source inventory](evidence/batch-4-database-design/source-inventory.json), [coverage](evidence/batch-4-database-design/README.md). Marker inventory hỗ trợ review, không phải runtime query success.

[Discrepancy report](evidence/batch-4-database-design/schema-discrepancy-report.md) phân loại KEEP/MODIFY/REMOVE_CANDIDATE/ADD/NEEDS_DECISION/UNKNOWN, 57 field/table deltas và các constraint/index/history findings riêng, kèm evidence/reason/impact/risk/recommendation.

Các khác biệt quan trọng: DRAFT thiếu trong dump Hotel CHECK; User email verification nullable khác Java; zero-UUID city default không dùng cho fresh design; cities timestamp wall-clock khác Instant; Payment dump thiếu unique VNPay transaction number; source room FK omission khác snapshot; money NUMERIC unbounded khác JPA(38,2); Booking String255 khác loyalty128/HotelReview UUID boundary. Duplicate media CHECK/name btree là fresh consolidation candidates, không DROP legacy. Cột/bảng JDBC-only, audit/receipt/history giữ dù không có entity.

Historical chains không được dùng làm V1: User thiếu core V1, Hotel V2 syntax/V5 duplicate expiry, Booking schema.sql UUID/local users FK mismatch; Batch3 SQL-only năm service FAIL vẫn nguyên kết luận. Không sửa history hoặc suy manual pgAdmin edits khi không có audit trail.

## E. Canonical Architecture

Khuyến nghị **một Supabase project/database + năm service schemas**, với namespace/role/history riêng, không shared business ownership/cross-service FK. So sánh separate databases và multiple schemas trong [architecture](database-design/SUPABASE_DATABASE_ARCHITECTURE.md). Source không chứng minh blocker cần hard isolation, nhưng môi trường/role/network/pool chưa được chứng nhận.

Auth dùng User API/gRPC + Redis; Media object storage với Hotel metadata, không schema riêng. Place ngoài migration scope. Shared database không tạo distributed transaction; giữ RPC, durable intents, transactional outboxes và idempotent consumers.

## F. Canonical Schema

[Canonical V1](database-design/CANONICAL_DATABASE_SCHEMA_V1.md): **46 application/feature tables, 458 columns**, thêm năm Flyway-owned histories do engine tạo.

| Schema | Tables | Ownership |
|---|---:|---|
| user_service | 7 | Identity/profile/OTP/loyalty ledger/commands/reservations |
| hotel_service | 24 | Hotel/catalog/content/room/availability/hold/review/audit/compatibility receipt |
| booking_service | 7 | Booking/items/payment/refund execution/audit/outbox |
| revenue_service | 5 | Latest projections + immutable cash + event receipt/quarantine/gaps |
| regulations_service | 3 | Policies/snapshots/outbox |

Mỗi bảng có source, purpose/lifecycle/invariant, từng column/type/null/default, PK/FK/unique/check/index và workload/write-cost rationale. [ERD](database-design/ERD.md) phân biệt physical service-local FK và logical references; không vẽ cross-service FK. [Machine-readable model](evidence/batch-4-database-design/canonical-model.json).

Giữ String IDs/UUID/bigint theo current contracts; không money floating point, không toàn bộ JSON→JSONB hoặc LocalDateTime→timestamptz. Explicit nullable total_rooms không default10; no oversell vẫn cần transaction locks + counters, không giả cross-table CHECK. Policy STANDARD_24H_V1, full Sandbox Internal Refund, immutable original receipt, CREDIT/DEBIT dedup, loyalty reversal và regulation snapshot/outbox giữ semantics đã nghiệm thu.

## G. Supabase Compatibility

Đã tham khảo official Supabase, pgJDBC và Flyway documentation; links và phân biệt inference nằm trong [architecture](database-design/SUPABASE_DATABASE_ARCHITECTURE.md). Direct/session connectivity, verify-full SSL/CA, restricted schema search_path, currentSchema/default_schema/Flyway alignment, roles/default privileges/sequences, pool budget và deployment order đã thiết kế. Không dựa hard-coded credentials hoặc giả hosted PG18.

Custom business schemas không expose Data API/grant web roles; không adoption Supabase Auth. Accommodation catalog guard cần namespace-hardening/negative probe trong Batch5, không disable. Local fresh DB phải mirror cùng topology/roles và target version; Supabase operational acceptance vẫn pending.

## H. Migration Strategy

[Batch5 plan](database-design/MIGRATION_IMPLEMENTATION_PLAN.md): dedicated exclusive canonical location, full V1 mỗi service, five histories; fresh migrate thật, không baseline marker skip DDL. Preserve historical SQL bytes và exclude chain cũ khỏi canonical profile. Migration job trước runtime JPA validate; SQL-only/replay/catalog/queries/jobs/guards/roles/concurrency/business/restore gates trước cutover.

Seed tách fixtures, không stock/financial history fabrication. Quiesce writers/consumers, kiểm broker routing/checkpoints để old demo events không tràn vào fresh ledger. DB cũ giữ nguyên. Trước new writes có config rollback; sau writes cần freeze/reconciliation/forward fix hoặc isolated restore, không flip ngược gây mất state. Future V2+ expand/contract, không sửa applied V1.

## I. Open Decisions

Chín quyết định NEEDS_DECISION chi tiết trong canonical spec: D01 money precision, D02 exact rate precision/mapping, D03 runtime wall-clock zone/Instant compatibility, D04 Room–Hotel FK/deletion, D05 ID length/type boundaries, D06 target version/network/roles/pool budget, D07 retention/backup/restore, D08 concrete cutover window/config approval, D09 policy seeds/version lifecycle.

D01–D06/D09 chặn SQL freeze; D07 trước live use; D08 chỉ xin duyệt khi có concrete passing cutover artifacts. Không tự quyết định tài chính/API/delete semantics để tuyên bố implementation-ready. Không cần dừng design để người dùng trả lời tất cả; những phần xác minh được đã đặc tả đầy đủ.

## J. Files Changed

- Tạo bốn documents trong `Docs/database-design/` và báo cáo này.
- Tạo reviewed metadata/inventory/diff/coverage/validation dưới `Docs/evidence/batch-4-database-design/`; raw inspection scripts/extracts chỉ dưới ignored `.local/evidence/batch-4-database-design/`.
- Append bounded Batch4 checkpoint vào canonical PLAN/AUDIT; không thay unrelated findings hoặc historical acceptance.

Validation: [design-validation.json](evidence/batch-4-database-design/design-validation.json). **18 static checks PASS** cho coverage/model/FK/hash preservation/local links/Git boundaries. Chỉ static design/source/hash/link/coverage checks; không application/database integration tests, migration execution hoặc Supabase deployment được chạy trong Batch4. Mermaid source được kiểm references; không claim renderer/browser visual acceptance.

## K. Protected Scope

Không sửa production Java/frontend/config/ddl-auto/Flyway settings/credentials/historical migration SQL. Place backend/frontend/showDistance/shared contracts giữ nguyên; frontend vẫn sạch. Không database connection/mutation, không Docker startup/reset/drop/truncate/volume changes. Không stage/commit/push/merge/PR. Existing user changes nguyên trạng ngoài canonical docs append được yêu cầu.

## L. Final Assessment

**Batch4 design DONE với open decisions được ghi rõ. Database standardization PARTIAL; STD-021 IN_PROGRESS, STD-006 physical provenance OPEN.** Không tuyên bố migrations, full historical upgrade, provider settlement hoặc Supabase deployment DONE.

Next: resolve recorded schema/target decisions, implement Batch5 fresh isolated canonical chains và prove acceptance/recovery trước review cutover. Không chạy chain mới trên database hiện tại.
