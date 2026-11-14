# Batch 3 reviewed evidence

Ngày 13/11/2026, Asia/Saigon. Audit/verification DONE trong bounded scope; overall database standardization PARTIAL. Không production certification.

- [Source inventory](schema-source-manifest.md): 131 SQL/entity/config/harness sources trong phạm vi truy cập; SHA-256/prerequisites/risk tại schema-sources.json. Object extraction heuristics cần đọc cùng ownership review, không là authoritative SQL parser.
- [Ownership](service-schema-ownership.md), [applied history](applied-history-manifest.md), [migration-only failures](migration-only-results.json).
- [Initial catalogs](clean-schema-manifest.json), [test-only final catalogs](testonly-final-schema-manifest.json), [final startup checks](startup-recheck-results.json), [Flyway history](user-flyway-history.json).
- [Legacy subset probes](compatibility-results.json), [financial constraints](financial-constraint-results.json), [schema diff](schema-diff-report.md).
- [Inventory provenance](inventory-provenance-review.md), [financial review](financial-schema-review.md), [encoding review](encoding-compatibility-review.md), [migration roadmap](migration-strategy.md).
- [Fresh Maven counts](regression-results.json), [reproduction](REPRODUCE.md), [evidence file hashes](evidence-manifest.json).

Raw logs + failed/intermediate harness attempts: checkinn/.local/evidence/batch-3-schema/ (Git ignored). Không database dumps, real customer data hoặc credentials trong reviewed evidence. Catalog snapshots schema-only, sorted, no owner/container OIDs or execution timestamps; environment image IDs có chủ ý để traceability.

Initial execution gặp reserved DB name current_user; resume chỉ trong owned project. First encoding text pipe changed LF→CRLF; fresh byte-pipe rerun PASS. First restart checker read retained old Docker log markers; final --since recheck separates current JVM run and waits past guards. Initial intermediate results moved to ignored raw evidence; authoritative startup is startup-recheck-results.json. Những lỗi harness này không counted application regressions.
