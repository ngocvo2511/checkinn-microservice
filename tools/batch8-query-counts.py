"""Separate low-volume instrumented pass; session JDBC execution includes network time."""
import json,re,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import importlib.util
spec=importlib.util.spec_from_file_location('readbench',ROOT/'tools/batch8-read-benchmark.py')
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
OUT=b.OUT
base='http://127.0.0.1:8080'
for attempt in range(90):
    r,_=b.request(base,'/cities/all-locations')
    if r['status']==200:break
    time.sleep(2)
city=json.loads(b.h.sql('hotel',"SELECT coalesce(json_agg(id),'[]')::text FROM (SELECT id FROM cities ORDER BY id LIMIT 1) t"))[0]
paths={'search_undated':'/hotels/offers?cityId='+city,'search_dated':'/hotels/offers?cityId='+city+'&checkIn=2026-12-10&checkOut=2026-12-12','public_catalog':'/hotels','location_catalog':'/cities/all-locations'}
log=b.h.PRIVATE/'hotel.console.private.log';rows=[]
for name,path in paths.items():
    for _ in range(3):b.request(base,path)
    for sample in range(3):
        offset=log.stat().st_size
        result,_=b.request(base,path);time.sleep(.2)
        with log.open('rb') as f:f.seek(offset);chunk=f.read().decode(errors='replace')
        sessions=re.findall(r'Session Metrics \{(.*?)\}',chunk,re.S)
        execute=[(int(ns),int(n)) for session in sessions for ns,n in re.findall(r'(\d+) nanoseconds spent executing (\d+) JDBC statements',session)]
        acquire=[int(ns) for session in sessions for ns in re.findall(r'(\d+) nanoseconds spent acquiring \d+ JDBC connections',session)]
        rows.append({'endpoint':name,'sample':sample+1,'httpStatus':result['status'],'apiMs':result['ms'],'sessions':len(sessions),'jdbcStatements':sum(n for ns,n in execute) if sessions else None,'jdbcExecutionMsIncludingNetwork':sum(ns for ns,n in execute)/1e6 if sessions else None,'connectionAcquisitionMs':sum(acquire)/1e6 if sessions else None})
        print(name+' statements='+str(rows[-1]['jdbcStatements']),flush=True)
b.write('query-counts.json',rows)
r,_=b.request(base,'/api/bookings','POST',{'Content-Type':'application/json'},b'{}')
b.write('booking-readiness-recheck.json',{'scenario':'anonymous booking after service discovery readiness','expected':[401,403],'actual':r['status'],'pass':r['status'] in (401,403),'initial503RetainedIn':'functional-results.json'})
