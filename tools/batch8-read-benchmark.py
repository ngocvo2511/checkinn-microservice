"""Bounded, anonymous read benchmark. No request bodies or response data are retained."""
import concurrent.futures, datetime, hashlib, json, platform, re, socket, statistics, subprocess, sys, time, urllib.error, urllib.request, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
OUT=ROOT/'Docs/evidence/batch-8-hosted-e2e-performance'
def write(name,value):(OUT/name).write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def request(base,path,method='GET',headers=None,body=None):
    start=time.perf_counter();code=0;value=None;timeout=False
    try:
        req=urllib.request.Request(base+path,method=method,headers=headers or {},data=body)
        with urllib.request.urlopen(req,timeout=15) as res:code=res.status;raw=res.read()
        try:value=json.loads(raw)
        except ValueError:pass
    except urllib.error.HTTPError as e:code=e.code;e.read()
    except (TimeoutError,socket.timeout):timeout=True
    except urllib.error.URLError as e:timeout=isinstance(e.reason,(TimeoutError,socket.timeout))
    return {'ms':(time.perf_counter()-start)*1000,'status':code,'timeout':timeout},value
def percentile(values,q):
    values=sorted(values);pos=(len(values)-1)*q;lo=int(pos);hi=min(lo+1,len(values)-1)
    return values[lo]+(values[hi]-values[lo])*(pos-lo)
def main():
    state=h.stack.state();gateway='http://127.0.0.1:'+str(state['ports']['gateway']);hotel='http://127.0.0.1:'+str(state['ports']['hotel'])
    for attempt in range(90):
        r,v=request(gateway,'/cities/all-locations')
        if r['status']==200:break
        time.sleep(2)
    else:raise RuntimeError('Gateway route readiness timeout')
    # City UUID is reference metadata, never export names or account data.
    cities=json.loads(h.sql('hotel',"SELECT coalesce(json_agg(id),'[]')::text FROM (SELECT id FROM cities ORDER BY id LIMIT 1) t"))
    city=cities[0];missing=str(uuid.uuid4());dates='checkIn=2026-12-10&checkOut=2026-12-12'
    paths={'search_undated':'/hotels/offers?cityId='+city,'search_dated':'/hotels/offers?cityId='+city+'&'+dates,
       'public_catalog':'/hotels','location_catalog':'/cities/all-locations'}
    health=[]
    for name in state['processes']:
        r,v=request('http://127.0.0.1:'+str(state['ports'][name]),'/actuator/health')
        health.append({'service':name,'httpStatus':r['status'],'status':v.get('status') if isinstance(v,dict) else None})
    write('runtime-health.json',health)
    # Get aggregate-only metric inventory and before/after SQL counters.
    def metrics():
        r,v=request(hotel,'/actuator/metrics');names=v.get('names',[]) if isinstance(v,dict) else []
        result={}
        for name in names:
            if name.startswith(('hibernate.','hikaricp.')):
                rr,vv=request(hotel,'/actuator/metrics/'+name)
                if rr['status']==200:result[name]=vv
        return result
    write('metrics-before.json',metrics())
    checks=[]
    for label,path in paths.items():
        r,v=request(gateway,path);checks.append({'scenario':label,'expected':200,'actual':r['status'],'pass':r['status']==200,'resultCount':len(v) if isinstance(v,list) else None})
    cases=[('anonymous proposal denied','/api/v1/'+missing+'/inventory-review','POST',{},b'{"quantity":1,"revision":0,"source":"synthetic"}'),
       ('anonymous ADMIN review denied','/api/hotel/admin/inventory-reviews/'+missing+'/review','POST',{},b'{"approve":true,"revision":0,"reason":"synthetic"}'),
       ('forged admin header denied','/api/hotel/admin/inventory-reviews','GET',{'X-Is-Admin':'true'},None),
       ('forged JWT denied','/api/hotel/admin/inventory-reviews','GET',{'Authorization':'Bearer eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4ifQ.invalid'},None),
       ('anonymous booking denied','/api/bookings','POST',{},b'{}'),
       ('anonymous hold denied','/api/v1/availability/hold','POST',{},b'{}')]
    for label,path,method,headers,body in cases:
        r,_=request(gateway,path,method,{'Content-Type':'application/json',**headers},body)
        checks.append({'scenario':label,'expected':[401,403],'actual':r['status'],'pass':r['status'] in (401,403)})
    # Missing identities are negative cases, never treated as successful business endpoint samples.
    for label,path in [('missing hotel detail','/hotels/'+missing),('missing room availability','/api/v1/availability/check?roomTypeId='+missing+'&'+dates+'&quantity=1')]:
        r,_=request(gateway,path);checks.append({'scenario':label,'expected':[404,409],'actual':r['status'],'pass':r['status'] in (404,409)})
    write('functional-results.json',checks)
    raw=[];summaries=[]
    for name,path in paths.items():
        for _ in range(5):request(gateway,path)
        for concurrency in (1,3,5,10):
            start=time.perf_counter()
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
                rows=list(pool.map(lambda _:request(gateway,path)[0],range(30)))
            elapsed=time.perf_counter()-start
            good=[r['ms'] for r in rows if r['status']==200];errors=len(rows)-len(good)
            raw.extend({'endpoint':name,'concurrency':concurrency,'sample':i+1,**row} for i,row in enumerate(rows))
            summary={'endpoint':name,'concurrency':concurrency,'samples':30,'successes':len(good),'elapsedSeconds':elapsed,'requestsPerSecond':30/elapsed,'errorRate':errors/30,'timeoutRate':sum(r['timeout'] for r in rows)/30}
            if good:summary.update({key:fn(good) for key,fn in {'p50Ms':lambda v:percentile(v,.5),'p95Ms':lambda v:percentile(v,.95),'p99Ms':lambda v:percentile(v,.99),'meanMs':statistics.mean,'minMs':min,'maxMs':max}.items()})
            summaries.append(summary);write('benchmark-raw.json',raw);write('benchmark-summary.json',summaries)
            print(name+' concurrency='+str(concurrency)+' successes='+str(len(good)),flush=True)
            if errors:raise RuntimeError('Stop on any benchmark error')
            time.sleep(.5)
    # Same workload/direct service measured separately: difference is not pure Gateway overhead.
    direct=[]
    for name,path in paths.items():
        rows=[request(hotel,path)[0] for _ in range(30)]
        direct.append({'endpoint':name,'samples':rows})
    write('direct-service-raw.json',direct);write('metrics-after.json',metrics())
    write('benchmark-environment.json',{'clientDate':'2026-11-17','platform':platform.platform(),'clientRegion':'Asia/Saigon (user context; physical IP geolocation not verified)','cpu':platform.processor(),'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.splitlines(),'connectionMode':'session pooler; TLS verify-full','poolMax':2,'poolMinIdle':1,'poolTimeoutMs':10000,'warmupPerEndpoint':5,'successfulSamplesPerLevelTarget':30,'maxConcurrency':10,'responseBodiesRetained':False,'productionReady':False})
if __name__=='__main__':main()
