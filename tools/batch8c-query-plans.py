"""Exact repository SELECT-only EXPLAIN on manifested populated IDs; no locks or DDL."""
import importlib.util,json,re,sys,uuid
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('d',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
ds=json.loads((R/'.local/batch8c/dataset.json').read_text());m=json.loads((d.OUT/'fixture-manifest.json').read_text())
ids=[str(uuid.UUID(r['id'])) for r in ds['rooms']];assert all(any(r['table']=='room_types' and r['id']==value for r in m['records']) for value in ids)
source=(R/'hotel-service/src/main/java/com/example/hotelservice/Room/repository/RoomTypeRepository.java').read_text()
patterns=re.findall(r'@Query\(value = """(.*?)"""',source,re.S)
queries={'single_authoritative_approval':patterns[0].replace('?1',"'"+ids[0]+"'::uuid"),'batched_approval':patterns[1].replace('?1',','.join("'"+value+"'::uuid" for value in ids))}
rows=[]
with d.connect('hotel') as conn:
 with conn.cursor() as cur:
  cur.execute('SET TRANSACTION READ ONLY');cur.execute('SET LOCAL statement_timeout=10000')
  for name,sql in queries.items():
   assert sql.lstrip().startswith('SELECT ') and not re.search(r'\bFOR\s+UPDATE\b',sql,re.I)
   cur.execute('EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) '+sql)
   rows.append(dict(name=name,role='migration read-only',exactRepositorySelect=True,fixtureRoomCount=len(ids),plan=cur.fetchone()[0]))
d.write('sql-plans-'+sys.argv[1]+'.json',rows)
for r in rows:print(r['name'],'serverExecutionMs',r['plan'][0]['Execution Time'],flush=True)
