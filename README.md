# Hotel Booking Microservices System

A distributed hotel booking system using Microservices architecture with gRPC communication.

## Daily runtime

There are two application modes, both launched from the backend root:

| Mode | Command | Private application config | Database |
| --- | --- | --- | --- |
| Main application | `.\docker\stack.ps1 -Environment Supabase -Action Up` | `docker/.env` | Five approved Supabase projects |
| Local tests | `.\docker\stack.ps1 -Environment Test -Action Up` | `docker/.env.test` | `checkinn-test` / `postgres-test:5432` |

Use the same selector with `Validate`, `Build`, `Status` or `Down`. Supabase is
the default environment; the selector should be explicit in saved commands.
Stop one application mode before starting the other because their local HTTP
ports overlap. Down preserves database/broker/cache volumes.

Main mode is the current localhost application with hosted databases; it is not
a production deployment. Provider limitations and migration approvals remain
separate. Detailed configuration and existing-volume notes: [docker/README.md](docker/README.md).

Docker local setup, actual service inventory, configuration, smoke checks and validation results: [docker/README.md](docker/README.md).

## Canonical database setup

Booking, Loyalty and Revenue domain references now use native UUID storage. Do not start this source against legacy VARCHAR schemas: there is no automatic in-place conversion or data import.

For fresh isolated databases, use each service's `db/canonical/V1__canonical_schema.sql` with the guarded migration job and service-specific roles, then start the `canonical` profile with JVM timezone UTC (`java -Duser.timezone=UTC ...`). That profile validates Hibernate mappings, disables runtime Flyway and SQL initialization, and requires `CANONICAL_DB_URL`, `CANONICAL_APP_USER` and `CANONICAL_APP_PASSWORD`. Default profiles and historical migration files remain as earlier configuration; they do not perform this cutover.

See the [local reproduction guide](Docs/evidence/batch-5b-canonical-flyway/REPRODUCE.md) and [migration plan](Docs/database-design/MIGRATION_IMPLEMENTATION_PLAN.md). Hosted database integration is verified for local development; overall Batch 6B remains PARTIAL because real VNPay/Storage acceptance is blocked. Backend cloud deployment, legacy mapping and cutover remain separate work. See the [Batch 6B closure review](Docs/BATCH6B_FINAL_COMPLETION_REPORT_2026-11-16.md).

## Core Services
- **API Gateway** – Single entry point for all clients
- **Auth Service** – JWT authentication & login/register
- **User Service** – User info management (gRPC)
- **Hotel Service** – Hotel, room, and availability management (gRPC)
- **Booking Service** – Booking, payment, cancellation and recovery (REST / gRPC); payment is a module of this service
- **Media Service** – Media metadata and storage access
- **Revenue Service** – Authorized financial reports and event projections
- **Regulations Service** – Business rules and typed rate APIs
- **Notification Service** – RabbitMQ consumers and SMTP email
- **Config Server** – Centralized configuration management
- **Service Registry** – Service discovery (Eureka)
- **Admin Server** – Spring Boot service monitoring

## Technology Stack
- Spring Boot
- Spring Cloud (Gateway, Config, Eureka)
- gRPC
- Docker
- PostgreSQL (User, Hotel, Booking, Revenue and Regulations)
- RabbitMQ (events), Redis (session revocation and caches)
- Protobuf 3
