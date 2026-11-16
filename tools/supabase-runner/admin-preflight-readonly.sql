-- READ ONLY: Run in the exact project's SQL Editor after verifying its Dashboard URL.
-- Export the single JSON cell to a private file; no passwords/API keys/business rows are selected.
-- SQL database name/IP alone CANNOT establish Supabase project identity or Data API exposure.
BEGIN TRANSACTION READ ONLY;
SELECT jsonb_build_object(
  'identity', jsonb_build_object(
    'database',current_database(),'currentUser',current_user,'sessionUser',session_user,
    'schema',current_schema(),'serverVersion',current_setting('server_version'),
    'serverVersionNum',current_setting('server_version_num'),
    'maxConnections',current_setting('max_connections'),
    'reservedConnections',current_setting('superuser_reserved_connections')),
  'tls', (SELECT coalesce(jsonb_agg(jsonb_build_object('ssl',ssl,'version',version)), '[]'::jsonb)
          FROM pg_stat_ssl WHERE pid=pg_backend_pid()),
  'historyExists',to_regclass('public.flyway_schema_history') IS NOT NULL,
  'publicSchema', (SELECT jsonb_build_object('owner',pg_get_userbyid(nspowner),'acl',nspacl::text)
                   FROM pg_namespace WHERE nspname='public'),
  'publicObjects', (SELECT coalesce(jsonb_agg(jsonb_build_object(
      'name',c.relname,'kind',c.relkind,'owner',pg_get_userbyid(c.relowner),
      'acl',c.relacl::text,'rls',c.relrowsecurity,'forceRls',c.relforcerowsecurity,
      'extension',(SELECT e.extname FROM pg_depend d JOIN pg_extension e ON e.oid=d.refobjid
                   WHERE d.classid='pg_class'::regclass AND d.objid=c.oid AND d.deptype='e' LIMIT 1))
      ORDER BY c.relname), '[]'::jsonb) FROM pg_class c WHERE c.relnamespace='public'::regnamespace),
  'publicFunctions', (SELECT coalesce(jsonb_agg(jsonb_build_object(
      'signature',p.oid::regprocedure::text,'owner',pg_get_userbyid(p.proowner),
      'securityDefiner',p.prosecdef,'acl',p.proacl::text,'config',p.proconfig)
      ORDER BY p.oid::regprocedure::text),'[]'::jsonb) FROM pg_proc p WHERE p.pronamespace='public'::regnamespace),
  'publicTriggers', (SELECT coalesce(jsonb_agg(jsonb_build_object(
      'table',c.relname,'name',t.tgname,'internal',t.tgisinternal,'definition',pg_get_triggerdef(t.oid))
      ORDER BY c.relname,t.tgname),'[]'::jsonb) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
      WHERE c.relnamespace='public'::regnamespace),
  'eventTriggers', (SELECT coalesce(jsonb_agg(jsonb_build_object(
      'name',evtname,'event',evtevent,'owner',evtowner::regrole::text,
      'function',evtfoid::regprocedure::text,'enabled',evtenabled,'tags',evttags)
      ORDER BY evtname),'[]'::jsonb) FROM pg_event_trigger),
  'policies',(SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY tablename,policyname),'[]'::jsonb)
              FROM pg_policies p WHERE schemaname='public'),
  'roles',(SELECT coalesce(jsonb_agg(jsonb_build_object(
      'name',rolname,'superuser',rolsuper,'createRole',rolcreaterole,'createDb',rolcreatedb,
      'login',rolcanlogin,'bypassRls',rolbypassrls,'inherit',rolinherit)
      ORDER BY rolname),'[]'::jsonb) FROM pg_roles),
  'memberships',(SELECT coalesce(jsonb_agg(jsonb_build_object(
      'role',roleid::regrole::text,'member',member::regrole::text,'adminOption',admin_option,
      'inheritOption',inherit_option,'setOption',set_option) ORDER BY roleid::regrole::text,member::regrole::text),'[]'::jsonb)
      FROM pg_auth_members),
  'creatorDefaults',(SELECT coalesce(jsonb_agg(jsonb_build_object(
      'creator',defaclrole::regrole::text,'schema',defaclnamespace::regnamespace::text,
      'kind',defaclobjtype,'acl',defaclacl::text)
      ORDER BY defaclrole::regrole::text,defaclnamespace,defaclobjtype),'[]'::jsonb) FROM pg_default_acl),
  'columnGrants',(SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY table_name,column_name,grantee,privilege_type),'[]'::jsonb)
                  FROM information_schema.column_privileges c WHERE table_schema='public'),
  'extensions',(SELECT coalesce(jsonb_agg(jsonb_build_object('name',extname,'version',extversion,
                'schema',extnamespace::regnamespace::text) ORDER BY extname),'[]'::jsonb) FROM pg_extension),
  'dataApiExposure','NOT VERIFIED BY SQL; inspect Dashboard/control-plane separately'
) AS checkinn_readonly_preflight;
ROLLBACK;
