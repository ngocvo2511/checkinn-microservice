# Review contract and consistency checkpoint

Branch: `fix/review-contract-consistency`, paired with the same frontend branch.
No place-service, place frontend, booking/payment integrations or production data
were changed. Existing local `.idea` changes are unrelated and must not be included.

## Behavior and causes

- Review gRPC mutations previously skipped the REST DTO constraints. The RPC
  adapter now validates the same DTOs, bounds page size using the existing Spring
  pageable setting, and distinguishes malformed input, missing resources,
  authorization, conflicts and unavailable storage without publishing exceptions.
  Proto3 update rating zero remains the existing omitted-field sentinel; this
  change does not redesign the wire schema or add detailed rating proto fields.
- JWT signature/subject validation precedes revocation lookup. A session-store
  outage fails closed with UNAVAILABLE instead of claiming the bearer is invalid.
  Anonymous public reads and backend ownership checks remain in place.
- Owner reply creation used an unlocked check-then-insert. All reply writes now
  acquire the parent review lock, matching review deletion/moderation/voting.
  Update/delete read the scalar parent ID first and load the child after locking.
  One reply per review is enforced in PostgreSQL. Duplicate review/reply errors
  return HTTP409 / RPC ALREADY_EXISTS; other unexpected illegal states are INTERNAL.
- `GET /api/v1/reviews/me` is authenticated and queries the verified JWT subject,
  with stable createdAt/id descending pagination. It includes the actor's own
  PENDING/REJECTED rows and batches hotel names/replies. Core storage failures
  propagate as safe503. Optional remote user profiles are not needed for this page.
- Frontend account history previously aggregated public reviews of booked hotels,
  including other guests and only the first100 rows per hotel. It now uses `/me`,
  keeps pagination and receives replies from that private query. SDK failures no
  longer become empty histories or false "not reviewed" flags. Booking history
  keeps bookings while review state is unknown and hides write/view actions until
  lookup succeeds. Both pages support explicit retry. SDK sends bearer authority
  without local JWT identity decoding, maps update rating fields to REST's flat DTO,
  and applies one15s deadline through JSON body consumption without mutation retries.

## Migration and rollout

Apply `docker/sql/review-response-integrity-20261109.sql` to HOTEL before the new
writers. Deploy backend `/me` before frontend. There is no fallback to a public
hotel list if the private endpoint is missing or unavailable.

The SQL is additive and replayable, serializes concurrent installers, rejects
duplicate parent replies rather than picking a winner, creates a unique index and
adds review reply/feedback FKs with ON DELETE CASCADE NOT VALID. Normal future
authorized review deletion removes only that review's dependent replies/votes.
The script never deletes or reassigns historical data and does not auto-validate
historical FKs. No Hibernate entity annotation is used to bypass the migration audit.

Safe read-only preflight on the designated target:

```sql
SELECT review_id,count(*) FROM review_responses GROUP BY review_id HAVING count(*)>1;
SELECT count(*) FROM review_responses r LEFT JOIN hotel_reviews h ON h.id=r.review_id WHERE h.id IS NULL;
SELECT count(*) FROM review_feedbacks r LEFT JOIN hotel_reviews h ON h.id=r.review_id WHERE h.id IS NULL;
```

On the isolated clone, duplicate groups were0 and historical orphan counts were
2 replies /3 feedbacks. Applying the final script twice preserved these counts;
both FKs intentionally remain unvalidated. Historical retention/repair needs an
explicit decision; no cleanup or production migration occurred. Do not mark full
schema standardization complete based on these targeted constraints.

## Verified evidence

```powershell
mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package
python -X utf8 docker/availability-range-integration.py review-history-final reviews
python -X utf8 docker/review-response-smoke.py
```

- Baseline hotel:290 executed PASS +4 opt-in skipped, JAR BUILD SUCCESS.
- Final hotel:304 executed PASS +8 opt-in skipped, JAR BUILD SUCCESS. This excludes
  the existing environment-dependent application test; opt-ins are not passes.
- Actual PostgreSQL/JPA integration:4/4 PASS separately, including8 concurrent
  service requests, unique/FK guards, scoped deletion cascade and private history
  pagination/ownership. Other optional database suites were not rerun.
- Rebuilt Docker hotel image healthy; native HTTP/gRPC runtime23 assertions PASS,
  including8 concurrent HTTP requests → one201 + seven409. Generated UUID fixtures
  were removed by exact scope; historical orphan counts were unchanged.
- Frontend Node134 PASS, development browser4 PASS. Standard typecheck still fails
  only at the unchanged place-owned NearbyPlacesSection.tsx:161 showDistance props.
  No frontend production build bypass or passing build is claimed.

Test-first failures: RPC boundary9/9, PostgreSQL integrity3/3 (8 replies before fix),
SDK lookup/history3/3, interceptor2/6, missing private route1/58, REST conflict1/13,
and malformed successful booking lookup1/18. Final green runs retain these checks.

Harness corrections: two host Maven runs accidentally overlapped and one failed
on disappearing compiler output; sequential reruns compiled and passed. A missing
test static import and browser selector collision with Next's route announcer were
corrected. These are test harness failures, not baseline business defects.

Logs are ignored under hotel-service/target-review-*.log and workspace logs.
The root PROJECT_STANDARDIZATION_PLAN.md is outside both Git repositories; this
checkpoint and Docker/frontend READMEs preserve the rollout evidence inside Git.

Remaining scope: historical orphans, other service APIs/logging, full gateway and
provider E2E, frontend production build, and existing review UI placeholder actions
are not certified by this batch. No staging, commits, pushes or branch merges.

After verification, the five test containers started by this batch were stopped
(hotel/eureka/rabbitmq/redis/postgres-test). Containers, volumes and clone data were
retained. Browser development server was torn down; restart the designated clone
before rerunning database/native checks. No machine shutdown was repeated.
