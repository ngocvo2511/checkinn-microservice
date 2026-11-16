"""Prepare one manually reviewed User role transaction. No network or SQL execution."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / '.local/batch6b'


def prepare():
    props = dict(line.split('=', 1) for line in (PRIVATE/'targets.properties').read_text(encoding='utf-8').splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    state = json.loads((PRIVATE/'user.preflight-review-2026-11-15.json').read_text(encoding='utf-8'))
    if state.get('userConfirmedDataApiEnabled') is not False:
        raise ValueError('User must confirm disabling Data API before preparing this action')
    policy_file = Path(props['user.eventTriggerReviewFile'])
    if hashlib.sha256(policy_file.read_bytes()).hexdigest() != props['user.eventTriggerReviewSha256']:
        raise ValueError('Review policy hash mismatch')
    policy = json.loads(policy_file.read_text(encoding='utf-8'))
    if policy.get('formatVersion') != 1 or policy.get('status') != 'STATIC_REVIEWED_NOT_MIGRATION_APPROVAL' or policy.get('serverMajor') != 17 or len(policy.get('eventTriggers', [])) != 6:
        raise ValueError('Exact six-event PostgreSQL17 static review required')
    if policy['owner'] != 'user' or policy['project'] != props['user.project'] or policy['v1Sha256'] != props['user.v1Sha256']:
        raise ValueError('Review policy scope mismatch')
    spec = importlib.util.spec_from_file_location('roles', ROOT/'tools/supabase-runner/prepare-role-review.py')
    roles = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(roles)
    scripts, info = roles.render('user', props['user.project'])
    if info['v1Sha256'] != props['user.v1Sha256']:
        raise ValueError('Canonical V1 source hash changed')
    java = (ROOT/'tools/supabase-runner/src/main/java/com/checkinn/db/HostedRunner.java').read_text(encoding='utf-8')
    match = re.search(r'static final String EVENT_SQL=("(?:\\.|[^"\\])*");', java)
    if not match:
        raise ValueError('Missing canonical event descriptor query')
    event_query = json.loads(match.group(1))
    expected = json.dumps(sorted(policy['eventTriggers'], key=lambda item: item['name']))
    if '$expected_user_events$' in expected:
        raise ValueError('Unsafe quote delimiter')
    precheck = f"""DO $user_objects$
DECLARE observed jsonb;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relnamespace='public'::regnamespace)
     OR EXISTS (SELECT 1 FROM pg_proc WHERE pronamespace='public'::regnamespace)
     OR EXISTS (SELECT 1 FROM pg_type WHERE typnamespace='public'::regnamespace AND typtype<>'p') THEN
    RAISE EXCEPTION 'User public namespace changed; stop and review new objects';
  END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(events) ORDER BY events.name),'[]'::jsonb)
    INTO observed FROM ({event_query}) events;
  IF observed IS DISTINCT FROM $expected_user_events${expected}$expected_user_events$::jsonb THEN
    RAISE EXCEPTION 'User event/function metadata changed; stop before role creation';
  END IF;
END
$user_objects$;
"""
    body = scripts['01-provision-roles.sql']
    marker = 'BEGIN;\n'
    if body.count(marker) != 1:
        raise ValueError('Unexpected transaction layout')
    attestation = f"""BEGIN;
-- By choosing to run this entire transaction, the operator confirms the exact Dashboard
-- project/service below, Data API disabled, and approval ONLY of the listed NOLOGIN roles/default ACL.
-- This is not independent server identity proof or V1/runtime/security acceptance.
SET LOCAL checkinn.approved_project_ref='{props['user.project']}';
SET LOCAL checkinn.approved_service='user';
SET LOCAL checkinn.security_reviewed='true';
"""
    body = body.replace(marker, attestation, 1)
    body = body.replace('DO $fresh$', precheck+'DO $fresh$', 1)
    result = """DO $app_denials$
BEGIN
  IF has_schema_privilege('checkinn_user_app','public','CREATE')
     OR pg_has_role('checkinn_user_app','checkinn_user_migrator','MEMBER') THEN
    RAISE EXCEPTION 'App schema/owner permissions unexpectedly broad';
  END IF;
END
$app_denials$;
SELECT jsonb_build_object(
  'operation','USER_NOLOGIN_ROLES_ONLY',
  'database',current_database(),
  'businessTablesCreated',false,
  'roles',(SELECT jsonb_agg(jsonb_build_object('name',rolname,'login',rolcanlogin,
      'superuser',rolsuper,'createDb',rolcreatedb,'createRole',rolcreaterole,
      'bypassRls',rolbypassrls,'connectionLimit',rolconnlimit) ORDER BY rolname)
      FROM pg_roles WHERE rolname IN ('checkinn_user_migrator','checkinn_user_app')),
  'runtimeCreatePublic',has_schema_privilege('checkinn_user_app','public','CREATE'),
  'runtimeOwnerMembership',pg_has_role('checkinn_user_app','checkinn_user_migrator','MEMBER')
) AS checkinn_user_role_result;
COMMIT;
"""
    if body.count('COMMIT;') != 1:
        raise ValueError('Unexpected commit layout')
    body = body.replace('COMMIT;', result, 1)
    body = '-- MANUAL USER-ONLY ACTION: execute ONLY if you approve this precise role/security plan.\n' + body
    body = body.replace('-- DRAFT FOR REVIEW: NOT AUTHORIZATION TO RUN AGAINST SUPABASE.',
                        '-- Prepared for operator review/approval; this file has NOT been executed by the assistant.')
    output = PRIVATE/'user.provision-nologin-roles.review.sql'
    if output.exists():
        raise FileExistsError('Refuse overwriting an existing review action')
    output.write_text(body, encoding='utf-8')
    record = {'status':'READY_FOR_USER_REVIEW_NOT_APPLIED', 'owner':'user', 'project':props['user.project'],
              'sourceManifestSha256':hashlib.sha256((PRIVATE/'targets.properties').read_bytes()).hexdigest(),
              'reviewPolicySha256':props['user.eventTriggerReviewSha256'],
              'actionFile':str(output), 'actionSha256':hashlib.sha256(output.read_bytes()).hexdigest(),
              'roles':[info['migrator'],info['runtimeRole']], 'loginEnabled':False,'passwordsCreated':False,
              'migrationApproved':False,'hostedActionByAssistant':False}
    (PRIVATE/'user.role-action-review.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print('Prepared one User-only NOLOGIN role transaction with fresh-object and exact-event checks. No SQL executed.')


if __name__ == '__main__':
    prepare()
