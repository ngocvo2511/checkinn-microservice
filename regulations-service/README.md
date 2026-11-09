# Regulation consistency checkpoint — 7 November 2026

Branch `fix/regulation-write-consistency` starts from backend `57cfe54`
(`fix/amenity-search-batching`). The companion frontend starts from `166877a`
(`fix/revenue-reporting-consistency`). Existing `.idea` edits are unrelated.
Other review branches are not implicitly merged by this change.

## Behavior

- Public regulation/typed-rate reads retain their existing access policy.
  Audit snapshots require ADMIN: anonymous401, customer/owner403. PUT/refresh
  remain ADMIN-only. Audit actor comes from the authenticated principal.
- Reads use committed PostgreSQL state rather than a process-local map. The
  compatibility refresh endpoint verifies the source. Loyalty rate calculations
  fetch the authoritative HTTP endpoint; an old positive advisory cache cannot
  hide a changed rate or an outage. Unavailable/invalid rates fail503.
- Clients send the version they read. Existing rows use JPA optimistic locking;
  stale/concurrent updates return409. A winner increments the existing version,
  writes valid JSON audit and enqueues an event in the SAME transaction.
  No new version column or historical rewrite is required.
- Snapshot serialization preserves Unicode, quotes, backslashes, newlines and
  nulls. Existing malformed audit rows are not automatically repaired.
- Source commit failure rolls back source/audit/outbox together. Broker failure
  leaves pending work. A separate worker uses persistent messages, stable IDs,
  mandatory returns and correlated confirms. Broker acceptance with local
  completion failure replays the same identity. Earlier pending versions block
  later delivery per key. Third-party consumer deduplication/ordering and
  exactly-once delivery are not guaranteed by this producer.
- History/storage failures are explicit503, never empty-success history. Error
  envelopes have status/code/message without raw database/transport details.
  Application phases omit regulation content and the audit actor.
- Session-store outages fail closed with503 instead of pretending the token is
  invalid401. Valid sessions recover without re-login; malformed signed UUID
  principals are rejected401 before controller extraction. Native session probe
  below is read-only HTTP and deliberately restarts ONLY isolated test Redis.
  Missing routes/unsupported methods retain404/405, including the Allow header.
- Existing default rates, numeric range and active/inactive policy are retained.
  Generic numeric range stays0..999999.99; no new commission/points bounds are
  invented. Storage-backed text limits and path/body key agreement are validated.
  Existing startup defaults now use a proxied source transaction.

## Rollout

Apply `../docker/sql/regulation-event-outbox-v1-20261107.sql` to REGULATIONS
before deploying the new writer, then deploy compatible frontend and loyalty
readers. The additive script audits source versions and creates outbox/indexes.
It is replayable, does not backfill events or change the four rates. Preserve
audit/outbox on application rollback; avoid mixed old writers which ignore
version preconditions and durable events.

The exchange/routing key and event fields remain compatible. Ensure a durable
queue is bound to `hotel.events` / `regulation.updated`; otherwise mandatory
returns intentionally keep work pending. Monitor pending age/count and safe
last_error codes. Payload content/actor remain private audit data, excluded
from public diagnostics. No production migration/deployment was performed.

The frontend SDK validates successful shapes and complete decimal input, maps
safe401/403/409/503 failures and uses a15s abort deadline covering JSON reads.
The UI retains drafts after409, requires reload before resubmission, and locks
form controls while saving. History outage remains visible.

## Verification

Observed final checks: regulations30 and user101 executed tests passed, with
one/two opt-in skips respectively; both JARs build successfully. Actual JPA
integration1, native source/outbox24 and final session/HTTP7 assertions pass.
Frontend Node110 and browser3 pass. These are separate runs; skipped tests
are not passes and no full-repository or clean frontend production build is claimed.

From backend root with the isolated clone running:

```powershell
mvn -o -B -ntp -pl regulations-service,user-service '-Dtest=*,!RegulationsServiceApplicationTests,!UserServiceApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' package
python -X utf8 docker/availability-range-integration.py regulation-atomic-integration-green regulations
python -X utf8 docker/regulation-consistency-smoke.py
python -X utf8 docker/regulation-session-smoke.py
```

The opt-in integration uses real Spring Data repositories/JPA/JDBC on loopback
forwarded clone PostgreSQL:8 competing updates, source/audit/event rollback,
deferred commit failure and accepted-send/local-commit replay. Default skips do
not count as integration passes. Native harness runs alone, restarting ONLY
test RabbitMQ/regulations. Its exact synthetic key, temporary durable queue
and unroutable exchange are cleaned; copied rates/audits/queues remain intact.
The harness queue must survive its deliberate broker restart. No payment
provider or external notification is called.

Frontend commands:

```powershell
npm run test:regression
npx playwright test --config=playwright.regulations.config.ts
npx tsc --noEmit
```

Browser tests use the actual Next dev server with intercepted fixtures; they
are separate from native API/database evidence and imply no production build
pass. Standard typecheck remains blocked by existing place-owned `showDistance`
props at `components/NearbyPlacesSection.tsx:161`; no place source or TypeScript
bypass is changed. Detailed observed counts/red-green/limits are in root
`PROJECT_STANDARDIZATION_PLAN.md` section66, outside both Git repositories.


## Fresh review — 9 November 2026

Fresh selected User/Regulations unit suites passed; frontend 110 Node regressions
and three development-server browser cases passed. Typecheck still reports only
the unchanged place-owned showDistance mismatch. Docker has no running test stack;
the integration runner stopped at clone verification, so integration/runtime
checkpoint counts above were NOT rerun or certified by this review. No place code,
local IDE settings, production schema/data or deployment changed.
