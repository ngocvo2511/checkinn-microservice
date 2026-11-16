# Dependency compatibility

**STATIC VERIFIED** all five: Java21, Boot3.3.4, Flyway10.10.0, pgJDBC42.7.4, Hibernate6.5.3.Final, Hikari5.1.0. POM parent + installed exact Boot3.3.4 BOM + existing executable JAR libraries inspected. [Manifest](source-inventory.json). Existing artifacts not rebuilt, potentially stale;6B release must capture fresh dependency tree/JAR SHA.

User Flyway core+database-postgresql compile/runtime; four others TEST scoped. PostgreSQL module present in test classpath; absent from those four runtime JARs by design. Dedicated runner must include both core and matching PostgreSQL module plus JDBC; do not assume `java -jar` app performs migration.

Local PostgreSQL18.0 acceptance is historical5B evidence. Actual major/minor of P_USER/P_HOTEL/P_BOOKING/P_REVENUE/P_REGULATIONS all **UNKNOWN / NOT VERIFIED**.

Version-specific [Flyway10.10.0 source](https://github.com/flyway/flyway/blob/flyway-10.10.0/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java) recommends upgrade above major16. [Current Redgate matrix](https://documentation.red-gate.com/fd/postgresql-database-277579325.html) describes current product, not retroactive10.10 certification. Checked14/11/2026. Rolling Boot3.3 docs now target a later patch; exact versions above derive from3.3.4 BOM/artifacts.

| Target | Gate |
|---|---|
| Supported major <=16 | no observed canonical feature blocker; exact hosted acceptance still mandatory; no blanket claim for arbitrary old PostgreSQL |
| 17/18 | support review before migration; local PASS is not vendor certification |
| Other/unknown | STOP to verify support |

No demonstrated hosted incompatibility yet; no automatic upgrades. If confirmed, choose **smallest release explicitly supporting actual major**, align core/PostgreSQL module and verify Java21/API/config/licensing. Prefer dedicated runner-only change, keep Boot fixed if feasible. No exact upgrade version invented without release-specific evidence. If User runtime Flyway changes, test Boot autoconfiguration even though disabled. Repeat fresh V1/rerun/catalog/Hibernate/JDBC/role/advisory-lock/concurrency/outbox regressions for affected release. No framework-major upgrade in6A.

V1 uses built-in uuid, numeric, timestamp/timestamptz/date, sequence/identity, partial/expression indexes/checks/unique/local FKs. No extension/custom function/trigger/policy requirement; Java generates UUID. Native runtime hashtextextended/pg_advisory_xact_lock require target acceptance. No cross-project SQL/FDW. Existing managed extension objects may occupy public; allowlist review required. SQL syntax alone cannot certify role/search_path/timezone/UUID binding behavior.

6B read-only evidence: SHOW server_version/server_version_num; version(); current database/schema/user/session_user; pg_extension versions; runner dependency tree and JDBC metadata versions. No hosted query6A.
