# Vietnamese semantic consistency fix v2

24/11/2026. LIVE_TECHNICAL_BLOCKED. This report separates model meaning, canonical compilation and independent backend admission.

## Compatibility and design

|Semantic intent|Canonical action/intent|Criteria/remove|Issues|Reference|Missing business information|
|---|---|---|---|---|---|
|SEARCH/BROWSE|CRITERIA, same intent|All explicit supported facts; no inferred/default facts|Empty/absent|Absent|Java derives missing prerequisites for stays; undated discovery needs none|
|REFINE|CRITERIA/REFINE|Explicit replacements/keeps/removals only, no conflicting operations|Empty/absent|Absent|No invented conversation state|
|CLARIFY|CLARIFY/CLARIFY|Empty|Nonempty grounded issue; no contradiction repair|Absent|Issue mapping independently corroborated|
|SELECTED/COMPARISON|Same action/CLARIFY|Empty|Empty/absent|Exact whole original utterance|Recognition only; trusted target/tool execution NOT_RUN|
|UNSUPPORTED/UNVERIFIABLE|CLARIFY, same intent|Empty|No mutation|Absent|No factual assertion/tool authority; selected intent is not unverifiable solely for missing state|

No new action-classification call, redundant action field, tool/schema framework or authority abstraction was introduced. V1/A contracts and observations remain frozen. V2 retains the flat representation with descriptive schema annotations; strict validation is authoritative and provider keywords are not assumed enforced. Original contradictory proposals still reject. Normalization is finite reviewed equivalence, followed by independent full field/op/value/intent/missing-slot checks. Derived missing slots and strict-price flag are not model-success credit. Generic people never become adults; absent children/rooms never become0/1.

Diagnostic revision1 and final revision hashes are distinct in [snapshots](semantic-quality-v2/). The single full-stay prompt example is separately authored synthetic text, not a frozen/fresh utterance or oracle/sample-ID lookup. No model-proposed IDs/USER_CONFIRMED/provenance can pass schema or authorize state. No date/calendar/range repair occurs.

Four natural-discovery false rejections were fixed with generalized clauses, retaining all unsupported/unconsumed constraints. Unknown-currency and unknown-suffix tests protect unit/politeness normalization. Candidate-only invalid-input corroboration derives invalid facts from the independent parser, admits only exact empty clarification and never mutates state; original signed/ordinary compatibility remains intact. Historical7/0/0/13 and strict-invalid3 are preserved. Actual B40 replay becomes22/0/0/4 with14 compiler failures unchanged; this is backend improvement only.

Date/occupancy omission/addition tests, missing endpoints/children/rooms/destination, invalid calendar/order, explicit zero, generic/conflicting guests, selected/ordinal references, canonical/case/NFC/no-accent labels and unreviewed typo/unknown/conflicting labels are covered in `VietnameseSemanticQualityTest`. SDK capture tests verify all four exact model endpoints and wire serialization against the local schema. See [verification](semantic-quality-v2/verification-v2.json).

## All14 original compiler rejections

Original B40 retained actual envelopes, clock and expectations were used; no provider call or fabricated response. The initial replay exactly reproduced the baseline. See [machine root causes](semantic-quality-v2/original-compiler-rejections-v1.json) for full action/intent/criteria/issues/reference/stage/risk fields. H3/H6/H7/H8 were considered but do not explain these observed raw contradictions; no oracle change or compiler output repair is justified.

### VNE1-003

Utterance: Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng

Expected:
```json
{
  "action": "CRITERIA",
  "intent": "SEARCH",
  "fields": {
    "cityName": {
      "operation": "REPLACE",
      "value": "Đà Lạt"
    },
    "checkIn": {
      "operation": "REPLACE",
      "value": "2026-12-01"
    },
    "checkOut": {
      "operation": "REPLACE",
      "value": "2026-12-03"
    },
    "adults": {
      "operation": "REPLACE",
      "value": 2
    },
    "children": {
      "operation": "REPLACE",
      "value": 0
    },
    "rooms": {
      "operation": "REPLACE",
      "value": 1
    }
  },
  "reference": "",
  "missingSlots": [],
  "inferredFields": []
}
```
Actual raw response:
```json
{
  "intent": "SEARCH",
  "destination": "Đà Lạt",
  "reference": "Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng"
}
```

Rule: Unexpected reference on ordinary/clarification request. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:126'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-004

Utterance: Tìm khách sạn từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng

Expected:
```json
{
  "action": "CRITERIA",
  "intent": "SEARCH",
  "fields": {
    "checkIn": {
      "operation": "REPLACE",
      "value": "2026-12-01"
    },
    "checkOut": {
      "operation": "REPLACE",
      "value": "2026-12-03"
    },
    "adults": {
      "operation": "REPLACE",
      "value": 2
    },
    "children": {
      "operation": "REPLACE",
      "value": 0
    },
    "rooms": {
      "operation": "REPLACE",
      "value": 1
    }
  },
  "reference": "",
  "missingSlots": [
    "cityName"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"SEARCH","arrival":"2026-12-01","departure":"2026-12-03","rooms":1,"price":{"kind":"STAY","operator":"GTE","unit":"VND","amount":0}}
```

Rule: Unsupported price unit/operator. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:114'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-005

