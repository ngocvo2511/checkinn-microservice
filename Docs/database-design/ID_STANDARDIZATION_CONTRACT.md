# ID Standardization Contract — Batch 4.1

> Current status: Batch5A U01/U03/U04 APPROVED;38 source conversions implemented, PARTIAL acceptance. Earlier pending/proposed statements below record Batch4.1 history; see Batch5A checkpoint at end. No database migration applied.


13/11/2026, Asia/Saigon. **DESIGN PROPOSAL — UUID conversions chưa được duyệt để implementation.** Five-project topology đã được người dùng quyết định; không xin duyệt lại topology. Java/frontend/proto/SQL runtime giữ nguyên trong batch này.

## Current evidence và target

[Inventory](../evidence/batch-4-1-uuid-topology/id-contract-inventory.md) có 167 persisted identity/key/version fields; [impact matrix](../evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md) liệt kê riêng **38 cột đề xuất chuyển sang uuid**, gồm bốn String entity PK và 34 references. 124 protobuf field occurrences, toàn bộ source/FE/test occurrence manifests hỗ trợ đối chiếu; counts không phải runtime tests. [Canonical JSON](../evidence/batch-4-database-design/canonical-model.json) giữ nguyên `java_type`/annotations/source, thêm current/target types, decision và snapshot provenance. `type` nay là proposed target SQL, không là actual deployed catalog.

| Identity category | Current fact | Target design / decision |
|---|---|---|
| User/Hotel entity IDs | UUID Java và PostgreSQL | KEEP UUID; không regenerate IDs hoặc reference values |
| Booking, BookingItem, Payment, RefundRequest PK | String JPA, varchar255; actual generators produce UUID text | PROPOSE UUID Java + uuid SQL; fresh target và coordinated code release, không chứng nhận mọi existing String/fixture UUID |
| Internal FKs to those PKs | Object associations hoặc String/JDBC references | Same uuid storage, associations giữ entity Java type; referenced entity key type là UUID |
| User/Hotel/Booking/Payment references | Có String/varchar255 và Loyalty booking128 | PROPOSE uuid cho typed persisted references, không cross-project FK; không UUID cast từ arbitrary legacy values |
| HotelReview.booking_id | UUID, ReviewEligibilityClient sends string UUID | KEEP UUID; Booking target cùng contract. Giữ guest/hotel/CHECKED_OUT eligibility, uniqueness và optional historical NULL |
| Regulations/snapshot surrogate IDs | Long, bigint identity, repositories Long | KEEP BIGINT exception; public DTO/event dùng regulationKey/version, không surrogate. Không cross-service key benefit đủ bù churn |
| Event IDs | Producer/outbox UUID; source_event_id/last_event_id UUID | KEEP UUID; event_version/source_version vẫn integer/bigint sequence, không đổi thứ tự/dedup |
| Loyalty command_key | bookingId + `|` + EARN/USE/CANCEL | KEEP varchar160. Canonical booking UUID được `.toString()` trước xây command; không UUID hóa structured key |
| RoomHold.command_id | deterministic name UUID from booking-hold-v1:keyHash | KEEP UUID và exact recipe; command identity khác RoomHold entity identity |
| Composite request/code PK | UUID request + varchar64 amenity code | KEEP heterogeneous composite; FK code theo amenity_catalog.code vẫn varchar64 |
| Provider/document/evidence identifiers | VNPay refs, tax/license/identity numbers, external/evidence refs | KEEP opaque strings, kể cả khi một value nhìn giống UUID |
| Policy/catalog/OTP/migration/routing keys | regulation_key, code, voucher, OTP, migration_id, routing_key | KEEP business strings; version/revision vẫn số hoặc policy-version string theo field |

## Generation ownership — không blanket UUID v4

Default cho entity mới không có deterministic identity: application/JPA UUID **v4**, pin generator một nơi trong Batch5A và kiểm actual pinned Hibernate/dependencies. Không đồng thời DB `gen_random_uuid()` DEFAULT và JPA generator. UUID v7 không cần cho batch này; adoption sau phải có workload/dependency/ordering evidence, không gọi v7 là requirement.

**Booking exception bắt buộc giữ:** BookingIdempotency.bookingId dùng `UUID.nameUUIDFromBytes(UTF8("booking-id-v1:" + keyHash))` rồi string, tức deterministic v3. Khi không idempotency key, Booking.onCreate dùng UUID.randomUUID (v4). Hold command dùng domain riêng `booking-hold-v1:` và v3. Target chuyển Java/storage type, **không đổi bytes/prefix/hash/namespace**, không thay deterministic UUID bằng random v4. Accept both existing v3 and v4, không v4-only regex. PostgreSQL uuid storage không tự quyết định generation version.

