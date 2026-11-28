# Vietnamese evidence-first remaining failure analysis v1

24/11/2026. One model-evidence failure and four provider/transport failures were observed. No frozen40/fresh20 semantic failures can be inferred from NOT_RUN. Full expected canonical objects, source identities, raw evidence, pipeline locations and decisions: [machine-readable failures](evidence-extraction-v1/remaining-failures-v1.json).


## DIAG-VNE1-013 (DIAGNOSTIC)

Utterance: Kiểm tra phòng này còn phòng không

Expected: ASK_SELECTED with a complete target expression; canonical SELECTED/CLARIFY, no fields, whole original reference, no missing/inferred slots. Actual request meaning was correct, but REFERENCE “này” omitted its noun and ROOMS “phòng” had no quantity. Evidence membership was valid; meaning/completeness were not. One missing reference witness, two wrong-type/incomplete fragments; invented0, duplicates0. Canonical result: none; evidence verification rejected before compilation/admission. Compatibility category EXTRACTION_SCHEMA_INVALID does not mean the raw JSON schema failed.

Root H1/H2: model evidence-type/target-expression misunderstanding and insufficiently specific reference instruction. No confirmed H4 compiler defect. Backend accepted=false; no state/tool/confirmation authority. Revision2 added a noun+qualifier instruction without weakening validation; its two labeled checks passed. Original failure remains in revision1; general single-utterance quality is unproven.

Actual evidence:
```json
{
  "request": "ASK_SELECTED",
  "facts": [
    {
      "type": "REFERENCE",
      "text": "này"
    },
    {
      "type": "ROOMS",
      "text": "phòng"
    }
  ]
}
```


## CMP-FLASH_35_LITE-VNE1-003 (COMPARISON)

Utterance: Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng

Expected meaning: Complete Đà Lạt stay01/12/2026–03/12/2026,2 adults,0 children,1 room. Actual raw evidence: **NOT_AVAILABLE**. Missing/invented evidence: **NOT_EVALUATED**, not zero semantic errors. Canonical result: NOT_EVALUATED; backend admission not reached. Failure stage: PROVIDER/TRANSPORT, category UNAVAILABLE; all local exchanges terminated, usage UNKNOWN, full17408 reservation retained.

Root H8. Actual HTTP503 provider body reported High Demand; fixed-model comparison correctly disabled fallback. No H1/H2 model-semantic conclusion is justified. No state/tool authority was granted. The failed attempt remains even where a later distinct sample succeeded. Next action: retain the evidence and review provider availability under a separately valid future scope; never retry/resume this sealed campaign or shop for an answer.

Actual evidence:
```json
null
```


## CMP-FLASH_35_LITE-VNE1-013 (COMPARISON)

Utterance: Kiểm tra phòng này còn phòng không

Expected meaning: Selected-room availability recognition with no target resolution. Actual raw evidence: **NOT_AVAILABLE**. Missing/invented evidence: **NOT_EVALUATED**, not zero semantic errors. Canonical result: NOT_EVALUATED; backend admission not reached. Failure stage: PROVIDER/TRANSPORT, category TIMEOUT; all local exchanges terminated, usage UNKNOWN, full17408 reservation retained.

Root H8. No response arrived before the local10s deadline. Provider queue/inference/network contributions cannot be separated. No H1/H2 model-semantic conclusion is justified. No state/tool authority was granted. The failed attempt remains even where a later distinct sample succeeded. Next action: retain the evidence and review provider availability under a separately valid future scope; never retry/resume this sealed campaign or shop for an answer.

Actual evidence:
```json
null
```


## CMP-FLASH_31_LITE-VNE1-002 (COMPARISON)

Utterance: Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu

Expected meaning: Nha Trang reference price strictly below2000000 VND. Actual raw evidence: **NOT_AVAILABLE**. Missing/invented evidence: **NOT_EVALUATED**, not zero semantic errors. Canonical result: NOT_EVALUATED; backend admission not reached. Failure stage: PROVIDER/TRANSPORT, category TIMEOUT; all local exchanges terminated, usage UNKNOWN, full17408 reservation retained.

Root H8. No response arrived before the local10s deadline. Provider queue/inference/network contributions cannot be separated. No H1/H2 model-semantic conclusion is justified. No state/tool authority was granted. The failed attempt remains even where a later distinct sample succeeded. Next action: retain the evidence and review provider availability under a separately valid future scope; never retry/resume this sealed campaign or shop for an answer.

Actual evidence:
```json
null
```


## CMP-FLASH_31_LITE-VNE1-003 (COMPARISON)

Utterance: Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng

Expected meaning: Complete Đà Lạt stay01/12/2026–03/12/2026,2 adults,0 children,1 room. Actual raw evidence: **NOT_AVAILABLE**. Missing/invented evidence: **NOT_EVALUATED**, not zero semantic errors. Canonical result: NOT_EVALUATED; backend admission not reached. Failure stage: PROVIDER/TRANSPORT, category TIMEOUT; all local exchanges terminated, usage UNKNOWN, full17408 reservation retained.

Root H8. No response arrived before the local10s deadline. Provider queue/inference/network contributions cannot be separated. No H1/H2 model-semantic conclusion is justified. No state/tool authority was granted. ATT-021 completed the second consecutive timeout and sealed the campaign. Next action: retain the evidence and review provider availability under a separately valid future scope; never retry/resume this sealed campaign or shop for an answer.

Actual evidence:
```json
null
```


## Not-run limitations and architecture

Comparison22 planned observations, frozen40 all40 and fresh20 all20 remain NOT_RUN, with every identity/utterance/expected outcome retained in the plan/observation artifacts. They are not semantic FAILs, successful recoveries or exclusions. No confirmed implementation defect caused the campaign stop; timeout metadata continued safely until the existing technical threshold required closure.

Recommendation MORE_EVIDENCE_REQUIRED. Parser-only40/40+20/20 shows substantial deterministic coverage, not incremental LLM value. Do not respond to this provider-limited result by indefinitely expanding prompt/parser rules or migrating runtime. D07/D08 unaccepted; E30/FULL_AGENT_E2E/context resolution/tools NOT_RUN. No production acceptance.
