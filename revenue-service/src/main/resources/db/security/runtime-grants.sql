-- Execute as owner with a safely quoted, service-specific runtime_role via psql.
-- Outside db/canonical: never an automatic Flyway migration.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_role";
REVOKE ALL ON flyway_schema_history FROM :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";
REVOKE UPDATE, DELETE ON revenue_cash_transactions FROM :"runtime_role";
REVOKE UPDATE, DELETE ON revenue_event_receipts FROM :"runtime_role";
