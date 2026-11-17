import importlib.util,json,sys,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('diag',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
s=importlib.util.spec_from_file_location('prior',R/'tools/batch8-hosted-readonly.py');a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
a.OUT=d.OUT
phase=sys.argv[1] if len(sys.argv)>1 else 'before'
for owner in d.h.GROUPS:
 if phase=='before':
  r=subprocess.run([sys.executable,str(R/'tools/supabase-runner/run-preflight.py'),owner,'--runtime-check'],capture_output=True,text=True,timeout=100)
  src=d.h.PRIVATE/(owner+'.hosted-runtime-readonly-2026-11-17.json')
  if src.exists():d.write(owner+'-runtime-preflight.json',json.loads(src.read_text()))
  print(owner,'preflight',r.returncode,flush=True)
 try:
  snap=a.snapshot(owner);d.write(owner+'-'+phase+'.json',snap);print(owner,'catalog',snap['baselineDifferences'],flush=True)
 except Exception as e: d.write(owner+'-'+phase+'.json',{'status':'BLOCKED','error':type(e).__name__});print(owner,'snapshot BLOCKED',flush=True)
