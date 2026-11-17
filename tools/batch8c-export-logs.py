"""Export timestamp-bounded sanitized logs, preserving metrics and lifecycle evidence."""
import datetime,importlib.util,json,re
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('d',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
values=d.h.stack.values();values.update(d.h.stack.runtime_values())
secrets=[value for key,value in values.items() if len(value)>=8 and any(word in key for word in ('PASSWORD','TOKEN','SECRET','DB_URL','PROJECT_REF','DB_USERNAME'))]
for p in (R/'.local/batch8c').glob('b8c_*.json'):
 data=json.loads(p.read_text());secrets.extend(data[key] for key in ('password','token') if data.get(key))
rows=[]
for name in ('eureka','gateway','user','auth','hotel','booking'):
 source=d.h.PRIVATE/(name+'.console.private.log');included=False;lines=[];historical=[]
 for line in source.read_bytes().decode('utf-8',errors='replace').splitlines():
  stamp=re.match(r'^(2026-11-17)[ T](\d\d:\d\d:\d\d)',line)
  if stamp:included=stamp[2]>='08:24:00'
  if not included:
   if name in ('hotel','user','booking') and ('too many connections for role' in line or 'Connection is not available' in line):historical.append(line)
   continue
  lines.append(line)
 text='\n'.join(lines)+'\n'
 for value in sorted(set(secrets),key=len,reverse=True):text=text.replace(value,'<REDACTED>')
 text=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','<REDACTED_JWT>',text)
 text=re.sub(r'\$2[aby]\$\d\d\$[./A-Za-z0-9]{50,}','<REDACTED_PASSWORD_HASH>',text)
 text=re.sub(r'jdbc:postgresql://[^\s\]\)\"\']+','<REDACTED_JDBC_URL>',text)
 (d.OUT/(name+'-runtime-sanitized.log')).write_text(text,encoding='utf-8')
 if historical:(d.OUT/(name+'-historical-connection-errors.log')).write_text('\n'.join(historical)+'\n',encoding='utf-8')
 rows.append(dict(service=name,exportedLines=len(lines),roleLimitErrors=text.count('too many connections for role'),hikariTimeoutLogOccurrences=text.count('Connection is not available'),hikariShutdownCompleteOccurrences=text.count('Shutdown completed.'),source='Private console, timestamp bounded from 2026-11-17 08:24 UTC; known secrets/JWT/bcrypt/JDBC URLs redacted'))
d.write('runtime-log-summary.json',rows);print(json.dumps(rows,indent=2))
