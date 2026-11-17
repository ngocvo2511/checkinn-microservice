# Batch 8C — Runtime stability & performance evidence

Các JSON trong thư mục này là dữ liệu đo thật của run Batch 8C; không trộn với evidence lịch sử Batch 8/8B. Báo cáo tổng hợp nằm ở `../../BATCH8C_RUNTIME_STABILITY_PERFORMANCE_2026-11-17.md`.

- `initial-git-*.json`: branch/HEAD/status và hash diff; Git status đầu tiên còn được ghi trong transcript trước khi tạo tooling.
- `*-runtime-preflight.json`, `*-before.json`, `*-after.json`: identity/TLS và schema/Flyway/count baseline.
- `connection-inventory.json`, `*-sessions-*.json`, `hikari-*.json`: budget, server backend visibility và Hikari client-pool measurements. Không đồng nhất Supavisor labels với client ownership.
- `root-cause-evidence.json`, `*-lifecycle-events.json`, `smoke-*.json`: startup/restart/shutdown, route propagation và ADMIN HTTP attempts, gồm failed attempts.
- `fixture-manifest.json`, `cleanup-feasibility.json`, `cleanup-*.json`, `broker-cleanup.json`: exact fixture ownership và cleanup; ADMIN được bảo vệ riêng.
- `benchmark-*-raw.json`, `benchmark-*-summary.json`, `direct-*.json`: mọi measured sample/failed sample; warmup và skipped safety cells được mô tả trong báo cáo.
- `query-counts-*.json`, `query-count-slope.json`: Hibernate JDBC statements và JDBC/acquisition/HTTP durations. Small initial windows và trace-scoped remeasurements được phân biệt.
- `before-after.json`: AFTER measured; Batch 8B chỉ là historical reference, không controlled percentage comparison.
- `concurrency-results.json`, `natural-expiry-*.json`: real unpaid booking/hold acceptance; không gọi provider hoặc sửa expiry.
- `regression-results.json`, `local-disposable/`: local regression/native canonical; không thay hosted acceptance.
- `*-digests-*.json`, `admin-preservation-*.json`, `final-verification.json`: HMAC preservation và final checks. Không chứa account rows, password hashes, credentials, JWT hoặc connection strings.

## Guarded local launcher

`python tools/batch8c-runtime.py start-core <label>` chỉ dùng prepared local Supabase-dev session đã được phê duyệt. Launcher khóa operation, từ chối module JVM/port trùng, kiểm role headroom, lưu PID+creation time, chờ từng health rồi polling chain. `stop` gọi Spring context close qua JDK local attach và xác minh JVM exit; không forced-kill fallback. Không mở remote JMX hoặc HTTP shutdown endpoint. Giữ nguyên launcher/config/profiles khác. Legacy `run-local-stack.py stop` vẫn là force-stop và không phải đường lifecycle được chấp nhận bởi Batch 8C.

Không dùng lại fixture manifest này cho run mới. Chỉ chạy cleanup với exact IDs của manifest, sau khi owned apps dừng; không wildcard DELETE, queue purge, Redis flush, schema/role/grant hoặc dashboard mutation.
