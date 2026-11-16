# Applied history manifest

**Production applied history: NOT VERIFIED.** Không kết nối production/developer/shared DB để điền lịch sử.

Các nhãn độc lập: SOURCE_PRESENT không đồng nghĩa HISTORY_CONFIRMED; HISTORY_CONFIRMED trong disposable không đồng nghĩa production-applied; SCHEMA_MATCHED chỉ dùng khi đối chiếu catalogs; TEST_ONLY không phải release baseline. [Per-file history](applied-history.json), [SQL receipts](test-applied-history.json), [Flyway](user-flyway-history.json).

| Scope | SOURCE_PRESENT | HISTORY_CONFIRMED | SCHEMA_MATCHED | CONFLICT / UNKNOWN |
|---|---|---|---|---|
| User V2–V5 | Có | Fresh Flyway trong user_testonly, baseline marker 1; checksums được lưu | Columns/constraints/indexes trong final catalog | V1 production UNKNOWN; marker không chứng minh baseline thực |
| Hotel/Booking explicit test order | Có | Các lệnh psql thành công trong test-applied-history.json | Catalog sau JPA + SQL | Test-only accommodation compatibility không thay historical migration |
| Revenue/Regulations explicit SQL | Có | Fresh Batch3 psql receipts | Catalog sau JPA + SQL | Production UNKNOWN |
| Cash original migration → nullable execution_mode | Có | Fresh source-derived subset, replay twice | Clean/upgrade subset MATCH | Không phải toàn service baseline |
| Hotel V1 → V2 | Có | V1 trên minimal synthetic hotels | Không đủ complete schema | V2 unsupported ADD CONSTRAINT IF NOT EXISTS |
| Hotel V5 sau JPA | Có | Attempt có log | expires_at đã tồn tại | Duplicate column; không rewrite history |
| Root SQL / archived SQL snapshots ngoài repo | Có + SHA-256 | UNKNOWN; không execute | UNKNOWN | Root total_rooms backfill nguy hiểm; các log cũ chỉ historical claims |
| Place | Metadata only | NOT VERIFIED | NOT VERIFIED | Protected, không chạy SQL |

Source-derived cash baseline: git ad58020d9995530d04085c3cb518e261e1b8d1d7 (revenue-cash-transactions-v1-20261107.sql). Upgrade source tại git 332f97a66c02b89abfd04720f73c9cc6d6cca47a. Chỉ synthetic receipt; không chứng minh production từng chạy các commit này.

Các Batch1/2 reports là evidence lịch sử có scope riêng; không tái sử dụng counts/logs đó làm fresh execution. Không có production schema snapshot, release-to-migration ledger hoặc verified historical full baseline. Cần DB owner cung cấp sanitized history/checksums, schema-only catalogs và release boundary qua quy trình được cấp quyền riêng; không tự truy cập DB.
