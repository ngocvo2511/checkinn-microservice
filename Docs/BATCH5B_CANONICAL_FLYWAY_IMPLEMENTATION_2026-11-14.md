# BATCH 5B — Canonical Flyway V1 và fresh PostgreSQL acceptance

14/11/2026, Asia/Saigon. Chốt bằng chứng 2026-11-14T11:35:56.805645+07:00. **DONE trong phạm vi Batch5B local**, không đồng nghĩa hosted deployment/full product E2E. Không chuyển Batch6.

## A. Initial State

Backend test/booking-hotel-gateway-acceptance / 8ee230b250863164dd47f83af037711a0ea4a0ef; frontend main / d446b9808f8732c9982da2c156979e245742aa7f. Giữ toàn bộ thay đổi Batch5A/IDE/docs/deletion có sẵn; không stage/commit/push/merge/reset. [Snapshot](evidence/batch-5b-canonical-flyway/session-initial-state.json), [initial-state](evidence/batch-5b-canonical-flyway/initial-state.md). Baseline TS2322 showDistance được tái lập trước sửa.

## B. Design Decisions

D01: bounded money38,2 giữ nguyên;5 khoản Loyalty/Revenue historical NUMERIC giữ không giới hạn để không rounding mới. D02 exact unbounded rate, roundtrip0.1234567890123456789. D03 Instant/timestamptz, LocalDateTime/timestamp, LocalDate/date; test JVM/JDBC/sessionUTC, giữ check-in timezone Việt Nam. D04 Room–Hotel logical theo NO_CONSTRAINT/deletion hiện có, không cascade. D09 Java sole seed owner15/0.10/10000/500, V1 không seed policy. [Source rationale](evidence/batch-5b-canonical-flyway/canonical-decision-log.md). D06 hosted/D07 backup/D08 cutover thuộc Batch6.

## C. Canonical Flyway Implementation

Năm V1 tại mỗi service src/main/resources/db/canonical/V1__canonical_schema.sql; public trong5 database độc lập,46 bảng/458 cột,5 history. Đủ SQL-owned outbox/audit/ledger/catalog/inventory-review/guards, Booking sequence và2 Regulations identities. Không extension/function custom/manual prerequisite/replay historical/Hibernate DDL. [Inventory/hash](evidence/batch-5b-canonical-flyway/migration-inventory.md).

Opt-in application-canonical.yml: validate, SQL init never, Flyway runtime disabled. Guarded dedicated job exclusively db/canonical, baseline=false/cleanDisabled/validateOnMigrate/no outOfOrder.4 Flyway deps mới TEST scoped để không auto-activate development runtime. Default application.yml/historical migrations giữ nguyên. Grants ngoài Flyway location, provisioning/runner/runtime tách biệt. [Reproduce](evidence/batch-5b-canonical-flyway/REPRODUCE.md).

## D. Fresh Bootstrap

PostgreSQL18.0/SCRAM/loopback55486/data directory riêng, mỗi retry database mới; không ALTER fix-up/history giả/repair/clean. **Flyway5/5 PASS và rerun no-op5/5**. [Targets/commands/times](evidence/batch-5b-canonical-flyway/fresh-bootstrap-results.json).

User7/59, Hotel24/188, Booking7/118, Revenue5/66, Regulations3/27 (bảng/cột). Catalog kiểm từng column/type/nullability/default/identity, constraints/validated state/indexes/sequences/history checksum; full expressions/predicates trong JSON. PG18 NOT NULL kind n được kiểm qua từng attnotnull. [Catalog PASS5/5](evidence/batch-5b-canonical-flyway/catalog-verification.md).

## E. Hibernate Validation

**PASS5/5**,32 entities trên Flyway schema bằng runtime role không DDL. Packaged Spring Boot5 apps chạy thật cùng RabbitMQ/Redis riêng health UP5/5; restart cùng dữ liệu UP5/5. Startup guards enabled; không create/update/create-drop. [Evidence](evidence/batch-5b-canonical-flyway/hibernate-validation-results.md).

## F. Repository/JDBC Acceptance

