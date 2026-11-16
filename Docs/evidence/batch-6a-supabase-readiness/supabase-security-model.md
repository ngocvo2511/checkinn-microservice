# Supabase security model

STATIC VERIFIED: V1 creates public tables without RLS/policies/API ACL controls. Local db/security/runtime-grants.sql revokes PUBLIC schema access but not Supabase managed-role existing/default ACLs. Actual exposed schemas/Data API/roles/default privileges/owners/RLS all NOT VERIFIED. HOSTED VERIFIED: none.

JDBC authenticates PostgreSQL login/password. Checkinn Auth/JWT/Redis remains app boundary; Supabase Auth separate, not automatically adopted. PostgREST/Data API maps HTTP authentication to anon/authenticated/service_role database roles. API service-role key is **not database password/migration credential**. service_role may bypass RLS; ordinary JDBC login has no automatic end-user JWT RLS claims. Owners/superusers/BYPASSRLS can also bypass row policies. [API security](https://supabase.com/docs/guides/api/securing-your-api), [managed roles](https://supabase.com/docs/guides/database/postgres/roles), [PostgreSQL RLS](https://www.postgresql.org/docs/current/ddl-rowsecurity.html), checked14/11/2026.

Preferred backend-only plan: disable Data API per project **after verifying no dependency needs it**, or remove public from exposure where supported/approved. Media uses Supabase Storage/service-role key; identify storage project mapping first and preserve required Auth/Storage. No dashboard action6A. Regardless of exposure, deny business table/view/sequence/function rights to anon/authenticated/service_role/PUBLIC and future grants FOR ROLE actual creator. Scope to business objects/public creator defaults, never blanket revoke auth/storage managed schemas. Inspect inherited memberships/column grants/grant options/views/security-definer functions/RPC paths. Revoking PUBLIC does not remove explicit roles; schema USAGE does not imply table access.

RLS is a separate decision: with Data API exposure removed and effective API grants denied, ordinary JDBC runtime uses app authorization+table ACL. If defense-in-depth RLS required, apply reviewed security step (not historical V1 edit), explicit runtime policy TO dedicated app and all API-role negative tests. No broad API USING(true), no disabling RLS to pass startup. Review owner/FORCE RLS behavior. RLS alone cannot protect against service_role BYPASSRLS; grants/exposure essential.

| Principal | Allowed | Must fail |
|---|---|---|
| runtime | owner-specific required CRUD/sequence | DDL/history/immutable updates/deletes/escalation |
| migrator/owner | canonical DDL/history, reviewed grants | unrelated managed objects/cross-project access |
| anon/authenticated/service_role | approved managed operations | every business SELECT/INSERT/UPDATE/DELETE/RPC/sequence |
| PUBLIC | reviewed platform baseline | business access/CREATE public |

Secure creator defaults/exposure **before V1** to avoid creation-to-revoke window; after V1 verify real owners/ACLs/RLS. PostgreSQL function EXECUTE defaults require creator-scoped review; V1 creates none but existing RPC/security-definer code may expose data. Sequence ACL independent. SQL has_table/sequence/function_privilege plus column/inherited grants, and actual HTTP Data API negative tests using securely injected identities on all46 tables/relevant objects. Disabled API should fail; empty200 is not denial proof. SET ROLE alone does not certify HTTP exposure.

Future negative DML tests require identity guard, syntactically/constraint-valid synthetic input and forced rollback transaction, so an unexpectedly allowed write cannot persist. Preserve managed objects; never print bearer keys/payload PII/signed URLs. Any accessible business path = NO-GO. No hosted tests6A.
