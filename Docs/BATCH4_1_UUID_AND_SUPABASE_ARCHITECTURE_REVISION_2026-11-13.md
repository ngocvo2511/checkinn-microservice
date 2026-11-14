# Batch 4.1 — UUID Contract Standardization & Five-Project Supabase Architecture

13/11/2026, Asia/Saigon. **DESIGN DONE với các proposals/approval gates được ghi rõ. UUID implementation, Flyway V1 và Supabase deployment NOT DONE.** Five-project topology confirmed by user; project configuration/connectivity chưa được kiểm tra.

## A. Initial State

Backend `test/booking-hotel-gateway-acceptance`, HEAD `8ee230b250863164dd47f83af037711a0ea4a0ef`. Frontend `main`, HEAD `d446b9808f8732c9982da2c156979e245742aa7f`, sạch. [Initial status](evidence/batch-4-1-uuid-topology/initial-state.json).

Có sẵn hai IDE files modified, Batch2 PR review deleted, PLAN/AUDIT modified, Batch3/4 reports/evidence/design và tám schema harness files untracked. Không reset/overwrite work; trước revision lưu SHA và bản thiết kế gốc vào ignored raw evidence. Tìm thấy toàn bộ required documents đúng paths được yêu cầu, đọc và đối chiếu lại source. PLAN/AUDIT chỉ append checkpoint; Batch4 report/evidence README thêm notice historical/superseded, giữ nguyên original bodies.

## B. UUID Audit

[ID inventory](evidence/batch-4-1-uuid-topology/id-contract-inventory.md), [machine-readable contracts](evidence/batch-4-1-uuid-topology/id-contracts.json): **167 persisted ID/reference/key/version fields**, không chỉ PK. Phân loại entity/local FK/logical reference/event/command/business/provider/hash/version/composite/raw/system actor; 38 đề xuất conversion, 129 KEEP.

Source/wire evidence: 905 code files inspected, 667 files có ID symbols/line references, 124 exact protobuf field occurrences, 35 repository declarations (bao gồm ba Place protected), 136 frontend files/227 typed declarations, 254 fixture/seed/harness files. Các counts overlap và không là số business workflows/tests. Lexical scan + reviewed critical workflows không thay runtime acceptance hoặc chứng minh valid values trong actual demo DB.

Đã kiểm current JPA/source SQL và supplied pgAdmin catalog riêng biệt: current_java_type/java_type/annotations/source giữ nguyên; proposed target types không giả làm current source/live database. Snapshot thiếu bảng/cột được ghi ABSENT, không invent actual storage. [Input SHA](evidence/batch-4-1-uuid-topology/design-input-manifest.json).

## C. Proposed Conversions

**38 cột đề xuất uuid, pending user design approval U01:**

| Owner | Count | Proposed fields |
|---|---:|---|
| Booking | 21 | Bốn PK bookings/booking_items/payments/refund_requests; dependent booking/payment/refund/execution/outbox references; user/hotel/hold/room references và human review/approval/submission actor references |
| User/Loyalty | 3 | loyalty_commands.booking_id, points_reservations.booking_id (PK/reference), points_transactions.booking_id |
| Revenue | 14 | Accepted booking/payment/customer/hotel/room references trong projections/cash/gaps, bao gồm gap booking PK/reference |

[Mỗi field/producer/consumer/API/event/repository/query/FE/test/dependency/steps](evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md). Association Java fields giữ Booking/User/entity object, referenced key type UUID; không đổi association thành scalar chỉ để chuẩn hóa.

User/Hotel/current event IDs đã UUID giữ nguyên. Booking generated IDs không toàn v4: idempotency path dùng deterministic UUIDv3 `booking-id-v1:<keyHash>`, hold command domain riêng `booking-hold-v1:<keyHash>`; non-idempotent Booking path v4. Giữ exact recipes, không đổi retry identity hoặc dùng v4-only validator.

## D. Exceptions

- Regulations/snapshot IDs giữ BIGINT/Long identity: internal-only surrogate, public API/events theo regulationKey/version, không lợi ích cross-service đủ bù churn.
- VNPay order/transaction identifiers, transaction_id/HOTEL UUID-looking receipt và `sandbox-refund:<UUID>`, document/evidence refs giữ opaque strings; original receipt không normalize/cast.
- command_key=`bookingId|TYPE`, hashes, policy/catalog/voucher/OTP/migration/routing keys, numeric versions/revisions và composite request/code key giữ semantics. Amenity code FK vẫn varchar64→varchar64.
- manual_refund_audit.actor_id giữ String vì có SYSTEM_RECOVERY; Regulations source_user/changedBy có system/service actors; JWT human UUID sub khác service subject text.
- revenue_event_receipts.booking_id giữ nullable raw TEXT để audit malformed source events; accepted ledger/projection references uuid. Không UUID cast khiến quarantine thất bại hoặc rewrite payload/event_hash.
- rate_plan_id giữ String compatibility alias: server đang ghi catalogRoom.id, chưa có RatePlan owner/entity; U04 để rõ semantic decision, không tự tạo domain mới.

## E. Cross-Service Impact

Booking–HotelReview–Loyalty–Revenue target booking ID thống nhất UUID ở typed storage/domain; wire REST/protobuf/events vẫn strings, field names/tags không đổi. Human User refs, Hotel/Room/Hold refs, Payment/Refund refs đồng bộ; no cross-project FK/transaction.

Frontend tiếp tục TS string cho API/types/routes/forms/state/query keys/storage/NextAuth; no numeric coercion/UUID object. Client idempotencyKey crypto.randomUUID vẫn command key riêng. Provider return query/signature params giữ raw format. Notification consumer và duplicated proto clients nằm trong release/test graph, không chỉ Revenue.

