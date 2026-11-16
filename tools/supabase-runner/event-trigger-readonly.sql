-- READ ONLY: save the single JSON result privately; do not paste function bodies into chat.
-- Does not disable triggers, grant privileges, create roles or approve a migration.
-- Function definitions can contain private literals; keep the exported file under ignored .local.
BEGIN TRANSACTION READ ONLY;
SELECT jsonb_build_object(
  'identity',jsonb_build_object(
      'database',current_database(),'currentUser',current_user,'sessionUser',session_user,
      'serverVersion',current_setting('server_version'),'searchPath',current_setting('search_path')),
  'publicSchemaOwner', (SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='public'),
  'databaseAcl', (SELECT datacl::text FROM pg_database WHERE datname=current_database()),
  'adminCapabilities', (SELECT jsonb_build_object(
      'superuser',rolsuper,'createRole',rolcreaterole,'createDb',rolcreatedb,'bypassRls',rolbypassrls,
      'schemaUsage',has_schema_privilege(current_user,'public','USAGE'),
      'schemaCreate',has_schema_privilege(current_user,'public','CREATE'))
      FROM pg_roles WHERE rolname=current_user),
  'eventTriggers',(SELECT coalesce(jsonb_agg(jsonb_build_object(
      'name',t.evtname,'event',t.evtevent,'tags',t.evttags,'enabled',t.evtenabled,
      'triggerOwner',pg_get_userbyid(t.evtowner),
      'functionSchema',n.nspname,'functionName',p.proname,
      'functionIdentityArguments',pg_get_function_identity_arguments(p.oid),
      'functionOwner',pg_get_userbyid(p.proowner),
      'language',l.lanname,'securityDefiner',p.prosecdef,'config',p.proconfig,
      'functionAcl',p.proacl::text,'definition',pg_get_functiondef(p.oid),
      'extension',(SELECT e.extname FROM pg_depend d JOIN pg_extension e ON e.oid=d.refobjid
                   WHERE d.classid='pg_proc'::regclass AND d.objid=p.oid AND d.deptype='e' LIMIT 1)
      ) ORDER BY t.evtname),'[]'::jsonb)
      FROM pg_event_trigger t JOIN pg_proc p ON p.oid=t.evtfoid
      JOIN pg_namespace n ON n.oid=p.pronamespace JOIN pg_language l ON l.oid=p.prolang),
  'serverPostgrestSchemaSetting',current_setting('pgrst.db_schemas',true),
  'dataApiExposure','SQL setting may be absent or incomplete; verify actual enabled/exposed schemas in Dashboard separately'
) AS checkinn_event_trigger_details;
ROLLBACK;
