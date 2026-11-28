"""Freeze synthetic development expectations before any new provider response. Offline only."""
import hashlib, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
def write(path,data):
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def expected(intent='SEARCH',fields=None,action='CRITERIA',missing=(),reference=''):
    return dict(action=action,intent=intent,fields={k:dict(operation='REPLACE',value=v) for k,v in (fields or {}).items()},reference=reference,missingSlots=sorted(missing),inferredFields=[])
rows=[]
def add(text,e,category):
    rows.append(dict(id=f'FRESH2-{len(rows)+1:02}',utterance=text,privacyClassification='SYNTHETIC_ONLY',classification='FRESH_DEVELOPMENT_GENERALIZATION',category=category,expectedExtraction=e,productDecisionRequired=False))
add('Mình muốn tìm một nơi lưu trú tại Vũng Tàu nhé',expected(fields={'cityName':'Vũng Tàu'}),'natural-discovery')
add('Có chỗ nào nghỉ tại Phú Quốc hông bạn?',expected(fields={'cityName':'Phú Quốc'}),'colloquial-discovery')
add('kiem giup tui khach san o Nha Trang nha',expected(fields={'cityName':'Nha Trang'}),'no-accent')
add('Khám phá khách sạn tại Hà Nội',expected('BROWSE',{'cityName':'Hà Nội'}),'browse')
add('Tìm khách sạn tại Đà Nẵng từ 08/12/2026 đến 11/12/2026 cho 3 người lớn, 2 trẻ em, 2 phòng, 4 sao, giá không quá 2 triệu mỗi đêm',expected(fields={'cityName':'Đà Nẵng','checkIn':'2026-12-08','checkOut':'2026-12-11','adults':3,'children':2,'rooms':2,'starRatings':[4],'maxPrice':2000000}),'complete-multiple')
add('Tìm khách sạn Huế từ 10/12/2026 đến 12/12/2026, 2 người lớn, không có trẻ em, 1 phòng',expected(fields={'cityName':'Huế','checkIn':'2026-12-10','checkOut':'2026-12-12','adults':2,'children':0,'rooms':1}),'explicit-zero')
add('Tìm khách sạn Vũng Tàu từ 15/12/2026 đến 17/12/2026 cho 2 người lớn, 1 phòng',expected(fields={'cityName':'Vũng Tàu','checkIn':'2026-12-15','checkOut':'2026-12-17','adults':2,'rooms':1},missing=['children']),'missing-children')
add('Tìm khách sạn Phú Quốc từ 09/12/2026 đến 13/12/2026, 2 người lớn và 1 trẻ em',expected(fields={'cityName':'Phú Quốc','checkIn':'2026-12-09','checkOut':'2026-12-13','adults':2,'children':1},missing=['rooms']),'missing-rooms')
add('Tìm khách sạn nhận phòng 14/12/2026',expected('CLARIFY',action='CLARIFY',missing=['checkOut']),'missing-departure')
add('Tìm khách sạn Nha Trang trả phòng 18/12/2026',expected('CLARIFY',action='CLARIFY',missing=['checkIn']),'missing-arrival')
add('Tìm khách sạn cho 4 người ở Hà Nội',expected('CLARIFY',action='CLARIFY',missing=['adults','children','rooms']),'generic-guests')
add('Tìm khách sạn Đà Nẵng giá tham khảo dưới 1,5 triệu',expected(fields={'cityName':'Đà Nẵng','maxPrice':1500000,'referencePriceExclusive':True}),'strict-reference')
add('Tìm khách sạn Huế giá từ 700 nghìn đến 1,2 triệu mỗi đêm',expected(fields={'cityName':'Huế','minPrice':700000,'maxPrice':1200000}),'price-range')
e=expected('REFINE'); e['fields']={k:dict(operation='CLEAR') for k in ('minPrice','maxPrice')}
add('Xóa giới hạn giá đi',e,'explicit-removal')
add('Chuyển điểm đến sang Phú Quốc',expected('REFINE',{'cityName':'Phú Quốc'}),'destination-replacement')
for text in ('Phòng đó giá bao nhiêu?','Xem khách sạn đầu tiên'):
    add(text,expected('CLARIFY',action='SELECTED',reference=text),'selected-reference')
