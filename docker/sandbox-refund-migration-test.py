"""Isolated PostgreSQL migration/constraint tests. No application/provider calls."""
from pathlib import Path
import subprocess, time, uuid
from concurrent.futures import ThreadPoolExecutor

def main():
 name='checkinn-manual-refund-test-'+uuid.uuid4().hex
 def run(*args,sql=None):
  p=subprocess.run(args,input=sql,text=True,capture_output=True)
  if p.returncode: raise RuntimeError(p.stderr)
  return p.stdout.strip()
 run('docker','run','-d','--name',name,'--env','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine')
 try:
  for _ in range(60):
   if subprocess.run(['docker','exec',name,'pg_isready','-U','postgres'],capture_output=True).returncode==0: break
   time.sleep(.5)
  def sql(s): return run('docker','exec','-i',name,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres',sql=s)
  sql("CREATE TABLE payments(id varchar(255) PRIMARY KEY); CREATE TABLE bookings(id varchar(255) PRIMARY KEY); CREATE TABLE refund_requests(id varchar(255) PRIMARY KEY,status varchar(64)); INSERT INTO payments VALUES ('p'); INSERT INTO bookings VALUES ('b'); INSERT INTO refund_requests VALUES ('r','APPROVED_PENDING_EXECUTION');")
  migration=(Path(__file__).parent/'sql/manual-refund-v1-20261112.sql').read_text()
  sql(migration);sql(migration)
  upgrade=(Path(__file__).parent/'sql/sandbox-internal-refund-v1-20261112.sql').read_text()
  sql(upgrade);sql(upgrade)
  def insert(_):
   return sql("INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state) VALUES ('"+str(uuid.uuid4())+"','r','p','b',100,'VND','VNPAY','PENDING_MANUAL_REFUND') ON CONFLICT DO NOTHING;")
  with ThreadPoolExecutor(max_workers=4) as pool: list(pool.map(insert,range(4)))
  assert sql('SELECT count(*) FROM manual_refund_executions;')=='1'
  for statement in ["UPDATE manual_refund_executions SET amount=-1;","UPDATE manual_refund_executions SET state='REFUNDED';","UPDATE manual_refund_executions SET state='PENDING_VERIFICATION';"]:
   try: sql(statement)
   except RuntimeError: pass
   else: raise AssertionError('constraint accepted invalid financial state')
  sql("BEGIN; UPDATE manual_refund_executions SET state='NEEDS_MANUAL_REVIEW'; ROLLBACK;")
  assert sql('SELECT state FROM manual_refund_executions;')=='PENDING_MANUAL_REFUND'
  sql("UPDATE manual_refund_executions SET state='PENDING_VERIFICATION',external_reference='external',evidence_reference='private:123',submitted_by='admin',submitted_at=now();")
  assert sql('SELECT state FROM manual_refund_executions;')=='PENDING_VERIFICATION'
  sql("UPDATE manual_refund_executions SET state='REFUNDED',execution_method='SANDBOX_INTERNAL';")
  print('PASS: migration replay, four concurrent inserts/one execution, invalid states/amount/evidence blocked, rollback, pending verification')
 finally: run('docker','rm','-f',name)
if __name__=='__main__': main()
