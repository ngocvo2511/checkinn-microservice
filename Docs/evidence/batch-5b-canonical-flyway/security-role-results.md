# Security — local PASS5/5

Independent owner/migrator and runtime login per database. PUBLIC CONNECT/TEMP revoked on owned targets and postgres/template1; public schema CREATE revoked. Runtime not SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS, no role membership across owners. Migrator additionally pg_read_all_settings only for target identity checks; not hosted-role guidance.

Runtime scoped DML/sequence USAGE/SELECT, denied CREATE/ALTER/DROP/history by actual Java attempts. Append-only ledger/command/audit/receipt/snapshot UPDATE/DELETE denied, Hotel feature receipt SELECT-only. Actual JDBC connects to other4 services + maintenance databases all rejected SQLSTATE42501.47 negative permission checks across roles, [full results](security-results.json).

Grant scripts outside migration location: db/security/runtime-grants.sql. Future V2 grants explicit owner job; no automatic runtime ownership/DDL. New broker/Redis loopback ports/owned labels, PG55486 SCRAM. Credentials only ignored .local. No shared broker/volumes/Supabase connection. Managed Data API/RLS/SSL/pools/roles require Batch6 revalidation.
