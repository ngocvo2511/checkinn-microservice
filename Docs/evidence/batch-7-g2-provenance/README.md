# Batch 7 — G2 provenance implementation

Ngày 16/11/2026, Asia/Saigon. Source fixes và disposable PostgreSQL verification; không hosted access/write, không chỉnh V1 hoặc dữ liệu lịch sử.

- [Initial Git state + protected hashes](initial-state.json).
- [Gap classification + safe OWNER/ADMIN review + migration boundary](gap-classification.md).
- [Inventory source/authorization/concurrency findings](inventory.md).
- [Text/JSONB contracts + historical financial boundary](compatibility-financial.md).
- [Schema verification + reproduction](schema.md).
- [Service-specific migration manifests](migration-manifests.json).
- [Báo cáo tiếng Việt](../../BATCH7_G2_PROVENANCE_IMPLEMENTATION_2026-11-16.md).

Không sử dụng actual hosted/legacy records làm fixtures. SQL review list chỉ chạy trên disposable synthetic data; output không chứng minh dữ liệu lịch sử thực tế. Không cộng các focused suites có thể trùng nhau thành tổng độc lập.
