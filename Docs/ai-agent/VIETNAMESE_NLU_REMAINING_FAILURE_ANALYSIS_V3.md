# Vietnamese NLU remaining failure analysis v3

24/11/2026. **LIVE_TECHNICAL_BLOCKED**. Frozen40/fresh20 are NOT_RUN. The latest attempted diagnostic failures are listed below with actual retained text and unchanged expected semantics. Compiler failures remain failures; no retest replaces them. Diagnostic failures are separately retained in [diagnostic observations](semantic-quality-v2/diagnostic-observations-v2.json) and [revision metrics](semantic-quality-v2/diagnostic-revision-metrics-v2.json).


## DIAG-VNE1-003 (DIAGNOSTIC, segment-01-diagnostic-r2, gemini-3.5-flash-lite)

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
Actual raw proposal:
```json
{
  "intent": "SEARCH",
  "destination": "Đà Lạt",
  "reference": "Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng"
}
```
Canonical proposal:
```json
null
```

Failure stage: COMPILER; root cause H1/H2 (H3 representation risk when contradictions repeat). The model copied an ordinary stay request into reference and omitted both explicit dates plus adults/children/rooms. The compiler correctly rejects the unrelated reference. Both diagnostic revisions fail despite explicit retention instructions; the added full-stay example did not establish improvement.. Exact pipeline: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:174'}`. Backend accepted: False. Safety implication: no execution authority is granted; missing/altered facts or ungrounded mutations must continue to reject. Next action: analyze this observed semantic/coverage defect with generalized offline controls before any new bounded retest; do not correct the model response or change the oracle. Context required: no trusted context required for these literal single-utterance facts.

## DIAG-VNE1-004 (DIAGNOSTIC, segment-01-diagnostic-r2, gemini-3.5-flash-lite)

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
Actual raw proposal:
```json
{"intent":"SEARCH","arrival":"2026-12-01","departure":"2026-12-03","destination":"Hà Nội","rooms":1,"price":{"kind":"STAY","operator":"LT","unit":"VND_PER_ROOM_NIGHT","amount":1000000}}
```
Canonical proposal:
```json
null
```

Failure stage: COMPILER; root cause H1/H2 (H3 representation risk when contradictions repeat). The model invented Hà Nội and a strict nightly budget absent from the utterance, omitted explicit adults/children and retained only dates/rooms. Unsupported STAY LT is correctly rejected. This is uncorroborated inference, not a compiler defect; no invented value was admitted.. Exact pipeline: `{'stage': 'EXTRACTION_JSON', 'envelope': 'PASS', 'sdk': 'PASS', 'extraction': 'FAIL', 'failureClass': 'java.lang.IllegalArgumentException', 'failureLocation': 'com.example.aiagent.model.VietnameseSemanticCompiler.compile:162'}`. Backend accepted: False. Safety implication: no execution authority is granted; missing/altered facts or ungrounded mutations must continue to reject. Next action: analyze this observed semantic/coverage defect with generalized offline controls before any new bounded retest; do not correct the model response or change the oracle. Context required: no trusted context required for these literal single-utterance facts.

## DIAG-VNE1-010 (DIAGNOSTIC, segment-01-diagnostic-r2, gemini-3.5-flash-lite)

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
Actual raw proposal:
```json
NO_RETAINED_EXTRACTION
```
Canonical proposal:
```json
null
```

Failure stage: PROVIDER; root cause H8. No response exists for ATT-015. Status0, TIMEOUT and missing termination evidence caused the required UNKNOWN_IN_FLIGHT stop. Do not infer a model answer or retry. A post-stop wrapper metadata fix is offline-tested only and does not reopen this campaign.. Exact pipeline: `{'stage': 'ADMISSION', 'envelope': 'NOT_RUN', 'sdk': 'NOT_RUN', 'extraction': 'NOT_RUN', 'failureClass': 'com.example.aiagent.model.ProviderException', 'failureLocation': 'com.example.aiagent.model.LlmProviderAdapter$CallControl.check:45'}`. Backend accepted: False. Safety implication: no execution authority is granted; missing/altered facts or ungrounded mutations must continue to reject. Next action: analyze this observed semantic/coverage defect with generalized offline controls before any new bounded retest; do not correct the model response or change the oracle. Context required: no trusted context required for these literal single-utterance facts.


## Remaining scope and recommendation

Retain B v2 as a local development candidate. Do not promote or begin context-aware runtime implementation while critical single-utterance failures remain. Next work should target the actual remaining semantic failures below, particularly full explicit-fact retention or action consistency when observed. The flat optional contract still permits contradictory combinations; consider a small action-specific serving representation if failures persist, rather than expanding the independent grammar without bound. No authorization/infrastructure batch is recommended.

Ordinary runtime remains OFF/FAKE/liveOFF; v2 is not promoted. No domain/Gateway/Supabase/payment/refund calls, WRITE tools, database/schema/frontend/dependency changes, commits/staging/push/reset/checkout. Recognition is evaluated; trusted target resolution, tool execution and FULL_AGENT_E2E are NOT_RUN. D07/D08 remain unaccepted; E30 remains NOT_RUN without authenticated ordered ingress. No production readiness claim.
