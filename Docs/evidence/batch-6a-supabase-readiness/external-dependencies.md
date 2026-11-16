# External dependencies

Supabase supplies PostgreSQL targets, not application RabbitMQ/Redis/Spring/Next hosting. No external dependency deployed/connected6A.

| Dependency | Source evidence | Classification / gate |
|---|---|---|
| RabbitMQ | Compose/definitions/outboxes/listeners | NEEDS HOSTING DECISION/config: isolated vhost/queues/DLQ/TLS/users/persistence/confirm/recovery |
| Redis | local redis7/auth revocation/cache | NEEDS HOSTING DECISION/config: isolation/auth/TLS/ACL/outage acceptance, no cache copy |
| Boot services | Java21 nonroot Dockerfile, build tests skipped | NEEDS HOSTING DECISION: CI/release/secrets/network/resources/replicas |
| Gateway | lb://Eureka/local CORS | NEEDS CONFIGURATION/hosting: HTTPS/CORS/cookies/auth routes |
| Eureka | localhost8761 | NEEDS HOSTING DECISION/private discovery/registration |
| Config Server | optional imports; local native config empty/disabled | NEEDS CONFIGURATION: explicitly disable or securely pin, cannot override DB safety |
| SMTP | STARTTLS587/timeouts/retry | NEEDS CONFIGURATION: credentials/sender/egress/delivery/duplicate runbook |
| Media | Supabase Storage/service-role/public+private buckets | NEEDS CONFIGURATION: existing project mapping/buckets/policies/key scope UNKNOWN; preserve managed services, object backup |
| VNPay Sandbox | configurable signing/pay/return; refund false default | NEEDS CONFIGURATION: sandbox merchant/callback TLS/receipt duplicate/retry; real-money/provider refund OUT OF SCOPE |
| Next.js | Docker/NextAuth/API contracts, prior PR build evidence | NEEDS HOSTING DECISION/config: build-time NEXT_PUBLIC_API_BASE_URL, API_INTERNAL_URL, NEXTAUTH_URL/SECRET, optional OAuth; browser acceptance |
| HTTP/gRPC | URL/discovery clients/plaintext local | NEEDS CONFIGURATION: private/TLS transport/service auth/timeouts/DNS/actual call matrix |
| Place/OSRM | existing protected source/routes/scaffold | OUT OF SCOPE changes/provisioning; full product integration not certified |

READY applies only to bounded static canonical artifacts/design. No dependency row is hosted READY. Required decisions: environment/DNS/egress/cert/private ports/hosts/storage mapping/secret injection. PostgreSQL connection success alone cannot enable traffic.
