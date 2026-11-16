# Canonical inventory

| Owner | Tables | Columns | V1 path | SHA256 |
|---|---:|---:|---|---|
| user | 7 | 59 | user-service/src/main/resources/db/canonical/V1__canonical_schema.sql | ec1311005b867250ca49afd1d7fdfa479c0e2064c99df6e8bb8868a4222b5d1d |
| hotel | 24 | 188 | hotel-service/src/main/resources/db/canonical/V1__canonical_schema.sql | 0d1dd1b726d1e22758ce098bca5b2eefd5ed20def0439a7cddf5d44a0e190c4a |
| booking | 7 | 118 | booking-service/src/main/resources/db/canonical/V1__canonical_schema.sql | 1d6c3b49fe1c70e04227a2e8c7a583aaef6dc3cec771402c8c083ac67a5cfad2 |
| revenue | 5 | 66 | revenue-service/src/main/resources/db/canonical/V1__canonical_schema.sql | bbbc05f29bdfd8282e5af5152b75a375787bb1fe19611e02774e4d53726c8223 |
| regulations | 3 | 27 | regulations-service/src/main/resources/db/canonical/V1__canonical_schema.sql | f90aaf2e94ef7fdf15df1a43b48d60f3cf003afa51287e311996b539a1a78fac |

46 tables/458 columns plus5 Flyway histories; [JSON](migration-inventory.json). Application UUIDv4 and deterministic Booking/Hold UUIDv3 retained, no database UUID default. Built-in UUID/hashtextextended/pg_advisory_xact_lock/now need no custom extension/function. Booking version sequence and2 Regulations identity sequences created by V1.

Local FKs applied after owner tables exist. Named Hotel guards/feature receipt retained after full DDL. Duplicate historical NOT VALID review FKs collapse into one validated FK; review response unique uses one backing index. No customer/geography/inventory/policy seed DML; Java owns Regulations seeds.

Runtime application-canonical.yml Flyway disabled/SQL init never/Hibernate validate. Guarded dedicated job: mvn -o -B -ntp -pl OWNER-service -Dtest=CanonicalPostgresAcceptanceTest test, private environment supplied by docker/batch5b-run-acceptance.py. Grant scripts db/security/runtime-grants.sql outside Flyway location. Five independent provisioning/connection configurations, no hosted deployment.
