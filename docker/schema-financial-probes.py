from importlib.machinery import SourceFileLoader
from pathlib import Path
import json
h=SourceFileLoader('h',str(Path(__file__).with_name('schema-smoke.py'))).load_module()
assert json.loads(h.run(['docker','inspect',h.PG]).stdout)[0]['Config']['Labels']['com.docker.compose.project']==h.PROJECT
r=[]
def check(n,b):r.append({'test':n,'status':'PASS' if b else 'FAIL'});h.save('financial-constraint-results.json',r);assert b,n
def migration(db,p):h.sql(db,(h.ROOT/p).read_text(encoding='utf-8-sig'))
h.sql('postgres','CREATE DATABASE financial_probe_v2')
h.sql('financial_probe_v2',"CREATE TABLE payments(id varchar(255) PRIMARY KEY);CREATE TABLE bookings(id varchar(255) PRIMARY KEY);CREATE TABLE refund_requests(id varchar(255) PRIMARY KEY,status varchar(64));INSERT INTO payments VALUES ('p');INSERT INTO bookings VALUES ('b');INSERT INTO refund_requests VALUES ('r','APPROVED_PENDING_EXECUTION');")
for _ in range(2):
 migration('financial_probe_v2','docker/sql/manual-refund-v1-20261112.sql');migration('financial_probe_v2','docker/sql/sandbox-internal-refund-v1-20261112.sql')
h.sql('financial_probe_v2',"INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state) VALUES ('00000000-0000-0000-0000-000000000003','r','p','b',100,'VND','VNPAY','PENDING_MANUAL_REFUND');")
for n,s in [('refund-negative-amount',"UPDATE manual_refund_executions SET amount=-1"),('refund-without-evidence',"UPDATE manual_refund_executions SET state='PENDING_VERIFICATION'"),('refund-duplicate-payment',"INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state) VALUES ('00000000-0000-0000-0000-000000000004','r','p','b',100,'VND','VNPAY','PENDING_MANUAL_REFUND')")]:check(n,h.sql('financial_probe_v2',s,False).returncode!=0)
check('refund-orphan-fk',h.sql('financial_probe_v2',"INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state) VALUES ('00000000-0000-0000-0000-000000000005','absent-r','absent-p','absent-b',100,'VND','VNPAY','PENDING_MANUAL_REFUND')",False).returncode!=0)
h.sql('financial_probe_v2',"BEGIN; UPDATE manual_refund_executions SET state='NEEDS_MANUAL_REVIEW'; ROLLBACK;")
check('refund-rollback',h.sql('financial_probe_v2','SELECT state FROM manual_refund_executions').stdout.strip()=='PENDING_MANUAL_REFUND')
h.sql('postgres','CREATE DATABASE loyalty_probe')
# Minimal fixture derived from published Batch2 baseline, not a historical production schema.
h.sql('loyalty_probe',"CREATE TABLE users(id uuid PRIMARY KEY); CREATE TABLE points_transactions(id uuid PRIMARY KEY,user_id uuid NOT NULL,booking_id varchar(128),transaction_type varchar(16) NOT NULL); INSERT INTO users VALUES ('00000000-0000-0000-0000-000000000001'); INSERT INTO points_transactions VALUES ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','legacy','EARN');")
before=h.sql('loyalty_probe','SELECT json_agg(t ORDER BY id) FROM points_transactions t').stdout
for p in sorted((h.ROOT/'user-service/src/main/resources/db/migration').glob('V[345]*.sql')):h.sql('loyalty_probe',p.read_text())
check('loyalty-existing-ledger-preserved',before==h.sql('loyalty_probe','SELECT json_agg(t ORDER BY id) FROM points_transactions t').stdout)
h.sql('loyalty_probe',"INSERT INTO loyalty_commands(command_key,booking_id,command_type,user_id,result_points,created_at) VALUES ('c','b','EARN','00000000-0000-0000-0000-000000000001',1,now());")
check('loyalty-command-dedup',h.sql('loyalty_probe',"INSERT INTO loyalty_commands(command_key,booking_id,command_type,user_id,result_points,created_at) VALUES ('c2','b','EARN','00000000-0000-0000-0000-000000000001',1,now());",False).returncode!=0)
check('loyalty-guard-indexes',h.sql('loyalty_probe',"SELECT count(*) FROM pg_indexes WHERE indexname IN ('points_transactions_guard_user','points_transactions_guard_booking')").stdout.strip()=='2')
h.sql('postgres','CREATE DATABASE failed_upgrade_probe')
h.sql('failed_upgrade_probe',"CREATE TABLE payments(id varchar(255) PRIMARY KEY); CREATE TABLE bookings(id varchar(255) PRIMARY KEY); CREATE TABLE refund_requests(id varchar(255) PRIMARY KEY,status varchar(64)); INSERT INTO refund_requests VALUES ('synthetic-legacy','LEGACY_UNKNOWN');")
migration('failed_upgrade_probe','docker/sql/manual-refund-v1-20261112.sql')
before=h.snapshot('failed_upgrade_probe')
p=h.sql('failed_upgrade_probe',(h.ROOT/'docker/sql/sandbox-internal-refund-v1-20261112.sql').read_text(),False)
check('incompatible-legacy-upgrade-rejected',p.returncode!=0 and 'check constraint' in p.stderr)
check('failed-migration-ddl-and-data-rollback',before==h.snapshot('failed_upgrade_probe') and h.sql('failed_upgrade_probe',"SELECT status FROM refund_requests WHERE id='synthetic-legacy'").stdout.strip()=='LEGACY_UNKNOWN')
h.save('financial-probe-schema-manifest.json',{d:h.snapshot(d) for d in ['financial_probe_v2','loyalty_probe','failed_upgrade_probe']})
print(json.dumps(r,indent=2))
