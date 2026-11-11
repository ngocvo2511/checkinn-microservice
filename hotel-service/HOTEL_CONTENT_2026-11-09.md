# Hotel policy and FAQ consistency checkpoint

Branch: `fix/hotel-content-consistency` in both repositories, based on the user's
committed review fixes (`3325d18` backend, `91ad18b` frontend). No place-service,
frontend place wiring, booking/payment code, production schema/data, commits or
pushes are included. Two existing `.idea` modifications are unrelated.

## Confirmed defects and behavior

- Policy/FAQ DTOs had no constraints. Single REST writes did not activate validation;
  direct service and whole-list writes accepted malformed content. Hotel creation
  did not cascade validation into these lists. Required text is now validated at
  the boundary and service; question/category lengths use the existing255-column
  schema. No arbitrary content-length policy or migration was added.
- A null whole-list replacement deleted all FAQ/policies; null elements could fail
  after deletion. Lists are fully validated before repository reads/deletes. A
  literal null collection is invalid; `[]` remains the explicit clear operation.
  Existing authorized DELETE-all behavior remains available.
- Concurrent replacements each deleted then inserted without a common parent lock.
  Eight requests containing two rows each produced16 mixed rows in PostgreSQL.
  All eight policy/FAQ add/replace/remove/clear paths now lock the hotel first,
  using the existing pessimistic lock and transactions. The final collection is
  one complete submitted list. Hotel ownership and child/hotel binding remain
  enforced; no role or authorization check was weakened.
- HotelUpdateRequest accepted policies/questions but updateHotel ignored them.
  Those fields now use the existing replacement methods inside the outer hotel
  transaction. Omitted/null embedded fields preserve the collection; embedded
  `[]` clears it. Invalid nested content or unknown category rolls back scalar
  changes as well. Other embedded fields, including amenities, are outside this fix.
- Frontend create/edit forms silently filtered incomplete content rows. They now
  validate every supplied row and require completion or explicit removal. Empty
  optional lists remain valid. Edit errors are visible inside the modal and drafts
  survive rejection/outage. The existing successful update flow remains intact.
- The two content SDK writes share one15s deadline through response-body parsing,
  preserve safe HTTP status, never auto-replay, and verify the returned hotel ID
  and the canonical policies/faqs collection. An invalid success is unknown rather
  than a false clear/acknowledgement. Raw status text/upstream bodies are not logged
  or shown. Unknown category errors no longer echo submitted category text.
- A targeted controller advice maps storage exceptions to the existing safe503
  envelope for policy/FAQ and hotel endpoints, including embedded content updates.

## Verification

```powershell
mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package
python -X utf8 docker/availability-range-integration.py hotel-content-final content
python -X utf8 docker/hotel-content-smoke.py
```

- Baseline hotel304 executed PASS +8 opt-ins skipped; JAR BUILD SUCCESS.
- Final hotel317 executed PASS +14 opt-ins skipped; JAR BUILD SUCCESS. Disabled
  parameterized opt-ins are counted as skipped methods by Surefire; they are not
  passes. The environment-dependent HotelServiceApplicationTests remains excluded.
- Actual PostgreSQL/JPA7 executed cases PASS separately: eight-way replacements
  for each collection, rollback after delete/first insert, null versus empty,
  ownership, embedded round-trip/omission/clear, and scalar rollback on content
  failure. Repository wrappers widen the race and inject one write exception;
  SQL, persistence, locks and transaction rollback are real. No provider E2E claim.
- Rebuilt Docker hotel image healthy; native HTTP24 assertions PASS. These include
  eight concurrent HTTP replacements for each collection, denied anonymous/foreign
  owners, cross-hotel child deletion denial,400 malformed inputs, unknown category
  rollback/no echo, embedded update semantics and explicit authorized clears.
- Frontend Node145 PASS; development browser6 PASS for both policies and FAQ:
  blank rows cannot silently clear,503 retains draft/no replay/no private logs,
  and success displays acknowledged content. Browser APIs are intercepted fixtures.
- Typecheck still fails only at unchanged place-owned
  NearbyPlacesSection.tsx:161 showDistance props. No place change, build bypass or
  passing frontend production build is claimed.

Test-first evidence: service7 failures/47 tests; standalone HTTP6/6 failures;
SDK6/6 failures; actual PostgreSQL3 failures/5 cases (two race cases and null-clear).
The two existing rollback/foreign-owner cases already passed before the fix:
they were not newly discovered security/atomicity bugs. New embedded-update
regressions are supported by clear source evidence and green DB/native checks.

Logs are ignored under hotel-service/target-content-*.log and workspace
logs/hotel-content-*.log. Final fixtures use fresh UUIDs and explicitly synthetic
hotel fields, retaining only an existing public city ID for referential integrity;
they do not clone contacts, identity numbers, documents or license values. Cleanup
uses only generated hotel/category IDs in the verified isolated test clone.

## Rollout and limits

No schema migration is required. Deploy backend validation/locking and embedded
field support, then the frontend adapter/forms. Existing malformed stored rows are
not backfilled or removed; read contracts remain compatible. Correct or explicitly
remove malformed rows before resubmitting a whole collection.

Replacement remains last-writer-wins at the server lock. This is not browser
optimistic concurrency: a client may intentionally submit an older complete list.
No ETag/revision requirement, new approval rule, category deduplication or policy
taxonomy was invented. Existing category names and authorization remain in use.
The outer hotel transaction is intentional: internal replacement calls join it;
their correctness does not rely on self-invoked @Transactional creating a new one.

The root PROJECT_STANDARDIZATION_PLAN.md section69 is outside both Git repos.
This checkpoint and Docker/frontend test READMEs preserve rollout/test evidence
for review and push. This batch closes selected STD-012/018/024/026/028/030 gaps;
the broader issues and other services are not automatically marked complete.

After final checks, the five test containers were stopped without removing any
container, volume or database. Docker Desktop was also stopped after confirming
no containers remained running, restoring its initially-off state. Browser dev
server was torn down. No machine shutdown occurred; restart the designated clone
before rerunning database/native checks.