BookingItem/Payment/RefundRequest hiện JPA GenerationType.UUID trên String; target UUID Java, pin strategy/verify generation. Existing User/Hotel @GeneratedValue UUID và SQL-owned audit/event randomUUID tiếp tục application authority. Regulations identity do PostgreSQL sinh, Java không cấp UUID. References chỉ copy từ owner, không UUID mới cho mỗi retry, import hoặc consumer.

## REST, gRPC, events và frontend

REST UUID vẫn JSON string dạng 36 ký tự `8-4-4-4-12`, serialized lowercase bằng UUID.toString; tên field/URL shape không đổi. Domain entities/repository keys dùng UUID; DTO entity IDs có thể typed UUID, transport adapters xuất text. Association field `BookingItem.booking` vẫn Booking, key UUID; không đổi association thành raw UUID trong JSON model audit. Human subject User UUID text; NextAuth session/user.id giữ TypeScript string.

Protobuf **giữ string wire type và field numbers**, không bytes hoặc tag renumber. Parse strict UUID tại entity/reference adapter, optional empty chỉ khi hiện contract thật sự optional; required empty INVALID_ARGUMENT. Các bản booking.proto/User/Hotel/Auth copied contracts phải compare descriptors và build tất cả clients. Provider callback orderId/transactionId, ratePlanId, regulationKey không đi qua UUID parser. [Exact wire fields/tags](../evidence/batch-4-1-uuid-topology/protobuf-id-contracts.json).

BookingStatusEvent/PaymentEvent JSON IDs tiếp tục string, eventId UUID serialized string; current Java event DTO String có thể giữ tại wire boundary với typed accessors/validation vào domain UUID, không bắt buộc toàn bộ payload Java UUID. Producer/Revenue/Notification consumer phải dùng cùng semantic format. `userId` và `customerId` aliases trong PaymentEvent không bỏ/đổi nghĩa. Outbox id/broker messageId/correlation dùng event UUID text; trace/correlation tokens không default là domain UUID. No event regeneration/re-hashing pending receipts hoặc đổi event_version vì đổi type.

Frontend API clients/types/forms/routes/query keys/storage tiếp tục string. Có thể thêm branded UUIDString và validation tại boundary khi implement; không UUID object/Number/parseInt. BookingAttempt giữ crypto.randomUUID key khi retry cùng payload, rotate khi payload đổi; không tự dùng booking.id thay idempotencyKey. VNPay return query giữ raw parameters/signature/provider refs, không lowercase/cast. Payment return lấy bookingId từ verified response. URL/cache/session identifiers vẫn exact canonical string; logout/session/back-button/stale-state flows cần regression. **Không tìm thấy numeric-ID coercion trong focused scan; lexical scan không thay TypeScript runtime proof.**

## Exceptions có bằng chứng — không được làm mất audit

1. `manual_refund_audit.actor_id`: varchar255, admin UUID text **hoặc SYSTEM_RECOVERY** từ ManualRefundRecovery. Giữ String. Không tạo system UUID giả, không new actor columns trong batch design này.
2. `regulation_snapshots.source_user` và event changedBy: String human/service/system, seed dùng `system`; giữ mixed actor text. JWT service subjects `auth-service`, `booking-service`, `hotel-service`, `user-service` có role riêng; không parse mọi JWT sub thành UUID. Human JWT sub mới UUID.
3. `revenue_event_receipts.booking_id`: nullable TEXT **raw invalid-event evidence**, không typed ledger reference. EventIngestionService.receipt ghi khi source invalid/null; arbitrary booking text phải vẫn ghi quarantine được. Giữ original payload/event_hash/raw booking text, không thay UUID để làm receipt insert fail. Typed accepted projection/cash/gap references đều uuid; receipt raw reference exception được đánh dấu machine-readable, không tuyên bố đồng nhất giả.
4. `transaction_id`: provider receipt có thể VNPay number, HOTEL UUID-looking application reference hoặc `sandbox-refund:<request UUID>`; giữ text, không suy type theo hình dạng. Original payment receipt/VNPay fields giữ nguyên sau internal refund.
5. `booking_items.rate_plan_id`: source BookingService hiện ghi catalogRoom.id; chưa có RatePlan entity/database owner. Giữ varchar255 compatibility alias, **U04 NEEDS_DECISION** về future semantic contract, không tự tạo RatePlan hoặc giả định external UUID PK. Không đổi pricing snapshot.
6. BIGINT Regulations/snapshot IDs và code-based/composite primary keys giữ theo nghiệp vụ; không chuyển chỉ vì có PK.

Booking `inventory_reviewed_by`, refund requested_by/approved_by và execution submitted_by được đề xuất UUID vì production callsites là authenticated human principal; phải giữ nullable fields và audit actor exception ở trên. Batch5A xác minh toàn caller set/test system actor trước freeze, không làm mất valid actor nếu phát hiện thêm callsite.

