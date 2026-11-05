# Booking/hotel validation — 05/11/2026

## Scope

This review covers booking creation, verified holds, onsite reservations/receipts,
loyalty recovery, room descriptions and published hotel facts. Backend
`place-service` and Docker composition match `origin/master`; frontend place
components/API/types, coordinate calculations and nearby-place wiring match
`origin/main`. No place-service or OSRM container was built or started.

## Environment and schema

Reused the existing `checkinn-test` database volumes. Verified all five database
services point to `postgres-test` clones and notification uses Mailpit. Rebuilt
and recreated only booking-service/hotel-service for the current Java source.
No source/production database migration was run.

All four additive migrations passed apply/replay checks, historical-row
fingerprints, nullable/no-default checks and (where applicable) unique-index
validation:

- `booking-idempotency-20261105.sql`
- `hold-idempotency-20261105.sql`
- `room-description-20261105.sql`
- `onsite-reservation-v1-20261105.sql`

## Real database and HTTP tests

Nine runtime suites passed, totaling **246 checks**, with no failing suite.
Run them sequentially: some deliberately stop/restart test services or install
fixture-scoped deferred-commit faults.

| Runtime suite | Passed checks |
| --- | ---: |
| `onsite-reservation-smoke.py` | 38 |
| `booking-hold-verification-smoke.py` | 19 |
| `room-description-smoke.py` | 20 |
| `search-offers-smoke.py` | 17 |
| `booking-security-smoke.py` | 20 |
| `inventory-smoke.py` | 7 |
| `hold-concurrency-smoke.py` | 11 |
| `points-reservation-smoke.py` | 42 |
| `cancellation-workflow-smoke.py` | 72 |

Coverage includes concurrent first inserts and last-room requests; stable
booking/hold command IDs and deadlines; outage/terminal/expired hold gates;
PostgreSQL commit failures; onsite confirmation/receipts; cancellation/no-show;
process restart; room-description persistence and owner authorization; inactive
public amenity filtering; REST/gateway/gRPC scopes; points reservation,
settlement and reversal; and synthetic report consumption. Harnesses remove
their generated fixtures and fault triggers.

A post-run audit found zero remaining temporary fault triggers in the booking,
hotel and user clones. All services used by these suites were healthy afterward.

The cancellation harness now waits for persisted V1 inventory confirmation
before submitting a host receipt. Its receipt fault targets `paid_amount>0`,
allowing the separate zero-money reservation transaction to complete first.

## Other validation and limits

The reviewed Java implementation previously passed 367 isolated tests (177 hotel
and 190 booking). The host-only `HotelServiceApplicationTests.contextLoads` was
not rerun with host database configuration; real service context startup and
the HTTP suites above were verified using the rebuilt Docker images.

Frontend Node regressions: 62 passed. Its full browser run on the revised scope
reported 51 passing tests and one unchanged upstream coordinate/place failure.
The standard frontend typecheck/build still fails on upstream
`NearbyPlacesSection.tsx:161` passing an undeclared `showDistance` prop to
`PlaceCard`. Those files are unchanged from main and deliberately left for the
place-service owner. Browser runtime tests used a temporary typecheck-bypass
build config, restored byte-for-byte and never committed; this is not a passing
production-build result. See the frontend test README and companion PR.

VNPay callbacks were locally simulated with test signatures. No provider request,
real cash collection, actual refund or production deployment was performed.
These tests do not establish a general RabbitMQ outbox, provider settlement or
unbounded failure recovery guarantee. Retain the additive schema on rollback
and follow the coordinated deployment instructions in `README.md`.
