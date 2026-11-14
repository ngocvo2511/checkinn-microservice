# ID contract verification

| Gate | Evidence / result |
|---|---|
| UUIDv3/v4, uppercase, malformed, required missing, nullable | UuidContractTest + UuidLegacyGoldenTest + isolated PostgreSQL tests PASS |
| JSON string serialization | Booking UUID response/DTO tests PASS; protobuf remains string |
| REST malformed400/no side effect | Booking MockMvc and User LoyaltyAuthorizationTest PASS, including short-group input |
| Auth401/403, missing404 | Existing BookingAuthorizationTest/GrpcBookingAuthorizationTest/LoyaltyAuthorizationTest PASS; not a Gateway E2E claim |
| gRPC malformed INVALID_ARGUMENT | Authenticated adapter test PASS; affected generated clients compile |
| JPA association / generated v4 / v3 lookup | Booking UuidPostgresContractTest PASS |
| JDBC native binding | Real UUID lookup PASS; deliberate String-to-uuid bind fails as expected; outbox/receipt mocks and Revenue native tests PASS |
| User loyalty typed references | Native command/ledger/reservation + advisory lock PASS |
| Revenue raw invalid receipt vs typed projection | Native receipt/projection/cash test PASS; raw booking_id remains TEXT |

No unresolved compiler/repository type mismatch found in the changed scope. Native tests cover representative operations, not a proof of every production query against future V1. All existing VARCHAR databases remain incompatible with new mappings until fresh approved canonical schemas exist. Never run the new application against the old demo schema.

Exceptions preserved: ratePlanId opaque String; command_key `<canonical-booking-uuid>|TYPE`; request/key hashes; provider transaction_id/VNPay refs; manual audit SYSTEM_RECOVERY and regulation source_user system strings; Revenue raw receipt booking TEXT; Regulations and snapshot BIGINT. No all-IDs-UUID rule introduced.
