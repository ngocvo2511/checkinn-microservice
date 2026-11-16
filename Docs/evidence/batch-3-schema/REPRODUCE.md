# Reproduce — isolated schema verification

Chạy PowerShell tại backend root. Requires Docker Desktop/Linux containers, Python, Maven/JDK21 và existing local images listed in startup-recheck-results.json. No image/source identity certification. Không dùng các probes trên service khác hoặc đổi hard-coded PostgreSQL target.

Project checkinn-schema-smoke phải chưa tồn tại cho full rerun. Primary script kiểm name collision, volume labels, mounts, no host ports và ownership; không tự down/delete/reuse volumes. Existing project của lần chạy này được giữ để review; PostgreSQL healthy, app containers stopped. Không tự cleanup stacks cũ.

```powershell
python docker/schema-source-inventory.py
python docker/schema-smoke.py
python docker/schema-migration-only.py
python docker/schema-testonly-bootstrap.py
python docker/schema-startup-recheck.py
python docker/schema-compatibility-probes.py
python docker/schema-financial-probes.py
mvn -pl hotel-service '-Dtest=HotelMapperTextTest,RoomDescriptionTest,InventoryCounterSafetyTest,RoomInventoryReviewServiceTest' test
mvn -pl regulations-service '-Dtest=RegulationConsistencyTest' test
```

Redirect console output vào .local/evidence/batch-3-schema/ khi cần giữ raw logs. Each SQL uses psql -X ON_ERROR_STOP; intended negative tests capture nonzero exits, no disabled constraints. Scripts create new fixture databases once; name collisions stop execution rather than reset tables/data. Follow-up scripts verify Batch3 label before issuing SQL. Compose network internal, no published host ports, dedicated named volume, no shared mounts, disposable trust auth only inside private project network. Không copy private env từ stacks trước.

Primary current bootstrap giữ defaults (User validate + Flyway; other JPA update). Follow-up test-only User uses exact Batch2 minimal baseline and explicit update override; Hotel accommodation compatibility bootstrap remains TEST_ONLY. Negative historical Hotel V2/V5 and cash-source-derived subset are separate from full release upgrade.

Snapshots tạo bởi schema-smoke.snapshot, stable ordering and JSON formatting; normalized schema equivalence uses complete record equality. Cash comparison excludes rows by design; original synthetic receipt fields checked independently. Source inventory outside repo retains original files and hashes; don't execute root stock scripts.

Final reviewed docs/summary are authored after inspecting outputs; re-running scripts does not auto-certify production or auto-close STD. Do not rerun probes against developer/shared/production DB.
