"""Batch8D SQL-count regression on a NEW owned local PostgreSQL18 cluster only.

Same isolation contract as tools/batch7b-schema-verify.py: fresh cluster/database, canonical
V1 applied by the test through Flyway on the disposable database, no hosted inputs.
Runs CanonicalPostgresAcceptanceTest + PublicReadAmplificationPostgresTest for hotel.
"""
import datetime, importlib.util, json, os, re, socket, subprocess, time, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'Docs/evidence/batch-8d-read-optimization/local-disposable'
TESTS = ('CanonicalPostgresAcceptanceTest', 'PublicReadAmplificationPostgresTest')


def require_test_passed(row):
    if row['exitCode'] != 0 or row['summary'] != [1, 0, 0, 0] or not row['buildSuccess']:
        raise RuntimeError('Native acceptance requires one executed passing test without skips')


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main():
    spec = importlib.util.spec_from_file_location('batch5b_cluster', ROOT / 'docker/batch5b-local-cluster.py')
    cluster = importlib.util.module_from_spec(spec); spec.loader.exec_module(cluster)
    run_id = uuid.uuid4().hex
    private = ROOT / '.local' / ('batch8d-native-' + run_id)
    cluster.PRIVATE, cluster.DATA = private, private / 'postgres-data'
    with socket.socket() as check: check.bind(('127.0.0.1', 55486))
    if private.exists(): raise RuntimeError('Refuse existing run directory')
    results = dict(testRunId=run_id, hosted=False, postgresVersion=cluster.run('postgres', '--version').strip(), port=55486, runs=[], cleanup={})
    started = False
    try:
        import sys
        sys.argv = [__file__, 'start']; cluster.main(); started = True
        cfg = json.loads((private / 'connections.private.json').read_text())
        env = os.environ.copy(); env['PGPASSWORD'] = (private / 'postgres-password.private').read_text()
        connection = cfg['hotel']
        for test in TESTS:
            db = 'batch5b_hotel_' + str(int(time.time())) + '_' + str(os.getpid())
            cluster.run('createdb', '-h', '127.0.0.1', '-p', 55486, '-U', 'batch5b_admin', '-O', connection['migrator'], db, env=env)
            manifest = json.loads((private / 'cluster-owner.json').read_text()); manifest['databases'].append(db)
            (private / 'cluster-owner.json').write_text(json.dumps(manifest))
            cluster.run('psql', '-X', '-At', '-h', '127.0.0.1', '-p', 55486, '-U', 'batch5b_admin', '-d', db, '-v', 'ON_ERROR_STOP=1', '-c',
                        f"REVOKE ALL ON DATABASE {db} FROM PUBLIC; GRANT CONNECT ON DATABASE {db} TO {connection['app']}; REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO {connection['app']}", env=env)
            test_env = env.copy()
            test_env.update(BATCH5B_ACCEPTANCE='true', BATCH5B_DATABASE=db, BATCH5B_URL=f'jdbc:postgresql://127.0.0.1:55486/{db}?currentSchema=public',
                            BATCH5B_MIGRATION_PASSWORD=connection['migrationPassword'], BATCH5B_APP_PASSWORD=connection['appPassword'], BATCH5B_DATA_DIR=str(cluster.DATA.resolve()))
            log = private / (test + '.log')
            with log.open('w', encoding='utf-8') as output:
                proc = subprocess.run(['mvn.cmd', '-o', '-B', '-ntp', '-pl', 'hotel-service', '-Dtest=' + test, 'test'], cwd=ROOT, env=test_env, stdout=output, stderr=subprocess.STDOUT, timeout=600)
            text = log.read_bytes().decode('utf-8', errors='replace')
            summaries = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', text)
            counts = [line.split('BATCH8D_SQL_COUNT ', 1)[1].strip() for line in text.splitlines() if 'BATCH8D_SQL_COUNT ' in line]
            row = dict(test=test, database=db, exitCode=proc.returncode, buildSuccess='BUILD SUCCESS' in text,
                       summary=list(map(int, summaries[-1])) if summaries else None,
                       sqlCounts=[json.loads(c) for c in counts])
            results['runs'].append(row); print(test, row['exitCode'], row['summary'], flush=True)
            write('native-results.json', results)
            require_test_passed(row)
    finally:
        if started or (cluster.DATA / 'postmaster.pid').exists():
            assert cluster.DATA.resolve().parent == private.resolve() and private.parent.resolve() == (ROOT / '.local').resolve()
            cluster.run('pg_ctl', '-D', cluster.DATA, '-m', 'fast', '-w', 'stop')
            results['cleanup'] = dict(clusterStopped=not (cluster.DATA / 'postmaster.pid').exists(), hostedWrites=0, developmentDatabaseTouched=False)
        results['completedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        write('native-results.json', results)


if __name__ == '__main__':
    main()
