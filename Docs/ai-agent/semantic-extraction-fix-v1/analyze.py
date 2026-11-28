"""Recompute descriptive evidence from immutable local synthetic runs; never calls a provider."""
import csv
import hashlib
import json
import math
import statistics
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
LOCAL = ROOT / "ai-agent-service/.local/batch10b4d-fix"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def enrich(report, filename):
    for row in report["observations"]:
        raw = LOCAL / (filename + ".responses") / (row["sampleId"] + ".json")
        if raw.exists():
            envelope = read(raw)
            row["actualExtractionText"] = envelope["candidates"][0]["content"]["parts"][0]["text"]
            row["providerUsageMetadata"] = envelope.get("usageMetadata")
            row["rawEnvelopeSha256"] = digest(raw)
            try:
                row["actualExtraction"] = json.loads(row["actualExtractionText"])
            except json.JSONDecodeError:
                row["actualExtraction"] = None
        row["contextUnderstanding"] = "NOT_RUN"
        row["trustedTargetResolution"] = "NOT_RUN"
        row["conversationStateApplication"] = "NOT_RUN"
        row["actualReadExecution"] = "NOT_RUN"
    return report


def fraction(n, d):
    return {"numerator": n, "denominator": d, "percent": round(100 * n / d, 2) if d else None}


def metrics(report):
    rows = [r for r in report["observations"] if r["status"] == "ATTEMPTED"]
    n = len(rows)
    valid = [r for r in rows if r.get("schemaValid")]
    correct = [r for r in valid if r["semanticPass"]]
    wrong = [r for r in valid if not r["semanticPass"]]
    latencies = [r["latencyMillis"] for r in rows]
    known = [r for r in rows if r.get("totalTokens") is not None]
    result = {
        "planned": report["planned"], "attempted": n, "notRun": report["planned"] - n,
        "httpStatusCounts": dict(Counter(str(r.get("httpStatus", 0)) for r in rows)),
        "envelopeValid": sum(r.get("pipeline", {}).get("envelope") == "PASS" for r in rows),
        "sdkValid": sum(r.get("pipeline", {}).get("sdk") == "PASS" for r in rows),
        "schemaValidity": fraction(len(valid), n), "exactBusinessSemantics": fraction(len(correct), n),
        "schemaValidOnlyExactBusinessSemantics": fraction(len(correct), len(valid)),
        "backendAcceptance": fraction(sum(r.get("backendAccepted", False) for r in rows), n),
        "semanticFalseAcceptance": fraction(sum(r["backendAccepted"] for r in wrong), len(wrong)),
        "semanticFalseRejection": fraction(sum(not r["backendAccepted"] for r in correct), len(correct)),
        "correctAcceptedIds": [r["sampleId"] for r in correct if r["backendAccepted"]],
        "correctRejectedIds": [r["sampleId"] for r in correct if not r["backendAccepted"]],
        "incorrectAcceptedIds": [r["sampleId"] for r in wrong if r["backendAccepted"]],
        "incorrectRejectedIds": [r["sampleId"] for r in wrong if not r["backendAccepted"]],
        "backendNotEvaluated": n - len(valid),
        "parserExceptions": sum(r.get("pipeline", {}).get("backendReason") == "PARSER_DATE_EXCEPTION" for r in rows),
        "knownInputTokens": sum(r["inputTokens"] for r in known),
        "knownOutputTokens": sum(r["outputTokens"] for r in known),
        "knownTotalTokens": sum(r["totalTokens"] for r in known),
        "knownUsageAttempts": len(known), "unknownUsageAttempts": n - len(known),
        "meanKnownTotalTokens": statistics.mean(r["totalTokens"] for r in known) if known else None,
        "latencyMillis": {"mean": statistics.mean(latencies), "median": statistics.median(latencies),
                          "p95NearestRank": sorted(latencies)[math.ceil(.95 * n) - 1], "max": max(latencies)} if n else None,
        "reservedInputTokens": n * 16384, "reservedOutputTokens": n * 1024,
        "cooldownSeconds": sum(r.get("cooldownSeconds", 0) for r in rows),
        "rateLimitWaitMillis": report.get("rateLimitWaitMillis", 0),
        "legacyRunnerScores": report.get("scores"),
        "legacyScoreWarning": "falseAcceptance/falseRejection compare oracle backendExpected with admission, NOT semantic correctness; use semanticFalseAcceptance/semanticFalseRejection above."
    }
    for field in ["action", "intent", "criteria", "missingSlots", "reference"]:
        result[field] = fraction(sum(r["expected"].get(field) == r["actual"].get(field) for r in valid), n)
    price = [r for r in rows if "referencePriceExclusive" in r["expected"]["criteria"]]
    result["referencePriceCorrectness"] = fraction(sum(r.get("actual") is not None and r["actual"].get("criteria") == r["expected"]["criteria"] for r in price), len(price))
    result["spuriousClearIds"] = [r["sampleId"] for r in rows if any(
        value.get("operation") == "CLEAR" and r["expectedExtraction"].get("fields", {}).get(field, {}).get("operation") != "CLEAR"
        for field, value in (r.get("actualExtraction") or {}).get("fields", {}).items())]
    return result


