# Batch 9C — Canonical disposable Gateway smoke (18/11/2026)

Reviewed, secret-free evidence for the Gateway READ/WRITE smoke run on a fresh canonical stack. Raw logs, generated passwords, JWTs and the generated `init.sql` stay in ignored `checkinn/.local/canonical-smoke/`.

| File | Content |
|---|---|
| `results.json` | Final run (run 3): 87 PASS / 0 FAIL / 1 observation; every check with its status code/observed values |
| `results-run1.json` | First complete run: 82 PASS / 5 FAIL. All 5 failures were check-definition errors (per-night counter rows summed, wrong VNPay field name `paymentUrl` vs `redirectUrl`, repeated cancel assumed non-idempotent); kept unchanged for transparency |

Run 2 aborted before writing results: the city name reused the prepare-time run suffix and hit the existing unique city from run 1 (fixture naming bug, fixed with a per-invocation suffix).

## Environment

- Compose project `checkinn-canonical-smoke` (`docker/docker-compose.canonical-smoke.yml`), own network, **no named volumes** (PostgreSQL/RabbitMQ/Redis on tmpfs), loopback ports 58080 (Gateway), 58432 (PostgreSQL), 58025 (Mailpit).
- PostgreSQL 18, five fresh owner databases with separate migrator/app roles; schema only from `<owner>-service/src/main/resources/db/canonical/V1__canonical_schema.sql` via Flyway 10.22 (`docker/CanonicalSmokeMigrate.java`): 46 tables, one successful V1 row each, immediate rerun executed 0; app roles DML-only, no Flyway history access.
- Images `checkinn/<module>:canonical-smoke` = existing `checkinn/<module>:local` runtime layer + jars packaged from current source (`96bfc4f` + uncommitted 9A–9C test/docs; F1 frontend not part of the backend stack). No image pulls.
- Apps: eureka, api-gateway, auth, user, hotel, booking, revenue, regulations, notification with profile `canonical` (Hibernate `validate`, Flyway/SQL init disabled); all 13 containers healthy on first start.
- Not used: Supabase, `docker/.env`, `docker/.env.test`, the legacy `checkinn-test` clone, media/Storage, VNPay provider, real SMTP.

## Fixtures

All accounts registered through Gateway `/api/auth/register[-owner]` with real OTP from local Mailpit and real login JWTs. Two labelled SQL steps in the disposable databases only: promotion of one freshly registered synthetic account to ADMIN (no API exists), and hotel publication DRAFT→APPROVED (document/Storage workflow not in this stack). City creation, hotel/room creation, inventory proposals and ADMIN approval used real APIs.

## Coverage

READ (anonymous Gateway): search page/detail/rooms/room/availability return only the approved room; unapproved room → 409 `INVENTORY_APPROVAL_REQUIRED`; 9 public catalogs/reviews/policy 200; 3 invalid searches → 400 envelope with matching `X-Request-ID`; unknown hotel 404; 6 private routes 401 with forged headers; 3 admin/internal routes 403 for CUSTOMER.

WRITE (CUSTOMER Gateway): anonymous create 401; create 201 with server total; same-key retry → same booking; changed input → 409; **lost response (socket closed before reading) then same-key retry → exactly one booking and one hold**; stale price 400 with no hold; unapproved room 409 with no hold; other customer 403 on read/cancel/payment/VNPay; 4 out-of-scope routes 403; VNPay URL signed locally (`redirectUrl` on sandbox host, provider not called); cancel 204 releases holds; repeated cancel idempotent 204 without effect.

Correlation: client `traceparent` reaches Booking (error `requestId` equals the trace id; 8 occurrences in Booking logs). **Hotel logs never contain it** — Hotel hold requests start new traces. Runtime confirmation of F-9C-2 (Booking `RestTemplate` built without `RestTemplateBuilder`). Gateway logs do not print trace ids.

## Cleanup

`down` removed every container and the network of the project; 0 project volumes existed. Other compose projects (`checkinn-test`, Supabase dev) were not started, stopped or modified. The 9 `:canonical-smoke` images remain for reruns.

Rerun: `python -X utf8 docker/canonical-smoke.py prepare|package|build|infra|migrate|apps|smoke|down` from `checkinn/`; `prepare` refuses existing private state, so remove `.local/canonical-smoke/state.private.json` deliberately after `down`.

## Batch 9D rerun — after the 9B/9C finding fixes (18/11/2026)

`results-fixes.json`: fresh stack (new images from current source, new V1 migration), **94 PASS / 0 FAIL / 0 observations**. New checks: public hotel detail/search carry no `ownerId`; availability `checkedAt`; VNPay create for a cancelled booking → 400 envelope without exception text; **trace id now in Hotel logs (6 occurrences; was 0)**; with PostgreSQL stopped, Hotel search and Booking read return a bounded Gateway 504 at 20.0 s (never 500/data) and both services log the 503 dependency handler (`CannotCreateTransactionException`), 0 unhandled. The 504 rather than 503 at the client is because Hikari waits 30 s (F-9D-1).

