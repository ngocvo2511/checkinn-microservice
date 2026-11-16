-- TEMPLATE ONLY. Not executed in Batch6A. Restricted evidence may identify accounts.
-- Establish approved target/TLS/control-plane identity before executing.
BEGIN TRANSACTION READ ONLY;
SELECT current_database(), current_user, session_user, current_schema(),
       current_setting('server_version'), current_setting('server_version_num'),
       current_setting('search_path'), current_setting('TimeZone');
SELECT ssl, version, cipher FROM pg_stat_ssl WHERE pid=pg_backend_pid();
SELECT current_setting('max_connections'), current_setting('superuser_reserved_connections');
SELECT extname, extversion, extnamespace::regnamespace FROM pg_extension;
SELECT nspname, pg_get_userbyid(nspowner) AS owner, nspacl
FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname<>'information_schema';
SELECT c.relname,c.relkind,pg_get_userbyid(c.relowner) AS owner,c.relacl,
       c.relrowsecurity,c.relforcerowsecurity,e.extname
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=c.oid AND d.deptype='e'
LEFT JOIN pg_extension e ON e.oid=d.refobjid
WHERE n.nspname='public' ORDER BY c.relname;
SELECT to_regclass('public.flyway_schema_history') AS existing_history;
SELECT p.oid::regprocedure,pg_get_userbyid(p.proowner) AS owner,p.proacl,p.prosecdef,p.proconfig
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public';
SELECT typname,typtype,pg_get_userbyid(typowner) AS owner
FROM pg_type WHERE typnamespace='public'::regnamespace;
SELECT c.relname,t.tgname,t.tgisinternal,pg_get_triggerdef(t.oid)
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
WHERE c.relnamespace='public'::regnamespace;
SELECT * FROM pg_policies WHERE schemaname='public';
SELECT rolname,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolbypassrls
FROM pg_roles;
SELECT roleid::regrole,member::regrole,grantor::regrole,admin_option FROM pg_auth_members;
SELECT defaclrole::regrole,defaclnamespace::regnamespace,defaclobjtype,defaclacl FROM pg_default_acl;
SELECT table_name,column_name,grantee,privilege_type FROM information_schema.column_privileges
WHERE table_schema='public';
SELECT conrelid::regclass,conname,contype,convalidated,pg_get_constraintdef(oid)
FROM pg_constraint WHERE connamespace='public'::regnamespace;
SELECT * FROM pg_indexes WHERE schemaname='public';
COMMIT;
-- If history exists, STOP first-run workflow. After reviewed rerun gate only:
-- SELECT installed_rank,version,type,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank;
-- Also collect full column/default/identity/sequence catalogs, views/function bodies
-- and effective has_* privileges under each actual login in restricted evidence.
