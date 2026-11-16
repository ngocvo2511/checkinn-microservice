# Roles/grants plan — not executed

5B local roles passed47 negative probes. Source scripts outside migration location grant runtime scoped intent and deny immutable writes. Actual local V1 object creator/owner is migrator; proposed NOLOGIN owner+SET ROLE is not an executed hosted fact.

| Control | Classification |
|---|---|
| separate runtime/migrator; runtime nonowner/no SUPERUSER/CREATEDB/CREATEROLE/BYPASSRLS | reusable design; hosted NOT VERIFIED |
| CONNECT/USAGE/DML/sequence/history restrictions | adapt explicit business allowlist to managed public |
| NOLOGIN owner + controlled migrator SET ROLE | project-admin action; verify supported capabilities |
| creator default ACL/owner attribution | Supabase adjustment required |
| revoke PUBLIC CONNECT/TEMP on postgres/template1/all DBs | local-only, do not copy to managed maintenance databases |
| pg_read_all_settings/data_directory guard | local-only, replace with control-plane/TLS/catalog |
| cross-service DB denial | separate projects/logins; verify against only approved other targets |

Preferred each project: NOLOGIN owner; migrator LOGIN with controlled SET ROLE owner; app LOGIN no owner/migrator membership/admin option. If managed role capability prevents this, review alternative migrator-as-owner with separate app; never give app owner/postgres/service_role membership. Supabase postgres is managed, not unrestricted superuser. [Roles](https://supabase.com/docs/guides/database/postgres/roles).

Order6B: admin capability/role memberships/catalog → supported role provisioning+secret injection → exposure and actual-creator defaults → migrate under intended owner → **explicit per-business-table** runtime grants → history revoke → immutable restrictions → effective/negative tests. Do not blindly use broad ON ALL TABLES script where managed objects exist. Future V2 requires grants job, no runtime DDL/blanket default DML enabling immutable mutations.

| Owner | Restrictions matching current local source |
|---|---|
| User | loyalty_commands/points_transactions SELECT+INSERT only |
| Hotel | checkinn_feature_migrations SELECT only |
| Booking | manual_refund_audit SELECT+INSERT only |
| Revenue | revenue_cash_transactions/revenue_event_receipts SELECT+INSERT only |
| Regulations | regulation_snapshots SELECT+INSERT only |

Other canonical tables have current mutable CRUD model; tighten unused DELETE after caller review. Outboxes/reservations/executions need status updates, not blanket append-only. No runtime TRUNCATE/REFERENCES/TRIGGER/grant option/DDL/history/sequence reset. USAGE+SELECT sequences, not UPDATE/setval. Booking version sequence and Regulations identities need grants. New objects deny by default until explicit grant.

Default privileges FOR ROLE actual creator: revoke table/sequence business grants from PUBLIC/anon/authenticated/service_role; revoke function EXECUTE. Inspect both global and schema-specific defaults: per-schema REVOKE cannot undo global grants. Do not alter managed creators blindly. pg_roles flags, pg_auth_members/admin options, pg_class/namespace ownership, pg_default_acl/ACL expansion/has_* checks under real login. Ownership bypasses ACL for ALTER/DROP, so app owns nothing.

6B positives: required CRUD/synthetic fixtures and sequences. Negatives: CREATE/ALTER/DROP/history, immutable UPDATE/DELETE, role escalation, public schema CREATE, cross-project credential failure, API-role access. All hosted provisioning/permissions NOT VERIFIED.
