# Secrets/configuration audit

Bounded static scan of project trees; [locations only](secret-locations.json), [inspector](audit-static.py). Labels are candidates, not live validation. Generated target/log/private evidence/dependencies excluded from bulk scan; targeted runner review without opening private credential files. Not full Git-history secret certification. No external authentication/real .env edits.

Ignored backend docker/.env contains nonempty JWT signing material(:3), DB password settings(:17/21/25/29/33), Supabase service-role key(:36), SMTP password(:42), VNPay signing configuration(:46). Values fully redacted, treated as sensitive until owner classifies. Frontend .env:2 has authentication-secret setting. Local smoke/test env files also contain candidates. Not tracked now does not prove never committed. Rotate if exposed/shared/previously committed, coordinating API/SMTP/payment/JWT clients; do not try keys to determine validity.

Tracked docker/.env.example sensitive fields empty, development broker fallback remains. Compose has shared local fallback identities. Booking/revenue application.yml DB_PASSWORD has nonempty local fallback(:11/:8). Canonical inputs mandatory but higher precedence SPRING_DATASOURCE_* or CLI/configserver can override canonical YAML. User default baseline true; four other default Hibernate update. Compose only docker. No hosted CI/manifest found. Hosted must fail closed on missing secrets/profile/target; no local fallback.

SQL logging enabled in User/Hotel/Revenue/Regulations base and inherited unless disabled. Bind TRACE/HTTP access query/exception logging may expose PII/URLs/signatures. Selected Auth/OTP/Media handlers log operation/error class rather than payload. PaymentService logs references/client IP; UserController logs full exception/message. This is not exhaustive redaction proof. Before cutover disable SQL/bind/payload logging, redact OTP email/VNPay query parameters at gateway/provider handlers, restrict logs/retention and inspect failure-path logs privately.

## Five isolated environment contracts

OWNER = USER/HOTEL/BOOKING/REVENUE/REGULATIONS. Prefixes are proposed deployment inputs, not already implemented app bindings. Each runtime receives only its own role secrets.

| Contract | Mapping/rule |
|---|---|
| OWNER_DB_HOST/PORT/NAME | approved private tuple, encode JDBC URL without credentials |
| OWNER_APP_USER/PASSWORD | CANONICAL_APP_USER/PASSWORD, separate per owner |
| OWNER_MIGRATION_USER/PASSWORD | runner FLYWAY_USER/PASSWORD only |
| OWNER_SSL_MODE/ROOT_CERT | verify-full/trusted mounted CA |
| OWNER_DB_URL | CANONICAL_DB_URL; reject conflicting SPRING_DATASOURCE_URL/DB_URL/CLI |
| OWNER_POOL_MAX/MIN/TIMEOUT/IDLE/LIFETIME/KEEPALIVE | reviewed spring.datasource.hikari settings only after budget |
| migration location | runner filesystem:<one-owner>/src/main/resources/db/canonical; runtime classpath:db/canonical |
| schema | public in JDBC/Hibernate/Flyway |
| runtime profile | SPRING_PROFILES_ACTIVE=canonical |
| safety | SPRING_FLYWAY_ENABLED=false; BASELINEONMIGRATE=false; CLEANDISABLED=true; SPRING_JPA_HIBERNATE_DDLAUTO=validate; SPRING_SQL_INIT_MODE=never |
| logging/time | SPRING_JPA_SHOWSQL=false, no bind TRACE; JVM/JDBC/sessionUTC; review existing cron wall-clock before deployment |

Pool env binding: SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE/MINIMUMIDLE/CONNECTIONTIMEOUT/IDLETIMEOUT/MAXLIFETIME/KEEPALIVETIME; resolved assertion rather than public config dump. Existing Compose USER_DB_URL/USERNAME/PASSWORD etc need approved per-container canonical mapping; not automatic. Disable config import or pin trusted Config Server; reject localhost/shared role refs/conflicting overrides. No overlay applied6A.

Runner inputs: private identity allowlist/expected major/schema/V1 SHA, FLYWAY_URL/USER/PASSWORD, CA. Secret manager injection only, not shell arguments/history/report/plaintext files. Redact exceptions/URLs, no debug -X or public docker compose config with interpolated secrets. Separate restricted full evidence from public alias/results. No secret values published.
