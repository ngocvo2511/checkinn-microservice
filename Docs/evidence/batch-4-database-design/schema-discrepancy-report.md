# Schema discrepancy report — 13/11/2026

Ba nguồn: pgAdmin dumps (snapshot actual, không phải live catalog), current Java/JDBC/query/workflow contract, historical Flyway/manual SQL. [Raw normalized snapshot metadata](snapshot-catalog.json), [field diff](schema-discrepancies.json), [canonical model](canonical-model.json), [SQL provenance/classification](sql-source-classification.json). Không suy pgAdmin manual edit hoặc applied version từ một mismatch. Không kết nối DB.

## Reviewed decisions và business risk

| Object / service | Classification | Actual snapshot / historical evidence | Canonical recommendation / reason | Impact / compatibility risk |
|---|---|---|---|---|
| User commands/reservations | ADD | Dump thiếu cả2; User V3/V4 + models/recovery cần | Giữ complete command receipts + reservation state/checks/indexes | Retry/recovery/points financial effect thiếu nếu dùng dump làm V1 |
| User users.is_email_verified | MODIFY | Dump nullable, Java primitive @Column(nullable=false) | Fresh NOT NULL; writer cấp; không backfill legacy NULL tự động | JPA validation/account verification compatibility |
| User legacy points ledger | KEEP / NEEDS_DECISION | Dump không unique user account hoặc booking/type; native hasAmbiguousEarningOrUse dùng historical duplicates | Keep signed/reversal ledger; unique command receipt, guard indexes; account user lookup nonunique | Không phá legacy review hoặc reversal bằng overconstraint |
| Hotel DRAFT / accommodation | MODIFY / ADD | Dump approval CHECK thiếu DRAFT; thiếu accommodation_type/revision/changes/feature receipt; source guard+SQL yêu cầu | DRAFT CHECK + feature schema, full source review metadata/distinct/FK/partial unique | Current createHotel/startup guard fails nếu adopt dump-only; JPA test baseline không đủ |
| Hotel room_types.description / hold.command_id / inventory review / amenity mapping | ADD | Dump thiếu, current entities/JDBC/manual SQL dùng | Keep columns/tables + command dedup/review audit | Stock provenance/manual catalog resolution/retries bị mất nếu bỏ SQL-owned tables |
| Hotel cities.created_at | NEEDS_DECISION D03 | Dump timestamp without TZ; Java Instant; historical city SQL wall clock | Fresh timestamptz theo Instant; không convert old local timestamps | Time interpretation/JPA validate risk; paired zone tests trước release |
| Hotel hotels.city_id default zero UUID | MODIFY | Dump DEFAULT zero UUID, current app supplies city; may violate FK/misrepresent facts | Fresh no default, explicit valid city required | No invented location; reject missing city, preserve current caller validation |
| Hotel room_types.hotel_id FK | NEEDS_DECISION D04 | Dump physical FK; source @ForeignKey(NO_CONSTRAINT), testonly catalog no FK | Proposed logical reference; freeze only sau delete lifecycle review | FK can block cleanup, omission can permit orphan; no arbitrary choice during Batch5 |
| Hotel review response/feedback | ADD / KEEP | Dump lacks one-response unique + integrity FK; explicit review-response-integrity SQL has validated-later FK | Fresh UNIQUE(review_id), FK ON DELETE CASCADE validated | Prevent duplicate/orphan owner responses; preserve current integrity policy |
| Hotel media duplicate CHECK / name lookup indexes | REMOVE_CANDIDATE | media target_type two equivalent CHECKs; cities/provinces standalone name btree duplicates UNIQUE(name) | Fresh single enum CHECK + unique backing only; do not DROP legacy | Lower redundant write cost; no valid row/business behavior removed |
| Hotel media partial target indexes | REMOVE_CANDIDATE / MODIFY | Dump three overlapping target indexes | Fresh composite(target_id,target_type); partial per-type variants require workload justification | Verify EXPLAIN if future workload differs; no broad JSON GIN |
| Hotel content/category/raw audit IDs | KEEP | Scalar/no-entity SQL history is intentional; target removal/replacement exists | No blanket target FKs or cascade audit; raw categories kept | History and legacy administration survive content replacement |
| Booking policy/intent/idempotency/review columns, refund/outbox | ADD | Dump3 core tables only; entities + historical feature SQL/recovery require more | Preserve all current nullable compatibility + due fields; schema V1 includes refund/manual/audit/outbox | Startup success with JPA-only is insufficient; cancellation/compensation/recovery effects |
| Payments VNPay transaction_no uniqueness | ADD | Dump only order_id unique; Payment @UniqueConstraint requires both | Nullable UNIQUE(transaction_no), retain order_id; multiple attempts per booking | Payment replay/dedup; do not unique booking_id or fabricate provider fields |
| Refund execution method/status | MODIFY / KEEP | Old V3 request CHECK only REQUESTED/APPROVED; latest sandbox SQL expands REJECTED/REFUNDED/internal | Latest current enum/internal guard, amount>0/VND/evidence/FKs; preserve original receipt | Internal demo semantics only, no provider refund capability introduced |
| Booking schema.sql | REMOVE_CANDIDATE as baseline authority; KEEP source | UUID IDs + local users FK, contrary String entities/service ownership; Batch3 SQL-only fails | Do not use as V1 chain; preserve historical bytes in original location | API IDs/ownership break if blindly replayed |
| Hotel V1–V5 chain | KEEP history / MODIFY future entrypoint | V2 unsupported ADD CONSTRAINT IF NOT EXISTS; V5 duplicate expires_at after JPA; missing prerequisites | Canonical fresh chain separate, never edit old migration history | Old environment cannot run new V1; no claimed historical upgrade |
| Revenue source versions/receipts/cash/gaps | ADD | Dump two projections only; current JDBC ingestion/reporting uses extra3 tables + metadata | Keep immutable ledger, latest projections + receipt quarantine and unresolved-history guard | Financial reporting false or query failure without SQL-owned objects |
| Regulations outbox | ADD | Dump2 core tables, provider atomically snapshots + outboxes | Add outbox key/version unique, pending/due; TEXT snapshot stays | No policy publication loss; no fabricated effective_from/effective_to |
| Cash/loyalty numeric vs JPA money | NEEDS_DECISION D01/D02 | Manual NUMERIC unbounded vs JPA implicit(38,2), rate precision unspecified | Proposed amounts(38,2), exact positive rate unbounded until explicit rate contract | Silent rounding financial risk; current source rounds integer points DOWN, must preserve |
| Booking/User/Revenue LocalDateTime | KEEP / NEEDS_DECISION D03 | Dump wall-clock matches mappings; hold converts systemDefault; Instant event/refund uses TZ | Keep distinction, pin compatible runtime zone and tests | Global timestamptz conversion would change jobs/cancellation/hold deadlines |
| Booking String IDs vs loyalty128/review UUID | NEEDS_DECISION D05 | Current boundaries verified in source, generated values look UUID but Booking contract String | Retain boundaries in spec, explicit test/reject unsupported IDs, no truncation/cast | API compatibility and cross-service review/loyalty integration |
| Flyway history structure | KEEP / UNKNOWN applied history | User dump has table only, no data/history checksums | New engine-managed history each schema; no manual clone | Cannot infer installed V2/V3 from table presence |
| Manual pgAdmin changes / omitted dump objects | UNKNOWN | No audit trail or export exclusion options | Record uncertainty; actual dumps only scope certified | Do not claim source of drift or completeness beyond supplied export |

