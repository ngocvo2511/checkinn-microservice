# Repository/JDBC — PASS

All32 repositories initialized, derived/JPQL validation + count on actual entity tables. Native tests also exercise User reservation/ledger/ambiguity/UUID command locks, exact rates; Hotel AccommodationSchemaGuard/AmenitySchemaGuard, real AvailabilityService locks and concurrent hold/confirm/release/retry/no oversell; Booking UUIDv3 aggregate/UUIDv4 item/payment/refund and association/native locks; Revenue real EventIngestionService/CashTransactionWriter projections/dedup/raw invalid TEXT/CREDIT/DEBIT; Regulations actual Java bootstrap/identity/snapshot/outbox/no duplicate seeds.

Production mismatch found: BookingItemRepository.findByRoomTypeId(String) cannot compare UUID field. Minimal parameter change to UUID, no callers; added real query assertion, all existing Batch5A changes retained. Error is Hibernate SemanticException before SQL, no SQLSTATE. Canonical native tests5/5 and running HTTP/broker workflows PASS. Logs (local only: `logs/`).
