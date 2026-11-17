"""Batch8C benchmark runner bound to separate manifest. Serial SQL windows exclude zero-query scheduler sessions."""
import importlib.util,json,re,secrets,sys,time
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('current',R/'tools/batch8c-acceptance.py');c=importlib.util.module_from_spec(s);s.loader.exec_module(c)
s=importlib.util.spec_from_file_location('benchmark',R/'tools/batch8b-benchmark.py');b=importlib.util.module_from_spec(s);s.loader.exec_module(b)
b.a=c.a;b.O=c.a.OUT
def slope(label):
 log=c.a.h.PRIVATE/'hotel.console.private.log';rows=[]
 for name,path in b.paths().items():
  warmup=b.request(path);c.a.write('query-warmup-'+label+'-'+name+'.json',warmup)
  if warmup['status']!=200 or warmup['ms']>25000:raise RuntimeError('STOP_QUERY_WARMUP_FAILURE_OR_SLOW')
  for sample in range(3):
   trace=secrets.token_hex(16);offset=log.stat().st_size;started=time.perf_counter()
   try:
    code,body=c.a.api(path,headers={'traceparent':'00-'+trace+'-'+secrets.token_hex(8)+'-01'});error=None
   except Exception as e:code=0;body=None;error=type(e).__name__
   elapsed=(time.perf_counter()-started)*1000;time.sleep(.2)
   with log.open('rb') as f:f.seek(offset);chunk=f.read().decode(errors='replace')
   sessions=re.findall(r'[^\r\n]*\[traceId=([^]\r\n]*)\][^\r\n]*Session Metrics \{(.*?)\}',chunk,re.S)
   matched=[metrics for found,metrics in sessions if found==trace]
   execute=[(int(ns),int(n)) for se in matched for ns,n in re.findall(r'(\d+) nanoseconds spent executing (\d+) JDBC statements',se)]
   acquisition=[int(ns) for se in matched for ns in re.findall(r'(\d+) nanoseconds spent acquiring \d+ JDBC connections',se)]
   rows.append(dict(endpoint=name,sample=sample,status=code,ms=elapsed,error=error,traceId=trace,matchedSessions=len(matched),excludedSessions=len(sessions)-len(matched),jdbcStatements=sum(n for ns,n in execute) if matched else None,jdbcMsIncludingNetwork=sum(ns for ns,n in execute)/1e6 if matched else None,acquisitionMs=sum(acquisition)/1e6 if matched else None))
   c.a.write('query-counts-'+label+'.json',rows);print(label,name,code,rows[-1]['jdbcStatements'],round(elapsed),flush=True)
   if code!=200 or elapsed>25000:raise RuntimeError('STOP_SLOW_OR_FAILED_ENDPOINT')
   if not matched:raise RuntimeError('REQUEST_TRACE_INSTRUMENTATION_NOT_MATCHED')
 return rows
if __name__=='__main__':
 if sys.argv[1]=='slope':slope(sys.argv[2])
 elif sys.argv[1]=='bench':b.bench(sys.argv[2])

def bench_per_cell(label):
 import concurrent.futures,math,statistics,threading
 rows=[];summary=[];warmups=[]
 for name,path in b.paths().items():
  serial=None
  for concurrency in (1,3,5,10):
   if serial and serial['p95']*(math.ceil(concurrency/2)-1)>8000:
    summary.append(dict(endpoint=name,concurrency=concurrency,status='SKIPPED_SAFETY',reason='Measured serial p95 predicts >8s acquisition queue headroom for pool2/10s timeout'))
    c.a.write('benchmark-'+label+'-summary.json',summary);print(label,name,concurrency,'SKIPPED_SAFETY',flush=True);continue
   with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:ws=list(pool.map(lambda _:b.request(path),range(3)))
   warmups.extend(dict(endpoint=name,concurrency=concurrency,sample=i,**w) for i,w in enumerate(ws));c.a.write('benchmark-'+label+'-warmup-raw.json',warmups)
   if any(w['status']!=200 or w['ms']>30000 for w in ws):raise RuntimeError('WARMUP_FAILURE_STOP_NO_RETRY')
   stop=threading.Event()
   def attempt(_):
    if stop.is_set():return None
    r=b.request(path)
    if r['status']!=200 or r['ms']>30000:stop.set()
    return r
   started=time.perf_counter()
   with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:results=[x for x in pool.map(attempt,range(30)) if x is not None]
   elapsed=time.perf_counter()-started
   rows.extend(dict(endpoint=name,concurrency=concurrency,sample=i,**r) for i,r in enumerate(results))
   success=[r['ms'] for r in results if r['status']==200];errors=sum(r['status']!=200 for r in results);timeouts=sum(bool(r['error']) and 'Timeout' in r['error'] for r in results)
   cell=dict(endpoint=name,concurrency=concurrency,samples=len(results),successful=len(success),errors=errors,timeouts=timeouts,errorRate=errors/len(results),timeoutRate=timeouts/len(results),rps=len(results)/elapsed,successfulRps=len(success)/elapsed,warmupSuccessful=3)
   if success:cell.update(p50=b.pct(success,.5),p95=b.pct(success,.95),p99=b.pct(success,.99),mean=statistics.mean(success),min=min(success),max=max(success))
   summary.append(cell);c.a.write('benchmark-'+label+'-raw.json',rows);c.a.write('benchmark-'+label+'-summary.json',summary)
   print(label,name,concurrency,'success',len(success),'p95',round(cell.get('p95',0)),flush=True)
   if errors or cell.get('max',0)>30000:raise RuntimeError('STOP_PRESSURE_NO_CONCURRENCY_ESCALATION')
   if concurrency==1:serial=cell

if __name__=='__main__' and sys.argv[1]=='bench-per-cell':bench_per_cell(sys.argv[2])
