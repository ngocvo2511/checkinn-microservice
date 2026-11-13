# Reproduce Batch 2 (disposable only)

Run from backend repository. Do not substitute developer/shared databases. No Place or provider operation is required.

## Fresh setup

Generate private random credentials once. The command refuses to overwrite an existing private env or issue credentials for existing project containers. `.env.booking-smoke` is ignored; retain it privately with the retained volumes.

```powershell
python -X utf8 docker/booking-gateway-smoke.py --init-env
docker build --build-arg SERVICE=hotel-service -t checkinn/booking-smoke-hotel:batch2 .
docker build --build-arg SERVICE=booking-service -t checkinn/booking-smoke-booking:batch2 .
docker compose -p checkinn-booking-smoke --project-directory docker --env-file docker/.env.booking-smoke -f docker/docker-compose.yml -f docker/docker-compose.booking-smoke.yml --profile app up -d --no-build postgres-smoke redis rabbitmq eureka-server user-service auth-service hotel-service booking-service api-gateway
```

Auth/User/Gateway/Eureka local images must already exist and match the intended source; build those separately if absent. This run reused their installed local images; exact runtime image IDs are in environment-results.json. Only Booking/Hotel were freshly built by Batch2.

Hotel JPA creates baseline tables before startup guards require explicit feature SQL. Until bootstrap it can restart/fail readiness; this is not a healthy application graph. Run bootstrap after tables exist, then wait until service registration/cache propagation and health are ready. The bootstrap may be replayed on this synthetic project, but is not a production migration procedure.

```powershell
python -X utf8 docker/booking-gateway-smoke.py --bootstrap
python -X utf8 docker/booking-gateway-smoke.py --ready
```

`--ready` asserts nine healthy containers, required Eureka registrations and a Gateway policy route. Wait for fresh discovery caches after a restart; acceptance itself additionally requires real catalog/Booking/Auth calls. Do not count an early transient probe as acceptance. If Rabbit has cookie permission errors on a brand-new verified volume, inspect ownership first; Batch2 corrected only that newly created project's Rabbit volume. The tracked overlay uses user rabbitmq to avoid the initialization race. Do not chown other projects' volumes.

## Acceptance and regressions

```powershell
python -X utf8 docker/booking-gateway-smoke.py
python -X utf8 docker/booking-expiry-smoke.py
python -X utf8 docker/booking-sql-assertions.py
mvn.cmd -o -B -ntp -pl booking-service,hotel-service '-Dtest=*,!*ApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
python -X utf8 docker/booking-postgres-integration.py
```

Keep raw stdout/stderr under `.local/evidence/batch-2-booking/`, not Git. Main runner writes reviewed assertions to gateway-results.json; expiry/final-SQL runners write separate reviewed files. Re-running creates fresh synthetic actors/catalog/commands; does not reset old fixtures. Reviewed files describe the latest run, raw per-run files preserve prior failures. Before replacing evidence after source changes, keep the old raw run for traceability.

Main runner sends business HTTP only through Gateway28080. It temporarily injects PostgreSQL triggers and stops/starts only Hotel in its project. Do not run against other endpoints/DBs or concurrently with a second main runner. Existing PostgreSQL integration suites use a new UUID-labelled container, schema only from synthetic DBs, newly seeded synthetic rows, and no application/scheduler. It removes only its labelled resource. Booking outbox suite uses mock RabbitTemplate; no live-broker recovery claim.

The current baseline has no positive-points/payment/provider fixture. Hold-timeout UNKNOWN, process crash/commit ambiguity, simultaneous compensation outage, multi-node and dedicated real-broker recovery remain NOT VERIFIED. Do not infer those from service-test PASS.

## Retained resources

Both Refund and Booking stacks are retained for debug. No cleanup command was executed against these stacks. Any later removal must verify project labels, named volumes, mounts and ownership again, use only the explicitly named Booking Compose project, and obtain a decision to discard its retained evidence/data. Never global prune.