Earlier attempts in this rerun: Eureka-cache race (Gateway 503 before hotel-service was resolvable; harness now waits for routes); 90/4 (two availability checks compared the whole body and broke on the additive `checkedAt`; two outage checks expected 503 but got the Gateway 504); one start failed because a Hikari timeout set through the environment is rejected by the canonical datasource ("pool is sealed once started"), reverted. `down` left 0 containers/networks/volumes.

## Batch 9D addendum rerun — F-9B-3 / F-9B-4 / F-9D-1 (18/11/2026)

`results-9e.json`: fresh stack, **101 PASS / 0 FAIL**. New checks: search with past check-in, 31 nights, 21 rooms, 41 adults, 41 children → 400 envelope; 30 nights → 200; availability `quantity=21` → 400; unapproved room read stays 409 and unapproved booking 409 with no hold; with PostgreSQL stopped Hotel search and Booking read return **503 `DEPENDENCY_UNAVAILABLE` after 10.0 s** (pool `connection-timeout` 10 s < Gateway 20 s). The previous attempt (100/1, overwritten) had changed the unapproved room read to 404, which turned the unapproved booking into a 400 because Booking maps that route's 409; the change was reverted. `down`: 0 containers/networks/volumes.

## Batch 9D booking-limits rerun (18/11/2026)

`results-9f.json`: fresh stack, **104 PASS / 0 FAIL**. Adds booking create with 31 nights, 41 adults and a past arrival → 400 with the expected `fieldErrors` key and unchanged room counters (no hold). `down`: 0 containers/networks/volumes.

## Batch 9F rerun (20/11/2026)

`results-9g.json`: fresh stack built from current source, **106 PASS / 0 FAIL**. Adds: with PostgreSQL stopped, `GET /api/user/profile` (CUSTOMER) and `GET /api/reports/owner/summary` (OWNER) return 503 within 15 s without exception text (user-service previously answered a dead pool with 401 and the raw message; revenue with 500). `down`: 0 containers/networks/volumes.

## Batch 9G runs (21/11/2026)

Stack now also runs Loki 2.9.8 + Promtail 2.9.8 with the project's `monitoring/` configs (tmpfs, port 58100 loopback) and mounts every service's file log into ignored `.local/canonical-smoke/logs`. `REFUND_INTERNAL_ENABLED=true` in this disposable stack only (Sandbox Internal accounting, never a provider refund); Eureka registers by IP.

- `results-9h.json` — 1 replica, `CANONICAL_SMOKE_LIVE=1`: **135 PASS / 0 FAIL**. Adds: paid lifecycle (VNPay return signed locally with the stack's synthetic secret → CONFIRMED → cancel → refund request → ADMIN approve/execute → REFUNDED, Revenue 1 CREDIT + 1 DEBIT net 0, inventory released); Gateway access log trace id, Gateway-created trace reaching Booking; Loki holds the client trace from Gateway/Booking/Hotel files, no JWT-like lines; non-intercepted Playwright (search, detail, payment page) through the real Gateway; paused Hotel → 504 envelope at 20.1 s; stopped Regulations → 503 envelope.
- `results-9h-multinode.json` — `CANONICAL_SMOKE_SCALE=2` (2 hotel + 2 booking replicas): **138 PASS / 0 FAIL**. Adds: Eureka 2 UP instances each; 6 concurrent distinct-key creates on a stock-2 room → two 201 + four 409, held ≤ 2; 4 concurrent same-key creates → one booking/one hold; both booking replicas served creates.

Attempts before green: Loki tmpfs ownership (fixed: uid 10001); Booking→Hotel 503 right after startup because Booking's own discovery cache lagged (harness now waits); browser 2/3 because booking-service CORS rejects `127.0.0.1:3000` (finding F-9G-1, live run uses `localhost:3000`); two harness bugs. A warm-stack debug run (132/0) was not kept. Every run ended with `down`: 0 containers/networks/volumes.

## Batch 9H run (21/11/2026)

`results-9i.json`: fresh stack from current source, live browser on `http://127.0.0.1:3000`, **144 PASS / 0 FAIL**. Adds: one error per service through the Gateway (auth, user, revenue, regulations, booking, hotel) carries `timestamp,status,error,code,message,path,requestId` with `X-Request-ID` = `requestId`; Booking returns `Access-Control-Allow-Origin` for `localhost:3000` and `127.0.0.1:3000`, unknown origin 403; the non-intercepted browser suite passes from `127.0.0.1:3000` (failed 2/3 in 9G, F-9G-1). `down`: 0 containers/networks/volumes. Multi-node not rerun (no change to booking/hold paths).
