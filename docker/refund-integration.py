"""Own disposable PostgreSQL only; never reads existing DB credentials."""
import subprocess,os,time,uuid,socket
from pathlib import Path
root=Path(__file__).resolve().parents[1]
name='checkinn-refund-it-'+uuid.uuid4().hex
broker=name+'-rabbit'

def run(*args,**kw):
 p=subprocess.run(args,text=True,capture_output=True,**kw)
 if p.returncode: raise RuntimeError(p.stdout[-2000:]+p.stderr[-2000:])
 return p.stdout.strip()
try:
 run('docker','run','-d','--name',name,'-p','127.0.0.1::5432','-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine')
 for _ in range(60):
  if subprocess.run(['docker','exec',name,'pg_isready','-U','postgres'],capture_output=True).returncode==0:break
  time.sleep(.5)
 run('docker','run','-d','--name',broker,'-p','127.0.0.1::5672','-e','RABBITMQ_DEFAULT_USER=refund_test','-e','RABBITMQ_DEFAULT_PASS=test-only','-e','RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=+S 2:2','rabbitmq:3-management')
 rabbit_port=run('docker','port',broker,'5672/tcp').rsplit(':',1)[1]
 for _ in range(120):
  try:
   with socket.create_connection(('127.0.0.1',int(rabbit_port)),timeout=1):break
  except OSError:time.sleep(.5)
 else:raise RuntimeError('Rabbit startup failed: '+run('docker','logs',broker))
 port=run('docker','port',name,'5432/tcp').rsplit(':',1)[1]
 for db in ['refund_booking','refund_revenue']:run('docker','exec',name,'createdb','-U','postgres',db)
 env=os.environ.copy();env.update(CHECKINN_REFUND_TEST='true',CHECKINN_REFUND_RABBIT_PORT=rabbit_port,CHECKINN_REFUND_BOOKING_URL=f'jdbc:postgresql://127.0.0.1:{port}/refund_booking',CHECKINN_REFUND_REVENUE_URL=f'jdbc:postgresql://127.0.0.1:{port}/refund_revenue')
 for stage in ['initial','retry']:
  env['CHECKINN_REFUND_STAGE']=stage
  with (root.parent/f'BATCH1_3_booking-{stage}.log').open('w',encoding='utf-8') as log:
   p=subprocess.run(['mvn.cmd','-o','-B','-ntp','-pl','booking-service','-Dtest=RefundPostgresTest','test'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
  if p.returncode: raise RuntimeError('Booking integration failed: '+stage)
  print('PASS booking '+stage,flush=True)
 if (root/'revenue-service/src/test/java/com/example/revenueservice/service/RefundPipelineTest.java').exists():
  with (root.parent/'BATCH1_3_revenue.log').open('w',encoding='utf-8') as log:
   p=subprocess.run(['mvn.cmd','-o','-B','-ntp','-pl','revenue-service','-Dtest=RefundPipelineTest','test'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
  if p.returncode:raise RuntimeError('Revenue integration failed')
  print('PASS revenue',flush=True)
finally:
 subprocess.run(['docker','rm','-f','--volumes',broker],capture_output=True)
 subprocess.run(['docker','rm','-f','--volumes',name],capture_output=True)
