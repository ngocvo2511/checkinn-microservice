"""Complete test-only bootstrap after negative clean probes; Batch3-owned targets only."""
from importlib.machinery import SourceFileLoader
from pathlib import Path
import json,time,re,datetime
h=SourceFileLoader('h',str(Path(__file__).with_name('schema-smoke.py'))).load_module()
assert json.loads(h.run(['docker','inspect',h.PG]).stdout)[0]['Config']['Labels']['com.docker.compose.project']==h.PROJECT
receipts=[];results=[]
def apply(db,path):
 p=h.sql(db,(h.ROOT/path).read_text(encoding='utf-8-sig'),False)
 (h.RAW/(db+'-'+Path(path).name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
 receipts.append({'database':db,'source':path,'status':'HISTORY_CONFIRMED' if not p.returncode else 'CONFLICT','error':p.stderr.strip() if p.returncode else None,'scope':'Batch3 TEST_ONLY; production UNKNOWN'})
 h.save('test-applied-history.json',receipts)
 return p
# Hotel: historical accommodation SQL collides with JPA-created tables, keep existing compatibility bootstrap explicit.
for p in ['hotel-service/src/main/resources/db/migration/V4__Room_Availability.sql','docker/sql/hold-idempotency-20261105.sql','docker/sql/amenity-catalog-v1-20261106.sql','docker/booking-smoke-accommodation-bootstrap.sql','docker/sql/hotel-draft-approval-upgrade.sql','docker/sql/room-inventory-review-v1-20261107.sql','docker/sql/room-description-20261105.sql','docker/sql/review-response-integrity-20261109.sql']:
 assert apply('current_hotel',p).returncode==0,p
for p in ['booking-service/src/main/resources/db/migration/V3__Cancellation_Policy_And_Refund_Requests.sql','docker/sql/booking-idempotency-20261105.sql','docker/sql/onsite-reservation-v1-20261105.sql','docker/sql/paid-reservation-v1-20261105.sql','docker/sql/loyalty-settlement-upgrade.sql','docker/sql/booking-transaction-time-v1-20261107.sql','docker/sql/booking-event-outbox-v1-20261107.sql','docker/sql/manual-refund-v1-20261112.sql','docker/sql/sandbox-internal-refund-v1-20261112.sql','docker/sql/booking-vnpay-create-date-v1-20261112.sql','docker/sql/inventory-review-audit-20261106.sql']:
 assert apply('current_booking',p).returncode==0,p
for p in ['docker/sql/revenue-event-receipts-v1-20261107.sql','docker/sql/revenue-source-versions-v1-20261107.sql','docker/sql/revenue-history-gaps-v1-20261107.sql','docker/sql/revenue-cash-transactions-v1-20261107.sql','docker/sql/revenue-sandbox-refund-v1-20261112.sql']:
 assert apply('current_revenue_service',p).returncode==0,p
assert apply('current_regulations_service','docker/sql/regulation-event-outbox-v1-20261107.sql').returncode==0
# Separate new User database with the exact Batch2 synthetic baseline. This is NOT a production V1.
h.sql('postgres','CREATE DATABASE user_testonly')
s=(h.ROOT/'docker/booking-smoke-init.sql').read_text();s=s.split('\\connect checkinn_smoke_user')[1]
h.sql('user_testonly',s)
name=h.PROJECT+'-user-testonly'
args=['docker','run','-d','--name',name,'--label','com.docker.compose.project='+h.PROJECT,'--network',h.PROJECT+'_schema']
for k,v in {'SPRING_DATASOURCE_URL':'jdbc:postgresql://'+h.PG+':5432/user_testonly','SPRING_DATASOURCE_USERNAME':'schema_runner','SPRING_DATASOURCE_PASSWORD':'','SPRING_JPA_HIBERNATE_DDL_AUTO':'update','SECRET_KEY':'batch3-disposable-only-secret-key-012345678901234567890123456789','EUREKA_CLIENT_ENABLED':'false','SPRING_BOOT_ADMIN_CLIENT_ENABLED':'false','SPRING_RABBITMQ_HOST':'no-broker','SPRING_DATA_REDIS_HOST':'no-redis'}.items():args+=['-e',k+'='+v]
user_since=datetime.datetime.now(datetime.timezone.utc).isoformat()
h.run(args+['checkinn/user-service:local'])
for svc,name in [('user',name),('hotel',h.PROJECT+'-hotel'),('booking',h.PROJECT+'-booking'),('revenue',h.PROJECT+'-revenue'),('regulations',h.PROJECT+'-regulations')]:
 since=user_since if svc=='user' else datetime.datetime.now(datetime.timezone.utc).isoformat()
 if svc!='user':h.run(['docker','start',name])
 for _ in range(65):
  p=h.run(['docker','logs','--since',since,name]);log=p.stdout+p.stderr
  # docker start retains prior logs: evaluate only latest JVM launch.
  if ':: Spring Boot ::' in log: log=log.rsplit(':: Spring Boot ::',1)[1]
  if re.search(r'Started \w+Application',log) or 'Application run failed' in log:
   time.sleep(5);p=h.run(['docker','logs','--since',since,name]);log=p.stdout+p.stderr
   if ':: Spring Boot ::' in log:log=log.rsplit(':: Spring Boot ::',1)[1]
   break
  time.sleep(1)
 running=h.run(['docker','inspect','--format','{{.State.Running}}',name]).stdout.strip()=='true'
 (h.RAW/('testonly-final-'+svc+'.log')).write_text(log,encoding='utf-8')
 results.append({'service':svc,'status':'PASS' if running and re.search(r'Started \w+Application',log) and 'Application run failed' not in log else 'BLOCKED','scope':'JPA/startup only; no broker/redis/gateway acceptance; existing image, source identity NOT VERIFIED','running_after_startup_grace':running,'errors':[x for x in log.splitlines() if 'Caused by:' in x][-5:]})
 h.run(['docker','stop','-t','3',name]);h.save('testonly-startup-results.json',results)
manifest={db:h.snapshot(db) for db in ['current_hotel','current_booking','current_revenue_service','current_regulations_service','user_testonly']}
h.save('testonly-final-schema-manifest.json',manifest)
h.save('user-flyway-history.json',json.loads(h.sql('user_testonly',"SELECT json_agg(t ORDER BY installed_rank) FROM (SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history) t").stdout))
print(json.dumps(results,indent=2))
