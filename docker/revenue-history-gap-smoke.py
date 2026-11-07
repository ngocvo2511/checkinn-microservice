"""A versioned overwrite must never erase known missing cash history; exact clone fixtures only."""
import importlib.util,json,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('revenue-service');h.verify_running_clone('hotel-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']));key=config['services']['revenue-service']['environment']['SECRET_KEY']
 booking,hotel,owner,record=[str(uuid.uuid4()) for _ in range(4)]
 def db(sql):return h.sql('checkinn_revenue',sql)
 def read():
  raw=h.run(h.COMPOSE+['exec','-T','revenue-service','curl','-sS','--max-time','20','-H','@-','-w','\n%{http_code}',
    'http://localhost:8088/api/reports/revenue?hotelId='+hotel+'&from=2027-01-01&to=2027-12-31'],'Authorization: Bearer '+h.token(key,owner,'OWNER')+'\n')
  body,_,code=raw.rpartition('\n');return int(code),json.loads(body)
 def check(ok,name):
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
 try:
  h.sql('checkinn_hotel',"INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','Synthetic history coverage hotel'))).* FROM hotels h LIMIT 1")
  db("INSERT INTO payment_records SELECT (jsonb_populate_record(NULL::payment_records,to_jsonb(p)||jsonb_build_object('id','"+record+"','booking_id','"+booking+"','hotel_id','"+hotel+"','event_version',NULL,'last_event_id',NULL))).* FROM payment_records p LIMIT 1")
  sql=(ROOT/'sql/revenue-history-gaps-v1-20261107.sql').read_text()
  db(sql);db(sql)
  check(read()[0]==503,'legacy history requires explicit reconciliation')
  db("UPDATE payment_records SET event_version=1,last_event_id='"+str(uuid.uuid4())+"' WHERE id='"+record+"'")
  status,body=read()
  check(status==503 and body.get('code')=='REPORT_RECONCILIATION_REQUIRED','new source position cannot wash out an existing history gap')
  check(db("SELECT count(*) FROM revenue_cash_transactions WHERE booking_id='"+booking+"'")=='0','coverage audit never fabricates historical cash movements')
  rejected=False
  try:db("UPDATE revenue_cashflow_gaps SET resolved_at=now(),resolved_by='"+owner+"' WHERE booking_id='"+booking+"'")
  except RuntimeError:rejected=True
  check(rejected and db("SELECT resolved_at IS NULL FROM revenue_cashflow_gaps WHERE booking_id='"+booking+"'")=='t','gap resolution requires explicit nonempty review evidence')
 finally:
  db("DELETE FROM revenue_cashflow_gaps WHERE booking_id='"+booking+"'; DELETE FROM payment_records WHERE id='"+record+"'")
  h.sql('checkinn_hotel',"DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
 print(json.dumps({'verified_cases':4,'synthetic_resources_cleaned':True}),flush=True)
if __name__=='__main__':
 try:main()
 except Exception as error:print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True);raise SystemExit(1)
