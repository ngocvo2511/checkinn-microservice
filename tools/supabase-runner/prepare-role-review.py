"""Render review-only SQL from the five V1 files. Never connects, creates secrets or applies SQL."""
from pathlib import Path
import argparse
import hashlib
import json
import re
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
OWNERS = ('user', 'hotel', 'booking', 'revenue', 'regulations')
IMMUTABLE = {
    'user': {'loyalty_commands': 'SELECT, INSERT', 'points_transactions': 'SELECT, INSERT'},
    'hotel': {'checkinn_feature_migrations': 'SELECT'},
    'booking': {'manual_refund_audit': 'SELECT, INSERT'},
    'revenue': {'revenue_cash_transactions': 'SELECT, INSERT', 'revenue_event_receipts': 'SELECT, INSERT'},
    'regulations': {'regulation_snapshots': 'SELECT, INSERT'},
}


def identifier(value):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', value):
        raise ValueError('Invalid SQL identifier (value withheld)')
    return value


def render(owner, project, database='postgres', administrator='postgres'):
    if owner not in OWNERS or not re.fullmatch(r'[a-z]{20}', project):
        raise ValueError('Invalid owner/project')
    identifier(database)
    identifier(administrator)
    migrator = 'checkinn_' + owner + '_migrator'
    app = 'checkinn_' + owner + '_app'
    v1 = ROOT / (owner + '-service/src/main/resources/db/canonical/V1__canonical_schema.sql')
    sql = v1.read_text(encoding='utf-8')
    tables = re.findall(r'CREATE TABLE (?:public\.)?([a-z_][a-z_0-9]*)\s*\(', sql, re.I)
    sequences = re.findall(r'CREATE SEQUENCE (?:public\.)?([a-z_][a-z_0-9]*)', sql, re.I)
    # V1 uses only conventional column identity sequences; reject any unrecognized identity layout.
    for table, body in re.findall(r'CREATE TABLE (?:public\.)?([a-z_][a-z_0-9]*)\s*\((.*?)\n\);', sql, re.I | re.S):
        for column in re.findall(r'^\s*([a-z_][a-z_0-9]*)\s+[^\n,]*GENERATED\s+(?:BY DEFAULT|ALWAYS)\s+AS IDENTITY', body, re.I | re.M):
            sequences.append(table + '_' + column + '_seq')
    if len(tables) != {'user': 7, 'hotel': 24, 'booking': 7, 'revenue': 5, 'regulations': 3}[owner]:
        raise ValueError('Canonical table inventory changed; review required')
    for name in tables + sequences:
        identifier(name)
    if not set(IMMUTABLE[owner]).issubset(tables):
        raise ValueError('Immutable table inventory changed')
    header = f"""-- DRAFT FOR REVIEW: NOT AUTHORIZATION TO RUN AGAINST SUPABASE.
-- Owner: {owner}; expected project: {project}; database: {database}; PostgreSQL major: 17.
-- Project identity must be verified independently in Dashboard/Management API BEFORE execution.
-- checkinn.* settings below are operator attestations, NOT proof of server project identity.
-- No passwords, app startup, historical migrations, role reuse, baseline, clean or repair.
"""

    def guard(login):
        return f"""DO $guard$
BEGIN
  IF current_database() <> '{database}' OR current_user <> '{login}'
     OR current_setting('server_version_num')::integer / 10000 <> 17 THEN
    RAISE EXCEPTION 'Wrong database, role or PostgreSQL major';
  END IF;
  IF current_setting('checkinn.approved_project_ref', true) IS DISTINCT FROM '{project}'
     OR current_setting('checkinn.approved_service', true) IS DISTINCT FROM '{owner}'
     OR current_setting('checkinn.security_reviewed', true) IS DISTINCT FROM 'true' THEN
    RAISE EXCEPTION 'Exact project/service/security approval settings required';
  END IF;
END
$guard$;
"""

    table_array = 'ARRAY[' + ','.join("'" + t + "'" for t in tables) + ']'
    provision = header + 'BEGIN;\n' + guard(administrator)
    provision += f"""DO $fresh$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('{migrator}', '{app}')) THEN
    RAISE EXCEPTION 'Proposed role already exists; stop, never reuse or alter it automatically';
  END IF;
  IF to_regclass('public.flyway_schema_history') IS NOT NULL OR EXISTS (
      SELECT 1 FROM pg_class WHERE relnamespace='public'::regnamespace AND relname=ANY({table_array})) THEN
    RAISE EXCEPTION 'Existing canonical/history objects; stop without modifying them';
  END IF;
  IF (SELECT count(*) FROM pg_roles WHERE rolname IN ('anon','authenticated','service_role')) <> 3 THEN
    RAISE EXCEPTION 'Expected API roles missing; managed role model requires review';
  END IF;
END
$fresh$;
CREATE ROLE {migrator} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT CONNECTION LIMIT 2;
CREATE ROLE {app} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT CONNECTION LIMIT 4;
-- Creator already receives ADMIN for new roles. Do not grant ADMIN back to its grantor.
-- Allow explicit SET ROLE without automatic inheritance; no app/API membership.
GRANT {migrator} TO {administrator} WITH INHERIT FALSE, SET TRUE;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE {database} TO {migrator}, {app};
GRANT USAGE, CREATE ON SCHEMA public TO {migrator};
GRANT USAGE ON SCHEMA public TO {app};
-- With INHERIT FALSE, change creator defaults as the actual object-creator role.
SET LOCAL ROLE {migrator};
"""
    for scope in ('', ' IN SCHEMA public'):
        for objects in ('TABLES', 'SEQUENCES', 'FUNCTIONS'):
            provision += f'ALTER DEFAULT PRIVILEGES FOR ROLE {migrator}{scope} REVOKE ALL ON {objects} FROM PUBLIC, anon, authenticated, service_role;\n'
    provision += '-- New tables receive no automatic app DML grants. Stage03 grants an exact allowlist after V1.\nCOMMIT;\n'

    enable = header + '\\set ON_ERROR_STOP on\nBEGIN;\n' + guard(administrator)
    enable += "SET LOCAL password_encryption='scram-sha-256';\n"
    enable += f"""-- psql interactive password prompts; no password literals or command-line passwords.
-- Choose unique generated passwords per role; save privately in the matching process env file.
-- Never use the project's postgres password for these dedicated roles.
\\password {migrator}
\\password {app}
ALTER ROLE {migrator} LOGIN;
ALTER ROLE {app} LOGIN;
COMMIT;
"""

    grants = header + 'BEGIN;\n' + guard(migrator)
    grants += f"""DO $history$
BEGIN
  IF (SELECT count(*) FROM public.flyway_schema_history) <> 1 OR
     (SELECT count(*) FROM public.flyway_schema_history
       WHERE version='1' AND type='SQL' AND script='V1__canonical_schema.sql' AND success) <> 1 THEN
    RAISE EXCEPTION 'Exact successful canonical V1 history required';
  END IF;
  IF (SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace
       AND relkind IN ('r','p') AND relowner=current_user::regrole) <> {len(tables) + 1} THEN
    RAISE EXCEPTION 'Unexpected owner table inventory';
  END IF;
END
$history$;
"""
    for table in tables + ['flyway_schema_history']:
        grants += f"""DO $ownership$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='public.{table}'::regclass AND relkind='r' AND relowner=current_user::regrole) THEN
    RAISE EXCEPTION 'Wrong canonical table owner or kind';
  END IF;
END
$ownership$;
REVOKE ALL ON TABLE public.{table} FROM PUBLIC, anon, authenticated, service_role, {app};
"""
        if table != 'flyway_schema_history':
            rights = IMMUTABLE[owner].get(table, 'SELECT, INSERT, UPDATE, DELETE')
            grants += f'GRANT {rights} ON TABLE public.{table} TO {app};\n'
    for sequence in sequences:
        grants += f"""DO $sequence_owner$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='public.{sequence}'::regclass AND relkind='S' AND relowner=current_user::regrole) THEN
    RAISE EXCEPTION 'Wrong sequence owner or kind';
  END IF;
END
$sequence_owner$;
REVOKE ALL ON SEQUENCE public.{sequence} FROM PUBLIC, anon, authenticated, service_role, {app};
GRANT USAGE, SELECT ON SEQUENCE public.{sequence} TO {app};
"""
    grants += 'COMMIT;\n'

    readonly = header + 'BEGIN TRANSACTION READ ONLY;\n'
    readonly += f"""SELECT current_database(),current_user,session_user,current_setting('server_version');
SELECT rolname,rolcanlogin,rolsuper,rolcreatedb,rolcreaterole,rolbypassrls,rolconnlimit
FROM pg_roles WHERE rolname IN ('{migrator}','{app}') ORDER BY rolname;
SELECT has_schema_privilege('{app}','public','CREATE') AS forbidden_app_schema_create,
       pg_has_role('{app}','{migrator}','MEMBER') AS forbidden_app_owner_membership;
SELECT has_table_privilege('{app}','public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') AS forbidden_history_access;
SELECT c.relname,r.rolname,
       has_table_privilege(r.oid,c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER') AS forbidden_api_table_right,
       has_any_column_privilege(r.oid,c.oid,'SELECT,INSERT,UPDATE,REFERENCES') AS forbidden_api_column_right
FROM pg_class c CROSS JOIN pg_roles r
WHERE c.relnamespace='public'::regnamespace AND c.relname=ANY({table_array})
AND r.rolname IN ('anon','authenticated','service_role') ORDER BY c.relname,r.rolname;
COMMIT;
"""
    return {
        '00-admin-readonly.sql': header + (ROOT/'tools/supabase-runner/admin-preflight-readonly.sql').read_text(encoding='utf-8'),
        '01-provision-roles.sql': provision,
        '02-enable-logins.psql': enable,
        '03-postmigration-grants.sql': grants,
        '04-readonly-verification.sql': readonly,
    }, {'owner': owner, 'migrator': migrator, 'runtimeRole': app, 'tables': tables,
        'sequences': sequences, 'v1Sha256': hashlib.sha256(v1.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', default='.local/batch6b/targets.properties')
    parser.add_argument('--output', default='.local/batch6b/role-review-2026-11-15')
    args = parser.parse_args()
    out = (ROOT / args.output).resolve()
    if not out.is_relative_to(ROOT / '.local'):
        raise ValueError('Review output must remain under ignored .local')
    manifest = (ROOT / args.manifest).resolve()
    p = dict(line.split('=', 1) for line in manifest.read_text(encoding='utf-8-sig').splitlines()
             if '=' in line and not line.lstrip().startswith('#'))
    if p.get('environment') != 'supabase-dev' or len({p[o+'.project'] for o in OWNERS}) != 5:
        raise ValueError('Five distinct reviewed project references required')
    out.mkdir(parents=True, exist_ok=False)
    inventory = []
    for owner in OWNERS:
        scripts, info = render(owner, p[owner+'.project'])
        if p[owner+'.v1Sha256'] != info['v1Sha256'] or p[owner+'.serverMajor'] != '17':
            raise ValueError('V1 hash/server major mismatch')
        dest = out / owner
        dest.mkdir()
        for name, body in scripts.items():
            (dest/name).write_text(body, encoding='utf-8')
        info['project'] = p[owner+'.project']
        info['host'] = p[owner+'.host']
        info['scripts'] = {n: hashlib.sha256((dest/n).read_bytes()).hexdigest() for n in scripts}
        inventory.append(info)
        ca = (ROOT/'.local/batch6b/certs'/(owner+'-ca.crt')).resolve()
        if hashlib.sha256(ca.read_bytes()).hexdigest() != p[owner+'.caSha256']:
            raise ValueError('CA hash mismatch')
        url = 'jdbc:postgresql://' + p[owner+'.host'] + ':5432/postgres?sslmode=verify-full&sslrootcert=' + quote(ca.as_posix(),safe='') + '&currentSchema=public&connectTimeout=10'
        prefix = owner.upper()
        (dest/'runtime.env.example').write_text(f'# PROPOSED ONLY: roles not created, no secrets. Do not load until gates pass.\n{prefix}_DB_PROJECT_REF={info["project"]}\n{prefix}_DB_RUNTIME_ROLE={info["runtimeRole"]}\n{prefix}_DB_URL={url}\n{prefix}_DB_USERNAME={info["runtimeRole"]}.{info["project"]}\n{prefix}_DB_PASSWORD=\n{prefix}_DB_POOL_MAX=\n{prefix}_DB_CONNECTION_BUDGET=\n', encoding='utf-8')
        (dest/'migration.env.example').write_text(f'# PROPOSED ONLY: roles not created, no secrets. Runner-only process.\n{prefix}_MIGRATION_DB_URL={url}\n{prefix}_MIGRATION_DB_USERNAME={info["migrator"]}.{info["project"]}\n{prefix}_MIGRATION_DB_PASSWORD=\n', encoding='utf-8')
    (out/'review-manifest.json').write_text(json.dumps({'status':'DRAFT_NOT_APPLIED','sourceManifestSha256':hashlib.sha256(manifest.read_bytes()).hexdigest(),'owners':inventory},indent=2)+'\n',encoding='utf-8')
    print('Prepared five private role/permission review packages. No SQL executed; no secrets generated; active config unchanged.')


if __name__ == '__main__':
    main()
