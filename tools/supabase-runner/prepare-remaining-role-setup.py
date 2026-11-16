"""Generate reviewed interactive role setup files. No network, SQL execution or secrets."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / '.local/batch6b'
OWNERS = ('hotel', 'booking', 'revenue', 'regulations')


def prepare():
    props = dict(line.split('=', 1) for line in (PRIVATE/'targets.properties').read_text(encoding='utf-8-sig').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    identities = json.loads((PRIVATE/'five-project-scoped-control-plane-2026-11-16.json').read_text())
    verified = {item['owner']: item for item in identities['results']}
    spec = importlib.util.spec_from_file_location('roles', ROOT/'tools/supabase-runner/prepare-role-review.py')
    roles = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(roles)
    java = (ROOT/'tools/supabase-runner/src/main/java/com/checkinn/db/HostedRunner.java').read_text(encoding='utf-8')
    event_query = json.loads(re.search(r'static final String EVENT_SQL=("(?:\\.|[^"\\])*");', java).group(1))
    output = PRIVATE/'remaining-role-setup'
    if output.exists():
        raise FileExistsError('Refuse overwriting an existing setup package')
    prepared = []
    for owner in OWNERS:
        identity = verified[owner]
        if identity.get('httpStatus') != 200 or not all(identity.get(k) is True for k in ('projectMatches', 'organizationMatches', 'databaseHostMatches')):
            raise ValueError('Independent project identity required')
        policy_path = Path(props[owner+'.eventTriggerReviewFile'])
        policy_bytes = policy_path.read_bytes()
        if hashlib.sha256(policy_bytes).hexdigest() != props[owner+'.eventTriggerReviewSha256']:
            raise ValueError('Policy hash mismatch')
        policy = json.loads(policy_bytes)
        project = props[owner+'.project']
        if policy['owner'] != owner or policy['project'] != project or policy['serverMajor'] != 17 or len(policy['eventTriggers']) != 6:
            raise ValueError('Policy scope mismatch')
        scripts, info = roles.render(owner, project)
        if info['v1Sha256'] != props[owner+'.v1Sha256'] or policy['v1Sha256'] != info['v1Sha256']:
            raise ValueError('V1 hash changed')
        migrator, app = info['migrator'], info['runtimeRole']
        expected = json.dumps(sorted(policy['eventTriggers'], key=lambda e: e['name']))
        if '$expected_events$' in expected:
            raise ValueError('Unsafe delimiter')
        precheck = f"""DO $objects$
DECLARE observed jsonb;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relnamespace='public'::regnamespace)
     OR EXISTS (SELECT 1 FROM pg_proc WHERE pronamespace='public'::regnamespace)
     OR EXISTS (SELECT 1 FROM pg_type WHERE typnamespace='public'::regnamespace AND typtype<>'p') THEN
    RAISE EXCEPTION 'Public namespace changed; stop and review';
  END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(events) ORDER BY events.name),'[]'::jsonb)
    INTO observed FROM ({event_query}) events;
  IF observed IS DISTINCT FROM $expected_events${expected}$expected_events$::jsonb THEN
    RAISE EXCEPTION 'Event/function metadata changed; stop before role creation';
  END IF;
END
$objects$;
"""
        body = scripts['01-provision-roles.sql']
        assert body.count('BEGIN;\n') == 1 and body.count('COMMIT;') == 1
        body = body.replace('BEGIN;\n', f"""BEGIN;
SET LOCAL checkinn.approved_project_ref='{project}';
SET LOCAL checkinn.approved_service='{owner}';
SET LOCAL checkinn.security_reviewed='true';
""", 1).replace('DO $fresh$', precheck+'DO $fresh$', 1)
        result_path = (PRIVATE/(owner+'.role-login-result.json')).as_posix()
        finish = f"""RESET ROLE;
DO $denials$
BEGIN
  IF has_schema_privilege('{app}','public','CREATE') OR pg_has_role('{app}','{migrator}','MEMBER') THEN
    RAISE EXCEPTION 'Unexpected runtime DDL or owner membership';
  END IF;
