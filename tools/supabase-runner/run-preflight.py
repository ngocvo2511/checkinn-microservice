"""Explicit private-env loader for ONE owner, read-only preflight only. Never invokes migrate."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OWNER_TOKEN_KEYS = {
    'user': 'USER_REGULATION_TOKEN',
    'regulations': 'USER_REGULATION_TOKEN',
    'hotel': 'HOTEL_BOOKING_TOKEN',
    'booking': 'HOTEL_BOOKING_TOKEN',
    'revenue': 'REVENUE_TOKEN',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('owner', choices=['user', 'hotel', 'booking', 'revenue', 'regulations'])
    modes=parser.add_mutually_exclusive_group()
    modes.add_argument('--runtime-check', action='store_true', help='Read-only dedicated runtime-role check; still no migrations')
    modes.add_argument('--tls-negatives', metavar='DISTINCT_CA_FILE', help='Two no-SQL TLS-negative probes to the approved endpoint')
    args = parser.parse_args()
    values = {}
    for line in (ROOT/'.local/batch6b/.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            if len(value) > 1 and value[0] == value[-1] and value[0] in ('"', "'"):
                value = value[1:-1]
            values[key.strip()] = value
    # Select exactly this owner's scoped PAT; never forward other groups' tokens.
    # A configured group token takes precedence; an invalid token must fail closed.
    token_key = OWNER_TOKEN_KEYS[args.owner]
    if values.get(token_key, '').strip():
        values['BATCH6B_MANAGEMENT_TOKEN'] = values[token_key]
    keys = [args.owner.upper() + suffix for suffix in ('_MIGRATION_DB_URL', '_MIGRATION_DB_USERNAME', '_MIGRATION_DB_PASSWORD')]
    keys += ['BATCH6B_MANAGEMENT_TOKEN', 'BATCH6B_TARGET_MANIFEST', 'BATCH6B_BACKEND_ROOT']
    if args.runtime_check:
        keys += [args.owner.upper()+suffix for suffix in ('_DB_URL','_DB_USERNAME','_DB_PASSWORD')]
    missing = [key for key in keys if not values.get(key, '').strip()]
    if missing:
        print('BLOCKED MISSING_KEYS ' + ','.join(missing))
        return 2
    if Path(values['BATCH6B_BACKEND_ROOT']).resolve() != ROOT:
        print('BLOCKED BACKEND_ROOT_MISMATCH')
        return 2
    manifest = Path(values['BATCH6B_TARGET_MANIFEST']).resolve()
    if not manifest.is_relative_to(ROOT/'.local/batch6b'):
        print('BLOCKED PRIVATE_MANIFEST_LOCATION')
        return 2
    pom = ET.parse(ROOT/'tools/supabase-runner/pom.xml').getroot()
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    version = pom.findtext('m:version', namespaces=namespace)
    flyway = pom.findtext('m:properties/m:flyway.version', namespaces=namespace)
    artifact = ROOT/f'tools/supabase-runner/target/supabase-runner-{version}.jar'
    dependency = ROOT/f'tools/supabase-runner/target/dependency-{flyway}'
    if not artifact.is_file() or not dependency.is_dir():
        print('BLOCKED BUILD_REQUIRED')
        return 2
    env = os.environ.copy()
    for key in OWNER_TOKEN_KEYS.values():
        env.pop(key, None)
    env.update({key: values[key] for key in keys})
    env.pop('BATCH6B_APPROVAL_FILE', None)
    command = ['java', '-cp', str(artifact) + os.pathsep + str(dependency/'*')]
    command += ['com.checkinn.db.TlsNegativeProbe',args.owner,args.tls_negatives] if args.tls_negatives else ['com.checkinn.db.RuntimeReadOnlyProbe',args.owner] if args.runtime_check else ['com.checkinn.db.HostedRunner', 'preflight', args.owner]
    result = subprocess.run(command, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=90)
    pattern = re.compile(r'(?:BLOCKED [A-Za-z0-9_]+|' + args.owner + r' (?:CONTROL_PLANE_IDENTITY_PASS|CLIENT_TLS_VERIFIED protocol=TLSv1\.[23]|POOLER_BACKEND_TLS_FALSE_MIGRATION_BLOCKED|TLS_NEGATIVE_(?:CA|HOST)_[A-Z_]+|RUNTIME_READONLY_PASS inheritedDatabaseTemp=(?:true|false)|PREFLIGHT_SQL_PASS inventorySha256=[a-f0-9]{64})|HOSTED_SECURITY_REVIEW_AND_EXACT_MIGRATION_APPROVAL_REQUIRED)')
    lines = result.stdout.splitlines()
    safe = [line for line in lines if pattern.fullmatch(line)]
    date = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).date().isoformat()
    mode='tls-negatives' if args.tls_negatives else 'runtime-readonly' if args.runtime_check else 'preflight'
    record = {'clientDate': date, 'owner': args.owner, 'mode': mode, 'exitCode': result.returncode,
              'safeOutput': safe, 'unpublishedLines': len(lines)-len(safe),
              'runnerVersion': version, 'runnerJarSha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
              'ddlDmlPerformed': False, 'migrationRequested': False, 'approvalFileLoaded': False}
    private = ROOT/'.local/batch6b'/f'{args.owner}.hosted-{mode}-{date}.json'
    private.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    for line in safe:
        print(line)
    print('PREFLIGHT_EXIT=' + str(result.returncode))
    return result.returncode


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, subprocess.TimeoutExpired):
        print('BLOCKED LOCAL_PREFLIGHT_WRAPPER_ERROR')
        raise SystemExit(2)
