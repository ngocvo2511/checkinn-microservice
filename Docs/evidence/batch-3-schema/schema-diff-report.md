# Clean vs upgrade schema diff

| Service / object | Expected | Actual | Classification | Impact / next action |
|---|---|---|---|---|
| Revenue cash subset | cash migration + nullable execution_mode | Source-derived historical cash migration with synthetic receipt then same upgrade | MATCH | Exact normalized catalogs equal; scope limited to table/index/check objects |
| Full User/Hotel/Booking/Revenue/Regulations legacy upgrade | Verified full release baseline | Không có authenticated historical schema/history | UNKNOWN / NOT VERIFIED | Obtain sanitized historical evidence; no invented full baseline |
| Hotel expires_at | JPA already creates field | V5 ALTER adds again | CONFLICT / EXTRA OBJECT attempt | One-time migration ordering; do not edit potentially applied V5 |
| Hotel V2 FK syntax | PostgreSQL executable chain | ADD CONSTRAINT IF NOT EXISTS rejected | CONSTRAINT MISMATCH | Versioned future remediation proposal after history confirmation |
| Booking schema.sql | Current String IDs and independent User service | UUID IDs and local users FK prerequisite | TYPE MISMATCH / MISSING OBJECT | Historical/manual source cannot be adopted as current baseline |
| JPA-only → test-only explicit SQL | Complete feature schema | 332 normalized object differences | EXPECTED DIFFERENCE / constraint-index changes | Detailed JSON; not production bug classification or full legacy upgrade |

[Object details](jpa-vs-explicit-schema-diff.json) contain database, object, before/after, classification, impact, evidence and action. [Cash before/after and clean catalogs](legacy-probe-schema-manifest.json) are deterministic schema-only metadata. No user rows/dumps are committed.

clean-schema-manifest.json is the initial actual bootstrap-attempt catalog, including partial/failed databases. testonly-final-schema-manifest.json is JPA+explicit disposable SQL. These names do not imply migration-only or historical production PASS. No full-schema equivalence is inferred from startup guards. SQL NOT VALID foreign keys retain validated=false in snapshots; historical validation/repair needs separate decision.

Failure atomicity: existing sandbox upgrade bị synthetic LEGACY_UNKNOWN status chặn; PostgreSQL rolls back cả DDL lẫn data, original row giữ nguyên. Hotel V2 không có BEGIN/COMMIT toàn script: các columns trước syntax error đã tồn tại sau fail; cần review partial application, không blind replay. Catalog hotel_v2_partial_failure lưu evidence.