END
$denials$;
SET LOCAL password_encryption='scram-sha-256';
\\echo Choose a NEW unique non-empty password for {migrator}, twice.
\\password {migrator}
\\echo Choose a DIFFERENT NEW unique non-empty password for {app}, twice.
\\password {app}
ALTER ROLE {migrator} LOGIN;
ALTER ROLE {app} LOGIN;
COMMIT;
\\pset format unaligned
\\pset tuples_only on
\\o '{result_path}'
SELECT jsonb_build_object('operation','{owner.upper()}_ROLES_PASSWORD_LOGIN_ONLY','database',current_database(),
 'businessTablesCreated',false,
 'roles',(SELECT jsonb_agg(jsonb_build_object('name',rolname,'login',rolcanlogin,'superuser',rolsuper,
 'createDb',rolcreatedb,'createRole',rolcreaterole,'bypassRls',rolbypassrls,'connectionLimit',rolconnlimit)
 ORDER BY rolname) FROM pg_roles WHERE rolname IN ('{migrator}','{app}')),
 'runtimeCreatePublic',has_schema_privilege('{app}','public','CREATE'),
 'runtimeOwnerMembership',pg_has_role('{app}','{migrator}','MEMBER'));
\\o
\\echo Saved non-secret role result. No canonical V1 or business tables created.
"""
        body = '\\set ON_ERROR_STOP on\n'+body.replace('COMMIT;', finish, 1)
        body = '-- MANUAL ROLE SETUP ONLY: running this file approves these two roles and scoped ACL changes.\n'+body
        body = body.replace('-- DRAFT FOR REVIEW: NOT AUTHORIZATION TO RUN AGAINST SUPABASE.', '-- Not executed by assistant. Canonical V1 requires separate approval.')
        ca = PRIVATE/'certs'/(owner+'-ca.crt')
        if hashlib.sha256(ca.read_bytes()).hexdigest() != props[owner+'.caSha256']:
            raise ValueError('CA hash mismatch')
        script = output/(owner+'.setup-roles.psql')
        uri = 'postgresql://postgres.'+project+'@'+props[owner+'.host']+':5432/postgres?sslmode=verify-full&sslrootcert='+quote(ca.as_posix(), safe='')+'&connect_timeout=10&gssencmode=disable&application_name=checkinn-'+owner+'-role-setup'
        prepared.append((owner, script, body, ca, uri, info))
    output.mkdir()
    manifest = []
    for owner, script, body, ca, uri, info in prepared:
        script.write_text(body, encoding='utf-8', newline='\n')
        digest = hashlib.sha256(script.read_bytes()).hexdigest()
        ps = f"""# Manual {owner} roles/password setup. Hidden prompts; never pass passwords in arguments.
$ErrorActionPreference = 'Stop'
if ([Console]::IsInputRedirected -or [Console]::IsOutputRedirected) {{ throw 'Run in a regular interactive PowerShell window without redirection.' }}
$psql = '{(ROOT/'.local/tools/postgresql-17.11/pgsql/bin/psql.exe').as_posix()}'
$script = '{script.as_posix()}'
$ca = '{ca.as_posix()}'
if ((Get-FileHash -LiteralPath $ca -Algorithm SHA256).Hash.ToLowerInvariant() -ne '{props[owner+'.caSha256']}') {{ throw 'CA hash changed' }}
if ((Get-FileHash -LiteralPath $script -Algorithm SHA256).Hash.ToLowerInvariant() -ne '{digest}') {{ throw 'Script hash changed; review required' }}
Write-Host 'Project {owner} only. First prompt: EXISTING postgres database password for this project.'
Write-Host 'Next: NEW distinct migrator and app passwords, each twice. Save privately in {owner.upper()}_MIGRATION_DB_PASSWORD and {owner.upper()}_DB_PASSWORD in .env.'
Write-Host 'Running this file approves two dedicated roles, scoped public/default ACL changes, and LOGIN; no canonical migration.'
& $psql -X -W -v ON_ERROR_STOP=1 -d '{uri}' -f $script
if ($LASTEXITCODE -ne 0) {{ throw 'Setup failed. Stop; preserve error and do not rerun blindly or disable TLS.' }}
"""
        (output/('setup-'+owner+'.ps1')).write_text(ps, encoding='utf-8')
        manifest.append({'owner':owner,'scriptSha256':digest,'roles':[info['migrator'],info['runtimeRole']],
                         'reviewPolicySha256':props[owner+'.eventTriggerReviewSha256'],
                         'hostedExecuted':False,'migrationApproved':False})
    (output/'review-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n', encoding='utf-8')
    print('Prepared four independently scoped interactive role setup files; no hosted SQL executed.')


if __name__ == '__main__':
    prepare()
