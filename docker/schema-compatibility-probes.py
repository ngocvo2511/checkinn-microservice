"""Follow-up probes: only Batch3-owned PostgreSQL. No other target accepted."""
from importlib.machinery import SourceFileLoader
h=SourceFileLoader('schema',str(__import__('pathlib').Path(__file__).with_name('schema-smoke.py'))).load_module()
from pathlib import Path
import json,uuid
R=h.ROOT;results=[];snapshots={}
info=json.loads(h.run(['docker','inspect',h.PG]).stdout)[0]
assert info['Config']['Labels']['com.docker.compose.project']==h.PROJECT
assert info['Config']['Image']=='postgres:18-alpine'
def new(db): h.sql('postgres','CREATE DATABASE '+db)
def apply(db,path):
 p=h.sql(db,(R/path).read_text(encoding='utf-8-sig'),False)
 (h.RAW/(db+'-'+Path(path).name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
 return p
def check(name,condition,detail):
 results.append({'test':name,'status':'PASS' if condition else 'FAIL','detail':detail});h.save('compatibility-results.json',results);assert condition,name
# Historical migration file is source-derived, real old deployment is UNKNOWN.
base='docker/sql/revenue-cash-transactions-v1-20261107.sql';upgrade='docker/sql/revenue-sandbox-refund-v1-20261112.sql'
for db in ['cash_clean_v2','cash_upgrade_v2']:
 new(db);assert apply(db,base).returncode==0
 if db=='cash_upgrade_v2':
  h.sql(db,"INSERT INTO revenue_cash_transactions(id,source_event_id,source_version,payment_id,kind,transaction_id,occurred_at,booking_id,customer_id,hotel_id,check_in_date,check_out_date,nights,rooms,amount,payment_method) VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',1,'synthetic-payment','CREDIT','synthetic-receipt','2026-11-01T03:00:00Z','synthetic-booking','synthetic-customer','synthetic-hotel','2026-12-01','2026-12-02',1,1,1000000.50,'VNPAY');")
  before=h.sql(db,"SELECT (to_jsonb(t))::text FROM revenue_cash_transactions t ORDER BY id").stdout
  snapshots['cash_before_upgrade']=h.snapshot(db)
 assert apply(db,upgrade).returncode==0;assert apply(db,upgrade).returncode==0
 snapshots[db]=h.snapshot(db)
 if db=='cash_upgrade_v2':
  after=h.sql(db,"SELECT (to_jsonb(t)-'execution_mode')::text FROM revenue_cash_transactions t ORDER BY id").stdout
  check('cash-original-fields-preserved',before==after,'Source-derived historical table; synthetic original receipt unchanged, nullable execution_mode not inferred')
  check('cash-mode-null',h.sql(db,"SELECT execution_mode IS NULL FROM revenue_cash_transactions").stdout.strip()=='t','No automatic provider/internal classification')
check('cash-clean-upgrade-schema-match',snapshots['cash_clean_v2']==snapshots['cash_upgrade_v2'],'Exact normalized catalog equality for cash table only, not whole Revenue')
for label,statement in [('negative-cash',"UPDATE revenue_cash_transactions SET amount=-1"),('duplicate-payment-kind',"INSERT INTO revenue_cash_transactions SELECT '00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004'::uuid,source_version,payment_id,kind,transaction_id,occurred_at,booking_id,customer_id,hotel_id,room_type_id,check_in_date,check_out_date,nights,rooms,amount,payment_method,recorded_at,execution_mode FROM revenue_cash_transactions")]:
 check(label,h.sql('cash_upgrade_v2',statement,False).returncode!=0,'PostgreSQL rejects invalid cash write; no repair')
h.sql('cash_upgrade_v2',"BEGIN; UPDATE revenue_cash_transactions SET amount=2; ROLLBACK;")
check('cash-transaction-rollback',h.sql('cash_upgrade_v2','SELECT amount FROM revenue_cash_transactions').stdout.strip()=='1000000.50','Synthetic amount unchanged after rollback')
# Inventory standalone review DDL is independent of physical stock and must not mutate it.
new('inventory_probe_v2');h.sql('inventory_probe_v2','CREATE TABLE room_types(id uuid PRIMARY KEY,total_rooms integer); INSERT INTO room_types VALUES (gen_random_uuid(),NULL),(gen_random_uuid(),10),(gen_random_uuid(),-1);')
before=h.sql('inventory_probe_v2','SELECT json_agg(t ORDER BY id) FROM room_types t').stdout
for _ in range(2): assert apply('inventory_probe_v2','docker/sql/room-inventory-review-v1-20261107.sql').returncode==0
check('inventory-no-backfill',before==h.sql('inventory_probe_v2','SELECT json_agg(t ORDER BY id) FROM room_types t').stdout,'Synthetic NULL/10/negative values preserved; invalid values require review')
check('inventory-no-auto-approval',h.sql('inventory_probe_v2','SELECT count(*) FROM room_inventory_review_requests').stdout.strip()=='0','No fabricated proposals, approvals or stock changes')
check('inventory-negative-proposal-blocked',h.sql('inventory_probe_v2',"INSERT INTO room_inventory_review_requests(id,room_id,hotel_id,proposed_quantity,source,requested_by,status) VALUES (gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),-1,'synthetic',gen_random_uuid(),'PENDING')",False).returncode!=0,'Positive quantity CHECK enforced')
snapshots['inventory_probe_v2']=h.snapshot('inventory_probe_v2')
# Show historical syntax defect after minimal synthetic prerequisite; not representative production baseline.
new('hotel_legacy_probe_v2');h.sql('hotel_legacy_probe_v2','CREATE TABLE hotels(id uuid PRIMARY KEY);')
check('hotel-v1-synthetic-prerequisite',apply('hotel_legacy_probe_v2','hotel-service/src/main/resources/db/migration/V1__Create_City_Table.sql').returncode==0,'Only empty synthetic hotels relation, no claim of complete historical baseline')
p=apply('hotel_legacy_probe_v2','hotel-service/src/main/resources/db/migration/V2__City_Add_Type_Parent_HotelCount.sql')
check('hotel-v2-syntax-defect-reproduced',p.returncode!=0 and 'syntax error' in p.stderr,'ADD CONSTRAINT IF NOT EXISTS is rejected by PostgreSQL 18; history not rewritten')
p=apply('current_hotel','hotel-service/src/main/resources/db/migration/V5__Add_Hold_Expiry.sql')
check('hotel-v5-jpa-conflict-reproduced',p.returncode!=0 and 'already exists' in p.stderr,'JPA expiry column collides with one-time V5')
# JSON/text database round trips; application tests run separately.
new('encoding_probe_v2');h.sql('encoding_probe_v2','CREATE TABLE probe(id integer PRIMARY KEY,raw text, structured jsonb, at timestamptz);')
cases=[None,'Tiáº¿ng Viá»‡t ðŸŒŠ\nDÃ²ng hai','"quoted"','{"legacy":"value"}'];structured=[None,'"legacy string"','{"street":"HÃ  Ná»™i","nested":{"x":null}}','["ðŸŒŠ",null,"quoted"]']
for i,(raw,j) in enumerate(zip(cases,structured)):
 q=lambda x:'NULL' if x is None else "'"+x.replace("'","''")+"'"
 h.sql('encoding_probe_v2',f"INSERT INTO probe VALUES ({i},{q(raw)},{q(j)}::jsonb,'2026-11-13 14:00:00+07');")
 rows=json.loads(h.sql('encoding_probe_v2','SELECT json_agg(t ORDER BY id) FROM probe t').stdout)
check('encoding-db-roundtrip',all(r['raw']==cases[i] and r['structured']==(None if structured[i] is None else json.loads(structured[i])) for i,r in enumerate(rows)),'Raw strings, JSON string/object/array, SQL NULL, nested JSON null, Unicode retained')
check('timezone-instant',h.sql('encoding_probe_v2',"SELECT count(*) FROM probe WHERE at='2026-11-13 07:00:00+00'::timestamptz").stdout.strip()=='4','timestamptz instant preserved; timestamp-without-time-zone requires application convention')
h.save('legacy-probe-schema-manifest.json',snapshots);h.save('compatibility-results.json',results)
print(json.dumps(results,ensure_ascii=False,indent=2))
