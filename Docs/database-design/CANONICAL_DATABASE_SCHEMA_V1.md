# Canonical Database Schema V1 — Revised Batch 4.1

> Current: Batch5B local canonical acceptance DONE (14/11/2026); see appended checkpoint. Older statuses are dated history. Supabase/Batch6 not executed.

> Current status: Batch5A U01/U03/U04 APPROVED;38 source conversions implemented, PARTIAL acceptance. Earlier pending/proposed statements below record Batch4.1 history; see Batch5A checkpoint at end. No database migration applied.


13/11/2026, Asia/Saigon. **DESIGN REVISION; UUID conversions PROPOSED_PENDING_USER_APPROVAL, topology FIVE PROJECTS APPROVED BY USER.** Implementation/migration/deployment NOT DONE. [ID standard](ID_STANDARDIZATION_CONTRACT.md), [inventory](../evidence/batch-4-1-uuid-topology/id-contract-inventory.md), [impact matrix](../evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md). Source Java types in tables remain current; proposed SQL types are future V1, not live catalog facts. Batch4 one-project/five-schema direction superseded.

## 1. Architecture và topology

**Five independent Supabase projects**, one PostgreSQL database per owner: P_USER, P_HOTEL, P_BOOKING, P_REVENUE, P_REGULATIONS (symbolic aliases, not real URLs/project refs). User includes Loyalty; Booking includes Payment/Refund. Independent endpoints/credentials/pools/Flyway histories/backups. Namespace proposal **public in each owned database**, conditional on Data API/least-privilege/security acceptance, not public safe by default. Local Batch5B five fresh databases, can share disposable PostgreSQL server; never one DB/five service schemas as target.

Auth has no separate application datasource, uses User APIs/gRPC and Redis sessions. Media storage + Hotel metadata ownership stays, no sixth project or storage relink assumed. Place default checkinn_place metadata only, protected outside design conversion/deployment; no Place snapshot. Config defaults currently point five separate local database names; no claim about deployed targets. Five projects exist per user, configuration/connectivity still NOT VERIFIED. [Architecture](SUPABASE_DATABASE_ARCHITECTURE.md).

Cross-service references follow owner UUID semantic contract, **no physical cross-project FK, no cross-project transaction/SQL joins/FDW/dblink**. APIs/gRPC/RabbitMQ/outbox/idempotent consumers remain integration boundaries. Ledger/projection/audit ownership unchanged by ID typing.

## 2. Service/project ownership

| Owner / project alias | Namespace | Tables / domains | Integration |
|---|---|---|---|
| User / P_USER | public | 7: users/profile/OTP/loyalty balance/ledger/commands/reservations | Auth/User APIs; Booking points commands/recovery |
| Hotel / P_HOTEL | public | 24: geography/hotel/room/hold/availability/content/review/catalog/inventory/approval audit | Booking/Media APIs/gRPC; User actor logical IDs |
| Booking / P_BOOKING | public | 7: booking/items/payment/refund/execution/audit/outbox | Hotel/User RPC, VNPay callback, Revenue/Notification events |
| Revenue / P_REVENUE | public | 5: projections/cash/receipts/gaps | RabbitMQ events, Hotel owner RPC |
| Regulations / P_REGULATIONS | public | 3: policy/snapshots/outbox | Policy APIs/event consumers |
| Tooling | each database public | Flyway creates its own history table | Independent per-service migrations |
| Place | existing protected namespace/database | Not migrated or changed | Hotel city UUID remains compatible; data-set alignment gate |

## 3. Naming và data types

Lower snake_case/stable current table/field names, no field/tag/API rename. Proposed business PKs native uuid with UUID domain/repository IDs. Current Booking/Item/Payment/Refund String→proposed UUID, current source not changed. Hotel/User UUID retained. Cross-service Booking IDs now proposed uuid in Loyalty/Revenue and same HotelReview UUID, replacing former varchar128/255 mismatch. [38 exact conversions](../evidence/batch-4-1-uuid-topology/id-migration-impact-matrix.md) require design approval. Java relationship fields remain entity objects with UUID PKs, not replaced by raw UUIDs.

Application/JPA v4 default for random entity IDs; **Booking and Hold deterministic command/idempotency paths retain UUIDv3 recipes**, validate compatible UUID formats, not v4-only. No conflicting DB UUID default; copied references never regenerated. REST JSON/protobuf/event wire entity IDs remain canonical strings; no proto wire/tag change, TypeScript remains string. BIGINT Regulations/snapshot identity retained: internal Long repositories, public key/version contracts do not expose surrogate. Code/composite/business/provider keys remain text/numeric semantics. Mixed audit actor/system references and invalid-event receipt raw booking text are explicit exceptions, not failed UUID standardization. See ID contract for U01–U04.

Money/rate rules unchanged: JPA amounts numeric(38,2), proposed bounded SQL-owned amounts D01 pending exact rounding/golden tests; rate numeric with D02 explicit precision contract pending, not scale2 money. No floating-point money. Rating numeric/detailed double coordinates retained. DATE for stay dates, LocalDateTime TIMESTAMP WITHOUT TIME ZONE, Instant TIMESTAMPTZ; Booking wall-clock/JVM/JDBC time D03 and city Instant mismatch remain, no timezone rewrite from UUID audit.

TEXT policy/description/snapshot preserved; JSONB address/capacity/amenities/outboxes/receipts retains serializer compatibility, no blanket conversion or object-only check. VARCHAR+CHECK enums, version/revision numeric; no new PostgreSQL enum type. Java initializer is not SQL DEFAULT. Default — means no DB default; nullable SQL fields keep NULL, no UUID zero sentinel. Regulations bigint GENERATED BY DEFAULT AS IDENTITY, no UUID conversion proposed.

## 4. Constraints, indexes và history

All physical REFERENCES in each table below are within that **same project/database public schema**. Names public.bookings/public.hotels are not accessible from another project's service via SQL. UUID FK storage matches owner PK; amenity request_codes.code FK stays varchar64 to amenity_catalog.code, composite PK retained. Scalar logical/polymorphic/audit targets do not acquire FK. Room–Hotel physical FK/delete D04 still open, preserve source logical omission pending decision; no cascade/constraint/policy redesign.

Keep guard names hotels_accommodation_type_allowed/accommodation_change_one_pending and compatibility receipt after full objects. PK/UNIQUE backing indexes created once; secondary indexes below retain Batch4 workloads/overhead, only namespace and key physical type revised. Index predicates/dedup/status/financial checks unchanged. Every index adds WAL/storage/maintenance on key/predicate writes; measure retries/due queues before adding indexes. Availability unique(room_type_id,date) supports date range, no redundant GiST/GIN assumptions. No cross-table CHECK for stock; current transactions/room locks still enforce no oversell.

## 5. Complete table specifications

**46 app tables / 458 columns**, plus five engine-managed public.flyway_schema_history tables in five independent databases. Sources and per-table purpose/lifecycle/invariants retained from Batch4. Column rows show target SQL and current→target Java/storage context, so Batch5A current UUID source is distinguished from unchanged historical SQL/snapshot types. [Machine-readable model](../evidence/batch-4-database-design/canonical-model.json) retains batch4_proposed_sql_type, java_type/annotations/source, snapshot state, reference contracts, current/target fields and decision_status.

### P_USER — independent database / public


#### `P_USER / public.loyalty_commands`

Receipt command EARN/USE/CANCEL, kết quả retry bất biến; PK command_key và unique booking/type dedup. Không tự tạo FK Booking.