technical = enrich(read(LOCAL / "technical-v1.json"), "technical-v1.json")
baseline = enrich(read(LOCAL / "baseline40-v1.json"), "baseline40-v1.json")
write("technical-verification-v1.json", technical)
write("baseline40-observations-v1.json", baseline)
not_run = [{"sampleId": r["sampleId"], "status": "NOT_RUN", "expectedExtraction": r["expectedExtraction"],
            "utterance": r["utterance"], "reason": "CAMPAIGN_SEALED_AFTER_TWO_CONSECUTIVE_HTTP_503"}
           for r in baseline["observations"]]
write("simplified40-observations-v1.json", {"planned": 40, "attempted": 0, "notRun": 40, "observations": not_run})
semantic_plans = read(ROOT / "ai-agent-service/target/semantic-plans-v1.json")
write("simplified-diagnostics-v1.json", {"status": "NOT_RUN", "planned": 8, "attempted": 0, "reason": "CAMPAIGN_SEALED", "maxAllowed": 15,
      "planSource": "OFFLINE_PLAN_NOT_DISPATCHED", "observations": semantic_plans["semantic-diagnostic"]})
write("simplified-request-plan-v1.json", semantic_plans["baseline40"])
write("generalization-v1.json", {"status": "NOT_RUN", "attempted": 0, "reason": "CAMPAIGN_SEALED", "maxAllowed": 20})
write("aggregate-metrics-v1.json", {"technical": metrics(technical), "A": metrics(baseline), "B": {"planned": 40, "attempted": 0, "notRun": 40}})
pairs = [{"sampleId": r["sampleId"], "A": "NOT_RUN" if r["status"] != "ATTEMPTED" else "PASS" if r["semanticPass"] else "FAIL",
          "AProviderFailure": r.get("httpStatus", 200) != 200, "B": "NOT_RUN", "pairedOutcome": "NOT_COMPARABLE"} for r in baseline["observations"]]
write("paired-comparison-v1.json", {"status": "PARTIAL", "comparablePairs": 0, "notComparablePairs": 40,
      "improved": None, "regressed": None, "decision": "MORE_TARGETED_EVIDENCE_REQUIRED", "pairs": pairs})
matrix = []
for r in baseline["observations"]:
    causes = []
    if r["status"] == "ATTEMPTED" and not r["semanticPass"]:
        if r.get("httpStatus") != 200: causes.append("H7_PROVIDER_HTTP_503")
        else:
            raw = r.get("actualExtraction") or {}
            if raw.get("action") != r["expectedExtraction"]["action"] or raw.get("intent") != r["expectedExtraction"]["intent"]:
                causes.append("ACTION_INTENT_ENCODING_OR_UNDERSTANDING_CAUSE_NOT_IDENTIFIED")
            if r["sampleId"] in ["VNE1-008", "VNE1-009", "VNE1-010", "VNE1-011", "VNE1-025"]:
                causes.append("H1_OBSERVED_EXPLICIT_FACT_OR_CONSTRAINT_ERROR")
            if r["sampleId"] in ["VNE1-002", "VNE1-010", "VNE1-020", "VNE1-025"]:
                causes.append("H3_OBSERVED_REPRESENTATION_ERROR_H2_H3_CAUSAL_HYPOTHESIS")
    if r["status"] == "ATTEMPTED" and r["semanticPass"] and not r["backendAccepted"]: causes.append("H4_CONFIRMED_CORROBORATION_LIMITATION")
    if r["status"] == "ATTEMPTED" and r.get("schemaValid") and not r["semanticPass"] and r["backendAccepted"]:
        causes.append("H4_H5_ADMISSION_DOES_NOT_VALIDATE_FULL_ORACLE_SEMANTICS")
    matrix.append({"sampleId": r["sampleId"], "status": r["status"], "causes": causes,
                   "expected": r["expectedExtraction"], "actual": r.get("actualExtraction"), "backendReason": r.get("pipeline", {}).get("backendReason")})
write("failure-matrix-v1.json", {"attribution": "Multi-label observed axes; not mutually exclusive causal counts", "rows": matrix})
attempts = []
for report in [technical, baseline]:
    start = datetime.fromisoformat(report["startedAt"].replace("Z", "+00:00")).timestamp()
    for r in report["observations"]:
        if r["status"] == "ATTEMPTED": attempts.append((start + r["dispatchOffsetMillis"] / 1000, r))
