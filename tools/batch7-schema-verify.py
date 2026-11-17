"""Reproduce canonical schema on a NEW, owned local PostgreSQL18 cluster only.

No hosted inputs or development database connections are accepted. Existing Batch5B
canonical tests supply Flyway/JPA/repository/permission assertions; catalog comparison
uses the recorded Batch5B PostgreSQL18 catalog, including constraint expressions.
"""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'Docs/evidence/batch-7-g2-provenance'
OWNERS = ('user', 'hotel', 'booking', 'revenue', 'regulations')


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def manifests():
    previous = json.loads((ROOT / 'Docs/evidence/batch-5b-canonical-flyway/migration-inventory.json').read_text())
    historical = json.loads((ROOT / 'Docs/evidence/batch-3-schema/schema-sources.json').read_text())
    records = []
    for owner in OWNERS:
        service = ROOT / (owner + '-service')
        canonical = service / 'src/main/resources/db/canonical/V1__canonical_schema.sql'
        digest = hashlib.sha256(canonical.read_bytes()).hexdigest()
        expected = next(row['sha256'] for row in previous if row['owner'] == owner)
        if digest != expected:
            raise RuntimeError('Applied V1 changed: ' + owner)
        scripts = []
        candidates = set(service.rglob('*.sql'))
        source_by_path = {}
        for source in historical:
            if source['service'] == owner and source['path'].endswith('.sql'):
                recorded = Path(source['path'])
                path = ROOT / Path(*recorded.parts[1:]) if recorded.parts[0] == 'checkinn' else ROOT.parent / recorded
                if path.is_file():
                    candidates.add(path.resolve())
                    source_by_path[path.resolve()] = source
        for path in sorted(candidates):
            if any(part in ('target', '.local') for part in path.parts):
                continue
            is_canonical = path == canonical
            is_security = 'security' in path.parts
            legacy = source_by_path.get(path.resolve(), {})
            scripts.append(dict(path=os.path.relpath(path, ROOT).replace('\\', '/'),
                                sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                                classification='CANONICAL_V1' if is_canonical else 'EXTERNAL_RUNTIME_GRANTS' if is_security else 'LEGACY_OR_TEST_ONLY',
                                canonicalReplayAllowed=is_canonical,
                                appliedHosted='V1 VERIFIED by Batch6B evidence' if is_canonical else 'Scoped runtime grants VERIFIED by Batch6B; external provisioning, not a Flyway version' if is_security else 'NOT APPLICABLE to canonical history; legacy applied history UNKNOWN',
                                replayRisk='fresh namespace only; existing V1 validated/no-op' if is_canonical else 'Explicit reviewed owner/role provisioning only; outside Flyway' if is_security else 'Do not run on canonical. Prerequisites/idempotence require individual legacy review',
                                historicalRisks=legacy.get('potential_conflicts', []),
                                historicalPrerequisites=legacy.get('required_predecessor_objects', [])))
        records.append(dict(owner=owner, location='classpath:db/canonical', history='public.flyway_schema_history',
                            independentDatabase=True, appliedVersion='1', v1MatchesBatch5B=True, scripts=scripts))
    write('migration-manifests.json', records)


