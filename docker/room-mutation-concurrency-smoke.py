"""Real owner API interleavings: metadata/activation must preserve current price/stock."""
import concurrent.futures,importlib.util,json,os,queue,secrets,subprocess,threading,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('hotel-service');config=json.loads(h.run(h.COMPOSE+['config','--format','json']));hotel,room,owner=[str(uuid.uuid4()) for _ in range(3)];prefix='roomrace_'+secrets.token_hex(8);count=0;failures=0;functions=[]
 token=h.token(config['services']['hotel-service']['environment']['SECRET_KEY'],owner,'OWNER')
 def db(q):return h.sql('checkinn_hotel',q)
 def check(ok,name):
  nonlocal count,failures
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True);count+=1;failures+=not ok
 def fingerprint(table):return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM "+table+' r')
 hotels_before=fingerprint('hotels');rooms_before=fingerprint('room_types')
 def api(path,body=None):
  settings='request = "PUT"\nheader = "Content-Type: application/json"\nheader = '+json.dumps('Authorization: Bearer '+token)+'\n'
  if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
  raw=h.run(h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','30','--config','-','-w','\n%{http_code}','http://localhost:8083/api/v1/'+room+path],settings);text,_,status=raw.rpartition('\n');return int(status)
 try:
  assert db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','"+prefix+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1'
  assert db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic concurrent room','description','Baseline','base_price',200000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1'
  cases=[('description preserves separately updated price','base_price',300000,{'basePrice':300000},'',{'description':'Concurrent metadata'},200,True),
         ('activation preserves separately updated price','base_price',300000,{'basePrice':300000},'/activate',None,200,False),
         ('deactivation preserves separately increased stock','total_rooms',4,{'roomAmount':4},'/deactivate',None,200,True),
         ('no-decrease guard uses current stock after waiting','total_rooms',4,{'roomAmount':4},'',{'roomAmount':3},400,True)]
  for index,(name,column,target,first,second_path,second,expected_status,active) in enumerate(cases):
   db("UPDATE room_types SET base_price=200000,total_rooms=2,is_active="+str(active).lower()+",description='Baseline' WHERE id='"+room+"'")
   function=prefix+'_'+str(index);functions.append(function);key=secrets.randbelow(2**62)+1;app=function+'_holder';holder=None
   db("CREATE FUNCTION "+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock("+str(key)+"); RETURN NEW; END $$; CREATE TRIGGER "+function+" BEFORE UPDATE ON room_types FOR EACH ROW WHEN (NEW.id='"+room+"'::uuid AND NEW."+column+" IS DISTINCT FROM OLD."+column+" AND NEW."+column+'='+str(target)+") EXECUTE FUNCTION "+function+"()")
   try:
    holder=subprocess.Popen(h.COMPOSE+['exec','-T','-e','PGAPPNAME='+app,'postgres-test','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','checkinn_test','-d','checkinn_hotel'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8',bufsize=1)
    holder.stdin.write('DO $$ BEGIN PERFORM pg_advisory_lock('+str(key)+'); END $$; SELECT pg_backend_pid();\n');holder.stdin.flush()
    ready=queue.Queue();threading.Thread(target=lambda:ready.put(holder.stdout.readline()),daemon=True).start();pid=int(ready.get(timeout=10).strip())
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
     try:
      changing=pool.submit(api,'',first);deadline=time.monotonic()+10;first_pid=None
      while not first_pid:
       row=db("SELECT pid FROM pg_stat_activity WHERE datname='checkinn_hotel' AND "+str(pid)+"=ANY(pg_blocking_pids(pid)) LIMIT 1")
       if row:first_pid=int(row);break
       if changing.done() or time.monotonic()>deadline:raise RuntimeError('First owner API did not reach the scoped transaction barrier')
       time.sleep(.05)
      mutating=pool.submit(api,second_path,second);deadline=time.monotonic()+10;waiting=False
      while not waiting:
       waiting=int(db("SELECT count(*) FROM pg_stat_activity WHERE datname='checkinn_hotel' AND "+str(first_pid)+"=ANY(pg_blocking_pids(pid))"))>0
       if waiting:break
       if mutating.done() or time.monotonic()>deadline:raise RuntimeError('Competing owner API did not wait on the same room')
       time.sleep(.05)
      check(waiting,name+' real PostgreSQL blocking interleaving observed')
     finally:
      if holder and holder.poll() is None:
       holder.stdin.write('SELECT pg_advisory_unlock('+str(key)+');\n\\q\n');holder.stdin.flush();holder.communicate(timeout=10)
     first_status=changing.result(timeout=35);second_status=mutating.result(timeout=35)
    check(first_status==200 and second_status==expected_status,name+' owner API statuses')
    actual=db(f"SELECT {column}::int FROM room_types WHERE id='{room}'");check(actual==str(target),name+' authoritative value retained')
   finally:
    # Terminate only this generated test holder, never application connections.
    db("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='checkinn_hotel' AND application_name='"+app+"' AND pid<>pg_backend_pid()")
    if holder and holder.poll() is None:holder.terminate();holder.wait(timeout=10)
    db('DROP TRIGGER IF EXISTS '+function+' ON room_types; DROP FUNCTION IF EXISTS '+function+'()');functions.remove(function)
 finally:
  for function in functions:db('DROP TRIGGER IF EXISTS '+function+' ON room_types; DROP FUNCTION IF EXISTS '+function+'()')
  db("DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='"+prefix+"'")
  check(fingerprint('hotels')==hotels_before and fingerprint('room_types')==rooms_before,'exact concurrency cleanup preserves pre-existing data')
 print(json.dumps({'cases':count,'failures':failures}),flush=True);return int(bool(failures))
if __name__=='__main__':raise SystemExit(main())