## Complete field/table comparison

Every current table missing from snapshot has one ADD row; every missing/mismatched column listed below. KEEP unchanged columns remain in full catalog/model. Constraints/indexes differences above supplement field diff; types equivalence is normalized varchar spelling, not a SQL semantic proof.

| Service/object | Class | Property | Actual | Proposed |
|---|---|---|---|---|
| booking.bookings.idempotency_key_hash | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.idempotency_request_hash | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.cancellation_policy_version | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.check_in_at | ADD | table/column | ABSENT | timestamp(6) with time zone |
| booking.bookings.free_cancellation_until | ADD | table/column | ABSENT | timestamp(6) with time zone |
| booking.bookings.loyalty_status | ADD | table/column | ABSENT | varchar(24) |
| booking.bookings.loyalty_next_attempt_at | ADD | table/column | ABSENT | timestamp(6) without time zone |
| booking.bookings.loyalty_attempts | ADD | table/column | ABSENT | integer |
| booking.bookings.loyalty_last_error | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.onsite_reservation_status | ADD | table/column | ABSENT | varchar(24) |
| booking.bookings.onsite_next_attempt_at | ADD | table/column | ABSENT | timestamp(6) without time zone |
| booking.bookings.onsite_attempts | ADD | table/column | ABSENT | integer |
| booking.bookings.onsite_last_error | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.paid_reservation_status | ADD | table/column | ABSENT | varchar(24) |
| booking.bookings.paid_reservation_payment_id | ADD | table/column | ABSENT | varchar(255) |
| booking.bookings.paid_reservation_next_attempt_at | ADD | table/column | ABSENT | timestamp(6) without time zone |
| booking.bookings.paid_reservation_attempts | ADD | table/column | ABSENT | integer |
| booking.bookings.paid_reservation_last_error | ADD | table/column | ABSENT | varchar(64) |
| booking.bookings.inventory_reviewed_by | ADD | table/column | ABSENT | varchar(255) |
| booking.bookings.inventory_review_reason | ADD | table/column | ABSENT | varchar(1000) |
| booking.bookings.inventory_reviewed_at | ADD | table/column | ABSENT | timestamp(6) with time zone |
| booking.payments.vnpay_create_date | ADD | table/column | ABSENT | varchar(14) |
| booking.payments.transaction_occurred_at | ADD | table/column | ABSENT | timestamp(6) with time zone |
| booking.refund_requests | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| booking.booking_event_outbox | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| booking.manual_refund_executions | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| booking.manual_refund_audit | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.cities.hotel_count | MODIFY | default | 0 | None |
| hotel.cities.created_at | NEEDS_DECISION | type | timestamp without time zone | timestamp(6) with time zone |
| hotel.provinces.hotel_count | MODIFY | default | 0 | None |
| hotel.hotel_accommodation_type_changes | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.hotels.city_id | MODIFY | default | '00000000-0000-0000-0000-000000000000'::uuid | None |
| hotel.hotels.accommodation_type | ADD | table/column | ABSENT | varchar(32) |
| hotel.hotels.revision | ADD | table/column | ABSENT | bigint |
| hotel.room_availability.held | MODIFY | default | None | 0 |
| hotel.room_availability.booked | MODIFY | default | None | 0 |
| hotel.room_holds.command_id | ADD | table/column | ABSENT | uuid |
| hotel.room_types.description | ADD | table/column | ABSENT | text |
| hotel.amenity_catalog | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.amenity_mapping_requests | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.amenity_mapping_request_codes | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.amenity_mapping_states | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.checkinn_feature_migrations | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.room_inventory_review_requests | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| hotel.room_inventory_review_states | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| regulations.regulation_event_outbox | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| revenue.booking_status_records.event_version | ADD | table/column | ABSENT | bigint |
| revenue.booking_status_records.last_event_id | ADD | table/column | ABSENT | uuid |
| revenue.payment_records.event_version | ADD | table/column | ABSENT | bigint |
| revenue.payment_records.last_event_id | ADD | table/column | ABSENT | uuid |
| revenue.revenue_cash_transactions | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| revenue.revenue_event_receipts | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| revenue.revenue_cashflow_gaps | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| user.loyalty_commands | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| user.points_reservations | ADD | table/column | ABSENT FROM PROVIDED DUMP | complete table in canonical V1 |
| user.users.is_email_verified | MODIFY | nullable | True | False |
| user.users.created_at | NEEDS_DECISION | type | timestamp without time zone | timestamp(6) without time zone |

Per-row source paths, reason/impact/risk are in JSON; all rows proposal-only. No confirmed unused business column/table deleted. Known duplicate constraints/indexes are fresh-design consolidation candidates, not destructive operations. No missing snapshot service except Place (protected) among five in-scope databases; Auth/media have no separate application datasource source evidence.
