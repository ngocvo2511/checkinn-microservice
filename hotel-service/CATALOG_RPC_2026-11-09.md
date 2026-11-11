# Public catalog RPC contract checkpoint

Branch: `fix/catalog-read-contract` in both repositories. The user asked to continue
before committing the previous hotel-content batch. Its uncommitted changes remain
intact on this branch, as do the two pre-existing IDE modifications. Review both
this checkpoint and HOTEL_CONTENT_2026-11-09.md before staging; no commits or pushes
were made. No new frontend code was changed by this RPC batch. Place-service and
frontend place/location wiring are untouched.

## Confirmed defects and fixes

- Capacity mapping used JsonNode.asInt(0), coercing fractional/string/boolean,
  missing and oversized values into reported occupancy. Object fields must now be
  integral int32 adults>=1 and children>=0, following the existing write DTO rules.
  Invalid stored structure is DATA_LOSS, never an invented zero/rounded capacity.
  A SQL/JSON-null capacity remains unknown: the optional protobuf message is absent.
  Callers must check hasCapacity; no default occupancy is introduced.
- Optional amenities are written as JSON null by existing writers. The RPC tried
  addAllAmenities(null) and failed the entire lookup. SQL/JSON null now yields no
  advertised amenities. Present arrays must contain strings; numeric/object/null
  elements are not coerced. Room create/update DTOs now reject null elements before
  writing, while the whole amenities field remains optional. Blank text policy was
  not changed and no arbitrary list/content limit was invented.
- RoomTypeBasic unboxed missing inventory and accepted zero/negative totals.
  The existing inventory-not-configured rule now becomes FAILED_PRECONDITION at
  this RPC boundary. Nothing is defaulted to zero or ten; no historical stock is
  changed. This is total inventory, not date-specific availability or a booking hold.
- Stored null/nonpositive prices or values that cannot be represented as a positive
  finite protobuf double are DATA_LOSS. Valid wire values and field numbers stay
  unchanged. This is catalog price validation, not a new quote/payment policy;
  protobuf double precision and authoritative BigDecimal booking flows are unchanged.
- All three RPCs reject abbreviated UUID forms before DAO access, map storage outages
  to UNAVAILABLE, keep unpublished resources NOT_FOUND, and return safe fixed error
  descriptions without logging exception causes or raw request identifiers.
- Room-list media caused2+3N SQL queries:1 room used5 statements,12 rooms used38.
  A single media query now joins the requested hotel and active rooms, enforcing
  active/APPROVED publication in SQL, ordered by sortOrder/id. The adapter groups
  images by target and rejects out-of-scope results. Actual cold-context tests
  verify at most3 statements at both sizes and correct image identity. Existing
  single-target public/preview/mutation authorization paths remain unchanged.

## Evidence

```powershell
mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package
python -X utf8 docker/availability-range-integration.py catalog-media-final catalog
python -X utf8 docker/catalog-rpc-smoke.py
```

- Baseline hotel317 executed PASS +14 opt-ins skipped, JAR BUILD SUCCESS.
- Test-first CatalogRpcContractTest23/23 FAIL; PostgreSQL query-count2 FAIL out of3
  cases (5/38 statements). The existing unpublished-resource case already passed;
  it is not presented as a newly fixed authorization defect.
- Final hotel344 executed PASS +17 opt-ins skipped (Surefire total361), zero
  failures/errors, JAR BUILD SUCCESS. Existing environment-dependent application
  test remains excluded; default skips are not counted as passes.
- Actual PostgreSQL/JPA4/4 PASS separately:1/12-room query budgets, inactive/foreign
  room images, and unpublished hotel filtering in both RPC and the new SQL query.
  Real repositories/services/SQL/statement statistics; only unused external clients
  are mocked. No new database migration or cloud storage request was needed.
- Rebuilt hotel image healthy; native grpcurl/HTTP20 assertions PASS. Positive
  price/inventory/amenity fields, correct image-room identity, malformed stored JSON,
  absent capacity, unconfigured inventory, unpublished-resource guards, null-amenity
  HTTP validation and no stored private marker in logs were checked on fresh UUIDs.
- Final audit:0 remaining fixture hotels,0 nonpositive persisted prices and0
  unconfigured total inventory in the designated clone. No production corruption
  is inferred from defensive mapped-entity price tests; no historical data was fixed.

Ignored evidence: hotel-service/target-catalog-*.log and workspace
logs/catalog-media-*.log. Synthetic fixtures contain no copied contacts, identities,
licenses or document paths; only an existing public city ID is reused. Cleanup is
scoped to generated hotel/room/media IDs in the verified checkinn-test clone.

## Rollout and limits

Deploy the hotel service; no proto, schema, frontend or consumer migration is
required. Consumers must respect non-OK statuses and protobuf message presence.
Malformed stored data is not silently omitted/repaired to return a partial success.
Existing optional description/amenities and positive numeric values stay compatible.

The query-count evidence is not a p95 latency/load/production benchmark. Whole
STD-025 remains open outside this query. Global tracing/MDC/framework logs, other
RPCs, provider integration, historical inventory provenance and full-system/gateway
E2E are not certified. Frontend145 Node/browser6 results belong to the prior content
batch and were not rerun here; its unchanged place showDistance typecheck blocker
is still recorded there, with no build bypass or place edit.

The root PROJECT_STANDARDIZATION_PLAN.md section70 is outside both repositories.
This file and Docker README preserve the checkpoint in Git for review/push.

After verification the five test containers were stopped; after confirming none
remained running, Docker Desktop was stopped successfully, restoring its initial
state. Containers, volumes and data remain intact. No machine shutdown occurred;
restart the designated clone before rerunning database/native checks.
