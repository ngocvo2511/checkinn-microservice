# ERD — Batch 4.1 UUID / Five Independent Databases

> Current status: Batch5A U01/U03/U04 APPROVED;38 source conversions implemented, PARTIAL acceptance. Earlier pending/proposed statements below record Batch4.1 history; see Batch5A checkpoint at end. No database migration applied.


13/11/2026. UUID changes proposed pending approval; five-project topology confirmed by user. public is a namespace proposal within **each independent project/database**. Physical edges below never cross project. [Complete types/nulls/constraints](CANONICAL_DATABASE_SCHEMA_V1.md), [ID exceptions](ID_STANDARDIZATION_CONTRACT.md). Tooling history table independently Flyway-owned in each database.

## P_USER — independent user database / public

```mermaid
erDiagram
  loyalty_commands {
    string command_key
    uuid booking_id
    uuid user_id
  }
  loyalty_points {
    uuid id
    uuid user_id
  }
  otps {
    uuid id
    string otp_code
  }
  points_reservations {
    uuid booking_id
    uuid user_id
  }
  points_transactions {
    uuid id
    uuid user_id
    uuid booking_id
  }
  users {
    uuid id
  }
  user_profiles {
    uuid id
    uuid user_id
    uuid loyalty_points_id
  }
  loyalty_points |o--o| user_profiles : "loyalty_points_id"
  users ||--o| user_profiles : "user_id"
```

## P_HOTEL — independent hotel database / public

```mermaid
erDiagram
  amenity_category_types {
    uuid id
  }
  hotel_amenities {
    uuid id
    uuid category_id
  }
  hotel_amenity_categories {
    uuid id
    uuid hotel_id
  }
  cities {
    uuid id
    uuid parent_id
  }
  provinces {
    uuid id
  }
  hotel_accommodation_type_changes {
    uuid id
    uuid hotel_id
    uuid requester_id
    uuid reviewer_id
  }
  hotels {
    uuid id
    uuid owner_id
    uuid city_id
    bigint revision
    string business_license_number
    string tax_id
    string operation_license_number
    string owner_identity_number
  }
  media_assets {
    uuid id
    uuid target_id
  }
  hotel_policies {
    uuid id
    uuid hotel_id
    uuid category_id
  }
  policy_category_types {
    uuid id
  }
  hotel_questions {
    uuid id
    uuid hotel_id
  }
  hotel_reviews {
    uuid id
    uuid hotel_id
    uuid guest_id
    uuid booking_id
  }
  review_feedbacks {
    uuid id
    uuid review_id
    uuid user_id
  }
  review_responses {
    uuid id
    uuid review_id
    uuid owner_id
  }
  room_availability {
    uuid id
    uuid room_type_id
  }
  room_holds {
    uuid id
    uuid command_id
    uuid room_type_id
    uuid hotel_id
  }
  room_types {
    uuid id
    uuid hotel_id
  }
  amenity_catalog {
    string code
    bigint revision
    uuid created_by
    uuid updated_by
  }
  amenity_mapping_requests {
    uuid id
    uuid hotel_id
    uuid amenity_id
    uuid requested_by
    uuid reviewed_by
  }
  amenity_mapping_request_codes {
    uuid request_id
    string code
  }
  amenity_mapping_states {
    uuid amenity_id
    uuid hotel_id
    bigint revision
    uuid latest_request_id
    uuid approved_request_id
  }
  checkinn_feature_migrations {
    string migration_id
  }
  room_inventory_review_requests {
    uuid id
    uuid room_id
    uuid hotel_id
    uuid requested_by
    uuid reviewed_by
  }
  room_inventory_review_states {
    uuid room_id
    bigint revision
    uuid latest_request_id
    uuid approved_request_id
  }
  hotel_amenity_categories ||--o{ hotel_amenities : "category_id"
  provinces |o--o{ cities : "parent_id"
  hotels ||--o{ hotel_accommodation_type_changes : "hotel_id"
  cities ||--o{ hotels : "city_id"
  policy_category_types ||--o{ hotel_policies : "category_id"
  hotels ||--o{ hotel_policies : "hotel_id"
  hotels ||--o{ hotel_questions : "hotel_id"
  hotel_reviews ||--o{ review_feedbacks : "review_id"
  hotel_reviews ||--o| review_responses : "review_id"
  amenity_catalog ||--o{ amenity_mapping_request_codes : "code"
  amenity_mapping_requests ||--o{ amenity_mapping_request_codes : "request_id"
  amenity_mapping_requests |o--o{ amenity_mapping_states : "approved_request_id"
  amenity_mapping_requests |o--o{ amenity_mapping_states : "latest_request_id"
  room_inventory_review_requests |o--o{ room_inventory_review_states : "approved_request_id"
  room_inventory_review_requests |o--o{ room_inventory_review_states : "latest_request_id"
```

## P_BOOKING — independent booking database / public

