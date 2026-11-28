# Local development semantic review v1

Frozen before any live response; TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION. Codex technical review against approved D1/D2/D3, not independent human approval. Original fixture/oracle bytes unchanged. Visible holdout is not blinded.

Single-utterance extraction only. For 027–033/051, only operation/reference extraction is scored; state application, identity resolution and full multiturn semantics remain NOT_RUN. Empty missing-slot expectations on undated browse follow approved D1; ambiguity and unsupported constraints must not invent usable criteria.

|ID|Decision|Reason|
|---|---|---|
|VNE1-001|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-002|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-003|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-004|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-005|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-006|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-007|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-008|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-009|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-010|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-011|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-012|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-013|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-014|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-015|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-016|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-017|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-018|EXCLUDED|NEEDS_PRODUCT_REVIEW: missing children hint contradicts explicit unknown occupancy|
|VNE1-019|EXCLUDED|NEEDS_PRODUCT_REVIEW: incomplete occupancy missing-slot convention|
|VNE1-020|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-021|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-022|EXCLUDED|NEEDS_PRODUCT_REVIEW: incomplete occupancy missing-slot convention|
|VNE1-023|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-024|EXCLUDED|NEEDS_PRODUCT_REVIEW: partial occupancy clarification convention|
|VNE1-025|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-026|EXCLUDED|NEEDS_PRODUCT_REVIEW: action differs from equivalent VNE1-006|
|VNE1-027|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-028|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-029|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-030|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-031|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-032|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-033|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-034|EXCLUDED|NEEDS_PRODUCT_REVIEW: selected reference versus clarification|
|VNE1-035|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-036|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-037|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-038|EXCLUDED|NEEDS_PRODUCT_REVIEW: invalid-date missing-slot convention differs from 009|
|VNE1-039|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-040|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-041|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-042|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-043|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-044|EXCLUDED|NEEDS_PRODUCT_REVIEW: typo normalization versus clarification|
|VNE1-045|EXCLUDED|NEEDS_PRODUCT_REVIEW: mixed-language price has no explicit per-room/night or reference unit|
|VNE1-046|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-047|SELECTED|Safe clarification without fabricating usable criteria; original missing-slot target retained|
|VNE1-048|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-049|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-050|SELECTED|Literal criteria only; original unambiguous expected values retained|
|VNE1-051|SELECTED|Action/operation/reference extraction only; context-dependent success NOT_RUN|
|VNE1-052|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-053|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-054|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-055|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-056|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-057|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-058|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-059|EXCLUDED|PREMODEL_DENY: remains local, never sent|
|VNE1-060|EXCLUDED|PREMODEL_DENY: remains local, never sent|

Selected 40 distinct VNE1 samples, plus 3 developer-approved VEX1 smoke samples in one run. No supplemental planned.
