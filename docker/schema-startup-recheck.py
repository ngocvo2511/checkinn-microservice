"""Restart only Batch3-owned app containers; wait past ApplicationRunner guards."""
from pathlib import Path
from importlib.machinery import SourceFileLoader
import json,time,re,datetime
h=SourceFileLoader('h',str(Path(__file__).with_name('schema-smoke.py'))).load_module();results=[]
for svc in ['hotel','booking','user','revenue','regulations']:
 name=h.PROJECT+('-user-testonly' if svc=='user' else '-'+svc)
 info=json.loads(h.run(['docker','inspect',name]).stdout)[0]
 assert info['Config']['Labels']['com.docker.compose.project']==h.PROJECT
 since=datetime.datetime.now(datetime.timezone.utc).isoformat()
 h.run(['docker','start',name]);log=''
 for _ in range(70):
  p=h.run(['docker','logs','--since',since,name]);log=p.stdout+p.stderr
  if re.search(r'Started \w+Application',log) or 'Application run failed' in log:
   time.sleep(7);p=h.run(['docker','logs','--since',since,name]);log=p.stdout+p.stderr;break
  time.sleep(1)
 running=h.run(['docker','inspect','--format','{{.State.Running}}',name]).stdout.strip()=='true'
 (h.RAW/('startup-recheck-'+svc+'.log')).write_text(log,encoding='utf-8')
 results.append({'service':svc,'image_id':info['Image'],'status':'PASS' if running and re.search(r'Started \w+Application',log) and 'Application run failed' not in log else 'BLOCKED','scope':'JPA/startup guard only; no broker/Redis/Gateway; existing image source correspondence NOT VERIFIED','errors':[x for x in log.splitlines() if 'Caused by:' in x][-4:]})
 h.save('startup-recheck-results.json',results);h.run(['docker','stop','-t','5',name])
print(json.dumps(results,indent=2))
