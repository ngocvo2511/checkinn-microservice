"""Local regression execution, isolated from hosted credentials."""
import importlib.util,json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('diag',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
P=R/'.local/batch8c';P.mkdir(exist_ok=True)
rows=[]
for owner in ('user','auth','hotel','booking'):
 module=R/(owner+'-service');start=__import__('time').time()
 with (P/(owner+'-regression.private.log')).open('w',encoding='utf-8') as f:
  r=subprocess.run(['mvn.cmd','-o','-B','-ntp','-pl',owner+'-service','-Dtest=*,!*ApplicationTests','test'],cwd=R,env=d.h.stack.clean_parent_env(),stdout=f,stderr=subprocess.STDOUT,timeout=600)
 counts=dict(tests=0,failures=0,errors=0,skipped=0)
 for p in (module/'target/surefire-reports').glob('TEST-*.xml'):
  if p.stat().st_mtime < start:continue
  t=ET.parse(p).getroot()
  for k in counts:counts[k]+=int(t.get(k,0))
 rows.append(dict(owner=owner,exitCode=r.returncode,**counts,executedPass=counts['tests']-counts['failures']-counts['errors']-counts['skipped']))
 d.write('regression-results.json',rows);print(owner,rows[-1],flush=True)
 if r.returncode:break
