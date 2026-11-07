# Event and cash-reporting review — 7 November 2026

Branch: `fix/booking-revenue-event-consistency`, based on `master`.
Companion frontend: `fix/revenue-reporting-consistency`, based on `main`.
Notification and amenity search are separate PRs, also based on `master`.

## Review fixes

Two newly added regressions failed before the fixes and passed afterwards:

- Mandatory publishing had correlated confirms but publisher returns disabled.
  An unroutable message could receive ACK and be marked delivered. Enable returns;
  a real broker regression verifies the outbox stays pending with UNROUTABLE_EVENT.
- CREDIT/DEBIT pairing compared only the amount. A foreign booking could reuse
  another payment's identity. Lock by payment ID and compare booking, customer,
  hotel, room, stay, quantity, amount and method before appending either direction.
  A real PostgreSQL concurrency regression verifies one movement is retained and
  the conflicting claim is quarantined.

## Validation

The reviewed combined working tree passed 720 executed selected package tests
across seven modules. Eight opt-in integrations were skipped by that command;
environment-dependent ApplicationTests are excluded and not counted as passes.
The changed Booking/Revenue suites were rerun after the fixes: 240 and 38 executed
passes respectively. Hotel, notification and the unchanged prerequisite modules
passed their selected packages separately in the initial seven-module run.

Commands:

```powershell
mvn -o -pl user-service,auth-service,hotel-service,booking-service,revenue-service,notification-service,regulations-service package "-Dtest=*Test,!*ApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false" -B -ntp
mvn -o -pl booking-service,revenue-service package "-Dtest=*Test,!*ApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false" -B -ntp
python -X utf8 docker/availability-range-integration.py review-events-revenue-integration revenue
python -X utf8 docker/availability-range-integration.py review-events-outbox-integration outbox
python -X utf8 docker/booking-events-smoke.py
python -X utf8 docker/revenue-broker-smoke.py
python -X utf8 docker/revenue-history-gap-smoke.py
```

Fresh PostgreSQL outbox and revenue integration tests both passed. Fresh native
runtime assertions: booking 34, revenue broker 9, history-gap 4. Four relevant
Docker images were rebuilt from reviewed source; only booking, revenue, hotel and
notification in `checkinn-test` were recreated with `--no-deps`. The booking
harness deliberately restarted only test RabbitMQ/booking. Fixtures and temporary
faults were cleaned. No place-service/OSRM operation was performed.

## Deployment and limitations

Apply BOOKING migrations `booking-event-outbox-v1-20261107.sql` and
`booking-transaction-time-v1-20261107.sql`; apply REVENUE migrations
`revenue-event-receipts-v1-20261107.sql`, `revenue-source-versions-v1-20261107.sql`,
`revenue-cash-transactions-v1-20261107.sql`, `revenue-history-gaps-v1-20261107.sql`.
Keep baseline migrations. Install the scoped revenue DLQ policy/bindings for the
correct RabbitMQ vhost before enabling bounded retries. Do not replace real
broker users/credentials with local definitions. Deploy compatible revenue
consumers before the booking producer and coordinate the reporting UI.

The outbox is at-least-once; SMTP may also deliver duplicates. Retain outbox,
receipts, cash movements and gap evidence on rollback. Do not restore writers
that ignore outstanding work. Legacy/conflicted history intentionally returns
REPORT_RECONCILIATION_REQUIRED until reviewed, rather than inventing cash dates.
Refund ingestion is a full-payment contract, not an implemented provider refund.
Commission/net figures use the current authoritative rule and remain estimates.
No production schema/data, migration or deployment was changed.

Frontend checks: 106 Node regressions and 6 dedicated browser report cases passed
without a typecheck bypass. Build/typecheck still reproduce the existing place
showDistance mismatch; no new lint diagnostics. That independent baseline issue
does not block these PRs. Place code, local IDE settings and secrets are excluded.