def main():
    manifests()
    spec = importlib.util.spec_from_file_location('batch5b_cluster', ROOT / 'docker/batch5b-local-cluster.py')
    cluster = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cluster)
    run_id = uuid.uuid4().hex
    private = ROOT / '.local' / ('batch7-schema-' + run_id)
    cluster.PRIVATE, cluster.DATA = private, private / 'postgres-data'
    # Existing canonical tests explicitly require this loopback port and database pattern.
    with socket.socket() as check:
        check.bind(('127.0.0.1', 55486))
    if private.exists():
        raise RuntimeError('Refuse existing run directory')
    results = dict(testRunId=run_id, hosted=False, postgresVersion=cluster.run('postgres', '--version').strip(),
                   port=55486, privateDirectory=private.relative_to(ROOT).as_posix(), services=[], cleanup={})
    started = False
    try:
        sys.argv = [__file__, 'start']
        # Ownership is generated here; never loads historical Batch5B private state.
        cluster.main()
        started = True
        cfg = json.loads((private / 'connections.private.json').read_text())
        env = os.environ.copy()
        env['PGPASSWORD'] = (private / 'postgres-password.private').read_text()

        def query(db, sql):
            assert db in json.loads((private / 'cluster-owner.json').read_text())['databases']
            return cluster.run('psql', '-X', '-At', '-h', '127.0.0.1', '-p', 55486, '-U', 'batch5b_admin', '-d', db,
                               '-v', 'ON_ERROR_STOP=1', '-c', sql, env=env).strip()

        assert Path(query('batch5b_user', 'SHOW data_directory')).resolve() == cluster.DATA.resolve()
        for owner in OWNERS:
            connection = cfg[owner]
            db = 'batch5b_' + owner + '_' + str(int(time.time())) + '_' + str(os.getpid())
            cluster.run('createdb', '-h', '127.0.0.1', '-p', 55486, '-U', 'batch5b_admin', '-O', connection['migrator'], db, env=env)
            manifest = json.loads((private / 'cluster-owner.json').read_text())
            manifest['databases'].append(db)
            (private / 'cluster-owner.json').write_text(json.dumps(manifest))
            query(db, f"REVOKE ALL ON DATABASE {db} FROM PUBLIC; GRANT CONNECT ON DATABASE {db} TO {connection['app']}; REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO {connection['app']}")
            test_env = env.copy()
            test_env.update(BATCH5B_ACCEPTANCE='true', BATCH5B_DATABASE=db,
                            BATCH5B_URL=f'jdbc:postgresql://127.0.0.1:55486/{db}?currentSchema=public',
                            BATCH5B_MIGRATION_PASSWORD=connection['migrationPassword'], BATCH5B_APP_PASSWORD=connection['appPassword'],
                            BATCH5B_DATA_DIR=str(cluster.DATA.resolve()))
            command = ['mvn.cmd', '-o', '-B', '-ntp', '-pl', owner + '-service', '-Dtest=CanonicalPostgresAcceptanceTest', 'test']
            log = private / (owner + '-canonical.log')
            with log.open('w', encoding='utf-8') as output:
                test = subprocess.run(command, cwd=ROOT, env=test_env, stdout=output, stderr=subprocess.STDOUT, timeout=420)
            # Maven/JVM output can use the Windows console charset independently
            # of Python's redirected stream. Summary markers are ASCII.
            summaries = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', log.read_bytes().decode('utf-8', errors='replace'))
            row = dict(owner=owner, database=db, exitCode=test.returncode,
                       tests=list(map(int, summaries[-1])) if summaries else None,
                       passCanonical=test.returncode == 0 and bool(summaries) and summaries[-1] == ('1', '0', '0', '0'))
            results['services'].append(row)
            if not row['passCanonical']:
                raise RuntimeError(owner + ' canonical test failed; see private log')
            baseline = json.loads((ROOT / f'Docs/evidence/batch-5b-canonical-flyway/{owner}-catalog.json').read_text())
            catalog = dict(owner=owner, database=db)
            catalog_sql = {
                'columns': "SELECT cl.relname table_name,a.attname column_name,format_type(a.atttypid,a.atttypmod) sql_type,NOT a.attnotnull nullable,pg_get_expr(d.adbin,d.adrelid) default_expression,a.attidentity identity_kind FROM pg_attribute a JOIN pg_class cl ON cl.oid=a.attrelid JOIN pg_namespace ns ON ns.oid=cl.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE ns.nspname='public' AND cl.relkind='r' AND cl.relname<>'flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped",
                'constraints': "SELECT cl.relname table_name,con.conname name,con.contype kind,con.convalidated validated,pg_get_constraintdef(con.oid) definition FROM pg_constraint con JOIN pg_class cl ON cl.oid=con.conrelid JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE n.nspname='public' AND cl.relname<>'flyway_schema_history'",
                'indexes': "SELECT tablename table_name,indexname name,indexdef definition FROM pg_indexes WHERE schemaname='public' AND tablename<>'flyway_schema_history'",
                'sequences': "SELECT sequencename,sequenceowner,data_type,start_value,increment_by FROM pg_sequences WHERE schemaname='public'",
                'history': "SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank"
            }
            differences = {}
            for key, sql in catalog_sql.items():
                catalog[key] = json.loads(query(db, "SELECT COALESCE(json_agg(t),'[]') FROM (" + sql + ') t'))
                # Sequence owner is deliberately identical; no data/counter/current-value comparisons.
                normalize = lambda values: sorted(json.dumps(value, sort_keys=True) for value in values)
                if normalize(catalog[key]) != normalize(baseline[key]):
                    differences[key] = dict(expectedOnly=sorted(set(normalize(baseline[key])) - set(normalize(catalog[key]))), actualOnly=sorted(set(normalize(catalog[key])) - set(normalize(baseline[key]))))
            row['catalogDifferences'] = differences
            row['passCatalog'] = not differences
            write(owner + '-catalog.json', catalog)
            print(owner, 'canonical PASS; catalog', 'PASS' if not differences else 'FAIL', flush=True)
        if any(not service['passCatalog'] or not service['passCanonical'] for service in results['services']):
            raise RuntimeError('Canonical acceptance or catalog comparison failed')
    finally:
        if started or (cluster.DATA / 'postmaster.pid').exists():
            assert cluster.DATA.resolve().parent == private.resolve() and private.parent.resolve() == (ROOT / '.local').resolve()
            cluster.run('pg_ctl', '-D', cluster.DATA, '-m', 'fast', '-w', 'stop')
            results['cleanup'] = dict(clusterStopped=not (cluster.DATA / 'postmaster.pid').exists(), dataPreservedForReview=True,
                                      hostedWrites=0, developmentDatabaseTouched=False)
        results['completedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        write('schema-verification-' + run_id + '.json', results)
        write('schema-verification.json', results)


if __name__ == '__main__':
    main()
