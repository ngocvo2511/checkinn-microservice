# Amenity search review — 7 November 2026

Branch: `fix/amenity-search-batching`, based on `master` independently of the event
and notification PRs. Replace one reviewed-code SQL lookup per property with
bounded 500-property queries. Keep approved-request, title snapshot, active item,
active category/catalog, hotel identity and all-requested-code guards.

The selected hotel package passed 275 executed tests; four opt-in integrations
were skipped and environment-dependent ApplicationTests excluded. The explicit
PostgreSQL AmenityCatalogQueryIntegrationTest passed: three hotels need one query,
501 identifiers need two, duplicate codes and publication guards are preserved,
and rollback removes generated fixtures. The rebuilt hotel image passed 79 fresh
amenity API assertions. A scoped 24-hotel/48-room, two-night read baseline checked
18 measured HTTP requests plus warmup, sorted/paged offers and unchanged inventory
rows. Timings include local Docker-exec/curl overhead and are not production SLOs.

```powershell
python -X utf8 docker/availability-range-integration.py review-events-amenity-integration amenities
python -X utf8 docker/amenity-catalog-smoke.py
python -X utf8 docker/search-read-baseline.py
```

The shared integration runner also recognizes optional suites supplied by the
separate event PR; run the amenities selector for this branch. No schema migration
or place-service change is introduced here. Full candidate loading and total
search throughput remain separate scaling work. No production deployment occurred.