Source: [LoyaltyCommand.java](../../user-service/src/main/java/com/example/userservice/model/LoyaltyCommand.java), [V3__add_loyalty_commands.sql](../../user-service/src/main/resources/db/migration/V3__add_loyalty_commands.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `command_key` | `varchar(160)` | NO | — | commandKey; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `command_type` | `varchar(16)` | NO | — | commandType |
| `user_id` | `uuid` | NO | — | userId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `input_amount` | `numeric(38,2)` | YES | — | inputAmount; NEEDS_DECISION D01: proposed numeric(38,2) matches JPA; historical SQL numeric is unbounded; rate uses D02 |
| `input_points` | `bigint` | YES | — | inputPoints |
| `result_amount` | `numeric(38,2)` | YES | — | resultAmount; NEEDS_DECISION D01: proposed numeric(38,2) matches JPA; historical SQL numeric is unbounded; rate uses D02 |
| `result_points` | `bigint` | NO | — | resultPoints |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `UNIQUE (booking_id, command_type)`
- `PRIMARY KEY (command_key)`
- `CHECK (command_type::text = ANY (ARRAY['EARN'::character varying, 'USE'::character varying, 'CANCEL'::character varying]::text[]))`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_USER / public.loyalty_points`

Projection số dư điểm theo user; nguồn lịch sử là points_transactions/commands/reservations. Giữ khả năng phát hiện nhiều tài khoản legacy, không tự unique user_id.

Source: [LoyaltyPoints.java](../../user-service/src/main/java/com/example/userservice/model/LoyaltyPoints.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `user_id` | `uuid` | NO | — | userId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `total_points` | `bigint` | NO | — | totalPoints |
| `used_points` | `bigint` | NO | — | usedPoints |
| `available_points` | `bigint` | NO | — | availablePoints |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) without time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED loyalty_points_user ON public.loyalty_points (user_id)` — LoyaltyPointsRepository.findAllByUserId; preserve legacy ambiguity detection; intentionally nonunique. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_USER / public.otps`

OTP và số lần thử theo email; bản ghi ngắn hạn, lock latest OTP khi xác minh/reset. Không seed mã thực, retention cần policy riêng.

Source: [Otp.java](../../user-service/src/main/java/com/example/userservice/model/Otp.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `email` | `varchar(255)` | NO | — | email |
| `otp_code` | `varchar(255)` | NO | — | otpCode; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `expiry_time` | `timestamp(6) without time zone` | NO | — | expiryTime |
| `is_verified` | `boolean` | NO | — | isVerified |
| `attempt_count` | `integer` | NO | — | attemptCount |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED otps_email_created ON public.otps (email, created_at DESC)` — OtpRepository.findAllForUpdate/latest email; bounded account OTP lookup. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_USER / public.points_reservations`

Điểm HELD trước thanh toán, COMMITTED/CANCELLED khi reconcile; deadline và next_check_at cho recovery. Không cộng/trừ điểm hai lần.

Source: [PointsReservation.java](../../user-service/src/main/java/com/example/userservice/model/PointsReservation.java), [V4__add_points_reservations.sql](../../user-service/src/main/resources/db/migration/V4__add_points_reservations.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `user_id` | `uuid` | NO | — | userId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `requested_points` | `bigint` | NO | — | requestedPoints |
| `total_amount` | `numeric(38,2)` | NO | — | totalAmount; NEEDS_DECISION D01: proposed numeric(38,2) matches JPA; historical SQL numeric is unbounded; rate uses D02 |
| `points` | `bigint` | NO | — | points |
| `discount_amount` | `numeric(38,2)` | NO | — | discountAmount; NEEDS_DECISION D01: proposed numeric(38,2) matches JPA; historical SQL numeric is unbounded; rate uses D02 |
| `redemption_rate` | `numeric` | NO | — | redemptionRate; NEEDS_DECISION D02: preserve exact positive rate; bounded precision/scale must follow rate contract, never numeric(38,2) by accident |
| `state` | `varchar(16)` | NO | — | state; CHECK values HELD, COMMITTED, CANCELLED |
| `hold_expires_at` | `timestamp(6) with time zone` | NO | — | holdExpiresAt |
| `next_check_at` | `timestamp(6) with time zone` | NO | — | nextCheckAt |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (discount_amount >= 0::numeric)`
- `PRIMARY KEY (booking_id)`
- `CHECK (points >= 0)`
- `CHECK (redemption_rate > 0::numeric)`
- `CHECK (state IN ('HELD','COMMITTED','CANCELLED'))`

Indexes / workload:

- `CREATE INDEX points_reservations_held_user ON public.points_reservations USING btree (user_id) WHERE ((state)::text = 'HELD'::text)` — HELD points sum/recovery due scan; predicate limits active working set. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX points_reservations_recovery ON public.points_reservations USING btree (next_check_at) WHERE ((state)::text = 'HELD'::text)` — HELD points sum/recovery due scan; predicate limits active working set. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_USER / public.points_transactions`

Ledger EARN/USE/reversal theo booking, giữ signed points và historical compatibility. Không thêm unique booking/type vì reversal/legacy cần phân tích.

Source: [PointsTransaction.java](../../user-service/src/main/java/com/example/userservice/model/PointsTransaction.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `user_id` | `uuid` | NO | — | userId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `transaction_type` | `varchar(255)` | NO | — | transactionType |
| `points` | `bigint` | NO | — | points |
| `booking_id` | `uuid` | YES | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `description` | `varchar(500)` | NO | — | description |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `CREATE INDEX points_transactions_guard_booking ON public.points_transactions USING btree (booking_id, transaction_type)` — PointsTransactionRepository native ambiguous legacy EARN/USE detection, no forced ledger uniqueness. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX points_transactions_guard_user ON public.points_transactions USING btree (user_id, booking_id, transaction_type)` — PointsTransactionRepository native ambiguous legacy EARN/USE detection, no forced ledger uniqueness. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED points_transactions_user_created ON public.points_transactions (user_id, created_at DESC)` — PointsTransactionRepository user history. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lịch sử append; không tự xóa/sửa hoặc cascade từ actor/business record. Recovery dùng receipt cũ; retention/archive cần policy riêng.


#### `P_USER / public.users`

Account identity, password hash, one application role và trạng thái xác minh/active. Auth đọc qua User API/gRPC; không liên kết Supabase auth.users.

Source: [User.java](../../user-service/src/main/java/com/example/userservice/model/User.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `username` | `varchar(50)` | NO | — | username |
| `email` | `varchar(254)` | NO | — | email |
| `password` | `varchar(100)` | NO | — | password |
| `role` | `varchar(20)` | NO | — | role; CHECK values ADMIN, STAFF, CUSTOMER, OWNER |
| `is_email_verified` | `boolean` | NO | — | isEmailVerified |
| `is_active` | `boolean` | NO | — | isActive |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `UNIQUE (email)`
- `PRIMARY KEY (id)`
- `UNIQUE (username)`
- `CHECK (role IN ('ADMIN','STAFF','CUSTOMER','OWNER'))`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_USER / public.user_profiles`

Hồ sơ one-to-one của User; liên kết optional loyalty account. Xóa theo lifecycle service, không cascade lịch sử tài chính.

Source: [UserProfile.java](../../user-service/src/main/java/com/example/userservice/model/UserProfile.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `full_name` | `varchar(100)` | NO | — | fullName |
| `gender` | `varchar(255)` | YES | — | gender |
| `birth_date` | `date` | YES | — | birthDate |
| `phone` | `varchar(255)` | YES | — | phone |
| `address` | `varchar(255)` | YES | — | address |
| `country` | `varchar(255)` | YES | — | country |
| `user_id` | `uuid` | NO | — | user; current Java User → target User; KEEP_EXISTING_CONTRACT |
| `loyalty_points_id` | `uuid` | YES | — | loyaltyPoints; current Java LoyaltyPoints → target LoyaltyPoints; KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (loyalty_points_id) REFERENCES loyalty_points(id)`
- `FOREIGN KEY (user_id) REFERENCES users(id)`
- `UNIQUE (loyalty_points_id)`
- `UNIQUE (user_id)`
- `PRIMARY KEY (id)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


### P_HOTEL — independent database / public


#### `P_HOTEL / public.amenity_category_types`

Danh mục loại tiện nghi legacy, name unique; vẫn có endpoint/repository, KEEP dù không có quan hệ FK tới raw category.

Source: [CategoryType.java](../../hotel-service/src/main/java/com/example/hotelservice/Amenity/entity/CategoryType.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (name)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_amenities`

Raw tiện nghi trong category; giữ title phục vụ legacy reads và mapping snapshot, không tự thay bằng code catalog.

Source: [HotelAmenity.java](../../hotel-service/src/main/java/com/example/hotelservice/Amenity/entity/HotelAmenity.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `category_id` | `uuid` | NO | — | category; current Java HotelAmenityCategory → target HotelAmenityCategory; KEEP_EXISTING_CONTRACT |
| `title` | `varchar(255)` | NO | — | title |
| `is_active` | `boolean` | NO | — | isActive |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (category_id) REFERENCES hotel_amenity_categories(id)`
- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED hotel_amenities_category ON public.hotel_amenities (category_id)` — HotelAmenityRepository category hydration. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_amenity_categories`

Raw nhóm tiện nghi của hotel; scalar hotel_id, lịch sử mapping tách rời và không bị xóa theo raw replacement.

Source: [HotelAmenityCategory.java](../../hotel-service/src/main/java/com/example/hotelservice/Amenity/entity/HotelAmenityCategory.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotelId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `title` | `varchar(255)` | NO | — | title |
| `is_active` | `boolean` | NO | — | isActive |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED amenity_categories_hotel ON public.hotel_amenity_categories (hotel_id)` — HotelAmenityCategoryRepository hotel hydration. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.cities`

Danh mục thành phố, optional parent province; name unique theo source hiện tại, không tự đổi thành parent/name.

Source: [City.java](../../hotel-service/src/main/java/com/example/hotelservice/City/entity/City.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |
| `latitude` | `double precision` | YES | — | latitude |
| `longitude` | `double precision` | YES | — | longitude |
| `parent_id` | `uuid` | YES | — | province; current Java Province → target Province; KEEP_EXISTING_CONTRACT |
| `hotel_count` | `integer` | NO | — | hotelCount |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `FOREIGN KEY (parent_id) REFERENCES provinces(id)`
- `UNIQUE (name)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.provinces`

Danh mục tỉnh, tọa độ optional và hotel_count projection; không suy tọa độ tỉnh là tọa độ khách sạn.

Source: [Province.java](../../hotel-service/src/main/java/com/example/hotelservice/City/entity/Province.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |
| `latitude` | `double precision` | YES | — | latitude |
| `longitude` | `double precision` | YES | — | longitude |
| `hotel_count` | `integer` | NO | — | hotelCount |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (name)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_accommodation_type_changes`

Đề nghị/duyệt loại lưu trú, một PENDING/hotel; reviewer metadata theo trạng thái. Giữ lịch sử và FK nội bộ đã có trong SQL.

Source: [AccommodationTypeChange.java](../../hotel-service/src/main/java/com/example/hotelservice/Hotel/entity/AccommodationTypeChange.java), [hotel-accommodation-type-20261104.sql](../../docker/sql/hotel-accommodation-type-20261104.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotelId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `requester_id` | `uuid` | NO | — | requesterId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `previous_type` | `varchar(32)` | YES | — | previousType; CHECK values HOTEL, VILLA, APARTMENT, GUEST_HOUSE |
| `requested_type` | `varchar(32)` | NO | — | requestedType; CHECK values HOTEL, VILLA, APARTMENT, GUEST_HOUSE |
| `status` | `varchar(20)` | NO | — | status; CHECK values PENDING, APPROVED, REJECTED, SUPERSEDED |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `reviewed_at` | `timestamp(6) with time zone` | YES | — | reviewedAt |
| `reviewer_id` | `uuid` | YES | — | reviewerId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `rejection_reason` | `varchar(1000)` | YES | — | rejectionReason |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `CHECK (previous_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE'))`
- `CHECK (requested_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE'))`
- `CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED'))`
- `FOREIGN KEY (hotel_id) REFERENCES hotels(id)`
- `CHECK (previous_type IS DISTINCT FROM requested_type)`
- `CHECK ((status IN ('PENDING','SUPERSEDED') AND reviewed_at IS NULL AND reviewer_id IS NULL AND rejection_reason IS NULL) OR (status='APPROVED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND rejection_reason IS NULL) OR (status='REJECTED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND rejection_reason IS NOT NULL AND length(trim(rejection_reason))>0))`

Indexes / workload:

- `CREATE INDEX accommodation_change_hotel_created ON public.hotel_accommodation_type_changes USING btree (hotel_id, created_at DESC)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE UNIQUE INDEX accommodation_change_one_pending ON public.hotel_accommodation_type_changes USING btree (hotel_id) WHERE ((status)::text = 'PENDING'::text)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.hotels`

Hồ sơ hotel, giấy tờ riêng tư, city, approval/publication, accommodation_type và optimistic revision. DRAFT hợp lệ; hoạt động công khai cần APPROVED + active.

Source: [Hotel.java](../../hotel-service/src/main/java/com/example/hotelservice/Hotel/entity/Hotel.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `owner_id` | `uuid` | NO | — | ownerId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `city_id` | `uuid` | NO | — | cityId; read-only alias city; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |
| `description` | `text` | YES | — | description |
| `accommodation_type` | `varchar(32)` | YES | — | accommodationType; CHECK values HOTEL, VILLA, APARTMENT, GUEST_HOUSE |
| `revision` | `bigint` | NO | `0` | revision; current Java long → target long; KEEP_EXISTING_CONTRACT |
| `star_rating` | `smallint` | YES | — | starRating |
| `address` | `jsonb` | NO | — | address |
| `contact_email` | `varchar(100)` | YES | — | contactEmail |
| `contact_phone` | `varchar(20)` | YES | — | contactPhone |
| `business_license_number` | `varchar(50)` | NO | — | businessLicenseNumber; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `tax_id` | `varchar(50)` | YES | — | taxId; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `operation_license_number` | `varchar(50)` | YES | — | operationLicenseNumber; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `owner_identity_number` | `varchar(50)` | YES | — | ownerIdentityNumber; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `business_license_file_path` | `varchar(255)` | YES | — | businessLicenseFilePath |
| `operation_license_file_path` | `varchar(255)` | YES | — | operationLicenseFilePath |
| `owner_identity_front_path` | `varchar(255)` | YES | — | ownerIdentityFrontPath |
| `owner_identity_back_path` | `varchar(255)` | YES | — | ownerIdentityBackPath |
| `is_active` | `boolean` | NO | — | isActive |
| `approved_status` | `varchar(20)` | NO | — | approvedStatus; CHECK values DRAFT, PENDING, APPROVED, REJECTED |
| `rejection_reason` | `text` | YES | — | rejectionReason |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (city_id) REFERENCES cities(id)`
- `PRIMARY KEY (id)`
- `CHECK (accommodation_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE'))`
- `CHECK (approved_status IN ('DRAFT','PENDING','APPROVED','REJECTED'))`

Indexes / workload:

- `PROPOSED idx_hotels_city_id ON public.hotels (city_id)` — city-scoped hotel catalog / HotelSearch source. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED hotels_owner ON public.hotels (owner_id)` — HotelRepository owner lookup and owner authorization lists. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.media_assets`

Metadata ảnh HOTEL/ROOM_TYPE; polymorphic target không FK; private document paths nằm ở hotels, không chuyển vào public gallery.

Source: [MediaAsset.java](../../hotel-service/src/main/java/com/example/hotelservice/MediaAsset/entity/MediaAsset.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `target_id` | `uuid` | NO | — | targetId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `target_type` | `varchar(20)` | NO | — | targetType; CHECK values HOTEL, ROOM_TYPE |
| `url` | `varchar(500)` | NO | — | url |
| `is_thumbnail` | `boolean` | NO | — | isThumbnail |
| `sort_order` | `integer` | NO | — | sortOrder |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `CHECK (target_type IN ('HOTEL','ROOM_TYPE'))`

Indexes / workload:

- `PROPOSED idx_media_assets_target ON public.media_assets (target_id, target_type)` — MediaAssetRepository polymorphic target lookup; replaces redundant per-target partial indexes. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_policies`

Nội dung chính sách khách sạn trong category; không thay snapshot hủy booking hoặc policy toàn hệ thống.

Source: [HotelPolicy.java](../../hotel-service/src/main/java/com/example/hotelservice/Policy/entity/HotelPolicy.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotel; current Java Hotel → target Hotel; KEEP_EXISTING_CONTRACT |
| `category_id` | `uuid` | NO | — | category; current Java PolicyCategoryType → target PolicyCategoryType; KEEP_EXISTING_CONTRACT |
| `content` | `text` | NO | — | content |
| `is_active` | `boolean` | NO | — | isActive |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (category_id) REFERENCES policy_category_types(id)`
- `FOREIGN KEY (hotel_id) REFERENCES hotels(id)`
- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED hotel_policies_hotel ON public.hotel_policies (hotel_id)` — HotelPolicyRepository hotel details. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.policy_category_types`

Danh mục nhóm chính sách hotel, unique name; khác global Regulations.

Source: [PolicyCategoryType.java](../../hotel-service/src/main/java/com/example/hotelservice/Policy/entity/PolicyCategoryType.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (name)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_questions`

Q&A hotel, active + timestamps; hotel ownership nội bộ.

Source: [Question.java](../../hotel-service/src/main/java/com/example/hotelservice/Question/entity/Question.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotel; current Java Hotel → target Hotel; KEEP_EXISTING_CONTRACT |
| `question` | `varchar(255)` | NO | — | question |
| `answer` | `text` | NO | — | answer |
| `is_active` | `boolean` | NO | — | isActive |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (hotel_id) REFERENCES hotels(id)`
- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED hotel_questions_hotel ON public.hotel_questions (hotel_id)` — QuestionRepository hotel details. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.hotel_reviews`

Review theo hotel/guest/booking, rating và counters feedback. booking_id UUID local contract, logical reference Booking UUID (Batch5A source); giữ unique NULL semantics hiện hành.

Source: [HotelReview.java](../../hotel-service/src/main/java/com/example/hotelservice/Review/entity/HotelReview.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotelId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `guest_id` | `uuid` | NO | — | guestId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `booking_id` | `uuid` | YES | — | bookingId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `rating` | `numeric(38,2)` | NO | — | rating |
| `staff_rating` | `double precision` | YES | — | staffRating |
| `amenities_rating` | `double precision` | YES | — | amenitiesRating |
| `cleanliness_rating` | `double precision` | YES | — | cleanlinessRating |
| `comfort_rating` | `double precision` | YES | — | comfortRating |
| `value_for_money_rating` | `double precision` | YES | — | valueForMoneyRating |
| `location_rating` | `double precision` | YES | — | locationRating |
| `title` | `varchar(255)` | YES | — | title |
| `content` | `text` | YES | — | content |
| `helpful_count` | `integer` | NO | — | helpfulCount |
| `unhelpful_count` | `integer` | NO | — | unhelpfulCount |
| `status` | `varchar(20)` | NO | — | status; CHECK values PENDING, PUBLISHED, REJECTED |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (hotel_id, guest_id, booking_id)`
- `CHECK (status IN ('PENDING','PUBLISHED','REJECTED'))`

Indexes / workload:

- `PROPOSED hotel_reviews_hotel_status_created ON public.hotel_reviews (hotel_id, status, created_at DESC)` — HotelReviewRepository paginated published reviews. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.review_feedbacks`

Một feedback mỗi review/user, HELPFUL/UNHELPFUL; FK review + cascade theo explicit integrity SQL.

Source: [ReviewFeedback.java](../../hotel-service/src/main/java/com/example/hotelservice/Review/entity/ReviewFeedback.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `review_id` | `uuid` | NO | — | reviewId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `user_id` | `uuid` | NO | — | userId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `feedback_type` | `varchar(255)` | NO | — | feedbackType; CHECK values HELPFUL, UNHELPFUL |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) without time zone` | YES | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE NOT VALID`
- `PRIMARY KEY (id)`
- `UNIQUE (review_id, user_id)`
- `CHECK (feedback_type IN ('HELPFUL','UNHELPFUL'))`
- `FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.review_responses`

Một phản hồi owner mỗi review, unique review_id và FK cascade theo explicit SQL; actor owner_id logical User reference.

Source: [ReviewResponse.java](../../hotel-service/src/main/java/com/example/hotelservice/Review/entity/ReviewResponse.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `review_id` | `uuid` | NO | — | reviewId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `owner_id` | `uuid` | NO | — | ownerId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `content` | `text` | NO | — | content |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE NOT VALID`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE`
- `UNIQUE (review_id)`

Indexes / workload:

- `CREATE UNIQUE INDEX uq_review_response_review ON public.review_responses USING btree (review_id)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.room_availability`

Counters theo room/night [check-in, checkout), unique(room_type_id,date). held/booked không âm; tổng không vượt stock được giữ bởi transaction/room lock, không thể CHECK xuyên bảng.

Source: [RoomAvailability.java](../../hotel-service/src/main/java/com/example/hotelservice/Room/availability/entity/RoomAvailability.java), [V4__Room_Availability.sql](../../hotel-service/src/main/resources/db/migration/V4__Room_Availability.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `room_type_id` | `uuid` | NO | — | roomTypeId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `date` | `date` | NO | — | date |
| `held` | `integer` | NO | `0` | held |
| `booked` | `integer` | NO | `0` | booked |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (room_type_id, date)`
- `CHECK (held >= 0)`
- `CHECK (booked >= 0)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.room_holds`

Hold/reservation HELD→CONFIRMED hoặc RELEASED. Command UUID unique; retry trả nguyên hold/deadline, không gia hạn. Expiry scheduler release một lần.

Source: [RoomHold.java](../../hotel-service/src/main/java/com/example/hotelservice/Room/availability/entity/RoomHold.java), [V4__Room_Availability.sql](../../hotel-service/src/main/resources/db/migration/V4__Room_Availability.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `command_id` | `uuid` | YES | — | commandId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `room_type_id` | `uuid` | NO | — | roomTypeId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotelId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `check_in_date` | `date` | NO | — | checkInDate |
| `check_out_date` | `date` | NO | — | checkOutDate |
| `quantity` | `integer` | NO | — | quantity |
| `status` | `varchar(255)` | NO | — | status; CHECK values HELD, CONFIRMED, RELEASED |
| `expires_at` | `timestamp(6) with time zone` | YES | — | expiresAt |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `UNIQUE (command_id)`
- `PRIMARY KEY (id)`
- `CHECK (status IN ('HELD','CONFIRMED','RELEASED'))`
- `CHECK (quantity > 0)`
- `CHECK (check_out_date > check_in_date)`

Indexes / workload:

- `PROPOSED room_holds_expiry ON public.room_holds (expires_at) WHERE status='HELD'` — RoomHoldRepository.findExpiredHolds / HoldCleanupScheduler. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.room_types`

Loại phòng, giá/khả năng chứa/amenities snapshots và explicit total_rooms. NULL stock biểu diễn chưa cấu hình, không default 10; bán phòng phải stock>0 và published.

Source: [RoomType.java](../../hotel-service/src/main/java/com/example/hotelservice/Room/entity/RoomType.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | hotelId; read-only alias hotel; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(100)` | NO | — | name |
| `description` | `text` | YES | — | description |
| `base_price` | `numeric(38,2)` | NO | — | basePrice |
| `capacity` | `jsonb` | NO | — | capacity |
| `amenities` | `jsonb` | YES | — | amenities |
| `is_active` | `boolean` | NO | — | isActive |
| `total_rooms` | `integer` | YES | — | totalRooms |
| `created_at` | `timestamp(6) with time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) with time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED room_types_hotel ON public.room_types (hotel_id)` — RoomTypeRepository hotel candidates and published room hydration. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.amenity_catalog`

Danh mục code canonical do ADMIN quản lý, revision/audit actor, soft active; không seed guessed mappings.

Source: [amenity-catalog-v1-20261106.sql](../../docker/sql/amenity-catalog-v1-20261106.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `code` | `varchar(64)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `label` | `varchar(255)` | NO | — | SQL/JDBC-owned |
| `description` | `varchar(1000)` | YES | — | SQL/JDBC-owned |
| `active` | `boolean` | NO | — | SQL/JDBC-owned |
| `revision` | `bigint` | NO | `0` | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `created_by` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `updated_by` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `created_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `updated_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (code::text ~ '^[A-Z][A-Z0-9_]{0,63}$'::text)`
- `CHECK (length(TRIM(BOTH FROM label)) > 0)`
- `PRIMARY KEY (code)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.amenity_mapping_requests`

Đề nghị mapping raw amenity với title snapshot và review evidence. hotel_id/amenity_id không FK để audit sống qua raw replacement.

Source: [amenity-catalog-v1-20261106.sql](../../docker/sql/amenity-catalog-v1-20261106.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `amenity_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `title_snapshot` | `varchar(255)` | NO | — | SQL/JDBC-owned |
| `requested_by` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `requested_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `note` | `varchar(1000)` | YES | — | SQL/JDBC-owned |
| `status` | `varchar(16)` | NO | — | SQL/JDBC-owned |
| `reviewed_by` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `reviewed_at` | `timestamptz` | YES | — | SQL/JDBC-owned |
| `review_reason` | `varchar(1000)` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUPERSEDED'::character varying]::text[]))`

Indexes / workload:

- `CREATE INDEX amenity_mapping_requests_pending ON public.amenity_mapping_requests USING btree (status, requested_at, id)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.amenity_mapping_request_codes`

Nhiều code cho một request, composite PK; FK request và catalog nội bộ. Không xóa code đang được history tham chiếu.

Source: [amenity-catalog-v1-20261106.sql](../../docker/sql/amenity-catalog-v1-20261106.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `request_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `code` | `varchar(64)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (code) REFERENCES amenity_catalog(code)`
- `PRIMARY KEY (request_id, code)`
- `FOREIGN KEY (request_id) REFERENCES amenity_mapping_requests(id)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.amenity_mapping_states`

Projection revision và latest/approved request theo raw amenity; scalar target IDs có chủ ý. Effective reads join raw row hiện tại.

Source: [amenity-catalog-v1-20261106.sql](../../docker/sql/amenity-catalog-v1-20261106.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `amenity_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `revision` | `bigint` | NO | `0` | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `latest_request_id` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `approved_request_id` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (approved_request_id) REFERENCES amenity_mapping_requests(id)`
- `FOREIGN KEY (latest_request_id) REFERENCES amenity_mapping_requests(id)`
- `PRIMARY KEY (amenity_id)`

Indexes / workload:

- `CREATE INDEX amenity_mapping_states_hotel ON public.amenity_mapping_states USING btree (hotel_id)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.checkinn_feature_migrations`

Compatibility receipt cho AccommodationSchemaGuard; giữ record hotel-accommodation-type-20261104 khi fresh V1 thực sự tạo đủ feature. Không giả receipt migration đã chạy trên DB cũ.

Source: [hotel-accommodation-type-20261104.sql](../../docker/sql/hotel-accommodation-type-20261104.sql), [AccommodationSchemaGuard.java](../../hotel-service/src/main/java/com/example/hotelservice/config/AccommodationSchemaGuard.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `migration_id` | `varchar(100)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `applied_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (migration_id)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_HOTEL / public.room_inventory_review_requests`

OWNER cung cấp nguồn và số lượng đề xuất, ADMIN review. previous_quantity nullable; không auto-approve/backfill stock. Audit IDs không FK target.

Source: [room-inventory-review-v1-20261107.sql](../../docker/sql/room-inventory-review-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `room_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `hotel_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `previous_quantity` | `integer` | YES | — | SQL/JDBC-owned |
| `proposed_quantity` | `integer` | NO | — | SQL/JDBC-owned |
| `source` | `varchar(1000)` | NO | — | SQL/JDBC-owned |
| `requested_by` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `requested_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `status` | `varchar(16)` | NO | — | SQL/JDBC-owned |
| `reviewed_by` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `reviewed_at` | `timestamptz` | YES | — | SQL/JDBC-owned |
| `review_reason` | `varchar(1000)` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `CHECK (proposed_quantity > 0)`
- `CHECK (length(TRIM(BOTH FROM source)) > 0)`
- `CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUPERSEDED'::character varying]::text[]))`

Indexes / workload:

- `CREATE INDEX room_inventory_review_queue ON public.room_inventory_review_requests USING btree (status, requested_at, id)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX room_inventory_review_room ON public.room_inventory_review_requests USING btree (room_id, requested_at, id)` — ADMIN queue/history/state lookup; partial uniqueness serializes one active accommodation proposal. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


#### `P_HOTEL / public.room_inventory_review_states`

Revision/latest/approved proposal theo room; FK request nội bộ, room_id không FK target để giữ audit khi target bị xóa.

Source: [room-inventory-review-v1-20261107.sql](../../docker/sql/room-inventory-review-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `room_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `revision` | `bigint` | NO | `0` | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `latest_request_id` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `approved_request_id` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (approved_request_id) REFERENCES room_inventory_review_requests(id)`
- `FOREIGN KEY (latest_request_id) REFERENCES room_inventory_review_requests(id)`
- `PRIMARY KEY (room_id)`
- `CHECK (revision >= 0)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Giữ audit/review history; raw replacement không xóa proposal/evidence. Target removal chỉ theo quan hệ explicit ở trên.


### P_BOOKING — independent database / public


#### `P_BOOKING / public.bookings`

Aggregate booking, quote canonical, policy snapshot, hold linkage, idempotency và durable recovery intents cho loyalty/onsite/paid reservation. Giữ tất cả compatibility/review fields.

Source: [Booking.java](../../booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `idempotency_key_hash` | `varchar(64)` | YES | — | idempotencyKeyHash; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `idempotency_request_hash` | `varchar(64)` | YES | — | idempotencyRequestHash; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `user_id` | `uuid` | NO | — | userId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_id` | `uuid` | NO | — | hotelId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_name` | `varchar(255)` | NO | — | hotelName |
| `check_in_date` | `date` | NO | — | checkInDate |
| `check_out_date` | `date` | NO | — | checkOutDate |
| `adults` | `integer` | NO | — | adults |
| `children` | `integer` | NO | — | children |
| `status` | `varchar(255)` | NO | — | status; CHECK values PENDING, PENDING_PAYMENT, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW |
| `total_amount` | `numeric(38,2)` | NO | — | totalAmount |
| `paid_amount` | `numeric(38,2)` | NO | — | paidAmount |
| `voucher_code` | `varchar(255)` | YES | — | voucherCode; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `voucher_discount` | `numeric(38,2)` | YES | — | voucherDiscount |
| `earned_points` | `bigint` | YES | — | earnedPoints |
| `used_points` | `bigint` | YES | — | usedPoints |
| `points_discount_amount` | `numeric(38,2)` | YES | — | pointsDiscountAmount |
| `contact_name` | `varchar(255)` | NO | — | contactName |
| `contact_email` | `varchar(255)` | NO | — | contactEmail |
| `contact_phone` | `varchar(255)` | NO | — | contactPhone |
| `special_requests` | `varchar(255)` | YES | — | specialRequests |
| `hold_id` | `uuid` | YES | — | holdId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hold_expires_at` | `timestamp(6) without time zone` | YES | — | holdExpiresAt |
| `cancellation_policy_version` | `varchar(64)` | YES | — | cancellationPolicyVersion; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `check_in_at` | `timestamp(6) with time zone` | YES | — | checkInAt |
| `free_cancellation_until` | `timestamp(6) with time zone` | YES | — | freeCancellationUntil |
| `loyalty_status` | `varchar(24)` | YES | — | loyaltyStatus |
| `loyalty_next_attempt_at` | `timestamp(6) without time zone` | YES | — | loyaltyNextAttemptAt |
| `loyalty_attempts` | `integer` | YES | — | loyaltyAttempts |
| `loyalty_last_error` | `varchar(64)` | YES | — | loyaltyLastError |
| `onsite_reservation_status` | `varchar(24)` | YES | — | onsiteReservationStatus |
| `onsite_next_attempt_at` | `timestamp(6) without time zone` | YES | — | onsiteNextAttemptAt |
| `onsite_attempts` | `integer` | YES | — | onsiteAttempts |
| `onsite_last_error` | `varchar(64)` | YES | — | onsiteLastError |
| `paid_reservation_status` | `varchar(24)` | YES | — | paidReservationStatus |
| `paid_reservation_payment_id` | `uuid` | YES | — | paidReservationPaymentId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `paid_reservation_next_attempt_at` | `timestamp(6) without time zone` | YES | — | paidReservationNextAttemptAt |
| `paid_reservation_attempts` | `integer` | YES | — | paidReservationAttempts |
| `paid_reservation_last_error` | `varchar(64)` | YES | — | paidReservationLastError |
| `inventory_reviewed_by` | `uuid` | YES | — | inventoryReviewedBy; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `inventory_review_reason` | `varchar(1000)` | YES | — | inventoryReviewReason |
| `inventory_reviewed_at` | `timestamp(6) with time zone` | YES | — | inventoryReviewedAt |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) without time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `UNIQUE (idempotency_key_hash)`
- `PRIMARY KEY (id)`
- `CHECK (status IN ('PENDING','PENDING_PAYMENT','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW'))`
- `CHECK (check_out_date > check_in_date)`
- `CHECK (adults >= 0 AND children >= 0)`
- `CHECK (total_amount >= 0 AND paid_amount >= 0)`

Indexes / workload:

- `CREATE INDEX bookings_loyalty_due ON public.bookings USING btree (loyalty_status, loyalty_next_attempt_at)` — feature JDBC/repository lookup. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX bookings_onsite_due ON public.bookings USING btree (onsite_reservation_status, onsite_next_attempt_at)` — feature JDBC/repository lookup. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX bookings_paid_reservation_due ON public.bookings USING btree (paid_reservation_status, paid_reservation_next_attempt_at)` — feature JDBC/repository lookup. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED bookings_user ON public.bookings (user_id)` — BookingRepository.findByUserId. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED bookings_hotel ON public.bookings (hotel_id)` — BookingRepository.findByHotelId. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED bookings_status ON public.bookings (status)` — BookingScheduledTask / findIdsByStatus. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED bookings_created ON public.bookings (created_at)` — BookingRepository.countBookingsCreatedToday half-open window. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `PROPOSED bookings_check_in ON public.bookings (check_in_date)` — BookingRepository.findByCheckInDateBetween. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_BOOKING / public.booking_items`

Line snapshots theo booking: room/rate plan names, stay dates, quantity, nights, price/tax/subtotal. Cascade/orphan removal ở JPA; FK mặc định NO ACTION, không tự DB cascade aggregate.

Source: [BookingItem.java](../../booking-service/src/main/java/com/example/bookingservice/booking/entity/BookingItem.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `booking_id` | `uuid` | NO | — | booking; Booking association retained; current referenced PK UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `room_type_id` | `uuid` | NO | — | roomTypeId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `room_type_name` | `varchar(255)` | NO | — | roomTypeName |
| `rate_plan_id` | `varchar(255)` | NO | — | ratePlanId; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `check_in_date` | `date` | NO | — | checkInDate |
| `check_out_date` | `date` | NO | — | checkOutDate |
| `quantity` | `integer` | NO | — | quantity |
| `unit_price` | `numeric(38,2)` | NO | — | unitPrice |
| `nights` | `integer` | NO | — | nights |
| `subtotal` | `numeric(38,2)` | NO | — | subtotal |
| `tax_fee` | `numeric(38,2)` | YES | — | taxFee |
| `cancellation_policy` | `varchar(255)` | YES | — | cancellationPolicy |
| `guest_name` | `varchar(255)` | YES | — | guestName |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `CHECK (quantity > 0 AND nights > 0)`
- `CHECK (check_out_date > check_in_date)`
- `CHECK (unit_price >= 0 AND subtotal >= 0)`

Indexes / workload:

- `PROPOSED booking_items_booking ON public.booking_items (booking_id)` — Booking items aggregate hydration. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_BOOKING / public.payments`

Nhiều payment attempts mỗi booking; VNPay order/transaction identifiers unique khi non-NULL. Original receipt và occurred_at giữ nguyên sau internal refund; không unique booking_id.

Source: [Payment.java](../../booking-service/src/main/java/com/example/bookingservice/payment/entity/Payment.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `amount` | `numeric(38,2)` | NO | — | amount |
| `method` | `varchar(255)` | NO | — | method; CHECK values HOTEL, VNPAY |
| `status` | `varchar(255)` | NO | — | status; CHECK values PENDING, ONSITE_PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED |
| `transaction_id` | `varchar(255)` | YES | — | transactionId; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `vnpay_order_id` | `varchar(255)` | YES | — | vnpayOrderId; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `vnpay_create_date` | `varchar(14)` | YES | — | vnpayCreateDate |
| `vnpay_response_code` | `varchar(255)` | YES | — | vnpayResponseCode; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `vnpay_transaction_no` | `varchar(255)` | YES | — | vnpayTransactionNo; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `paid_at` | `timestamp(6) without time zone` | YES | — | paidAt |
| `transaction_occurred_at` | `timestamp(6) with time zone` | YES | — | transactionOccurredAt |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) without time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (vnpay_order_id)`
- `UNIQUE (vnpay_transaction_no)`
- `CHECK (method IN ('HOTEL','VNPAY'))`
- `CHECK (status IN ('PENDING','ONSITE_PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED'))`
- `CHECK (amount >= 0)`

Indexes / workload:

- `PROPOSED payments_booking_created ON public.payments (booking_id, created_at DESC)` — PaymentRepository latest attempt per booking; no unique booking_id. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_BOOKING / public.refund_requests`

Một request/payment, full approved amount và approval actor; optimistic version. REQUESTED/APPROVED_PENDING_EXECUTION/REJECTED/REFUNDED; refunded chỉ internal sandbox theo execution.

Source: [RefundRequest.java](../../booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java), [V3__Cancellation_Policy_And_Refund_Requests.sql](../../booking-service/src/main/resources/db/migration/V3__Cancellation_Policy_And_Refund_Requests.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `payment_id` | `uuid` | NO | — | paymentId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `amount` | `numeric(38,2)` | NO | — | amount |
| `reason` | `varchar(1000)` | NO | — | reason |
| `requested_by` | `uuid` | NO | — | requestedBy; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `approved_by` | `uuid` | YES | — | approvedBy; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `status` | `varchar(64)` | NO | — | status; CHECK values REQUESTED, APPROVED_PENDING_EXECUTION, REJECTED, REFUNDED |
| `requested_at` | `timestamp(6) with time zone` | NO | — | requestedAt |
| `approved_at` | `timestamp(6) with time zone` | YES | — | approvedAt |
| `execution_mode` | `varchar(32)` | YES | — | executionMode |
| `refunded_at` | `timestamp(6) with time zone` | YES | — | refundedAt |
| `version` | `bigint` | NO | `0` | version; current Java long → target long; KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (payment_id)`
- `CHECK (status IN ('REQUESTED','APPROVED_PENDING_EXECUTION','REJECTED','REFUNDED'))`
- `FOREIGN KEY (payment_id) REFERENCES payments(id)`
- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `CHECK (amount > 0)`

Indexes / workload:

- `PROPOSED refund_requests_recovery ON public.refund_requests (approved_at) WHERE status='APPROVED_PENDING_EXECUTION'` — ManualRefundRecovery missing execution scan LIMIT 20. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_BOOKING / public.booking_event_outbox`

Transactional event staging cùng booking/payment/refund commit; event_version global sequence, unique; delivery ordered per booking, retry pending, không xóa để che lỗi.

Source: [booking-event-outbox-v1-20261107.sql](../../docker/sql/booking-event-outbox-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `booking_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `event_version` | `bigint` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `exchange_name` | `varchar(255)` | NO | — | SQL/JDBC-owned |
| `routing_key` | `varchar(128)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `payload` | `jsonb` | NO | — | SQL/JDBC-owned |
| `state` | `varchar(16)` | NO | `'PENDING'` | SQL/JDBC-owned |
| `attempts` | `integer` | NO | `0` | SQL/JDBC-owned |
| `next_attempt_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `last_error` | `varchar(64)` | YES | — | SQL/JDBC-owned |
| `created_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `delivered_at` | `timestamptz` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (attempts >= 0)`
- `UNIQUE (event_version)`
- `PRIMARY KEY (id)`
- `CHECK (state::text = ANY (ARRAY['PENDING'::character varying, 'DELIVERED'::character varying]::text[]))`

Indexes / workload:

- `CREATE INDEX booking_event_due ON public.booking_event_outbox USING btree (state, next_attempt_at, event_version)` — delivery scan due + earlier pending stream ordering; reduces queue scan and preserves publisher ordering. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX booking_event_stream_pending ON public.booking_event_outbox USING btree (booking_id, event_version) WHERE ((state)::text = 'PENDING'::text)` — delivery scan due + earlier pending stream ordering; reduces queue scan and preserves publisher ordering. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: PENDING giữ tới publisher-confirm + local delivery commit; delivered retention chưa quyết định, không xóa trong V1. Không cascade theo aggregate.


#### `P_BOOKING / public.manual_refund_executions`

Command immutable amount/currency/request/payment + execution evidence. MANUAL chỉ staging/review; SANDBOX_INTERNAL mới được REFUNDED. Không thiết kế provider execution mới.

Source: [manual-refund-v1-20261112.sql](../../docker/sql/manual-refund-v1-20261112.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `request_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `payment_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `booking_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `amount` | `numeric(38,2)` | NO | — | SQL/JDBC-owned |
| `currency` | `varchar(3)` | NO | — | SQL/JDBC-owned |
| `provider` | `varchar(32)` | NO | — | SQL/JDBC-owned |
| `execution_method` | `varchar(16)` | NO | `'MANUAL'` | SQL/JDBC-owned |
| `state` | `varchar(64)` | NO | — | SQL/JDBC-owned |
| `external_reference` | `varchar(200)` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `evidence_reference` | `varchar(200)` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `submitted_by` | `uuid` | YES | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `submitted_at` | `timestamptz` | YES | — | SQL/JDBC-owned |
| `created_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `updated_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (amount > 0::numeric)`
- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `CHECK (currency::text = 'VND'::text)`
- `FOREIGN KEY (payment_id) REFERENCES payments(id)`
- `UNIQUE (payment_id)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (request_id) REFERENCES refund_requests(id)`
- `UNIQUE (request_id)`
- `CHECK (state::text <> 'REFUNDED'::text OR execution_method::text = 'SANDBOX_INTERNAL'::text)`
- `CHECK (execution_method::text = ANY (ARRAY['MANUAL'::character varying, 'SANDBOX_INTERNAL'::character varying]::text[]))`
- `CHECK (state::text = ANY (ARRAY['PENDING_MANUAL_REFUND'::character varying, 'PENDING_VERIFICATION'::character varying, 'NEEDS_MANUAL_REVIEW'::character varying, 'REFUNDED'::character varying]::text[]))`
- `CHECK (state::text <> 'PENDING_VERIFICATION'::text OR external_reference IS NOT NULL AND evidence_reference IS NOT NULL AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_BOOKING / public.manual_refund_audit`

Append audit theo execution; không retention/delete cascade tự động; evidence_reference là private identifier, không credential/public URL.

Source: [manual-refund-v1-20261112.sql](../../docker/sql/manual-refund-v1-20261112.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `execution_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `action` | `varchar(64)` | NO | — | SQL/JDBC-owned |
| `actor_id` | `varchar(255)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT; human UUID or system/service actor text; SYSTEM_RECOVERY/system must remain legal |
| `occurred_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `FOREIGN KEY (execution_id) REFERENCES manual_refund_executions(id)`
- `PRIMARY KEY (id)`

Indexes / workload:

- `PROPOSED manual_refund_audit_execution ON public.manual_refund_audit (execution_id, occurred_at)` — ManualRefundService execution audit trail. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lịch sử append; không tự xóa/sửa hoặc cascade từ actor/business record. Recovery dùng receipt cũ; retention/archive cần policy riêng.


### P_REVENUE — independent database / public


#### `P_REVENUE / public.booking_status_records`

Latest booking status projection mỗi booking, event ordering/version receipt; không FK tới Booking service.

Source: [BookingStatusRecord.java](../../revenue-service/src/main/java/com/example/revenueservice/entity/BookingStatusRecord.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_id` | `uuid` | NO | — | hotelId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `room_type_id` | `uuid` | YES | — | roomTypeId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `check_in_date` | `date` | NO | — | checkInDate |
| `check_out_date` | `date` | NO | — | checkOutDate |
| `nights` | `integer` | NO | — | nights |
| `rooms` | `integer` | NO | — | rooms |
| `booking_status` | `varchar(255)` | NO | — | bookingStatus |
| `event_at` | `timestamp(6) without time zone` | NO | — | eventAt |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `event_version` | `bigint` | YES | — | eventVersion; current Java Long → target Long; KEEP_EXISTING_CONTRACT |
| `last_event_id` | `uuid` | YES | — | lastEventId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `CREATE UNIQUE INDEX revenue_status_booking_unique ON public.booking_status_records USING btree (booking_id)` — EventIngestionService findByBookingId single projection + event dedup ownership. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_REVENUE / public.payment_records`

Latest payment projection mỗi booking, monotonic event_version/last_event_id; phục vụ compatibility, không phải sổ tiền. NULL metadata legacy giữ nguyên và báo reconciliation.

Source: [PaymentRecord.java](../../revenue-service/src/main/java/com/example/revenueservice/entity/PaymentRecord.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | id; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |
| `booking_id` | `uuid` | NO | — | bookingId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `customer_id` | `uuid` | NO | — | customerId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_id` | `uuid` | NO | — | hotelId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `room_type_id` | `uuid` | YES | — | roomTypeId; Batch4.1 Java String → current UUID; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `check_in_date` | `date` | NO | — | checkInDate |
| `check_out_date` | `date` | NO | — | checkOutDate |
| `nights` | `integer` | NO | — | nights |
| `rooms` | `integer` | NO | — | rooms |
| `amount` | `numeric(38,2)` | NO | — | amount |
| `payment_status` | `varchar(255)` | NO | — | paymentStatus |
| `payment_method` | `varchar(255)` | NO | — | paymentMethod |
| `paid_at` | `timestamp(6) without time zone` | YES | — | paidAt |
| `event_at` | `timestamp(6) without time zone` | NO | — | eventAt |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `event_version` | `bigint` | YES | — | eventVersion; current Java Long → target Long; KEEP_EXISTING_CONTRACT |
| `last_event_id` | `uuid` | YES | — | lastEventId; current Java UUID → target UUID; KEEP_EXISTING_CONTRACT |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`

Indexes / workload:

- `CREATE UNIQUE INDEX revenue_payment_booking_unique ON public.payment_records USING btree (booking_id)` — EventIngestionService findByBookingId single projection + event dedup ownership. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_REVENUE / public.revenue_cash_transactions`

Immutable CREDIT/DEBIT theo payment/kind và source event; positive amount, occurred_at là transaction day authoritative. Không tính tiền từ latest payment projection.

Source: [revenue-cash-transactions-v1-20261107.sql](../../docker/sql/revenue-cash-transactions-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `source_event_id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `source_version` | `bigint` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `payment_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `kind` | `varchar(16)` | NO | — | SQL/JDBC-owned |
| `transaction_id` | `varchar(255)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `occurred_at` | `timestamptz` | NO | — | SQL/JDBC-owned |
| `booking_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `customer_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `room_type_id` | `uuid` | YES | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `check_in_date` | `date` | NO | — | SQL/JDBC-owned |
| `check_out_date` | `date` | NO | — | SQL/JDBC-owned |
| `nights` | `integer` | NO | — | SQL/JDBC-owned |
| `rooms` | `integer` | NO | — | SQL/JDBC-owned |
| `amount` | `numeric(38,2)` | NO | — | SQL/JDBC-owned; NEEDS_DECISION D01: historical SQL numeric; exact roundtrip/rounding gate before adoption |
| `payment_method` | `varchar(255)` | NO | — | SQL/JDBC-owned |
| `recorded_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `execution_mode` | `varchar(32)` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (amount > 0::numeric)`
- `CHECK (kind::text = ANY (ARRAY['CREDIT'::character varying, 'DEBIT'::character varying]::text[]))`
- `CHECK (nights > 0)`
- `UNIQUE (payment_id, kind)`
- `PRIMARY KEY (id)`
- `CHECK (rooms > 0)`
- `UNIQUE (source_event_id)`

Indexes / workload:

- `CREATE INDEX revenue_cash_period ON public.revenue_cash_transactions USING btree (occurred_at, hotel_id)` — RevenueCashFlows occurred_at range + hotel scoping, source/payment dedup. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lịch sử append; không tự xóa/sửa hoặc cascade từ actor/business record. Recovery dùng receipt cũ; retention/archive cần policy riêng.


#### `P_REVENUE / public.revenue_event_receipts`

Dedup kind/event_hash và quarantine APPLIED/IGNORED/REVIEW_REQUIRED. JSONB payload có thể là null JSON hợp lệ khi invalid event; không ép object-only.

Source: [revenue-event-receipts-v1-20261107.sql](../../docker/sql/revenue-event-receipts-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `kind` | `varchar(16)` | NO | — | SQL/JDBC-owned |
| `booking_id` | `text` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT; invalid event audit may contain arbitrary/non-UUID booking text; preserve exact original receipt/hash/payload |
| `event_hash` | `varchar(64)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `event_at` | `timestamp` | YES | — | SQL/JDBC-owned |
| `disposition` | `varchar(32)` | NO | — | SQL/JDBC-owned |
| `reason` | `varchar(64)` | NO | — | SQL/JDBC-owned |
| `payload` | `jsonb` | NO | — | SQL/JDBC-owned |
| `received_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (disposition::text = ANY (ARRAY['APPLIED'::character varying, 'IGNORED'::character varying, 'REVIEW_REQUIRED'::character varying]::text[]))`
- `CHECK (kind::text = ANY (ARRAY['BOOKING'::character varying, 'PAYMENT'::character varying]::text[]))`
- `UNIQUE (kind, event_hash)`
- `PRIMARY KEY (id)`

Indexes / workload:

- `CREATE INDEX revenue_event_review_queue ON public.revenue_event_receipts USING btree (disposition, received_at, id)` — EventIngestionService hash dedup + admin review queue. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_REVENUE / public.revenue_cashflow_gaps`

Bằng chứng thiếu financial history độc lập projections; resolve cần actor + nonblank evidence. Không tự tạo CREDIT/DEBIT từ trạng thái.

Source: [revenue-history-gaps-v1-20261107.sql](../../docker/sql/revenue-history-gaps-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `booking_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `hotel_id` | `uuid` | NO | — | SQL/JDBC-owned; SQL/JDBC-owned; current UUID bind/read; APPROVED_IMPLEMENTED_SOURCE; canonical migration pending |
| `source_record_id` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `reason` | `varchar(64)` | NO | — | SQL/JDBC-owned |
| `detected_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `resolved_at` | `timestamptz` | YES | — | SQL/JDBC-owned |
| `resolved_by` | `uuid` | YES | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `resolution_evidence` | `text` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (booking_id)`
- `CHECK (resolved_at IS NULL OR resolved_by IS NOT NULL AND resolution_evidence IS NOT NULL AND length(TRIM(BOTH FROM resolution_evidence)) > 0)`

Indexes / workload:

- `CREATE INDEX revenue_cashflow_gaps_scope ON public.revenue_cashflow_gaps USING btree (hotel_id) WHERE (resolved_at IS NULL)` — RevenueCashFlows unresolved hotel reconciliation guard. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


### P_REGULATIONS — independent database / public


#### `P_REGULATIONS / public.regulations`

Global key/value active + optimistic version; atomic update/snapshot/outbox. Hiện không có effective_from/effective_to, không bịa lịch hiệu lực tương lai.

Source: [Regulation.java](../../regulations-service/src/main/java/com/example/regulationsservice/model/Regulation.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `bigint` | NO | `GENERATED BY DEFAULT AS IDENTITY` | id; current Java Long → target Long; KEEP_EXISTING_CONTRACT |
| `regulation_key` | `varchar(255)` | NO | — | regulationKey; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `name` | `varchar(255)` | NO | — | name |
| `value` | `varchar(255)` | NO | — | value |
| `description` | `varchar(2000)` | YES | — | description |
| `active` | `boolean` | NO | — | active |
| `version` | `integer` | NO | — | version; current Java int → target int; KEEP_EXISTING_CONTRACT |
| `created_at` | `timestamp(6) without time zone` | NO | — | createdAt |
| `updated_at` | `timestamp(6) without time zone` | NO | — | updatedAt |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `UNIQUE (regulation_key)`
- `CHECK (version > 0)`

Indexes / workload:

- PK/UNIQUE backing indexes only. Identity/uniqueness lookup; no additional secondary index justified by current reviewed workload.

Lifecycle: Lifecycle do owner service hiện tại quản lý; nullable legacy fields giữ nguyên, không seed/backfill guessed facts. Retention purge không nằm trong V1.


#### `P_REGULATIONS / public.regulation_snapshots`

Append policy snapshot TEXT được ObjectMapper serialize và applied_at wall clock. Key/version historical logical reference; giữ qua đổi/tắt regulation.

Source: [RegulationSnapshot.java](../../regulations-service/src/main/java/com/example/regulationsservice/model/RegulationSnapshot.java).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `bigint` | NO | `GENERATED BY DEFAULT AS IDENTITY` | id; current Java Long → target Long; KEEP_EXISTING_CONTRACT |
| `regulation_key` | `varchar(255)` | NO | — | regulationKey; current Java String → target String; KEEP_EXISTING_CONTRACT |
| `regulation_name` | `varchar(255)` | NO | — | regulationName |
| `version` | `integer` | NO | — | version; current Java int → target int; KEEP_EXISTING_CONTRACT |
| `snapshot_data` | `text` | NO | — | snapshotData |
| `applied_at` | `timestamp(6) without time zone` | NO | — | appliedAt |
| `source_user` | `varchar(255)` | YES | — | sourceUser; current Java String → target String; KEEP_EXISTING_CONTRACT; human UUID or system/service actor text; SYSTEM_RECOVERY/system must remain legal |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `PRIMARY KEY (id)`
- `CHECK (version > 0)`

Indexes / workload:

- `PROPOSED regulation_snapshots_applied ON public.regulation_snapshots (applied_at DESC)` — RegulationSnapshotRepository.findTop50ByOrderByAppliedAtDesc. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: Lịch sử append; không tự xóa/sửa hoặc cascade từ actor/business record. Recovery dùng receipt cũ; retention/archive cần policy riêng.


#### `P_REGULATIONS / public.regulation_event_outbox`

Transactional policy events, unique key/source_version, ordered pending stream và retry due; không publish policy mới chỉ vì migrate schema.

Source: [regulation-event-outbox-v1-20261107.sql](../../docker/sql/regulation-event-outbox-v1-20261107.sql).

| Column | PostgreSQL type | NULL? | SQL default | Mapping / decision |
|---|---|---|---|---|
| `id` | `uuid` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target UUID (JDBC typed bind/read); KEEP_EXISTING_CONTRACT |
| `regulation_key` | `varchar(255)` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `source_version` | `integer` | NO | — | SQL/JDBC-owned; current Java JDBC_SQL_OWNED (no JPA field) → target JDBC_SQL_OWNED (no JPA field); KEEP_EXISTING_CONTRACT |
| `exchange_name` | `varchar(255)` | NO | — | SQL/JDBC-owned |
| `payload` | `jsonb` | NO | — | SQL/JDBC-owned |
| `state` | `varchar(16)` | NO | `'PENDING'` | SQL/JDBC-owned |
| `attempts` | `integer` | NO | `0` | SQL/JDBC-owned |
| `last_error` | `varchar(64)` | YES | — | SQL/JDBC-owned |
| `next_attempt_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `created_at` | `timestamptz` | NO | `now()` | SQL/JDBC-owned |
| `delivered_at` | `timestamptz` | YES | — | SQL/JDBC-owned |

Physical constraints (canonical definitions; equivalent spelling/casts consolidate):

- `CHECK (attempts >= 0)`
- `PRIMARY KEY (id)`
- `UNIQUE (regulation_key, source_version)`
- `CHECK (source_version > 0)`
- `CHECK (state::text = ANY (ARRAY['PENDING'::character varying, 'DELIVERED'::character varying]::text[]))`

Indexes / workload:

- `CREATE INDEX regulation_event_due ON public.regulation_event_outbox USING btree (state, next_attempt_at)` — delivery scan due + earlier pending stream ordering; reduces queue scan and preserves publisher ordering. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.
- `CREATE INDEX regulation_event_pending ON public.regulation_event_outbox USING btree (regulation_key, source_version) WHERE ((state)::text = 'PENDING'::text)` — delivery scan due + earlier pending stream ordering; reduces queue scan and preserves publisher ordering. Benefit: indexed predicate/order or uniqueness instead of full scan/race. Write cost: maintain btree on affected inserts/updates; retain only this reviewed key set, measure before further indexes.

Lifecycle: PENDING giữ tới publisher-confirm + local delivery commit; delivered retention chưa quyết định, không xóa trong V1. Không cascade theo aggregate.

## 6. Relationships, sequences và explicit exceptions

[ERD](ERD.md) shows five independent owner databases; all physical edges local. Booking UUID references: booking_items/payments/refund/execution/outbox, Loyalty commands/reservations/ledger, Revenue accepted projections/cash/gaps, HotelReview all match target UUID. No cross-project FK. UUID user/hotel/room/payment/review/event identities copied, not regenerated. Optional IDs retain nullability.

Exception registry: manual_refund_audit.actor_id varchar255 (human UUID text or SYSTEM_RECOVERY), regulation_snapshots.source_user varchar255 (human/system/service), revenue_event_receipts.booking_id nullable TEXT (invalid raw event evidence), rate_plan_id varchar255 compatibility alias (no RatePlan owner proven), provider/document/evidence IDs and structured command/hash/business keys unchanged. No physical UUID FK from an exception. HotelReview unique(hotel_id,guest_id,booking_id) NULL semantics unchanged; no new NULLS NOT DISTINCT. Loyalty legacy ledger not forced unique booking/type, commands enforce idempotency separately.

Flyway engine creates public.flyway_schema_history independently per database (installed_rank PK, version/description/type/script/checksum/installed_by/installed_on/execution_time/success according pinned version); do not manually copy old histories. Regulations bigint identity sequences owner-local increment1/start1/no cycle; Booking public.booking_event_outbox_version_seq bigint increment1/start1/no cycle; gaps valid, no cross-project global ordering. No extensions/functions/triggers/views/materialized views added by UUID revision.

## 7. Critical business invariants và transaction boundaries

| Workflow | DB protection | Application protection / remaining gate | Evidence |
|---|---|---|---|
| Hotel publication/capacity/stock | approval/accommodation CHECK; explicit nullable stock, no default10 | Hotel APPROVED+active, room active; capacity JSON parsed/validated; unconfigured stock fails closed; no guessed star/address/media | Hotel/Room services, Batch2 negative matrix |
| Inventory no oversell | unique room/date; nonnegative held/booked; unique hold command | command advisory lock → room lock → date rows; check held+booked<=configured total using widened arithmetic. Cross-table CHECK not possible; stock update same room lock/review path | AvailabilityService, RoomInventoryReviewService, Batch2 concurrency |
| Hold release/confirm/expiry | durable status/deadline, unique command | lock hold, same terminal replay returns, HELD confirm checks expiry; held→booked atomic local transaction; confirmed cancellation decrements booked once | HoldCleanupScheduler, AvailabilityService |
| Booking quote/ownership | stable UUID ID with unchanged JSON string representation; unique key hash; items FK | principal ownership; canonical room/hotel/price, quantity/nights; same key+different request rejects; DB transaction synchronization compensation releases remote hold/points on failure | BookingService, BookingIdempotency, BookingCreationCompensation |
| Cancellation/status | nullable policy version + instant snapshot; intent fields | STANDARD_24H_V1 remains 14:00 Vietnam, free until24h; unknown legacy stays reviewed; preserve noRollbackFor HoldConfirmationException path and durable paid/onsite/loyalty intents | CancellationPolicy, BookingScheduledTask, Paid/Onsite/Loyalty recovery |
| Payments | unique VNPay order and transaction_no; multiple attempts/booking | callback verification + locked payment; original amount/receipt/time preserved; no unique booking_id; paidAt is processing wall clock, not cash transaction day | PaymentService/Repository, Batch1.6 |
| Refund | unique payment request/execution; amount>0; VND; internal execution/evidence checks; FKs | ADMIN approved immutable full amount, sandbox feature gate; preserve original CREDIT/payment transaction identity; transactional outbox DEBIT once | RefundRequestService, ManualRefundService, PaymentService (sandbox internal path) |
| Revenue | source_event_id unique; payment/kind unique; kind CHECK; receipt kind/hash unique | local ingestion transaction/advisory lock; CREDIT/DEBIT immutable; ordering from source versions; contradictory events quarantine; unresolved history blocks report, no guessed settlement | EventIngestionService, CashTransactionWriter, RevenueCashFlows |
| Loyalty | command_key PK + unique booking/type; reservation state/points/rate checks; legacy guard indexes | transaction command lock booking→user; EARN/USE/CANCEL replay result; held reservations recovery; signed reversal history kept, no broad nonnegative ledger CHECK | LoyaltyPointsService, LoyaltyCommandLock, PointsReservationRecovery |
| Regulations | key unique, version optimistic, outbox unique key/version | persist+flush → snapshot TEXT → outbox same transaction; cache refresh compatibility; existing current policy seeds only; no new future-effective policy | RegulationCacheProvider, RegulationEventPublisher |

Application invariants không tự trở thành database CHECK. Không thêm paid_amount<=total_amount nếu chưa audit accepted financial transitions; không total_points>=used_points blanket check làm loại legacy recovery. Không DB trigger enforcing financial immutability khi current owner code dùng JPA/projection updates; ledger owner app phải append-only, least-privilege INSERT/SELECT là target có verify riêng. Revenue immutable history không đồng nghĩa database superuser không thể sửa.


## 8. Discrepancies và current vs target

[Original Batch4 discrepancy report](../evidence/batch-4-database-design/schema-discrepancy-report.md) remains as-of Batch4 source/dump audit, not revised target authority. Batch4.1 [ID inventory](../evidence/batch-4-1-uuid-topology/id-contract-inventory.md) and impact matrix override earlier String-ID preservation recommendation with **proposed** UUID contracts, and override shared-project topology with approved five-project target. Snapshot SQL types remain actual exported facts; no data validation/import/cast performed. Java current types/annotations intact in model; no runtime migration claimed. Duplicate index/CHECK candidates and unrelated business decisions remain as previously reviewed.

## 9. Open decisions and superseded items

| ID | Current design status | Gate |
|---|---|---|
| U01 / former D05 | PROPOSED:38 UUID columns,4 entity PKs; consistent typed references, listed raw/system/business exceptions and BIGINT Regulations exception | User design approval before Batch5A, not implementation permission from Batch4.1 |
| U02 | Hash/wire/JDBC/consumer compatibility, pending events/commands and error semantics | Concrete coordinated release/adapter tests; no assumed rolling compatibility |
| U03 | Fresh no-import versus approved bijective legacy-ID mapping; actual non-UUID demo validity UNKNOWN | No old-DB casts/deletes; retain old DB, review queue/callback/import behavior |
| U04 | ratePlanId currently server room-ID alias; no separate domain owner proven | Keep String; future semantic contract separate, no new RatePlan entity |
| D01/D02 | Money/rate exact precision/rounding unchanged and unresolved | Owner-approved golden tests/mappings before5B SQL freeze |
| D03 | Runtime zone/LocalDateTime versus Instant compatibility unchanged | Zone/deadline equality tests; no automatic timezone conversion |
| D04 | Room–Hotel FK/deletion/retention conflict unchanged | Review lifecycle before fresh FK decision, no cascade changes here |
| D06 topology | **APPROVED:five projects**, old one-project/five-schema proposal SUPERSEDED | Version/network/roles/Data API/pool budgets still staging acceptance pending |
| D07 | Per-project retention/backup/restore and financial event reconciliation | Before live Batch6, plan features not assumed |
| D08 | Concrete fresh cutover/old queues/callbacks/config window | Batch6 approval after passing reviewable artifacts; old DB kept |
| D09 | Regulations seed/version lifecycle unchanged | Approved values/one seed authority before5B; no future-effective policy invented |

## 10. Implementation phases / readiness

Batch5A approved UUID contract code/repository/DTO/transport/consumer/frontend/fixture changes; Batch5B five fresh local DBs and exclusive Flyway V1 chains, JPA validate/native/JDBC/guard/role/workflow/restore gates; Batch6 five existing Supabase projects connectivity/security/credentials/migration/startup/E2E/cutover. [Implementation plan](MIGRATION_IMPLEMENTATION_PLAN.md). Dedicated history/location per service, fresh migrate creates schema objects, never baseline marker substituting V1; original migrations unchanged and excluded from new location.

Design revision DONE with explicit decisions, executable migration/deployment not ready until gates passed. No Java/frontend/proto/runtime configuration/SQL/database/Supabase/Place change in Batch4.1; no stage/commit/push. STD-021 remains IN_PROGRESS and STD-006 physical stock provenance OPEN.

## Batch 5A implementation checkpoint — 13/11/2026

U01 approved38 conversions implemented in source (User3 / Booking21 / Revenue14); U03 approved fresh databases without demo import; U04 approved ratePlanId String. U02 evidence: golden legacy/new PASS, eight backend builds PASS (901 tests,39 existing skips), three isolated PostgreSQL contract tests PASS, frontend regression211 PASS. JSON/protobuf strings, Booking/hold UUIDv3 recipes, hashes, provider refs, raw Revenue receipts and domain policies retained.

**Batch5A PARTIAL:** frontend tsc retains baseline protected Place showDistance error; no Place/frontend edits allowed. Full broker/Gateway/multi-node/restart and canonical migration acceptance NOT RUN. Current source mapping is UUID; actual supplied snapshot types and historical migrations remain unchanged. Never start new code against old VARCHAR schema. Native create-only synthetic tests are not canonical Flyway V1/JPA validate acceptance.

Current model preserves batch4_1_* provenance; current source/binding status supersedes earlier pending-U01 language. Historical reports/inventories remain as-of Batch4.1; this checkpoint records the approved implementation. Five independent Supabase projects unchanged. STD-021 IN_PROGRESS, STD-006 OPEN; Batch5B/6 not started. No current DB/Supabase/runtime-config/history/Place or Git index/commit mutations. [Batch5A report](../BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md), [evidence](../evidence/batch-5a-uuid/implementation-inventory.md).


## Batch 5B implementation checkpoint — 14/11/2026

**DONE for local canonical acceptance; Supabase/Batch6 NOT STARTED.** Five standalone canonical V1 chains/service db/canonical,46 tables/458 columns, five independent PostgreSQL databases/public histories. Bootstrap/rerun/catalog/Hibernate5/5 PASS;32 repositories/guards; runtime roles deny DDL/history/cross-service CONNECT. Guarded external job, opt-in runtime canonical validates/disables Flyway/SQL init; legacy defaults/migrations retained.

D01 historical unbounded Loyalty input/result/reservation total/discount and Revenue cash NUMERIC retained; bounded money38,2 unchanged. D02 exact rate; D03 Instant/timestamptz vs LocalDateTime/timestamp/date retained, JVM/JDBC/sessionUTC tested; D04 logical Room–Hotel/no cascade; D09 Java sole seed owner. Earlier proposal/model provenance stays historical design, executed local facts are recorded separately in5B evidence.

901 backend regressions executed/47 opt-in skips,8 packages PASS;5 canonical native tests PASS. Actual5 apps UP/restart5/5 with owned RabbitMQ/Redis; positive-points Booking/Hotel/User/Payment/refund/Revenue/concurrency/broker-outage/outbox/replay PASS. Place showDistance fixed minimally, tsc PASS,3 component+211 regressions PASS, lint0 errors/2 existing warnings. Minimal repository parameter correction findByRoomTypeId(UUID).

STD-021 local canonical gate complete; hosted deployment/managed security/backup/cutover and vendor PG/Flyway version certification future gates. STD-006 stock provenance OPEN/no default10/import. Full Gateway/provider/SMTP/Media/multi-node/all skipped opt-ins NOT VERIFIED. No Supabase connection/automatic Batch6. [Report](../BATCH5B_CANONICAL_FLYWAY_IMPLEMENTATION_2026-11-14.md), [evidence](../evidence/batch-5b-canonical-flyway/REPRODUCE.md).
