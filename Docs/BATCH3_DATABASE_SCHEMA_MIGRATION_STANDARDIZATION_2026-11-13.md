# Batch 3 — Database Schema, Migration History & Legacy Data Standardization

Ngày hoàn thành: **13/11/2026, Asia/Saigon**. **DONE cho audit/verification trong phạm vi được truy cập; database standardization tổng thể PARTIAL / IN_PROGRESS. Production applied history: NOT VERIFIED.**

## A. Initial State

Backend branch test/booking-hotel-gateway-acceptance, HEAD 8ee230b250863164dd47f83af037711a0ea4a0ef. Có sẵn .idea/compiler.xml, .idea/encodings.xml modified và Docs/BATCH2_PR_REVIEW_2026-11-13.md deleted; không overwrite/reset. Frontend main sạch, HEAD d446b9808f8732c9982da2c156979e245742aa7f. Không staged changes ban đầu. [Snapshot](evidence/batch-3-schema/initial-state.json).

Đã đọc canonical PLAN/AUDIT, Batch1.6/1.7/2 và Batch2 schema evidence. SQL ngoài repository/root snapshots giữ nguyên; không coi artifacts Batch2 là Batch3. .local/ đã ignored, không cần đổi .gitignore.

## B. Schema Source Inventory

[Manifest](evidence/batch-3-schema/schema-source-manifest.md) inventory 131 sources (SQL, entity, runtime/Compose config, harness), per-file SHA-256, object/prerequisite, target/ownership, execution, replay status, DDL/DML/risk và production UNKNOWN. [Ownership](evidence/batch-3-schema/service-schema-ownership.md): User/Loyalty chung checkinn_user, Auth không có DB riêng; Hotel sở hữu Room/availability/media metadata; Booking sở hữu Payment/Refund; Revenue và Regulations DB riêng theo config defaults. Runtime overrides không tự chứng minh deployment isolation. Place metadata-only.

User có Flyway V2–V5 nhưng không V1; Hotel/Booking có migration directory không được tự coi Flyway-enabled. Các service còn lại kết hợp JPA update và SQL thủ công. Không có single authoritative full clean entrypoint. Root migration_add_total_rooms.sql actively backfills NULL=10; không execute/sửa.

## C. Applied History

[Applied-history manifest](evidence/batch-3-schema/applied-history-manifest.md) phân biệt SOURCE_PRESENT, HISTORY_CONFIRMED, SCHEMA_MATCHED, TEST_ONLY, UNKNOWN và CONFLICT. Fresh psql receipts và User Flyway checksums chỉ xác nhận Batch3 disposable. Baseline marker 1 không chứng minh actual V1. Production/schema history ngoài disposable NOT VERIFIED; không kết nối để cố điền manifest.

## D. Clean Bootstrap

Project mới **checkinn-schema-smoke**, PostgreSQL18-alpine phù hợp Batch2, named volume schema-data, internal network schema, no host ports/shared mounts. Kiểm initial collision, mount và project volume labels; giữ nguyên Booking/Refund stacks. [Ownership evidence](evidence/batch-3-schema/environment.json).

Current defaults thử trên empty DB: User fail Flyway missing users; Hotel JPA tạo core rồi ApplicationRunner guard fail missing migration receipt. Booking/Revenue/Regulations có core tables nhưng thiếu SQL feature objects; Started banner không chứng minh complete schema.

Sau **TEST_ONLY** explicit SQL order và User minimal Batch2 baseline + JPA update override: năm services **PASS JPA/startup checks** với existing recorded images, đợi qua ApplicationRunner guards. [Final startup](evidence/batch-3-schema/startup-recheck-results.json), [final catalog](evidence/batch-3-schema/testonly-final-schema-manifest.json). Không live broker/Redis/Gateway; image-source correspondence không newly certified. No all-service validate conversion.

## E. Migration-Only Bootstrap

[Fresh results](evidence/batch-3-schema/migration-only-results.json): **5 FAIL**, đều không dùng Hibernate:

| Service | Entrypoint | Failure |
|---|---|---|
| User | V2 | Missing users / V1 baseline |
| Hotel | V1 | Missing hotels prerequisite |
| Booking | schema.sql | Missing local users FK target, trái current service ownership; UUID baseline khác current String IDs |
| Revenue | event receipts SQL | Missing payment_records/booking_status_records |
| Regulations | event outbox SQL | Missing regulations |

Tách khỏi negative-regression assertions: những assertions xác nhận expected failure có thể PASS, nhưng migration-only service acceptance vẫn FAIL. Hotel V2 syntax ADD CONSTRAINT IF NOT EXISTS và V5 duplicate expires_at cũng reproduced trên PostgreSQL. Không sửa lịch sử đã có khả năng applied.

## F. Legacy Upgrade

Cash baseline **SOURCE-DERIVED HISTORICAL BASELINE**: original cash SQL git ad58020d9995530d04085c3cb518e261e1b8d1d7; additive nullable execution_mode tại 332f97a. Fresh DB seed synthetic CREDIT, snapshot trước/sau, apply/replay upgrade, original receipt fields unchanged, new mode NULL, checks/dedup/rollback PASS. Không coi đây là full Revenue release baseline.

