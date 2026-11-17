# STD-006 — inventory provenance, 16/11/2026

## Existing implementation and acceptance scope

Source checked: RoomType, RoomAvailability, RoomInventoryReviewService/Controller,
RoomTypeServiceImpl, AvailabilityService, RoomTypeRepository,
RoomAvailabilityRepository, RoomHoldRepository, create/update DTOs and immutable
Hotel canonical V1. No implicit stock=10 occurs in the canonical Room runtime path;
the nullable physical quantity has no database default. A missing/nonpositive count
fails availability/hold closed. The number 10 alone establishes neither corruption
nor verification.

| Question | Current implementation / finding |
|---|---|
| Physical quantity origin | OWNER room create `roomAmount` maps to `RoomType.totalRooms`; reviewed changes use stored proposal quantity/source. |
| Who proposes | OWNER only; target hotel's owner identity must match authenticated actor. |
| Who approves/rejects | ADMIN only with actor UUID, reason and matching revision. |
| Audit | Immutable-by-workflow request ID, old/new quantity, source, requester/time, decision/reviewer/time/reason; state points to latest and approved requests. Superseding a pending proposal preserves its row. |
| Missing/invalid quantity | Positive integer DTO validation; null/nonpositive persisted stock rejected for new holds. No repair/backfill. |
| Verification | Previously `approved_request_id` plus quantity equality alone could report verified for incomplete/non-approved evidence. Batch7 now additionally checks decision, room/hotel linkage and source/review metadata. |
| Old vs new stock | Unmanaged stock remains explicitly unverified in review state. A valid newly submitted proposal is pending evidence, never an approval. A consistent approved proposal may establish verification. |
| Active commitments | Approval rejects reduction below max nightly held+booked, rejects negative counters and stale previous-quantity snapshot. Ordinary unmanaged update retains existing increase-only rule; managed quantities cannot use that bypass. |
| Races | Proposal, review, direct room mutations and hold creation share room FOR UPDATE; hold rows/counters have separate pessimistic locks. Confirm transfers held to booked without increasing total commitment, release/cancel decrease it. Approval/hold race is tested natively. |
| Remaining admission boundary | Availability/hold requires positive configured quantity but does not require `State.verified`. Positive unmanaged historical or new room quantities may remain bookable. This is not reported as fully verified stock. |

The plan's historical §56 explicitly preserves “unchanged count/unrelated edits
and unmanaged legacy API retain compatibility”; §56.1 does not apply reviewed
flags to all old stock. A universal verified-only booking gate would change this
admission contract and needs a separate user decision, including pending bookings,
new-room onboarding and rollout. Batch7 does not silently introduce that policy.

## Implementation

- `RoomInventoryReviewService.state`: require linked requests to match the room
  and hotel before returning private source metadata (409 on mismatch); report
  verified only for positive matching quantity, APPROVED status, nonblank source
  and reason, requester, reviewer and review timestamp.
- `review`: apply the same request linkage guard before either decision mutates
  audit state. No data rewrite or automatic approval.
- `propose`: validate the prior linked request before superseding it, preventing
  a malformed cross-room pointer from rewriting another room's pending audit.
- Unit coverage includes valid reviewed ten, unreviewed ten, missing metadata,
  pending/rejected/superseded matching quantities, foreign request pointers,
  cross-owner denial, wrong-role actions, invalid input before effects and
  preservation of managed/direct-update boundary.
- Canonical native helper adds real OWNER proposal, ADMIN refusal below existing
  holds, unchanged pending audit after refusal, concurrent reduction-vs-hold,
  exactly one incompatible winner, final held<=physical invariant, later rejected
  proposal preserving previous approval, and metadata-only classifier fixtures.

No column/table/index changes are necessary for these fixes. Optional stronger
database pointer/status constraints could be evaluated through an additive V2
proposal after read-only data review, but no V2 or V1 edit is made here.

## Historical review list

`docker/batch7-inventory-provenance.sql` is the metadata-only read-only classifier.
It emits room/hotel IDs and quantity/load/state metadata, no free text or actor
IDs. Classifications are VERIFIED, UNVERIFIED, INVALID_CONFIRMED and UNKNOWN_SOURCE.
INVALID_CONFIRMED means an objective configured quantity/counter inconsistency,
not an inferred physical count. NULL/10 alone remain unknown without evidence.
OWNER must submit a real physical source and ADMIN must explicitly review it.
The script never backfills, creates proposals, approves, or changes history.

No actual historical/hosted inventory was read in Batch7. Actual review list and
physical counts remain NOT VERIFIED; synthetic fixtures certify classifier
behavior only. No hosted test fixtures or cleanup obligations were created.

## Tests

Initial focused command:

```text
mvn.cmd -o -B -ntp -pl hotel-service -Dtest=RoomInventoryReviewServiceTest,RoomTypeServiceImplTest,AvailabilityServiceTest,InventoryCounterSafetyTest,AvailabilityBatchTest,AvailabilitySnapshotTest test
```

Initial PASS: 70 executed, 0 failures/errors/skips (review suite then 10 tests).
After the final cross-request guards and compatibility changes:

```text
mvn.cmd -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' test
```

Final result: **423 discovered, 390 executed PASS, 0 failures/errors, 33 opt-in
SKIPPED**, BUILD SUCCESS, completed 19:35:01 Asia/Saigon. Infrastructure-dependent
`HotelServiceApplicationTests` is explicitly excluded, not claimed PASS. Review
unit suite is 13/13 PASS. Private raw local log:
`.local/batch7-hotel-regression-final.log` (not published).

An intermediate full run had 1 new test assertion failure: it expected Jakarta
ConstraintViolationException while the existing service validation contract
correctly throws InputValidation.InvalidInput. Corrected the test expectation,
then reran the entire suite with the result above; no production behavior was
changed to satisfy the assertion.

The schema harness separately executed the extended Hotel canonical native test:
**1/1 PASS**, including inventory concurrency and all four classifier labels,
plus exact catalog PASS. See shared schema-verification evidence for fresh
disposable PostgreSQL identity, five-service results and cluster cleanup.
Synthetic fixtures only; never historical physical proof.

## Status

STD-006 PARTIAL: source integrity fix implemented; safe review workflow already
exists; no implicit ten-room stock. Actual physical/historical provenance remains
NOT VERIFIED. Universal verification-dependent admission is a separate policy
decision; do not mark the whole finding FIXED_VERIFIED.
