"""Read live local PostgreSQL databases and restore new copies into checkinn-test.

Requires PostgreSQL 18 client binaries on the host. Refuses to overwrite target DBs.
Credentials come from resolved Compose config; dumps/metadata stay in ignored logs/.
"""
import datetime
import argparse
import json
import os
import secrets
from pathlib import Path
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
PG_BIN = Path(os.environ.get('CHECKINN_PG_BIN', 'C:/Program Files/PostgreSQL/18/bin'))
BASE = ['docker', 'compose', '--project-directory', str(ROOT), '--env-file', str(ROOT / '.env'),
        '-f', str(ROOT / 'docker-compose.yml')]
TEST = ['docker', 'compose', '-p', 'checkinn-test', '--project-directory', str(ROOT),
        '--env-file', str(ROOT / '.env'), '--env-file', str(ROOT / '.env.test'),
        '-f', str(ROOT / 'docker-compose.yml'), '-f', str(ROOT / 'docker-compose.test.yml')]
NAMES = ('user', 'hotel', 'booking', 'revenue', 'regulations')


def capture(command, **kwargs):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        # Database/tool errors may contain copied row data or URLs. Keep them private.
        error_file = ROOT / 'logs' / 'clone-error.log'
        error_file.parent.mkdir(exist_ok=True)
        error_file.write_bytes(result.stderr)
        raise RuntimeError(f'Command failed (exit {result.returncode}); private diagnostics: {error_file}')
    return result.stdout.decode('utf-8')


def inventory(query):
    tables = query("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
    counts = {}
    for table in tables:
        quoted = '"' + table.replace('"', '""') + '"'
        counts[table] = int(query(f'SELECT count(*) FROM public.{quoted}').strip())
    columns = query("SELECT table_name,column_name,data_type,is_nullable,coalesce(column_default,'') "
                    "FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position")
    return {'rows_per_table': counts, 'columns': columns.splitlines()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resume', action='store_true', help='Verify and reuse a partial clone before any app container was created')
    args = parser.parse_args()
    if not (ROOT / '.env').is_file():
        raise RuntimeError('Configure source database credentials in .env first.')
    if not (ROOT / '.env.test').exists():
        (ROOT / '.env.test').write_text('# Local test credentials; never commit.\nTEST_POSTGRES_PASSWORD='
                                      + secrets.token_urlsafe(36) + '\nNEXTAUTH_SECRET='
                                      + secrets.token_urlsafe(48) + '\n')
    if not (PG_BIN / 'pg_dump.exe').is_file():
        raise RuntimeError('Set CHECKINN_PG_BIN to the PostgreSQL 18 bin directory.')
    config = json.loads(capture(BASE + ['--profile', 'app', 'config', '--format', 'json']))
    resolved_test = json.loads(capture(TEST + ['--profile', 'app', 'config', '--format', 'json']))
    for name in NAMES:
        env = resolved_test['services'][name + '-service']['environment']
        assert env['SPRING_DATASOURCE_URL'] == f'jdbc:postgresql://postgres-test:5432/checkinn_{name}'
    assert resolved_test['services']['notification-service']['environment']['SPRING_MAIL_HOST'] == 'mailpit'
    print('PASS isolated target URLs and SMTP sink; source databases will only be read.', flush=True)
    subprocess.run(TEST + ['up', '-d', '--wait', '--wait-timeout', '120', 'postgres-test', 'mailpit'], check=True)
    existing = capture(TEST + ['exec', '-T', 'postgres-test', 'psql', '-X', '-U', 'checkinn_test', '-d',
                              'postgres', '-Atc', 'SELECT datname FROM pg_database']).splitlines()
    if set(existing) & {'checkinn_' + n for n in NAMES}:
        if not args.resume:
            raise RuntimeError('Target databases already exist. Refusing to overwrite; use --resume only for an interrupted clone.')
        containers = capture(TEST + ['--profile', 'app', '--profile', 'config', 'ps', '-a', '--format', 'json'])
        if any(json.loads(line)['Service'] not in ('postgres-test', 'mailpit') for line in containers.splitlines() if line.strip()):
            raise RuntimeError('Cannot resume cloning after app containers have been created.')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    snapshots = ROOT / 'logs' / 'db-clones' / stamp
    snapshots.mkdir(parents=True, exist_ok=False)
    manifest = {}
    for name in NAMES:
        source = config['services'][name + '-service']['environment']
        url = urlsplit(source['SPRING_DATASOURCE_URL'].removeprefix('jdbc:'))
        if url.scheme != 'postgresql' or url.hostname not in ('localhost', '127.0.0.1', 'host.docker.internal'):
            raise RuntimeError(f'{name}: expected an explicitly local source PostgreSQL URL.')
        source_db = url.path.lstrip('/')
        if not source_db or '/' in source_db:
            raise RuntimeError(f'{name}: invalid source database name.')
        conn = ['-h', 'localhost', '-p', str(url.port or 5432), '-U', source['SPRING_DATASOURCE_USERNAME'], '-d', source_db]
        process_env = os.environ.copy()
        process_env.update(PGPASSWORD=source['SPRING_DATASOURCE_PASSWORD'], PGCONNECT_TIMEOUT='8',
                           PGOPTIONS='-c default_transaction_read_only=on')
        def source_query(sql):
            return capture([str(PG_BIN / 'psql.exe'), '-X', '-w', *conn, '-Atc', sql], env=process_env)
        before = inventory(source_query)
        dump = snapshots / (name + '.dump')
        capture([str(PG_BIN / 'pg_dump.exe'), '-w', *conn, '--no-owner', '--no-acl', '-Fc', '-f', str(dump)], env=process_env)
        target_db = 'checkinn_' + name
        if target_db not in existing:
            capture(TEST + ['exec', '-T', 'postgres-test', 'createdb', '-U', 'checkinn_test', target_db])
            with dump.open('rb') as stream:
                capture(TEST + ['exec', '-T', 'postgres-test', 'pg_restore', '-U', 'checkinn_test', '-d', target_db,
                                '--no-owner', '--no-acl', '--exit-on-error'], stdin=stream)
        def target_query(sql):
            return capture(TEST + ['exec', '-T', 'postgres-test', 'psql', '-X', '-U', 'checkinn_test', '-d', target_db, '-Atc', sql])
        after = inventory(target_query)
        if before != after:
            raise RuntimeError(f'{name}: source/clone inventory differs; do not start apps. Source may have changed while dumping.')
        manifest[name] = {'source_database': source_db, 'target_database': target_db,
                          'tables': len(before['rows_per_table']), 'rows': sum(before['rows_per_table'].values()),
                          'rows_per_table': before['rows_per_table'], 'dump': dump.name}
        (snapshots / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(f'PASS {name}: live pg_dump restored; table/row counts and column metadata match.', flush=True)
    print(f'PASS all 5 clones verified. Private snapshots: {snapshots}', flush=True)


if __name__ == '__main__':
    main()
