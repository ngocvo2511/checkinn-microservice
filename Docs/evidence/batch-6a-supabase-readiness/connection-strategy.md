# Connections and Hikari budget

Checked14/11/2026. Recommendations, not applied. [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [pool limits](https://supabase.com/docs/guides/database/connecting-to-postgres/pooling-and-limits), [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/), [driver settings](https://jdbc.postgresql.org/documentation/use/).

| Workload | Preferred | Conditional alternative |
|---|---|---|
| Dedicated Flyway | direct TLS | shared session pooler after custom-role/lock/DDL/rerun tests |
| Persistent Boot/Hikari | direct if deployment network supports it | shared session pooler for IPv4-only egress |
| Admin verification/logical backup | direct, low concurrency, separate login | tested session pooler |

Direct generally requires IPv6 unless IPv4 provisioned; shared session pooler supports IPv4. Exact endpoints/login formats from Connect panels, never infer mode from port alone. Transaction pooling not selected for these workloads. Shared Supavisor transaction mode does not support server prepared statements or session continuity; dedicated PgBouncer capabilities differ. `prepareThreshold=0` disables pgJDBC named server preparation, not Java PreparedStatement APIs, and alone does not solve session state. [Preparation](https://supabase.com/kb/troubleshooting/disabling-prepared-statements-qL8lEL), [pgJDBC](https://jdbc.postgresql.org/documentation/server-prepare/).

Canonical Hikari SET TIME ZONE UTC runs once per physical client connection: transaction pooling cannot guarantee next backend state. Source pg_advisory_xact_lock in Booking/Hotel/User/Revenue is transaction-scoped, unlike session locks; test same-transaction pinning. Flyway default transactional lock/history/DDL behavior needs dedicated acceptance. Reserve **2 migration connections** conservatively pending measurement, not1: Flyway can use multiple connections. No CREATE INDEX CONCURRENTLY in V1; no reason to change locking. [Flyway PostgreSQL](https://documentation.red-gate.com/fd/postgresql-database-277579325.html).

```text
jdbc:postgresql://<HOST>:<PORT>/<DATABASE>?currentSchema=public&sslmode=verify-full&sslrootcert=<MOUNTED_CA>&connectTimeout=10&ApplicationName=checkinn-<OWNER>
```

Credentials separately injected. Encode URL parameters/CA path correctly. verify-full validates chain+hostname; require only encryption. No trust-all/nonvalidating factory or IP hostname bypass. Verify current pg_stat_ssl, but encryption alone does not prove client hostname checks. Wrong CA and wrong hostname must fail. Driver connectTimeout is seconds, Hikari timeout milliseconds. socketTimeout/statement_timeout/lock_timeout/idle_in_transaction_session_timeout are separate budgets chosen after workload review; dedicated DDL timeout differs from runtime. Bound long transactions/slow RPC and test outage ambiguity without blind financial retry.

## Current Hikari5.1.0

No explicit max/min/timeouts/lifetime/keepalive in five runtime YAML/Java datasource definitions. Canonical adds UTC init; Regulations base auto-commit=false. External overrides NOT VERIFIED. Pinned [5.1.0 source](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-5.1.0/src/main/java/com/zaxxer/hikari/HikariConfig.java): max10, minIdle defaults to max, connectionTimeout30000ms, idleTimeout600000ms, maxLifetime1800000ms, keepalive0. Library-derived, not measured deployed values; latest README may differ.

For each project independently: `D_i = R_i × P_i + M_i + H_i <= B_i`. B_i is approved app-usable backend budget after managed/reserved/other-client allocation; also inspect raw max_connections/reserved slots and avoid double subtracting reserve. Session-pool backend size/client ceiling are separate limits. R_i includes rolling surge.

| Project | Unoverridden demand | Illustrative R1/P4/M2/H3 | Real plan/B/R |
|---|---|---|---|
| User | 10R_user+M_user+H_user | 9 | UNKNOWN |
| Hotel | 10R_hotel+M_hotel+H_hotel | 9 | UNKNOWN |
| Booking | 10R_booking+M_booking+H_booking | 9 | UNKNOWN |
| Revenue | 10R_revenue+M_revenue+H_revenue | 9 | UNKNOWN |
| Regulations | 10R_regulations+M_regulations+H_regulations | 9 | UNKNOWN |

9 is arithmetic, not a Supabase limit/allocation. H includes admin/backup/monitoring; M=2 provisional. Candidate max4/min1/connectionTimeout10000/idleTimeout300000/maxLifetime900000/keepalive120000ms **only after budget and provider/network lifetime acceptance**. Keepalive < lifetime; retirement does not interrupt in-use transactions. No changes applied.

HTTP/gRPC/jobs/consumers share process pool, not additive separate pools. User recovery30s+policy listener; Hotel cleanup60s; Booking scheduling pool2, paid/onsite/outbox1s, loyalty5s, refund60s and cron jobs; Revenue payment/booking/policy consumers; Regulations outbox1s/startup seed. Measure overlap/pending/transaction duration. Start one replica each pending multi-node seed/recovery tests; terminate runner before apps. Actual replicas/concurrency/rolling surge remain unknown.
