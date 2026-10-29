# Tripto Implementation ToDo

This file tracks code work that is mentioned by the architecture documents but is not yet fully implemented or verified in the current project.

Priority legend:

| Priority | Meaning |
|---|---|
| P0 | Must clarify or implement before the next serious coding/deployment phase. |
| P1 | Important quality or feature work that should be implemented soon. |
| P2 | Useful enhancement, but can be scheduled after core risks are handled. |
| P3 | Future/optional work or documentation cleanup. |

---

## P0 - Architecture Alignment Before Coding

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Keep planned components clearly marked | `SAD.md`, `ADD.md` | Redis, Elasticsearch, Circuit Breaker, CQRS/read replica, Kubernetes, and Blue/Green deployment are documented as planned. | In code/config, add these only when actually implemented. In diagrams/docs, keep `Planned` tag until running/configured. |

---

## P1 - Security And Account Management

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Server-side logout / token revocation | `SAD.md` Risk, `utility_tree.md` Session & Token Management | Implemented with Redis-backed token blacklist. Auth Service exposes server-side logout; protected services check the blacklist during JWT validation. | Verify end-to-end with Redis running: logout invalidates the current token/session and protected services reject revoked tokens. |
| Rate limiting for sensitive endpoints | `SAD.md` Security future improvements | No gateway/service-level rate limiting was confirmed for login, OTP resend, reset password, or VNPay return endpoints. | Add rate limit rules at API Gateway or service layer. Cover login, OTP resend/verify, forgot/reset password, and payment callback/return endpoints. |
| Durable audit trail for admin actions | `SAD.md` Auditability, `utility_tree.md` Auditability | Hotel approve/reject/activate/deactivate writes runtime logs, and user lock/unlock endpoints exist, but no durable audit table/entity/event was found. User lock/unlock also extracts `adminId` but does not persist it with the action. | Add audit log model/table or shared audit event pattern. Capture actor, target, action, previous state, new state, reason/note, timestamp, and request correlation id. Keep existing runtime logs as operational logs, not the audit source of truth. |
| Account lock/unlock notification | `utility_tree.md` Auditability | User Service supports lock/unlock, but email notification to affected users was not confirmed. | Publish account-status notification event and send email through Notification Service when admin locks/unlocks an account. |

---

## P1 - Booking, Payment, And Reliability

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| VNPay callback idempotency and replay safety | `ADD.md` VNPay Integration, `SAD.md` Runtime View | Implemented callback replay guard, VNPay transaction reuse check, payment-table uniqueness hints, and unit test coverage for successful callback replay. Maven is not available in the current environment, so the test has not been executed here. | Run `PaymentServiceTest` when Maven is available. Repeated successful callbacks must not double-confirm bookings, double-confirm holds, publish duplicate events, or double-award loyalty points. |
| Booking/hold concurrency test | `ASR.md` Concurrency Control, `ADD.md` Booking Concurrency | Added k6 script `performance/booking-hold-concurrency.k6.js`, but no measured run has been recorded yet. | Run against a test room type with one available room. `successful_bookings` must stay at or below `EXPECTED_SUCCESSES`. |
| Expired hold cleanup verification | `ASR.md` Asynchronous Task Execution, `SAD.md` Expired Hold Cleanup | Added unit test coverage for expired `PENDING_PAYMENT` booking cancellation and active payment cancellation. Maven is not available in the current environment, so the test has not been executed here. | Run `BookingScheduledTaskTest` when Maven is available. Add broader integration verification with Hotel Service hold release if needed. |
| Circuit Breaker implementation | `ASR.md` Fault Tolerance, `SAD.md` Circuit Breaker Module | Circuit Breaker is documented as planned but no Resilience4j/Spring Cloud Circuit Breaker implementation was found. | Add circuit breakers/timeouts/fallbacks around Hotel/User/Media/VNPay/Email-provider calls where appropriate. Core search/booking should degrade gracefully when secondary calls fail. |
| Voucher service integration | Code path in `BookingService.java` | Booking Service now rejects `voucherCode` explicitly instead of silently applying a zero discount, but no Voucher Service/module exists yet. | Implement Voucher Service/module and validation flow, or remove voucher inputs from frontend until scheduled. |

---

## P1 - Regulations And Admin Operations

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Regulation propagation tests | `SAD.md` Runtime View, `ADD.md` Modifiability | Regulations Service publishes events and dependent caches exist, but propagation without restart needs proof. | Add integration tests or manual verification script showing dependent services observe updated regulations without restart. |
| Admin hotel approval notification | `SAD.md` Auditability, SRS/BRD admin flows | Hotel approve/reject/activate/deactivate exists and writes runtime logs, but owner notification behavior was not confirmed. Durable audit is tracked in the security/admin audit item above. | Publish hotel-status notification events and notify owner when hotel status changes. Include rejection note where available. |

---

