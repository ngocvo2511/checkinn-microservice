"""Batch evidence snapshots, no provider or credentials. Existing snapshots are immutable."""
import hashlib,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
stage=sys.argv[1]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
files=[p for base in ('ai-agent-service/src/main','ai-agent-service/src/test') for p in (ROOT/base).rglob('*') if p.is_file()]
record={'stage':stage,'sources':{str(p.relative_to(ROOT)).replace('\\','/'):{'sha256':digest(p),'content':p.read_text(encoding='utf-8')} for p in files},'jarSha256':digest(ROOT/'ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar')}
with (OUT/f'source-{stage}.json').open('x',encoding='utf-8') as f:json.dump(record,f,ensure_ascii=False,indent=2)
preserved=json.loads((ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/preserved-hashes-v1.json').read_text(encoding='utf-8'))
checked={p:{'expected':v['expected'],'actual':digest(ROOT/p),'preserved':v['expected']==digest(ROOT/p)} for p,v in preserved.items()}
assert all(v['preserved'] for v in checked.values())
with (OUT/f'preserved-hashes-{stage}.json').open('x',encoding='utf-8') as f:json.dump(checked,f,indent=2)
print(stage,'snapshotted',len(files),'source/test/resources; original hashes preserved')