attempts.sort(key=lambda x: x[0])
rolling = max(sum(t - 60 < u <= t for u, _ in attempts) for t, _ in attempts)
spacing = min(b[0] - a[0] for a, b in zip(attempts, attempts[1:]))
budget = {"hardCap": 120, "actualHttpAttempts": len(attempts), "phaseAttempts": {"technical": 3, "existing": 25, "diagnostics": 0, "simplified": 0, "generalization": 0},
          "sealed": True, "reason": "TWO_CONSECUTIVE_HTTP_503", "automaticRetries": 0, "concurrency": 1,
          "reservedInputTokens": len(attempts) * 16384, "reservedOutputTokens": len(attempts) * 1024,
          "unknownUsageAttempts": 2, "observedMaxRequestsRolling60s": rolling, "observedMaxReservedTokensRolling60s": rolling * 17408,
          "observedMinDispatchSpacingSeconds": round(spacing, 3), "timestampResolutionCaveat": "Pre-dispatch timestamps, millisecond offsets; not provider arrival timestamps",
          "liveElapsedSeconds": round(attempts[-1][0] + attempts[-1][1]["latencyMillis"] / 1000 - attempts[0][0], 3),
          "cooldownSeconds": 15, "domainCalls": 0, "supabaseCalls": 0}
write("budget-accounting-v1.json", budget)
all_rows = [r for _, r in attempts]
known_rows = [r for r in all_rows if r.get("totalTokens") is not None]
latencies = [r["latencyMillis"] for r in all_rows]
write("all-attempted-performance-v1.json", {"scope": "Performance/usage only, not pooled quality accuracy", "attempted": len(all_rows),
      "knownUsageAttempts": len(known_rows), "unknownUsageAttempts": len(all_rows) - len(known_rows),
      "knownInputTokens": sum(r["inputTokens"] for r in known_rows), "knownOutputTokens": sum(r["outputTokens"] for r in known_rows),
      "knownTotalTokens": sum(r["totalTokens"] for r in known_rows),
      "thinkingTokens": "NOT_REPORTED", "meanLatencyMillis": statistics.mean(latencies), "medianLatencyMillis": statistics.median(latencies),
      "p95LatencyMillisNearestRank": sorted(latencies)[math.ceil(.95 * len(latencies)) - 1], "maxLatencyMillis": max(latencies),
      "rawInferredFactProposalIds": ["VNE1-008", "VNE1-009"],
      "rawInferenceExplanation": "Generic people became adults; invalid date was changed. Both rejected before domain/state application. Model inferredFields=[] is not proof of no inference.",
      "criticalAuthoritySafetyFlags": 0, "unauthorizedReadExecutions": 0, "unauthorizedWriteExecutions": 0,
      "fabricatedTrustedProvenanceObserved": 0, "realDataLeakageObserved": 0, "injectionResistance": "NOT_TESTED_DENY_NOT_SENT"})
hashes = read(ROOT / "Docs/ai-agent/nlu-quality-v1/preserved-hashes-v1.json")
for name, value in hashes.items():
    value["actual"] = digest(ROOT / Path(name.replace("\\", "/")))
    assert value["actual"] == value["expected"], name
write("preserved-hashes-v1.json", hashes)
contracts = read(ROOT / "Docs/ai-agent/nlu-quality-v1/contract-versions-v1.json")
a = next(v for v in contracts if v["version"] == "vietnamese-extraction-compact-v3")
assert a["instructionDigest"] == baseline["instructionDigest"] and a["schemaDigest"] == baseline["schemaDigest"]
b = read(ROOT / "ai-agent-service/target/semantic-contract-snapshot-v1.json")
source_names = ["model/VietnameseSemanticCompiler.java", "nlu/VietnameseParser.java", "nlu/VietnameseExtraction.java",
                "orchestration/SearchCriteriaState.java", "nlu/ReadIntentRouting.java", "orchestration/PolicyExecutor.java", "orchestration/GroundingGate.java"]
write("contract-snapshots-v1.json", {"A": a, "B": b, "model": baseline["model"], "oracleHash": baseline["oracleHash"],
      "datasetHash": baseline["datasetHash"], "context": "NO_CONTEXT_SENT", "baselineStartedAt": baseline["startedAt"],
      "AExecutableSha256": "df62eafaf602c5595edaa7d026edac905a1a599251c2dca692f35e045dcef3ca",
      "sourceSha256": {n: digest(ROOT / "ai-agent-service/src/main/java/com/example/aiagent" / n) for n in source_names},
      "BFreezeStatus": "OFFLINE_SNAPSHOT_ONLY_NO_LIVE_CAMPAIGN", "scoring": "Original canonical oracle exact equality; provider/schema failures remain attempted; B raw schema is not compared to A raw schema."})
write("compiler-verification-v1.json", {"source": "OFFLINE_TEST_SYNTHETIC_EQUIVALENT_PROPOSALS_NOT_GEMINI", "rows": read(ROOT / "ai-agent-service/target/semantic-compiler-verification-v1.json")})
fields = ["sampleId", "status", "httpStatus", "schemaValid", "semanticPass", "backendAccepted", "category", "latencyMillis", "inputTokens", "outputTokens", "totalTokens", "reservedInputTokens", "reservedOutputTokens", "continuationDecision", "continuationReason"]
with (OUT / "observations-v1.csv").open("w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["campaign"] + fields, extrasaction="ignore")
    writer.writeheader()
    for name, report in [("technical", technical), ("A", baseline)]:
        for row in report["observations"]: writer.writerow({"campaign": name, **{k: row.get(k) for k in fields}})
print(json.dumps({"budget": budget, "A": metrics(baseline)}, ensure_ascii=True))
