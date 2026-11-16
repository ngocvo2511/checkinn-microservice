# User admin snapshot review — 15/11/2026

**PARTIAL; migration/security approval remains BLOCKED.** Source: user-supplied private `user.admin-preflight.json`, not a direct authenticated query by the assistant. No hosted mutation or project setting change.

| Observation from supplied snapshot | Assessment |
|---|---|
| PostgreSQL17.11, postgres/public, current/session role postgres | Matches expected database/major shape; cannot prove Supabase project identity |
| No public relations/functions/triggers/policies/column grants; no Flyway history | Fresh public business namespace in this snapshot; recheck freshness before execution |
| Six enabled event triggers, all owned by supabase_admin | Require fully-qualified function-body/security/search-path review; current runner correctly stops |
| postgres CREATEROLE true, SUPERUSER false | Managed admin capability still needs supported-operation verification; flags alone do not certify provisioning SQL |
| No checkinn_* roles | Dedicated runtime/migrator not provisioned; no active credentials should be filled with postgres values |
| postgres/public creator defaults grant table/sequence/function rights to API roles | Do not create canonical tables using postgres; separate migrator defaults must be verified before V1 |
| SQL Editor TLSv1.3 | Does not establish local JDBC CA/hostname/pooler connectivity |
| Data API exposure not determined by SQL | User previously reports enabled; exposed schemas/dependencies still unknown |

Event trigger names observed: `issue_graphql_placeholder`, `issue_pg_cron_access`, `issue_pg_graphql_access`, `issue_pg_net_access`, `pgrst_ddl_watch`, `pgrst_drop_watch`. Ownership/names alone do not prove harmless behavior. No automatic allowlist, name-only exemption, disabling, dropping or re-owning these objects. The four extension-related tag filters and two generic DDL/drop watchers need function implementation review before any guard adjustment. Helper calls/security-definer behavior may need further inspection.

Existing creator default ACLs are facts about postgres/supabase-managed creators; do not rewrite them broadly across managed Auth/Storage/GraphQL schemas. Proposed new dedicated migrator defaults remain the scoped approach. Empty public currently has no canonical business data; default-grant findings concern the creation path, not proof of an existing data leak.

Next read-only query: source `tools/supabase-runner/event-trigger-readonly.sql`, private User copy `.local/batch6b/user.event-trigger-details.sql`. It exports actual trigger function schemas/owners/definitions, SECURITY DEFINER/config/ACL, database ACL and admin schema capabilities. Full bodies stay in ignored private storage because function literals may be sensitive. Save the JSON cell as `.local/batch6b/user.event-trigger-details.json`. Also record actual exposed schemas in private project notes, without changing Dashboard settings.

Do not run role/provision/login/grant/migration scripts yet. Four other hosted owner snapshots remain NOT RUN. Authenticated mapping, Data API/dependencies, managed function review, effective role capabilities and exact approvals remain gates. Local tests of the new JSON query use only synthetic event-trigger fixtures in fresh owned PostgreSQL17.11; they do not approve the observed Supabase triggers.


## Follow-up: function details received

Static review of six User definitions is complete for the pinned V1 only. [Exact-policy runner1.2.0/16 local tests](../../BATCH6B_USER_EVENT_TRIGGER_REVIEW_2026-11-15.md). No provider object was changed. Actual target equality still requires authenticated connection; Data API still exposes public/graphql_public and role/security/migration approvals remain pending.
