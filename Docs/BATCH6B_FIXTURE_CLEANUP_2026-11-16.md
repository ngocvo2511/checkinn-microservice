# Batch6B fixture cleanup

The user requested removal of the retained test data. Applications were stopped
before cleanup; the five approved Supabase databases were backed up privately.
Only explicitly identified Batch6B synthetic hotel/account/policy roots and
their dependent rows were deleted using the verified migration-role connection.
No DDL, TRUNCATE, migration replay, grant changes or sequence resets were used.

| Database | Deleted rows |
| --- | ---: |
| User | 26 |
| Hotel | 29 |
| Booking | 70 |
| Revenue | 54 |
| Regulations | 13 |
| Total | 192 |

This includes 4 synthetic hotels, 4 room types, 10 bookings against those
synthetic hotels, their payments/refunds/outbox/revenue records, 2 fixture
accounts and their loyalty/OTP/profile records, and 3 synthetic regulations
with their snapshots/outbox. All bookings against a removed synthetic hotel
were included, including a later booking from an account outside the fixture
set. The 2 accounts outside the fixture set and their profiles/OTP records
were preserved, as were the 4 default regulations and their history.

Every unselected row was compared against the before snapshot. Flyway history
and the Hotel feature-migration marker were retained. All protected canonical
SQL and base application configuration files remain unchanged.

Seven fixture-recipient Mailpit messages were backed up and removed; three
other messages were preserved. RabbitMQ queues were empty, so no queue purge
was performed. No Redis key matched the known fixture users/policies/tokens;
the two unrelated revocation keys were preserved. No FLUSHDB/FLUSHALL was used.

Private backup and exact row manifest:
`.local/batch6b/backups/fixture-cleanup-20261116/` (ignored, contains sensitive
row snapshots; do not publish).

Earlier acceptance reports describe historical results with retained fixtures.
Those fixtures have now been removed and must not be replayed automatically.
Current verification is recorded in
`evidence/batch-6b-supabase-local-integration/fixture-cleanup-2026-11-16.json`.