## Validation và backward compatibility

Required entity/reference IDs: strict canonical UUID shape + parse, accept uppercase input rồi canonical serialize lowercase; không Java UUID.fromString permissive short groups, không trim/sửa arbitrary IDs. Không giới hạn chỉ UUIDv4; reject absent/malformed required IDs trước business side effects. SQL NULL/optional legacy field vẫn NULL theo spec, không zero UUID sentinel hoặc generated replacement. Không tự thêm NOT NULL/checks/business policy bằng UUID audit.

Authenticated malformed REST domain ID → 400 controlled response; authenticated valid-but-missing → existing404; ownership/role denial giữ401/403 contract, không expose resource existence qua validation/order. gRPC malformed required ID → INVALID_ARGUMENT; auth/availability distinctions giữ nguyên. Invalid event IDs → existing durable REVIEW_REQUIRED/quarantine path, không drop/ACK thành công khi chưa durable handling; keep raw receipt types cho malformed source. External/provider/business keys dùng validators riêng.

Fresh database không bỏ qua running compatibility: old stack đang String/varchar không được nhận UUID JDBC binds mới, và new UUID schema không chạy old String bind queries. UUID-vs-varchar equality cần typed bind (`setObject UUID`, getObject UUID, native casts reviewed); tránh blanket `column::text` mất index. Repository derived/JPQL signatures/locks/schedulers/client maps/equality `.equals(UUID)` versus String phải đồng bộ.

Idempotency/security fingerprints là đặc biệt nhạy cảm: requestHash hiện serialize request fields/IDs và exact payload sequence; normalize case/UUID DTO có thể đổi fingerprint. Không rewrite stored hashes/receipts, không đổi command key recipes; golden tests chạy same canonical old/new input và response-loss/retry, uppercase/legacy cases. Mixed old/new hash algorithms cần versioned adapter/dual reader hoặc drain/quiesce boundary; không tự giữ retry an toàn chỉ vì JSON wire vẫn string.

Không in-place SQL cast trên demo hiện tại. Nếu sau này import non-UUID IDs: owner-approved bijective mapping + mọi references/events/cache/audit + invariant/reconciliation evidence, hoặc retain old DB isolated; không new random replacement per row/consumer, không xóa invalid rows để convert thành công. Actual demo value validity chưa kiểm tra vì không truy cập DB/data.

## Approval và staged work

U01: duyệt cụ thể 38 column conversions + generation/exception contract trước Batch5A. U02: transport/error/hash rollout strategy và pending legacy broker events/receipts cần review concrete implementation. U03: fresh cutover/import/no-import + non-UUID demo handling, no data assumptions. U04: ratePlanId compatibility alias remains String, future semantic change riêng. Existing money/time/delete/seed decisions D01–D04/D07/D09 của Batch4 vẫn mở, topology D06 đã five-project confirmed nhưng connectivity/version/pool chưa certified.

Batch5A implement approved contracts/test adapters **không connect old production/demo schema bằng UUID code**. Batch5B tạo fresh per-service PostgreSQL canonical chains + JPA validate/workflow/role tests. Batch6 dùng five existing Supabase projects, staging connectivity/credentials/Flyway/startup/E2E/backup/cutover. Không batch nào đã được thực thi bởi tài liệu này.

## Batch 5A implementation checkpoint — 13/11/2026

U01 approved38 conversions implemented in source (User3 / Booking21 / Revenue14); U03 approved fresh databases without demo import; U04 approved ratePlanId String. U02 evidence: golden legacy/new PASS, eight backend builds PASS (901 tests,39 existing skips), three isolated PostgreSQL contract tests PASS, frontend regression211 PASS. JSON/protobuf strings, Booking/hold UUIDv3 recipes, hashes, provider refs, raw Revenue receipts and domain policies retained.

**Batch5A PARTIAL:** frontend tsc retains baseline protected Place showDistance error; no Place/frontend edits allowed. Full broker/Gateway/multi-node/restart and canonical migration acceptance NOT RUN. Current source mapping is UUID; actual supplied snapshot types and historical migrations remain unchanged. Never start new code against old VARCHAR schema. Native create-only synthetic tests are not canonical Flyway V1/JPA validate acceptance.

Current model preserves batch4_1_* provenance; current source/binding status supersedes earlier pending-U01 language. Historical reports/inventories remain as-of Batch4.1; this checkpoint records the approved implementation. Five independent Supabase projects unchanged. STD-021 IN_PROGRESS, STD-006 OPEN; Batch5B/6 not started. No current DB/Supabase/runtime-config/history/Place or Git index/commit mutations. [Batch5A report](../BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md), [evidence](../evidence/batch-5a-uuid/implementation-inventory.md).
