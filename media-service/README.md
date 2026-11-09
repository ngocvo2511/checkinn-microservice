# Media and private document boundary — 9 November 2026

Review branch `fix/media-storage-boundaries` starts from backend `c36e143`
and frontend `73cfb16`. No place-service, place frontend, schema or protobuf
change is included. Two existing backend IDE diffs are unrelated.

## Behavior and compatibility

- Storage RPCs still require the signed hotel-service / HOTEL_SERVICE identity.
  Owner/admin resource checks remain in hotel-service before private signing;
  forged owner/admin headers do not establish that identity.
- The shared configured HTTP client now has a2s connection timeout and a5s
  operation budget. Both are positive configurable values:
  `supabase.connect-timeout-ms` / `supabase.request-timeout-ms`. The operation
  budget covers response bodies and the entire legacy batch fallback; timeout
  cancels the pending HTTP future. Redirects are disabled for credentialed calls.
  Hotel gRPC's existing10s deadline and frontend's15s document deadline bound
  their respective hops. These are safeguards, not measured production SLOs.
- Batch503/429/auth errors do not fan out into individual signing calls or
  produce an empty-success map. Only404/405 retain legacy batch-route fallback,
  within one shared budget. Missing/error/duplicate/unrequested items are an
  explicit failure. No stored paths still means genuinely absent documents.
- Signed URLs accept the existing provider field aliases and relative/full
  forms, join the storage prefix once and must identify the requested private
  object on the configured origin. Malformed JSON/URL data is not trusted.
- Object keys reject empty/absolute/control/backslash/dot-traversal segments
  before privileged HTTP. Uploads require nonempty bytes and a MIME value;
  this does not introduce a new file-format allowlist. Filename sanitization,
  bucket defaults and300s default signing lifetime are retained.
- Error paths in the changed storage/RPC/client/document code omit filenames,
  private paths, signed URLs, raw provider bodies and exception causes. Upstream
  service authentication failure becomes503, not a customer's login401.
  Timeouts become504 through hotel REST; other dependency/protocol errors502/503.
  Invalid upload input is400; invalid/missing stored signing/deletion data is
  a dependency error, not a claim that the requested hotel is missing.
- Private preview failures are explicit; frontend shows unknown/retry and hides
  stale/missing-document cards. A successful read with absent paths retains the
  ordinary missing/optional UI. Saving does not invent PENDING approval state.
  An unavailable preview refresh does not replay the successful write.
- Document SDK uses the actual upload acknowledgement `{message}` and validates
  GET hotel identity/status/fields. It never logs a private response and never
  retries an upload automatically using a second route. Read-only legacy404
  routing remains compatible under the same total frontend deadline.

Provider contract was checked against the official
[signing API](https://supabase.com/docs/reference/javascript/storage-from-createsignedurls)
and [storage SDK](https://github.com/supabase/storage-js/blob/master/src/packages/StorageFileApi.ts).
The existing upload PUT was deliberately retained: the official
[server PUT route](https://github.com/supabase/storage/blob/master/src/http/routes/object/updateObject.ts)
supports upsert. The SDK's distinct upload/update method names alone do not
prove the current PUT is broken. Live provider version/permissions were not tested.

## Verification

From backend root:

```powershell
mvn -o -B -ntp -pl media-service package
mvn -o -B -ntp -pl hotel-service '-Dtest=*,!HotelServiceApplicationTests' package
```

Observed: media29/29 executed PASS, including its real Spring context; hotel290
executed PASS and4 opt-in integrations skipped. Both JARs build. Context test
properties are synthetic/loopback-only with discovery disabled and an ephemeral
loopback gRPC port; no production configuration or authorization is bypassed.

`MediaSocketIntegrationTest`7 cases run real HTTP/2 gRPC, real JWT validation,
and loopback HTTP through production storage/RPC code. Unauthorized callers
make zero provider HTTP calls; valid service supports all five operations.
`MediaClientContractTest`7 cases cover35 actual hotel-client gRPC calls and
safe status mapping. `StorageBoundaryTest`14 cases include slow response bodies,
legacy fallback's total deadline, private URL identity and malformed responses.
`MediaGrpcFailureTest` preserves interruption and private error boundaries.
Hotel regression includes private principal/owner checks, upload ACK shape,
stored-document outage/incomplete maps and no metadata save on upload failure.

Frontend root:

```powershell
npm run test:regression
npx playwright test --config=playwright.documents.config.ts
npx tsc --noEmit
```

Node116/116 and browser4/4 pass. Browser uses the actual Next dev server with
intercepted synthetic APIs; it is not proof of a real provider or database flow.
Typecheck still reports the existing place-owned `showDistance` props mismatch
at `NearbyPlacesSection.tsx:161`. No bypass or production frontend build pass
is claimed. The unchanged main Playwright configuration remains intact.

## Rollout and limits

No migration, bucket ACL change, new credentials or provider endpoint change is
required. Deploy media-service before hotel/frontend readers so signing failures
become explicit across all hops. Review the whole source/test diff, including
untracked files, before committing; root plan section67 is outside both repos.

All provider writes/deletes were loopback simulations. No Compose services,
database, real bucket or existing documents were changed. Authorized live bucket
operations still require a designated safe test environment. Upload acceptance
with a lost response or later DB failure can leave an unlinked external object;
automatic orphan deletion, durable cross-store compensation and provider
idempotency are not implemented by this batch. They require reviewed evidence
and permissions, not guessed cleanup. Signed URL lifetime is unchanged; these
capabilities remain private and must not be exported into logs.