Hotel minimal hotels table, refund minimal references và loyalty minimal ledger là **SYNTHETIC TEST BASELINES** dựa trên existing test prerequisites; chỉ compatibility probes. Không có verified historical full release schema, full Booking/Payment/Refund/Hotel/loyalty upgrade **NOT VERIFIED**. Upgrade incompatible synthetic refund status bị từ chối và rollback toàn DDL/data; Hotel V2 nontransactional có partial columns sau lỗi, không blind replay. Không copy real data. [Probe catalogs](evidence/batch-3-schema/legacy-probe-schema-manifest.json).

## G. Schema Diff

[Diff report](evidence/batch-3-schema/schema-diff-report.md): cash clean-vs-upgrade **MATCH** exact normalized catalogs. Initial JPA-only vs explicit test bootstrap **332 object differences**, chi tiết deterministic JSON (columns/constraints/indexes/tables/sequences/extensions), không mặc định mọi difference là bug. Full historical clean-vs-upgrade **UNKNOWN** do thiếu representative baseline. Test-only accommodation không thay production migration.

## H. Inventory Provenance

[Review](evidence/batch-3-schema/inventory-provenance-review.md) phân loại Explicitly configured, Reviewed/approved, Legacy default suspected, Unknown provenance, Invalid/inconsistent, Requires manual review. Root NULL→10 script là risk có bằng chứng, nhưng 10 không tự là sai. Synthetic NULL/10/-1 giữ nguyên sau review SQL, no proposals/approvals fabricated. Không thay total_rooms/counters/holds. Actual stock phải OWNER source + ADMIN review; STD-006 không DONE.

## I. Financial Schema

[Review](evidence/batch-3-schema/financial-schema-review.md): Payment unique VNPay IDs; request payment uniqueness; refund execution reference FKs/positive money/evidence/VND checks; immutable cash unique event + payment-kind; outbox ordering/indexes; loyalty command dedup/legacy guard indexes. **10 fresh financial assertions PASS**, thêm cash checks trong compatibility probes. Historical cash gaps/provider outcome không thể suy từ latest projection; không backfill DEBIT hoặc sửa statuses. Internal demo không là provider settlement.

## J. Encoding Compatibility

[Review](evidence/batch-3-schema/encoding-compatibility-review.md): scalar text mapper, room description, regulation serializer và raw SQL JSONB string/object/array/null/nested-null/Unicode/quoted/newline PASS. timestamptz +07/UTC equality PASS; không tự convert LocalDateTime history. Native JPA roundtrip mọi legacy shape/media provider và real historical corpus NOT VERIFIED. Giữ adapters; không mass rewrite JSON.

## K. Changes Made

Chỉ scripts/Compose test-only, schema/history/catalog/result manifests và docs. Không production Java/frontend/migration source changes. [Migration roadmap](evidence/batch-3-schema/migration-strategy.md): owner-approved history → sanctioned baseline → additive versioned changes → clean+legacy+runtime gates → controlled deployment → gradual removal JPA update. Không rewrite existing migrations hoặc all-service Flyway conversion.

Harness fixes: reserved current_user database name; Windows text-pipe newline conversion (byte UTF-8 fix); stale retained Docker startup markers (fresh --since recheck). Raw failed/intermediate attempts được giữ ignored, không counted application defects. Final results là reviewed JSON được chỉ rõ.

## L. Regression Results

| Group | Executed PASS | FAIL | SKIPPED | Scope |
|---|---:|---:|---:|---|
| Hotel focused Maven |22|0|0|Mapper/description/inventory review/counter guards |
| Regulation Maven |4|0|0|Consistency + snapshot serializer |
| Compatibility PostgreSQL |14|0|0|Includes expected-defect assertions; not migration acceptance |
| Financial PostgreSQL |10|0|0|Refund/loyalty constraints/FK/rollback |
| Final startup checks |5|0|0|Existing images, no external dependencies acceptance |
| Published SQL-only service chains |0|5|0|Missing baselines/prerequisites |

Source-only review không gọi test PASS. **Full historical release upgrade NOT VERIFIED; fresh Booking/Hotel Gateway integration NOT RUN** (Batch2 evidence retained as historical), provider settlement NOT VERIFIED, full Maven/frontend/Place suites NOT RUN. [Fresh Maven totals](evidence/batch-3-schema/regression-results.json). Không cộng skips hoặc assertions thành independent workflows.

## M. Protected Scope

Không sửa Place backend/frontend/showDistance/shared contracts. Booking lifecycle/cancellation/refund/internal demo/revenue/loyalty/inventory policy giữ nguyên. Chỉ Batch3-owned disposable PostgreSQL mutation; không host/developer/shared/production DB, không sửa Booking/Refund volume, không global prune/drop/truncate dữ liệu thật. Không stage/commit/push/merge/PR. App containers Batch3 stopped; PostgreSQL volume giữ để review.

## N. Final Assessment

**Batch3 audit/verification DONE; migration/deployment readiness PARTIAL.** STD-021 IN_PROGRESS, STD-006 physical provenance OPEN; historical STD-024 acceptance NOT VERIFIED, giữ nguyên selected codec fixes đã verified. STD-007/027/028/029 dependencies không tự đóng. Canonical PLAN/AUDIT cập nhật, Batch1–2 history giữ nguyên.

Next workstream: **G2 follow-up — sanitized owner-confirmed applied history + full representative service baseline and versioned remediation proposal**, ưu tiên User missing V1, Hotel chain defects và Booking baseline ownership/types; physical stock/cash/encoding review theo evidence, không guessed repairs. [Reproduce](evidence/batch-3-schema/REPRODUCE.md), [evidence index](evidence/batch-3-schema/README.md).
