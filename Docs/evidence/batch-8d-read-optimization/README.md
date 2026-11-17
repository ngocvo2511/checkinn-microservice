# Batch 8D — Read amplification & hotel search optimization evidence

Dữ liệu đo thật của run `batch8d-20261117T120501Z-83cc83bf` (17/11/2026, UTC 12:05–15:2x). Không trộn với Batch 8/8B/8C. Báo cáo tổng hợp: [`../../BATCH8D_READ_OPTIMIZATION_2026-11-17.md`](../../BATCH8D_READ_OPTIMIZATION_2026-11-17.md).

| Nhóm | File |
|---|---|
| Git baseline | `initial-git-backend.json`, `initial-git-frontend.json` (ghi trước khi tạo bất kỳ file 8D nào trong repo), `final-git-*.json` |
| Artifact | `before-artifact.json`, `after-artifact.json` (SHA256 JAR + fingerprint source), `hotel-artifact-events.json` (JAR nào chạy khi nào) |
| Supabase preflight | `*-runtime-preflight.json`, `*-before.json`/`*-after.json` (schema fingerprint, Flyway, counts), `*-sessions-*.json`, `preflight-summary-*.json` |
| Runtime readiness | `smoke-*.json`, `benchmark-readiness-gate.json`, `*-prestart-headroom.json`, `runtime-metrics-*.json` (Hikari/JVM/CPU), `runtime-stability.json` |
| Fixture | `fixture-manifest.json` (exact IDs + trạng thái cleanup), `dataset-*.json`, `shrink-3x5.json`, `shrink-2x2.json`, `fixture-scope.json` |
| SQL attribution | `sql-trace-{before,after}-{small,medium,larger}.json` (fingerprint SQL không bind value), `root-cause-attribution-{before,after}.json`, `sql-trace-before-small-parser-v1.json` (lần đầu, parser chưa gom log đa dòng — giữ làm lịch sử) |
| Benchmark | `benchmark-{before,after}-*-{raw,summary,warmup-raw}.json`, `benchmark-after-larger-dated-repeat-*.json`, `direct-{before,after}-{small,larger}.json`, `trace-warmup-*.json` |
| So sánh | `before-after.json` (SQL, p50/p95, throughput, slope), `response-equivalence.json`, `responses-*.json` |
| Correctness | `e2e-results.json`, `concurrency-results.json`, `search-approval-visibility-{before,after}.json` |
| Regression | `regression-results.json`, `local-disposable/native-results.json` (PostgreSQL 18 disposable, không hosted) |
| Cleanup | `cleanup-exact-id-plan.json`, `cleanup-progress.json`, `cleanup-results.json`, `broker-cleanup.json`, `redis-cleanup.json`, `manifest-parent-boundary-verification.json`, `final-schema-history-data-verification.json` |
| ADMIN | `admin-preservation-{before,after}.json` (HMAC identity + full users-table digest, không xuất hàng/password hash) |
| Secret scan | `secret-handling-verification.json` |

Quy ước: `hibernateStatisticsStatements` lấy từ Hibernate Session Metrics theo đúng W3C trace ID; `loggedJdbcTemplateSql` từ logger JdbcTemplate (Hibernate statistics không đếm JdbcTemplate); `physicalTransactions` từ JpaTransactionManager. SQL/JdbcTemplate/transaction DEBUG logger chỉ bật trên Hotel JVM do run sở hữu, chỉ trong cửa sổ trace (actuator loopback), không bật trong lúc đo latency; binder logging luôn OFF nên không có tham số SQL. JDBC duration gồm driver/network, không phải CPU PostgreSQL.

Tooling: `tools/batch8d.py` (tái dùng contract 8B/8C, evidence trỏ về thư mục này), `tools/batch8d-native.py` (cluster PostgreSQL 18 mới, tự hủy). Không dùng lại manifest này cho run khác.