## P1 - Performance And Observability

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Login latency baseline run | `ASR.md`, `ADD.md`, `SAD.md` Login latency | A k6 script now exists in `performance/login-latency.k6.js`, but no measured baseline result has been recorded yet. | Run the script against a local/test deployment with a valid test account. Record p95 latency, error rate, environment, and bottlenecks. |
| 150 concurrent users test | `ASR.md` Infrastructure / Concurrency, `SAD.md` Quality Requirements | Architecture target exists, but no load test was confirmed. | Add k6/JMeter/Gatling scenario for search, login, booking creation, and owner booking management. Target: 150 CCU without unacceptable error rate. |
| Prometheus/Grafana dashboard and alerts | `SAD.md` Observability | Monitoring stack exists, but alert rules/dashboards were not confirmed. | Add dashboard JSON or setup notes for service health, request latency, JVM metrics, RabbitMQ queue depth, booking/payment failures, and notification failures. |
| Log path and Promtail mount verification | `SAD.md` Observability, `monitoring/` | Promtail reads mounted logs; path correctness should be verified before deployment. | Confirm services write to the mounted log directory. Fix compose volume path if logs are not collected. |

---

## P2 - Planned Infrastructure Tactics

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Redis integration | `SAD.md`, `ADD.md` Redis planned extension | Implemented for the token blacklist use case. Compose includes Redis; relevant services include Redis config, actuator health support, and configurable revocation fallback behavior. | Expand Redis later only for additional concrete use cases such as OTP cache, dynamic config cache, or delayed jobs. |
| Elasticsearch hotel search | `SAD.md`, `ADD.md` Search Index | Search currently relies on relational queries. Elasticsearch is planned. | Add indexing pipeline for approved hotels/locations/amenities and search endpoint using Elasticsearch. Keep relational fallback. |
| Reporting read model / CQRS | `SAD.md`, `ADD.md` Reporting performance | Revenue Service consumes events, but read replica/CQRS projection is not implemented. | Add reporting projection tables/read model or read replica strategy. Reporting queries must not slow booking transactions. |
| Kubernetes / container deployment | `SAD.md` Deployment View | Target production mentions container platform, but project deployment appears local/service based. | Add Dockerfiles or compose profiles first, then Kubernetes manifests if required. Include health checks and environment variable docs. |
| Blue/Green deployment plan | `ASR.md` Maintenance Window, `SAD.md` Planned Extension Points | Deployment window is documented, but no deployment automation was found. | Add deployment runbook or scripts supporting rollback and low-downtime deployment inside the Sunday 11 PM maintenance window. |

---

## P2 - Frontend And User Experience

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Printable booking detail UI | User stories | Booking detail/history exists, but no print-specific UI or `window.print()` flow was confirmed. | Add print button and print stylesheet for booking confirmation/detail. |
| Real-time owner booking updates | User stories | Owner booking page appears to reload/poll manually; no WebSocket/SSE was confirmed. | Add polling first or SSE/WebSocket later. Owner sees new booking requests without manual refresh. |
| Graceful degradation UI | `ADD.md` Availability / Graceful Degradation | Some fallbacks may exist, but review/reporting/notification failures are not consistently handled in UI. | Hotel detail/search/booking flows should continue when review/reporting sections fail; show temporary unavailable state for secondary features. |

---

## P2 - Review And Content Safety

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Review profanity/inappropriate content filtering | `utility_tree.md` Input Sanitization, `SAD.md` Quality Scenarios | Review length/content validation exists, but no automatic inappropriate-content filter was confirmed. | Add a server-side review content validator and user-facing rejection message. Keep rules configurable. |
| Sentiment score for reviews | User stories | Review metrics exist, but sentiment analysis was not found. | Add sentiment scoring only if still required by SRS/user stories. Store score per review or aggregate by hotel. |

---

## P2 - File And Media Integrity

| Item | Source | Current Gap | Implementation / Acceptance Criteria |
|---|---|---|---|
| Enforce MIME/extension/size validation consistently | `ADD.md` File Upload Integrity, `SAD.md` Media and File Handling | Current media upload flow uses Supabase through Media Service, but full validation needs verification. | Add server-side checks for allowed MIME types, file extension, max size, and rejected-file error messages. |
| Storage URL lifecycle cleanup | `SAD.md` Risks / Media handling | Object lifecycle behavior on hotel/media delete should be verified. | Ensure deleting media metadata also deletes or invalidates the object in Supabase. Add failure handling when object deletion fails. |

---

## P3 - Optional Or Needs Confirmation

| Item | Source | Current Gap | Recommendation |
|---|---|---|---|
| Automatic legitimacy cross-check for owner accounts | User stories | Hotel legal fields and admin approval exist, but no external/business verification integration was found. | Keep as manual admin review unless a real verification data source is required. |
| Sentiment analytics dashboard | User stories / review analytics | Not core to booking reliability/security. | Schedule after core booking/payment/admin/security work. |
| Utility tree wording cleanup | `utility_tree.md` | Some old/non-Tripto terms may still remain. | Handle as documentation cleanup, not a coding blocker. |

---

## Suggested First Coding Order

1. P0 planned-component marking.
2. P1 booking/VNPay idempotency tests and room-hold concurrency tests.
3. P1 token revocation.
4. P1 durable admin audit trail and admin/hotel notification flows.
5. P1 Circuit Breaker for the highest-risk service calls.
6. P1 performance/load test baseline.
7. P2 Redis/Elasticsearch/CQRS implementation after the core risks are covered.
