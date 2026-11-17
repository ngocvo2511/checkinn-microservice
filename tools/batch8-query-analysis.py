"""Hosted read-only EXPLAIN; ANALYZE is restricted to SELECT without row locks."""
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
OUT=ROOT/'Docs/evidence/batch-8-hosted-e2e-performance'
source=(ROOT/'hotel-service/src/main/java/com/example/hotelservice/Room/repository/RoomTypeRepository.java').read_text()
predicate=re.search(r'@Query\(value = """(.*?)"""',source,re.S)[1].replace('?1',"'00000000-0000-0000-0000-000000000000'::uuid")
queries={'effective_approval':predicate,
 'hotel_search':"SELECT * FROM hotels WHERE is_active=true AND approved_status='APPROVED' AND city_id='00000000-0000-0000-0000-000000000000'::uuid",
 'public_catalog':"SELECT * FROM hotels WHERE is_active=true AND approved_status='APPROVED'",
 'lowest_price_candidate':"SELECT min(base_price) FROM room_types WHERE hotel_id='00000000-0000-0000-0000-000000000000'::uuid AND is_active=true",
 'availability':"SELECT * FROM room_availability WHERE room_type_id='00000000-0000-0000-0000-000000000000'::uuid AND date>='2026-12-10' AND date<'2026-12-12'"}
results=[]
for name,sql in queries.items():
    try:
        plan=json.loads(h.sql('hotel','EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) '+sql))
        results.append({'scenario':name,'readOnly':True,'actualRepositoryPredicate':name=='effective_approval','plan':plan})
        print(name+' EXPLAIN SELECT PASS',flush=True)
    except Exception as e:results.append({'scenario':name,'blocked':str(e)});print(name+' BLOCKED',flush=True)
    (OUT/'sql-plans.json').write_text(json.dumps(results,indent=2)+'\n')
try:
    plan=json.loads(h.sql('hotel',"EXPLAIN (FORMAT JSON) SELECT * FROM room_types WHERE id='00000000-0000-0000-0000-000000000000'::uuid FOR UPDATE"))
    results.append({'scenario':'hold_room_lock','analyze':False,'plan':plan})
except Exception as e:results.append({'scenario':'hold_room_lock','blocked':str(e)})
(OUT/'sql-plans.json').write_text(json.dumps(results,indent=2)+'\n')