```mermaid
erDiagram
  bookings {
    uuid id
    string idempotency_key_hash
    string idempotency_request_hash
    uuid user_id
    uuid hotel_id
    string voucher_code
    uuid hold_id
    string cancellation_policy_version
    uuid paid_reservation_payment_id
    uuid inventory_reviewed_by
  }
  booking_items {
    uuid id
    uuid booking_id
    uuid room_type_id
    string rate_plan_id
  }
  payments {
    uuid id
    uuid booking_id
    string transaction_id
    string vnpay_order_id
    string vnpay_response_code
    string vnpay_transaction_no
  }
  refund_requests {
    uuid id
    uuid payment_id
    uuid booking_id
    uuid requested_by
    uuid approved_by
    bigint version
  }
  booking_event_outbox {
    uuid id
    uuid booking_id
    bigint event_version
    string routing_key
  }
  manual_refund_executions {
    uuid id
    uuid request_id
    uuid payment_id
    uuid booking_id
    string external_reference
    string evidence_reference
    uuid submitted_by
  }
  manual_refund_audit {
    uuid id
    uuid execution_id
    string actor_id
  }
  bookings ||--o{ booking_items : "booking_id"
  payments ||--o| refund_requests : "payment_id"
  bookings ||--o{ refund_requests : "booking_id"
  bookings ||--o{ manual_refund_executions : "booking_id"
  payments ||--o| manual_refund_executions : "payment_id"
  refund_requests ||--o| manual_refund_executions : "request_id"
  manual_refund_executions ||--o{ manual_refund_audit : "execution_id"
```

## P_REVENUE — independent revenue database / public

```mermaid
erDiagram
  booking_status_records {
    uuid id
    uuid booking_id
    uuid hotel_id
    uuid room_type_id
    bigint event_version
    uuid last_event_id
  }
  payment_records {
    uuid id
    uuid booking_id
    uuid customer_id
    uuid hotel_id
    uuid room_type_id
    bigint event_version
    uuid last_event_id
  }
  revenue_cash_transactions {
    uuid id
    uuid source_event_id
    bigint source_version
    uuid payment_id
    string transaction_id
    uuid booking_id
    uuid customer_id
    uuid hotel_id
    uuid room_type_id
  }
  revenue_event_receipts {
    uuid id
    string booking_id
    string event_hash
  }
  revenue_cashflow_gaps {
    uuid booking_id
    uuid hotel_id
    uuid source_record_id
    uuid resolved_by
  }
```

## P_REGULATIONS — independent regulations database / public

```mermaid
erDiagram
  regulations {
    bigint id
    string regulation_key
    int version
  }
  regulation_snapshots {
    bigint id
    string regulation_key
    int version
    string source_user
  }
  regulation_event_outbox {
    uuid id
    string regulation_key
    int source_version
  }
```

## Logical integration — no physical FK or database transaction

```mermaid
flowchart LR
  U[P_USER users UUID]
  H[P_HOTEL hotel room hold UUID]
  B[P_BOOKING booking payment refund UUID]
  R[P_REVENUE accepted projections cash gaps UUID refs]
  L[P_USER loyalty booking UUID refs]
  V[P_HOTEL reviews booking UUID refs]
  G[P_REGULATIONS bigint surrogate key and policy version]
  A[Auth and Redis] -->|API gRPC| U
  B -. UUID text API gRPC refs .-> U
  B -. UUID text API gRPC refs .-> H
  L -. booking UUID text recovery RPC .-> B
  V -. booking guest hotel eligibility RPC .-> B
  B -. JSON strings and event UUID via outbox .-> R
  G -. policy key version APIs and events .-> B
```

Explicit unresolved/exception references: RoomType→Hotel deletion/FK D04 stays logical pending review; media.target_id polymorphic UUID and inventory/amenity audit target IDs no FK by lifecycle. manual_refund_audit.actor_id and regulation source_user remain mixed String human/system/service actors. revenue_event_receipts.booking_id stays raw nullable TEXT for invalid source events, not a typed accepted booking FK. rate_plan_id stays opaque String compatibility alias/U04. Provider transaction/document/evidence IDs, command/hash/catalog/policy keys are strings; event/version/revision counters remain numeric. Same physical FK key types align; amenity code FK is varchar→varchar, not UUID. Place excluded/protected; Hotel city UUID wire format retained, fresh dataset identity alignment must be checked without editing Place.

## Batch 5A implementation checkpoint — 13/11/2026

U01 approved38 conversions implemented in source (User3 / Booking21 / Revenue14); U03 approved fresh databases without demo import; U04 approved ratePlanId String. U02 evidence: golden legacy/new PASS, eight backend builds PASS (901 tests,39 existing skips), three isolated PostgreSQL contract tests PASS, frontend regression211 PASS. JSON/protobuf strings, Booking/hold UUIDv3 recipes, hashes, provider refs, raw Revenue receipts and domain policies retained.

**Batch5A PARTIAL:** frontend tsc retains baseline protected Place showDistance error; no Place/frontend edits allowed. Full broker/Gateway/multi-node/restart and canonical migration acceptance NOT RUN. Current source mapping is UUID; actual supplied snapshot types and historical migrations remain unchanged. Never start new code against old VARCHAR schema. Native create-only synthetic tests are not canonical Flyway V1/JPA validate acceptance.

Current model preserves batch4_1_* provenance; current source/binding status supersedes earlier pending-U01 language. Historical reports/inventories remain as-of Batch4.1; this checkpoint records the approved implementation. Five independent Supabase projects unchanged. STD-021 IN_PROGRESS, STD-006 OPEN; Batch5B/6 not started. No current DB/Supabase/runtime-config/history/Place or Git index/commit mutations. [Batch5A report](../BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md), [evidence](../evidence/batch-5a-uuid/implementation-inventory.md).