32 repositories được initialize/validate/count; representative UUID association/binds/locks/native queries/guards PASS. Phát hiện findByRoomTypeId(String) không tương thích field UUID; sửa tối thiểu tham số UUID, không caller, thêm real query assertion. Không đụng thay đổi Batch5A khác. Canonical PostgreSQL5 tests PASS,0 skip/failure/error. [Chi tiết](evidence/batch-5b-canonical-flyway/repository-jdbc-results.md).

## G. Business Workflow Acceptance

8 module package PASS;901 regression tests executed/47 opt-in skips/0 errors/failures.5 canonical tests chạy riêng PASS. Frontend3 component+211 regression PASS.

Real5-service HTTP **30 checks PASS**: booking có điểm, Hotel hold/User reservation theo rate Regulations, retry deterministic UUIDv3/one item, HOTEL payment/inventory commit, duplicate receipt/one CREDIT qua RabbitMQ, Loyalty commit, reviewed cancellation/release/reversal, refund ADMIN approval,4 concurrent internal executions+retry/one execution/audit/DEBIT, receipt fields giữ nguyên/zero net cash. Native2-transaction Hotel no-oversell PASS.

Real broker outage/restart **14 checks PASS**: source/snapshot/outbox PENDING khi broker tắt, restart5 JVMs/broker, pending delivery, không duplicate seeds, replay cùng event2 lần không thêm DEBIT/execution. Unit/mock bổ sung VNPay retry/expiry/callback, compensation/recovery, rejection, report/gaps, review/OTP/profile. [Phân loại/kết quả](evidence/batch-5b-canonical-flyway/business-workflow-results.md).

## H. Security

Local PASS5/5,47 negative permission checks. Owner/migrator/app riêng; runtime không superuser/createdb/createrole/bypassrls; revoke PUBLIC CREATE và DB CONNECT/TEMP. CREATE/ALTER/DROP/history denied; actual JDBC cross-service/maintenance CONNECT denied SQLSTATE42501. Append-only commands/ledger/audit/receipts/snapshots deny UPDATE/DELETE. [Evidence](evidence/batch-5b-canonical-flyway/security-role-results.md). Hosted Data API/RLS/managed roles phải kiểm Batch6.

## I. Frontend Place Fix

Chỉ production PlaceCard.tsx: optional showDistance/default=true; false ẩn badge time/distance. Giữ intended caller NearbyPlacesSection, directions/search/calculation/styles/routing/Place backend. Typecheck PASS;3 real-render tests/default/true/false PASS;211 regression PASS; lint0 errors/2 warnings cũ. [Root cause/tests](evidence/batch-5b-canonical-flyway/frontend-place-fix-results.md).

## J. Remaining Risks

[Đầy đủ](evidence/batch-5b-canonical-flyway/remaining-risks.md). Supabase/D06 hosted/D07/D08 NOT VERIFIED. Flyway10.10 warns PG18 newer than certified16: local PASS không là vendor certification. Mixed JVM zones, multi-node seed/recovery, Gateway/browser/SMTP/Media/provider/full positive Review→Booking gRPC chưa chứng minh. VNPay callback/retry/expiry chỉ unit/mock; không real refund. Baseline mapper nullable room amenities không nhận NULL, normal fixture dùng[], không mở rộng mapper/business scope. STD-006 stock provenance OPEN, không default10/import/guess.

## K. Safety

Không kết nối/reset/migrate demo DB/Supabase; không sửa Place backend/historical migrations/default application.yml/business rules; không stage/commit/push/merge/reset. [Protected hash proof](evidence/batch-5b-canonical-flyway/protected-scope-results.json). Đã dừng toàn bộ JVM/broker/Redis/cluster test; kiểm tra cuối 0 owned listeners/0 owned JVMs,2 containers exited, test data giữ lại và PostgreSQL5432 vẫn chạy ([cleanup proof](evidence/batch-5b-canonical-flyway/cleanup-results.json)); Windows shutdown theo yêu cầu người dùng sau hoàn tất. Không rewrite historical reports.

## L. Final Status

**DONE — local Batch5B canonical implementation/acceptance.** Bootstrap/catalog/Hibernate/repository/guards/rerun/security5/5, critical regression+selected real workflows/restart, frontend targeted contract/tsc/tests/lint và documentation/evidence đầy đủ. Không khẳng định toàn product E2E/distributed failover hoặc hosted acceptance; Batch6 chưa bắt đầu.
