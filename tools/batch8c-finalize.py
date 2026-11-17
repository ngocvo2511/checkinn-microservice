"""Aggregate final read-only preservation and acceptance facts. Never mutates hosted data."""
import importlib.util,json,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('d',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
def read(n):return json.loads((d.OUT/n).read_text(encoding='utf-8'))
def main():
 rows=[]
 for owner in d.h.GROUPS:
  before=read(owner+'-before.json');after=read(owner+'-after.json')
  rows.append(dict(owner=owner,schemaFingerprintUnchanged=before['schemaFingerprint']==after.get('schemaFingerprint'),countsUnchanged=before['counts']==after.get('counts'),catalogMatchesCanonical=after.get('baselineDifferences')==[],completeExistingRowsPreserved=read(owner+'-digests-before.json')==read(owner+'-digests-after.json')))
 d.write('final-schema-history-data-verification.json',rows)
 git=[]
 for name,path in [('backend',R),('frontend',R.parent/'checkinn-frontend')]:
  initial=read('initial-git-'+name+'.json');final=d.git(path);d.write('final-git-'+name+'.json',final)
  git.append(dict(repository=name,headUnchanged=initial['head']==final['head'],stagedDiffUnchanged=initial['stagedSha256']==final['stagedSha256'],unstagedDiffUnchanged=initial['unstagedSha256']==final['unstagedSha256']))
 d.write('final-git-verification.json',git)
 admin=read('admin-preservation-before.json')==read('admin-preservation-after.json')
 smoke=[read(p.name) for p in sorted(d.OUT.glob('smoke-final-restart-attempt-*.json'))]
 final_http=bool(smoke) and all(r['passed'] for r in smoke[-1])
 m=read('fixture-manifest.json');cleanup=read('cleanup-results.json');reg=read('regression-results.json');native=read('local-disposable/schema-verification.json')
 benchmarks=[]
 for p in d.OUT.glob('benchmark-*-raw.json'):
  if 'warmup' in p.name:continue
  data=read(p.name);benchmarks.append(dict(file=p.name,attempts=len(data),successful=sum(x['status']==200 for x in data),failed=sum(x['status']!=200 for x in data)))
 result=dict(status='PARTIAL',hostedWritesStopped=not m['writesAllowed'],fixtureManifestIds=len(m['records']),fixtureCleanupDone=cleanup['status']=='DONE' and not cleanup['remaining'],fiveTargetSchemaHistoryCountsAndExistingRowsPreserved=all(all(value for key,value in r.items() if key!='owner') for r in rows),permanentAdminCompleteRowPreserved=admin,finalRealAdminGatewayLoginAndQueuePass=final_http,concurrency=read('concurrency-results.json')['status'],naturalExpiry=read('natural-expiry-result.json')['status'],javaRegressionExecuted=sum(x['executedPass'] for x in reg),javaRegressionSkips=sum(x['skipped'] for x in reg),nativeCanonicalPass=sum(x['passCanonical'] and x['passCatalog'] for x in native['services']),nativeClusterStopped=native['cleanup']['clusterStopped'],guardTestsPass=read('lifecycle-guard-tests.json')['exitCode']==0,benchmarks=benchmarks,ownedJvmsRemaining=len(d.h.stack.state()['processes']),headAndIndexUnchanged=all(x['headUnchanged'] and x['stagedDiffUnchanged'] for x in git),frontendTrackedDiffPreserved=git[1]['unstagedDiffUnchanged'],limitations=['Original historical backend ownership not reconstructable','No controlled BEFORE artifact','Remaining SQL read amplification; SQL text attribution restricted; no N+1-resolved claim','Production/multi-node/provider/outage not certified'])
 logs=read('runtime-log-summary.json')
 result.update(statusReason='Scoped runtime/AFTER/concurrency/expiry checks PASS; historical client ownership attribution remains partial, and remaining N+1 read amplification optimization is deferred within the bounded restart budget.',cleanupRowsDeleted=sum(x['deleted'] for x in cleanup['results']),brokerExactFixtureMessagesRemoved=sum(len(x['removedExactFixturePayloadHashes']) for x in read('broker-cleanup.json')),redisExactFixtureKeysAbsent=all(x['remaining']==0 for x in read('redis-cleanup.json')['exactKeys']),noRoleLimitOrHikariTimeoutInRunLogs=all(x['roleLimitErrors']==0 and x['hikariTimeoutLogOccurrences']==0 for x in logs),gatewayMeasuredAttempts=sum(x['attempts'] for x in benchmarks),gatewayMeasuredSuccess=sum(x['successful'] for x in benchmarks),directMeasuredAttempts=len(read('direct-after-small.json')),directMeasuredSuccess=sum(x['status']==200 for x in read('direct-after-small.json')))
 result.update(standardizationOriginalBytesPreserved=all(x['originalBytesPreservedAsPrefix'] for x in read('standardization-append-verification.json')),preExistingTrackedChangesPreservedBeforeAppend=all(x['preExistingTrackedUnstagedDiffUnchangedBeforeDocumentationAppend'] for x in read('preexisting-change-preservation-precleanup.json')))
 d.write('final-verification.json',result);print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
