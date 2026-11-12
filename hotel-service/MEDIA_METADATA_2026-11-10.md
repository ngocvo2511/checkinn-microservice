# Media metadata consistency — 10/11/2026

Branch: `fix/media-metadata-consistency` in both repositories. Contains the earlier
uncommitted content/catalog/amenity batches; review those checkpoints too.
Root `PROJECT_STANDARDIZATION_PLAN.md` is outside Git. No commit/push is performed.

## Confirmed defects and fixes

- Thumbnail selection returned an old detached entity with `isThumbnail=false`.
  Uploading a new thumbnail left the previous flag true. Concurrent selection and
  upload could also leave two flags. `MediaMutationService` now serializes writes
  using the hotel lock (then the room lock for ROOM_TYPE), rechecks ownership and
  parent existence inside the transaction, clears peers and returns the updated
  managed entity. No automatic thumbnail promotion or legacy backfill is added.
- Delete and reorder were separate repository transactions. Reorder failure left
  the deletion committed. Both metadata operations now commit/roll back together.
  Scalar media-location queries avoid hydrating a stale entity before locking.
- Draft deletion did not coordinate with metadata registration or room creation.
  It now takes the same hotel lock. The user explicitly chose to block deletion
  if ANY room remains, including inactive rooms; response is 409 before child writes.
  Room creation also locks that hotel. The actual test clone has a room->hotel FK:
  the old concurrent create failed at the FK, rather than leaving an orphan there.
  The regression now verifies a clean EntityNotFound result after waiting for deletion.
- Amenity response field `items` was silently ignored on writes, clearing children
  when `amenities` was absent. Both SDK and backend reject `items` (including when
  `amenities` is also supplied). Genuine omitted/null optional child lists retain
  their previous semantics. No alias or catalog approval is inferred from labels.
- Policy/FAQ/amenity modal controls stayed editable/closable during pending saves.
  A shared synchronous write gate and disabled controls now preserve the submitted
  draft until acknowledgement/failure, with no overlapping content writes.
- Upload logged the supplied filename before authorization. That field is removed.
  Media database failures use the existing safe 503 handler instead of generic 500.

## Evidence

Fresh baseline: hotel 360 executed PASS +24 opt-ins skipped, JAR build success.
Before fixes: MediaMetadataIntegrationTest 7 failures /8 tests; MVC 4 failures /21;
SDK 2 failures /19; policy/FAQ browser 2 failures /2. Subsequent focused DB red run
confirmed draft guard and registration race; final room-creation red case received
DataIntegrityViolationException where a clean missing-parent result was required.
Harness correction: initial targeted MVC green attempt had a test stub compile
error (Hotel vs Optional<Hotel>); fixed at the repository stub without weakening tests.
One SDK invocation omitted the required --experimental-strip-types flag and failed
loading .ts; the package regression script was then used successfully.

Actual PostgreSQL: `python -X utf8 docker/availability-range-integration.py media-metadata-green media-metadata`
**10/10 PASS**, including real JPA commits/rollbacks and gated races. MediaClient is
mocked; upload/delete assert that no database transaction is active during provider I/O.
Node: `npm run test:regression` **164/164 PASS**.
Browser: `npx playwright test --config=playwright.content-webpack.config.ts` **8/8 PASS**;
`npx playwright test --config=playwright.amenities.config.ts` **7/7 PASS**.
Browser runs use actual Next Webpack development components and intercepted APIs.
Typecheck remains blocked only by the pre-existing out-of-scope
`NearbyPlacesSection.tsx:161 showDistance`/PlaceCardProps mismatch. No place code,
typechecking bypass, default Turbopack success or production frontend build claimed.

Full Maven/package: **396 total, 364 executed PASS +32 skipped**, BUILD SUCCESS.
`mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package`
produced `target-media-final-suite.log`. Image rebuilt and healthy, then
`python -X utf8 docker/media-metadata-smoke.py` **16/16 PASS**
(`target-media-native-expanded.log`): HOTEL/ROOM_TYPE true ACKs, six competing
thumbnail PATCHes each, 401/403, legitimate room creation/deactivation, inactive
room deletion guard409/preservation, items400 and empty draft200. No cloud I/O.
Root-plan section72 lists evidence, scopes and residual blockers.

## Residual scope and rollout

No schema/migration or provider contract changes. Deploy backend before frontend.
Hotel-level locking serializes image writes across rooms of one hotel; watch contention.
Provider objects and database metadata are NOT a distributed transaction. An upload
acknowledged before DB failure may leave an orphan object; a provider-acknowledged
delete followed by DB rollback may leave metadata referencing a missing object.
Do not blindly delete objects on ambiguous commits. Live storage, reconciliation/
idempotency policy and crash recovery remain NEEDS_ENVIRONMENT / separate work.
Tests only write generated synthetic fixtures in the verified checkinn-test clone.
No production data, place-service, place-related frontend, legacy IDs or catalog
approvals are rewritten. Existing IDE changes remain untouched.


Final audit: both git diff --check PASS; backend46/frontend18 pending paths,
including previous batches and two untouched IDE changes. No changed/untracked
place/OSRM path. Remaining synthetic metadata hotels=0. Stopped exactly the five
started test containers without removing volumes/data; Docker Desktop remains
running as it was at entry. Browser servers are torn down. No commit/push.


## Fresh review — 11 November 2026

Fresh selected hotel suite passed (ApplicationTests excluded, opt-in database
tests skipped). Frontend 164 Node regressions, eight content and seven amenity
browser tests passed using Webpack development mode and synthetic APIs. Standard
typecheck retains the known place-owned showDistance mismatch; no bypass or
production-build pass. Earlier PostgreSQL/native counts are historical checkpoint
evidence, not rerun/certified in this review. Logs and IDE files are excluded.


## Frontend follow-up — 11/11/2026

On `fix/media-client-write-state`, the sibling frontend now consumes these existing
contracts with bounded media/draft adapters, validates target-bound ACKs, serializes
gallery writes and reconciles unknown results through GET /medias/preview. Wizard
partial uploads stop and open the existing draft instead of replaying prior files.
No backend production/schema/provider code changes in that follow-up; no fresh
Maven/native/database run is claimed there. Previous evidence above remains dated.
Frontend evidence: Node211/211, new media browser13/13; typecheck still has the
place-owned showDistance baseline. See root PROJECT_STANDARDIZATION_PLAN.md73 and
frontend tests/MEDIA_CLIENT_2026-11-11.md for compatibility rerun/final scope.
