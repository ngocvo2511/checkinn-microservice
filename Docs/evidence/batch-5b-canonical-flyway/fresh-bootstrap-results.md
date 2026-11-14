# Fresh bootstrap — PASS5/5

PostgreSQL18.0/SCRAM/loopback55486, independent public histories and owner/app roles. [Exact commands, UTC timestamps and databases](fresh-bootstrap-results.json), logs (local only: `logs/`).

| Owner | Fresh database | First migrate | Rerun |
|---|---|---|---|
| user | batch5b_user_1794592498_33124 | PASS:1 SQL V1 | PASS:0 migrations |
| hotel | batch5b_hotel_1794592498_33124 | PASS:1 SQL V1 | PASS:0 migrations |
| booking | batch5b_booking_1794592498_33124 | PASS:1 SQL V1 | PASS:0 migrations |
| revenue | batch5b_revenue_1794592498_33124 | PASS:1 SQL V1 | PASS:0 migrations |
| regulations | batch5b_regulations_1794592498_33124 | PASS:1 SQL V1 | PASS:0 migrations |

Harness verifies exact local URL/database pattern, current_database, real data_directory and empty public schema before Flyway. Every retry creates a new timestamp/PID database; failed targets retained. No ALTER fix-up, manual history insertion, repair, clean, old-chain scanning or Hibernate DDL.

Intermediate failures: migrator lacked read-settings permission; JDBC uses JVM timezone rather than server default; then BookingItemRepository String-vs-UUID query failed before SQL execution. Fixed harness/read-only migration inspection and explicit JVMUTC; corrected repository parameter UUID, reran on fresh databases. No V1 edited after successful application. Flyway10.10 warns PG18 newer than certified16; local runtime proof is not vendor certification.