Current String JDBC binds không tự tương thích uuid tables; `.equals(UUID)`/String maps/native predicates/UUID serialization có thể đổi behavior. requestHash/keyHash/lock-name recipes đặc biệt cần golden vectors, không rehash old commands/events. Mixed old/new workers trên cùng old/new schema không được giả safe chỉ vì JSON vẫn string.

Place entity/reference types read-only: city_id UUID và FE string wire giữ compatible shape; no Booking/Payment refs trong Place entity models. Fresh Hotel city UUID datasets phải preserve/align IDs hoặc retain compatible routing/data; mismatch là release blocker, không quyền sửa Place/google/fsq identifiers/nearby/showDistance.

## F. Supabase Architecture

[Five-project architecture](database-design/SUPABASE_DATABASE_ARCHITECTURE.md): User+Loyalty, Hotel, Booking+Payment/Refund, Revenue, Regulations sở hữu **năm projects/database endpoints/credentials/pools/histories/backups độc lập**. Không Auth project; Place ngoài scope. Batch4 one-project/five-schema recommendation explicitly superseded.

Đề xuất public namespace trong mỗi project vì source/SQL/guards hiện tương thích và không cần schema-per-service isolation trong cùng DB. Public chỉ được deploy sau Data API exposure/default grants/RLS/app-role/negative access gates; không mặc định an toàn. Direct/session endpoint theo network/IPv4/IPv6, verify-full SSL/CA, per-project Hikari budgets, independent migration credentials; không hardcode hoặc ghi real URLs/passwords. Official references nằm trong architecture, không kết nối actual projects.

## G. Canonical Schema Changes

[Spec](database-design/CANONICAL_DATABASE_SCHEMA_V1.md), [JSON model](evidence/batch-4-database-design/canonical-model.json), [ERD](database-design/ERD.md) đã revised: vẫn **46 app tables/458 columns**, type changes chính xác 38 cột, provenance/current_java_type/target_java_type/current_sql_type/target_sql_type/snapshot/decision_status/migration_required được tách rõ. Per-project public namespace và references ghi owner, không shared tables.

FK shape/default/NULL/unique/check/index predicates và business constraints giữ nguyên ngoài namespace/type proposal. Không thay no-oversell/stock10 policy, canonical pricing/cancellation, payment retry/VNPay Sandbox, Internal Refund Demo, Revenue CREDIT/DEBIT, loyalty ledger/reversal, Regulations snapshots/outbox hoặc audit lifecycle. Model annotations/current Java vẫn String nơi source đang String.

## H. Migration Plan Changes

[Revised plan](database-design/MIGRATION_IMPLEMENTATION_PLAN.md): **5A** approved UUID code/repository/DTO/adapters/consumer/frontend/tests; **5B** fresh five local PostgreSQL databases, exclusive per-service canonical V1, JPA validate/query/role/workflow/restore; **6** five existing Supabase projects staging connectivity/security/secrets/Flyway/startup/E2E/cutover.

Không stage nào thực hiện trong Batch4.1. Original migrations/history preserved, no old+canonical scan, no baseline marker thay schema creation. Fresh cutover vẫn cần coordinated producers/consumers/queues/pending commands/callback routing/cache, no automatic legacy casts. Old databases giữ nguyên; post-write rollback cần checkpoint/receipt reconciliation, không blind flip hoặc regenerate IDs.

## I. Open Decisions

U01: duyệt cụ thể 38 conversions/generator/exception contract trước Batch5A. U02: concrete hash/wire/error/rolling-or-quiesced strategy và pending event handling. U03: fresh/no-import vs owner-approved legacy mapping; actual demo UUID validity UNKNOWN. U04: ratePlanId compatibility alias future semantics.

Batch4 D01 money/D02 rate/D03 timezone/D04 Room FK/delete/D07 backup/retention/D08 cutover/D09 seeds vẫn mở đúng scope. D06 topology đã được user quyết định five projects, nhưng server versions/network/roles/public security/pool budgets chưa certified. Không cần xin lại approval cho topology đã chốt; chưa bắt đầu implementation để đòi password hoặc deployment approval sớm.

## J. Validation

**32/32 static checks PASS** trong [results](evidence/batch-4-1-uuid-topology/design-validation.json): JSON/schema/model/entity/source/wire/reference/ERD/topology/plan/link/Git-boundary checks; counts/results chi tiết trong artifact. Runtime Java/TypeScript behavior, Flyway/JPA/native SQL tests, Mermaid renderer và Supabase connectivity **NOT RUN**. Static passes không chứng nhận UUID migration đã xong.

Files: ID_STANDARDIZATION_CONTRACT mới; inventory/matrix/manifests/validation/report mới; canonical spec/model/ERD/Supabase architecture/migration plan cập nhật; PLAN/AUDIT append; Batch4 report/evidence README historical notices. Raw scripts/before-design copies giữ ignored `.local/evidence/batch-4-1-uuid-topology/`.

## K. Safety

Không sửa Java/frontend/proto/runtime config/connection strings/ddl-auto/Flyway settings/historical migrations. Không database/Supabase access, SQL execution, role/schema creation, demo data/volume reset/drop/truncate. Place backend/frontend/shared contracts/nearby/showDistance unchanged. Existing work và source hashes được bảo toàn; frontend sạch; no stage/commit/push/merge/PR.

## L. Final Assessment

**Batch4.1 DESIGN DONE; UUID implementation NOT DONE, canonical SQL migration NOT DONE, Supabase deployment NOT DONE.** STD-021 IN_PROGRESS, STD-006 provenance OPEN. Next: user review U01/related contract decisions, then separately authorized Batch5A;5B/6 theo acceptance gates, không automatic deployment.
