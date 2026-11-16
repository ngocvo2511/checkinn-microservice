"""Test real catalog guards in a NEW loopback cluster; preserve files and stop on completion."""
from pathlib import Path
import os, subprocess, secrets, socket, uuid, json, sys, tempfile, argparse, importlib.util

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--canonical-roles', action='store_true', help='Test five canonical V1s and review SQL against a NEW PostgreSQL17 cluster')
args = parser.parse_args()

root = Path(__file__).resolve().parents[2]
pg = Path(os.environ.get('BATCH6B_PG_BIN', 'C:/Program Files/PostgreSQL/18/bin'))
version = subprocess.check_output([str(pg/'postgres.exe'), '--version'], text=True).strip()
if args.canonical_roles and 'PostgreSQL) 17.' not in version:
    raise RuntimeError('Canonical role acceptance requires PostgreSQL17 binaries')
private = root / '.local' / ('batch6b-guard-' + uuid.uuid4().hex)
private.mkdir(parents=True, exist_ok=False)
data = private / 'data'
password = secrets.token_urlsafe(32)
pw = private / 'password.private'
pw.write_text(password)
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
env = os.environ.copy()
env['PGPASSWORD'] = password

def run(name, *args):
    # Windows background postgres can inherit pipe handles; real temporary files avoid hanging.
    with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
        result = subprocess.run([str(pg / (name + '.exe')), *map(str, args)], env=env,
                                stdout=out, stderr=err, timeout=90)
    if result.returncode:
        raise RuntimeError(name + ' failed (output withheld)')

started = False
try:
    run('initdb', '-D', data, '-U', 'batch6b_guard_admin', '--pwfile', pw,
        '--auth-host=scram-sha-256', '--auth-local=scram-sha-256', '--encoding=UTF8', '--locale=C')
    with (data / 'postgresql.conf').open('a') as f:
        f.write(f"\nlisten_addresses='127.0.0.1'\nport={port}\ncluster_name='batch6b_disposable_guards'\n")
    run('pg_ctl', '-D', data, '-l', private / 'server.log', '-w', 'start')
    started = True
    run('createdb', '-h', '127.0.0.1', '-p', port, '-U', 'batch6b_guard_admin', 'batch6b_guard')
    run('psql', '-X', '-h', '127.0.0.1', '-p', port, '-U', 'batch6b_guard_admin', '-d', 'batch6b_guard', '-v', 'ON_ERROR_STOP=1', '-c', 'CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role;')
    env.update(BATCH6B_LOCAL_GUARD_TEST='true', BATCH6B_LOCAL_TEST_PASSWORD=password,
               BATCH6B_LOCAL_TEST_URL=f'jdbc:postgresql://127.0.0.1:{port}/batch6b_guard')
    if args.canonical_roles:
        run('createdb', '-h', '127.0.0.1', '-p', port, '-U', 'batch6b_guard_admin', 'batch6b_events')
        spec = importlib.util.spec_from_file_location('role_review', root/'tools/supabase-runner/prepare-role-review.py')
        review = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(review)
        plans = private/'plans'
        plans.mkdir()
        for owner in review.OWNERS:
            db = 'batch6b_roles_' + owner
            run('createdb', '-h', '127.0.0.1', '-p', port, '-U', 'batch6b_guard_admin', db)
            scripts, info = review.render(owner, 'abcdefghijklmnopqrst', database=db, administrator='batch6b_guard_admin')
            dest = plans/owner
            dest.mkdir()
            for name, body in scripts.items():
                (dest/name).write_text(body, encoding='utf-8')
            (dest/'inventory.json').write_text(json.dumps(info), encoding='utf-8')
        env.update(BATCH6B_LOCAL_CANONICAL_TEST='true', BATCH6B_LOCAL_PLAN_DIR=str(plans), BATCH6B_LOCAL_TEST_ROOT=str(root))
    with (private / 'tests.log').open('w') as log:
        result = subprocess.run(['mvn.cmd', '-o', '-B', '-ntp', '-f', 'tools/supabase-runner/pom.xml', 'test'],
                                cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    print('Local catalog guard Maven exit:', result.returncode)
    evidence = 'pg17-role-validation-2026-11-15.json' if args.canonical_roles else 'local-catalog-test.json'
    (root / 'Docs/evidence/batch-6b-supabase-local-integration'/evidence).write_text(
        json.dumps({'exitCode': result.returncode, 'scope': 'five fresh local canonical V1/role plans' if args.canonical_roles else 'new isolated loopback PostgreSQL guard tests only',
                    'postgresVersion':version,
                    'hosted': False, 'privateLog': str(private.relative_to(root) / 'tests.log')}, indent=2)+'\n')
    sys.exit(result.returncode)
finally:
    if started:
        # Exact freshly generated directory, never an externally supplied existing target.
        assert data.resolve().parent == private.resolve() and private.parent.resolve() == (root / '.local').resolve()
        run('pg_ctl', '-D', data, '-m', 'fast', '-w', 'stop')