add('Tìm khách sạn tại Vũng Tàu có hồ bơi',expected('CLARIFY',action='CLARIFY'),'unsupported-amenity')
add('Tìm khách sạn Huế từ 31/04/2027 đến 02/05/2027',expected('CLARIFY',action='CLARIFY',missing=['checkIn','checkOut']),'invalid-calendar')
add('Tìm khách sạn Đà Nẵng giá từ 3 triệu đến 800 nghìn mỗi đêm',expected('CLARIFY',action='CLARIFY',missing=['minPrice','maxPrice']),'reversed-price')
data=dict(version='semantic-quality-fresh-v2',classification='FRESH_DEVELOPMENT_GENERALIZATION',evaluationClock='2026-11-24T10:18:48.242851600Z',notBlindedHoldout=True,fixtures=rows)
write(OUT/'fresh-dataset-v2.json',data)
write(ROOT/'ai-agent-service/src/main/resources/gemini/semantic-quality-fresh-v2.json',data)
before=json.loads((OUT/'historical-preservation-before.json').read_text(encoding='utf-8-sig'))
preserved={p['path']:dict(expected=p['sha256'],actual=hashlib.sha256((ROOT/p['path']).read_bytes()).hexdigest()) for p in before}
assert all(v['expected']==v['actual'] for v in preserved.values())
write(OUT/'historical-preservation-check-v1.json',preserved)
# Read only authorized synthetic campaign journals, never environment/private configuration.
attempts={}
for directory in (ROOT/'ai-agent-service/.local').glob('batch10b4*'):
    for path in directory.glob('*.json'):
        try: record=json.loads(path.read_text(encoding='utf-8'))
        except (ValueError,UnicodeError): continue
        if not isinstance(record,dict): continue
        for row in record.get('observations',[]):
            for a in row.get('attempts',[]):
                if a.get('dispatchAt','').startswith('2026-11-24'):
                    attempts[(a['dispatchAt'],a.get('actualModel'))]=a
from collections import Counter
write(OUT/'prior-known-daily-attempts.json',dict(date='2026-11-24',scope='retained batch10b4 synthetic journals only; not account-wide quota proof',modelCounts=dict(Counter(m for _,m in attempts)),knownAttempts=len(attempts)))
print('fresh frozen',len(rows),'prior known',len(attempts))
original=json.loads((ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/b40-v1.json').read_text(encoding='utf-8'))
recorded=[]
rejections=[]
for row in original['observations']:
    recorded.append({k:row[k] for k in ('sampleId','utterance','actualExtractionText','expectedExtraction','schemaValid','semanticPass','backendAccepted','compilerResult')})
    if row.get('compilerResult')!='FAIL': continue
    proposal=row['semanticProposal']
    if proposal.get('reference') and proposal['intent'] not in ('SELECTED','COMPARISON'):
        rule='Unexpected reference on ordinary/clarification request'
    elif proposal.get('issues') and proposal['intent'] not in ('CLARIFY','UNSUPPORTED','UNVERIFIABLE'):
        rule='Issues incompatible with criteria mutation'
    elif proposal['intent']=='CLARIFY' and any(k in proposal for k in ('destination','adults','children','rooms','arrival','departure','price')):
        rule='Clarification cannot mutate criteria'
    else: rule='Unsupported price unit/operator'
    rejections.append(dict(sampleId=row['sampleId'],utterance=row['utterance'],expected=row['expectedExtraction'],actualRaw=row['actualExtractionText'],actualProposal=proposal,action=row.get('rawSemanticExpected',{}).get('intent'),intent=proposal['intent'],criteria={k:v for k,v in proposal.items() if k not in ('intent','issues','reference')},issues=proposal.get('issues',[]),reference=proposal.get('reference'),compilerRule=rule,exactRejectionStage=row['pipeline'],rootCause=['H1','H2'],confirmedCompilerDefect=False,proposedFix='Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests',regressionRisk='Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals'))
write(OUT/'original-compiler-rejections-v1.json',rejections)
write(ROOT/'ai-agent-service/src/test/resources/gemini/actual-b40-semantic-v1.json',dict(evaluationClock='2026-11-24T10:18:48.242851600Z',observations=recorded))
