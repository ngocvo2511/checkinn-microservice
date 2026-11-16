# Batch6B Docker runtime

> Current consolidated entry point: `docker/stack.ps1 -Environment Supabase`
> (`docker/.env`) or `-Environment Test` (`docker/.env.test`, local PostgreSQL).
> See [current Docker instructions](../docker/README.md). Private Batch6B state
> remains operator evidence; its old runtime values are not application fallbacks.

The host JVM/Next dev launcher was used during staged database integration to
isolate startup, migrations, pool limits and provider checks. Docker is now given
an explicit Supabase development mode instead of reusing the original Compose
defaults for local PostgreSQL.

From `checkinn`:

```powershell
# Build after application source changes (packages current source and checks guards).
.\docker\stack.ps1 -SupabaseDev -Action Build
# Start the 10 JVM services and production frontend.
.\docker\stack.ps1 -SupabaseDev -Action Up
.\docker\stack.ps1 -SupabaseDev -Action Status
# Stops application containers; retains external broker/cache/mail and all volumes.
.\docker\stack.ps1 -SupabaseDev -Action Down
```

`-SupabaseDev` always selects this complete application group. It cannot be
combined with `-Test`. The original stack commands still use `docker/.env` for
local PostgreSQL. Do not start both application modes simultaneously.

The approved session credentials remain in `.local/batch6b/.env`. The generated
`.local/batch6b/docker-compose.private.json` is private too: do not publish
`docker compose config`, container environment dumps, or private build logs.
Only the owning runtime database credentials enter each application container;
management tokens and migration passwords do not. Host CA paths are rewritten to
the read-only Linux mount `/run/checkinn-ca/root.crt` without changing endpoints,
runtime roles, TLS verification, schema validation or connection pool limits.

Applications join the existing owned session network. RabbitMQ vhost, Redis
database, persistent volumes and Mailpit are reused. No first-run migration,
queue/cache reset, acceptance fixture replay or database creation is performed.
The container transport guard requires exactly `rabbitmq:5672` and `redis:6379`;
host mode retains its localhost and non-default published-port checks.

Frontend uses `next build --webpack` and the standalone production server.
Browser API calls target localhost:8080; server-side NextAuth calls use
api-gateway:8080. Only frontend:3000, gateway:8080 and Eureka:8761 are published
by the application stack, bound to host loopback.

VNPay Sandbox and Storage retain their previously reviewed modes. Running
containers does not resolve the externally blocked merchant approval or Storage
project DNS. No real payment success or Storage upload is claimed here.

Validation: 11/11 application containers healthy, zero restarts, all 10 running
JAR hashes match the freshly packaged artifacts. Five read-only CA mounts,
verify-full URLs and pool max 2 verified. No management/migration credentials
in application environments; all 30 protected SQL/base config files unchanged.
40 guard tests and 33 frontend search regression tests passed. Production build
including TypeScript passed.

The first browser attempt occurred before Eureka's registry refresh and received
503. The Up command now waits for the actual Gateway cities route after health
checks. Typing a location also exposed excessive intermediate requests; both
location pickers now wait 300 ms after typing stops, retaining stale-result and
error handling. A repeat browser test typed `Synthetic` normally, selected the
synthetic city, Nov 20–21 2026, 1 room / 2 adults / 0 children, submitted the home
form and displayed 2 hotel results on `/search`. NextAuth login through the
Docker frontend/Gateway/Auth/User/Supabase chain passed using the existing test
account. No new booking/payment fixtures were created.

Machine evidence: `evidence/batch-6b-supabase-local-integration/docker-runtime-2026-11-16.json`.