Utterance: Tìm khách sạn ở Đà Lạt nhận phòng 01/12/2026

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "checkOut"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{
  "intent": "SEARCH",
  "destination": "Đà Lạt",
  "issues": [
    "DEPARTURE_MISSING"
  ],
  "reference": "Tìm khách sạn ở Đà Lạt nhận phòng 01/12/2026"
}
```

Rule: Unexpected reference on ordinary/clarification request. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:124'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-006

Utterance: Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026

Expected:
```json
{
  "action": "CRITERIA",
  "intent": "SEARCH",
  "fields": {
    "cityName": {
      "operation": "REPLACE",
      "value": "Đà Lạt"
    },
    "checkIn": {
      "operation": "REPLACE",
      "value": "2026-12-01"
    },
    "checkOut": {
      "operation": "REPLACE",
      "value": "2026-12-03"
    }
  },
  "reference": "",
  "missingSlots": [
    "adults",
    "children",
    "rooms"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{
  "intent": "SEARCH",
  "destination": "Đà Lạt",
  "reference": "Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026"
}
```

Rule: Unexpected reference on ordinary/clarification request. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:126'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-007

Utterance: Tìm khách sạn cho 2 người lớn, 1 trẻ em, 1 phòng

Expected:
```json
{
  "action": "CRITERIA",
  "intent": "SEARCH",
  "fields": {
    "adults": {
      "operation": "REPLACE",
      "value": 2
    },
    "children": {
      "operation": "REPLACE",
      "value": 1
    },
    "rooms": {
      "operation": "REPLACE",
      "value": 1
    }
  },
  "reference": "",
  "missingSlots": [
    "cityName",
    "checkIn",
    "checkOut"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"CLARIFY","adults":2,"children":1,"rooms":1,"issues":["DESTINATION_UNRESOLVED"]}
```

Rule: Clarification cannot mutate criteria. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:127'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-009

Utterance: Tìm khách sạn ở Đà Lạt từ 31/02/2026 đến 03/03/2026

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "checkIn",
    "checkOut"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent": "CLARIFY", "destination": "Đà Lạt", "issues": ["DATE_INVALID"]}
```

Rule: Clarification cannot mutate criteria. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:127'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-010

Utterance: Tìm khách sạn ở Đà Lạt giá từ 2 triệu đến 1 triệu VND mỗi phòng mỗi đêm

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "minPrice",
    "maxPrice"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent": "SEARCH", "destination": "Đà Lạt", "price": {"kind": "STAY", "operator": "RANGE", "unit": "VND_PER_ROOM_NIGHT", "amount": 2000000, "upper": 1000000}, "issues": ["PRICE_RANGE_INVALID"]}
```

Rule: Issues incompatible with criteria mutation. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:124'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-011

Utterance: Tìm khách sạn có hồ bơi ở Đà Lạt

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent": "SEARCH", "destination": "Đà Lạt", "issues": ["UNSUPPORTED_FILTER"]}
```

Rule: Issues incompatible with criteria mutation. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:124'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-023

Utterance: Tìm khách sạn Đà Lạt cho 3 người

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "adults",
    "children",
    "rooms"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{
  "intent": "CLARIFY",
  "destination": "Đà Lạt",
  "issues": [
    "GUESTS_AMBIGUOUS"
  ]
}
```

Rule: Clarification cannot mutate criteria. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:127'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-025

Utterance: Tìm khách sạn Huế ngày 20/12/2026 cho 1 phòng 2 người lớn

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "checkOut"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"SEARCH","destination":"Huế","rooms":1,"reference":"Tìm khách sạn Huế ngày 20/12/2026 cho 1 phòng 2 người lớn"}
```

Rule: Unexpected reference on ordinary/clarification request. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:126'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-039

Utterance: Tìm khách sạn Huế giá dưới âm 2 triệu

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"CLARIFY","destination":"Huế","issues":["PRICE_INVALID"]}
```

Rule: Clarification cannot mutate criteria. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:127'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-040

Utterance: Tìm khách sạn Huế cho 0 phòng và 0 người lớn

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "rooms",
    "adults"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"CLARIFY","destination":"Huế","issues":["ROOMS_INVALID","ADULTS_INVALID"]}
```

Rule: Clarification cannot mutate criteria. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:127'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-046

Utterance: Tìm khách sạn Huế trả phòng 22/12/2026

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [
    "checkIn"
  ],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent": "CLARIFY", "destination": "Hu\u1EBF", "issues": ["ARRIVAL_MISSING"], "reference": "T\u00ECm kh\u00E1ch s\u1EA1n Hu\u1EBF tr\u1EA3 ph\u00F2ng 22/12/2026"}
```

Rule: Unexpected reference on ordinary/clarification request. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:126'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.

### VNE1-047

Utterance: Tìm khách sạn Huế từ 22/12/2026 đến 20/12/2026

Expected:
```json
{
  "action": "CLARIFY",
  "intent": "CLARIFY",
  "fields": {},
  "reference": "",
  "missingSlots": [],
  "inferredFields": []
}
```
Actual raw response:
```json
{"intent":"SEARCH","destination":"Huế","issues":["DATE_ORDER_INVALID"]}
```

Rule: Issues incompatible with criteria mutation. Original exact stage: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:124'}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: Action-specific instruction matrix; preserve strict rejection; retain all supported explicit facts on valid criteria requests. Regression risk: Over-clarifying missing business prerequisites, losing explicit facts or accepting contradictory proposals.


Ordinary runtime remains OFF/FAKE/liveOFF; v2 is not promoted. No domain/Gateway/Supabase/payment/refund calls, WRITE tools, database/schema/frontend/dependency changes, commits/staging/push/reset/checkout. Recognition is evaluated; trusted target resolution, tool execution and FULL_AGENT_E2E are NOT_RUN. D07/D08 remain unaccepted; E30 remains NOT_RUN without authenticated ordered ingress. No production readiness claim.
