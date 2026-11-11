# Amenity writes and category-read checkpoint

Branch `fix/amenity-write-contract` in both repositories retains the uncommitted
hotel-content and catalog-RPC batches. Their checkpoints remain applicable; no
staging/commits/pushes or resets occurred. Two pre-existing IDE modifications stay
separate. No place-service, place frontend, location/map wiring, financial code or
production schema/data changed.

## Confirmed fixes

- Amenity/category/item DTOs lacked constraints, the replacement wrapper and single
  controller did not activate @Valid, and hotel DTOs did not cascade validation.
  Names now use NotBlank and the existing255-column limits. Nullable/empty child
  lists remain compatible; null elements are rejected. The entire replacement is
  validated before reading/deleting through the service as well as HTTP.
- Literal null/missing replacement list formerly cleared all amenities. It is now
  invalid; explicit[] remains the supported clear. HotelUpdateRequest accepted
  amenityCategories but ignored it. It now uses the existing replacement method
  inside the outer transaction: omitted/null embedded fields preserve,[] clears.
  Invalid nested content cannot change hotel scalars.
- Create/edit forms filtered incomplete categories/items before submission. They
  now validate every row and require completion or explicit removal. The editor
  can clear the optional collection by removing its categories, keeps failed drafts,
  and shows inline errors. No server minimum category/item count was introduced.
- The existing shared content adapter now handles the amenity PUT wrapper and
  canonical response amenityCategories[].items. It validates input, returned hotel
  identity and nested text/collection shape, has one15s request/body budget, preserves
  safe HTTP status, and never auto-replays mutations or logs upstream bodies.
- A browser regression exposed an existing read loop: empty/failing suggestion
  results toggled loading, which was an effect dependency and triggered another
  fetch. Policy had the same pattern. Both load once per modal opening or explicit
  retry, ignore stale completions after close, expose read errors and preserve
  drafts/current selections. Public dictionary SDK reads use the existing bounded
  readJson/readArray contract; successful null is not a genuine empty dictionary.
- Amenity storage errors now use the existing safe503 controller advice. Missing
  category exception text no longer echoes submitted titles. Request paths/global
  logging outside this scope are not certified or redesigned.

## Business semantics retained

The catalog migration explicitly keeps audit target IDs without foreign keys and
joins effective codes to the current raw identity. Full raw replacement creates
new IDs; an identical label does not inherit approval. This is intentional, not a
defect to fix by matching names or auto-aliasing. Real PostgreSQL and native HTTP
tests show old APPROVED history survives, while new raw IDs require review.

The hotel parent lock, authorization and transaction rollback already worked before
this batch. Their baseline tests passed and remain regressions; they are not claimed
as newly repaired races/security holes. No catalog code seeds, mapping migration,
deduplication, schema change or production cleanup was added.

## Verification

```powershell
mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package
python -X utf8 docker/availability-range-integration.py amenity-write-green amenity-writes
python -X utf8 docker/amenity-write-smoke.py
```

- Baseline hotel344 executed PASS +17 opt-ins skipped; JAR BUILD SUCCESS. Frontend
  baseline145 Node tests PASS.
- Test-first service/HTTP63 total:16 FAIL,47 PASS; SDK8/8 FAIL; actual PostgreSQL7
  cases:3 FAIL and4 existing-behavior PASS. The failed DB cases were null-clear,
  ignored embedded fields and invalid nested scalar update.
- Final hotel360 executed PASS +24 opt-ins skipped (Surefire384), zero failures/
  errors, JAR BUILD SUCCESS. The environment-dependent application test remains
  excluded; skips are not counted as passes. Selected service/HTTP63 PASS separately.
- Actual PostgreSQL/JPA7/7 PASS: explicit list semantics, nested round-trip, scalar
  protection, approved mapping audit/identity semantics, rollback after delete/first
  insert,8-way replacement and other-owner denial. Repositories/SQL/transactions and
  catalog workflow are real; one write fault/sleep is injected through a repository
  wrapper, not represented as a live database outage.
- Rebuilt hotel image healthy; native HTTP29 assertions PASS, including invalid
  nested requests400, owner/anonymous/foreign-scope denial, omitted/null/[] semantics,
  ADMIN catalog creation/review on generated data, approved-code visibility and
  retained audit after identical-label replacement,8 simultaneous replacements.
- Final fixtures use fresh UUIDs and synthetic fields, reusing only a public city ID.
  Exact hotel/code scoped cleanup leaves0 fixture hotels. Clone metadata confirms
  both name columns255 and audits0 null/blank legacy category/item names; this does
  not certify production or trigger historical repair.

Frontend commands:

```powershell
npm run test:regression
npx playwright test --config=playwright.amenities.config.ts
npx playwright test --config=playwright.content-webpack.config.ts
npx tsc --noEmit
```

- Node162 PASS, including17 new amenity/dictionary cases. Successful optional empty
  child lists, correct wrapper/response keys, malformed ACKs, safe errors, no replay,
  body deadline and genuine-empty versus malformed/outage dictionary reads covered.
- Browser6 new cases PASS plus6 unchanged policy/FAQ cases PASS on the same source.
  Real Next development server with intercepted APIs; not live backend/browser E2E.
- Default Turbopack navigation/readiness stalled while compiling the route. The
  new test runners use the installed supported Next --webpack dev option and warm
  the actual route. Assertions/security/type validation were not bypassed. The
  initial Webpack run3 PASS/1 FAIL exposed the real empty-result fetch loop; it was
  fixed in source, not hidden with force-click or fabricated dictionary data.
- Typecheck reports only the unchanged place-owned NearbyPlacesSection.tsx:161
  showDistance props mismatch. No place fix/build bypass or passing production
  frontend build/default Turbopack acceptance is claimed. The original test runner
  remains unchanged; the Webpack compatibility runner is included as a reviewable
  repository file rather than relying on an ignored temporary config.

Ignored logs: hotel-service/target-amenity-*.log and workspace
logs/amenity-write-*.log. A malformed escaped string in a new test initially failed
test compilation; replacing it with a typed input map allowed the real red run
before production changes. This was a harness error, not a baseline business bug.

## Rollout and remaining limits

No new migration: deploy backend validation/embedded support, then frontend. Existing
valid wrappers, optional child lists and explicit clear remain supported. Complete
or explicitly remove malformed rows before replacing a collection; no old rows are
rewritten. Full replacement remains last-writer-wins; it is not browser optimistic
concurrency or idempotency by label. Mapping review/audit rules remain authoritative.

Root PROJECT_STANDARDIZATION_PLAN.md section71 is outside Git. This checkpoint and
Docker/frontend READMEs preserve evidence for review/push. Main selected scopes are
STD-011/012/018/024/026/028, with limited030 privacy handling and016 compatibility
verification. Broader issues/services, global logging, full-system/provider E2E and
the environment's Turbopack stall remain outside the certified scope.

After verification, the five test containers started by this batch were stopped
without removing containers/volumes/data; no containers remain running. Docker
Desktop was already running before this batch and was left running. Browser dev
servers were torn down. No engine or machine shutdown was performed; start the
designated test clone before rerunning database/native checks.
