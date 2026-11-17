"""Bounded populated-data HTTP/SQL measurements. No mutations, no retry masking."""
import importlib.util,json,re,sys,time,statistics,concurrent.futures,threading,math
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('acceptance',R/'tools/batch8b-acceptance.py');a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
O=a.OUT
def paths():
    d=json.loads((a.PRIVATE/'dataset.json').read_text());r=d['rooms'][0]
    return {'search_undated':'/hotels/offers?cityId='+d['city'],'search_dated':'/hotels/offers?cityId='+d['city']+'&checkIn=2027-02-10&checkOut=2027-02-11','public_catalog':'/hotels','hotel_detail':'/hotels/'+r['hotelId'],'room_availability':'/api/v1/availability/check?roomTypeId='+r['id']+'&checkIn=2027-02-10&checkOut=2027-02-11&quantity=1'}
def request(path,port=8080):
    start=time.perf_counter()
    try:code,d=a.api(path,port=port);err=None
    except Exception as e:code=0;err=type(e).__name__;d=None
    return {'status':code,'ms':(time.perf_counter()-start)*1000,'error':err,'size':len(d) if isinstance(d,list) else None}
def slope(label):
    log=a.h.PRIVATE/'hotel.console.private.log';rows=[]
    for name,path in paths().items():
        request(path)
        for sample in range(3):
            offset=log.stat().st_size;r=request(path);time.sleep(.15)
            with log.open('rb') as f:f.seek(offset);chunk=f.read().decode(errors='replace')
            sessions=re.findall(r'Session Metrics \{(.*?)\}',chunk,re.S)
            execute=[(int(ns),int(n)) for se in sessions for ns,n in re.findall(r'(\d+) nanoseconds spent executing (\d+) JDBC statements',se)]
            acquisition=[int(ns) for se in sessions for ns in re.findall(r'(\d+) nanoseconds spent acquiring \d+ JDBC connections',se)]
            rows.append({'endpoint':name,'sample':sample,**r,'sessions':len(sessions),'jdbcStatements':sum(n for ns,n in execute) if sessions else None,'jdbcMsIncludingNetwork':sum(ns for ns,n in execute)/1e6 if sessions else None,'acquisitionMs':sum(acquisition)/1e6 if sessions else None})
            a.write('query-counts-'+label+'.json',rows);print(label,name,r['status'],rows[-1]['jdbcStatements'],round(r['ms']),flush=True)
            if r['status']!=200 or r['ms']>25000:raise RuntimeError('STOP_SLOW_OR_FAILED_ENDPOINT')
    return rows
def pct(v,p):
    v=sorted(v);x=(len(v)-1)*p;lo=int(x);return v[lo]+(v[min(lo+1,len(v)-1)]-v[lo])*(x-lo)
def bench(label):
    rows=[];summary=[]
    for name,path in paths().items():
        for _ in range(3):
            w=request(path)
            if w['status']!=200:raise RuntimeError('WARMUP_FAILURE')
        for concurrency in ([1] if '--serial-only' in sys.argv else [1,3,5,10]):
            serial=next((s for s in summary if s['endpoint']==name and s['concurrency']==1 and 'p95' in s),None)
            if serial and serial['p95']*(math.ceil(concurrency/2)-1)>8000:
                summary.append({'endpoint':name,'concurrency':concurrency,'status':'SKIPPED_SAFETY','reason':'Estimated pool2 queue from measured serial p95 exceeds 8s headroom against 10s acquisition timeout'})
                a.write('benchmark-'+label+'-summary.json',summary);print(label,name,concurrency,'SKIPPED_SAFETY',flush=True);continue
            start=time.perf_counter()
            stop=threading.Event()
            def attempt(_):
                if stop.is_set():return None
                r=request(path)
                if r['status']!=200 or r['ms']>30000:stop.set()
                return r
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:results=[r for r in pool.map(attempt,range(30)) if r is not None]
            elapsed=time.perf_counter()-start
            rows.extend(dict(endpoint=name,concurrency=concurrency,sample=i,**r) for i,r in enumerate(results))
            success=[r['ms'] for r in results if r['status']==200];errors=sum(r['status']!=200 for r in results)
            d={'endpoint':name,'concurrency':concurrency,'samples':len(results),'successful':len(success),'errors':errors,'timeouts':sum(r['error'] is not None and 'Timeout' in r['error'] for r in results),'rps':len(results)/elapsed}
            if success:d.update(p50=pct(success,.5),p95=pct(success,.95),p99=pct(success,.99),mean=statistics.mean(success),min=min(success),max=max(success))
            summary.append(d);a.write('benchmark-'+label+'-raw.json',rows);a.write('benchmark-'+label+'-summary.json',summary);print(label,name,concurrency,'success',len(success),'p95',round(d.get('p95',0)),flush=True)
            if errors or d.get('max',0)>30000:raise RuntimeError('STOP_PRESSURE_OR_ERRORS')
    direct=[]
    for name,path in paths().items():
        for sample in range(30):direct.append(dict(endpoint=name,sample=sample,**request(path,8083)))
        a.write('direct-'+label+'.json',direct);print(label,'direct',name,flush=True)
if __name__=='__main__':
    if sys.argv[1]=='slope':slope(sys.argv[2])
    else:bench(sys.argv[2])
