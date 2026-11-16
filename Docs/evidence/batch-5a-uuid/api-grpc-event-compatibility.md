# API / gRPC / event compatibility

REST routes, field names and JSON ID string shapes retained. UUID domain responses serialize canonical lowercase strings; uppercase accepted at parse boundaries. CreateBooking request IDs stay wire Strings to preserve original requestHash bytes. Typed payment request uses strict JSON deserializer + required validation. Optional null references stay null. Provider raw query/signature/reference strings remain unchanged.

Protobuf files byte-identical to initial SHA-256 inventory, including all copies/tags/string field types/service/RPC names. Booking/Payment adapters parse String→UUID and serialize UUID→String. Eight modules compiled their affected protobuf clients; Notification/Auth tests passed without production changes. Hotel existing UUID IDs and gRPC strings unchanged; Booking hold/catalog adapters aligned. Regulations BIGINT/source actor strings untouched.

Booking event snapshot UUIDs serialize string JSON; outbox booking_id binds UUID, payload/routing remain Strings. Revenue event DTOs retain raw Strings so malformed input can be durably recorded; only accepted projections parse UUID. Event IDs, routing keys, hash recipes, source ordering/dedup receipts unchanged; no pending event regeneration. Native Revenue test exercises APPLIED, duplicate IGNORED, malformed REVIEW_REQUIRED and two immutable cash entries CREDIT/DEBIT with duplicate suppression.

Notification/loyalty consumers verified by source/tests, not an active RabbitMQ multi-service deployment. No Gateway/broker full E2E, multi-node race, process-restart certification, real VNPay or provider refund calls performed.

Frontend remains byte-identical to baseline; API/session/routes/idempotency keys still strings. Regression211 PASS and focused booking lint PASS. TypeScript remains FAIL due to pre-existing NearbyPlacesSection showDistance (protected Place); no frontend edits made.
