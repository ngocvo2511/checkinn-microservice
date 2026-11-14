# Business workflow acceptance

Eight-module package/regression PASS:901 executed,47 opt-in skips,0 failures/errors (948 discovered). Canonical PostgreSQL5 tests executed separately,0 skips; their skipped broad entries not counted as execution. [Class results](backend-regression-results.json), build log (local only: `logs/backend-regression-build.log`).

Command: mvn -o -B -ntp -fae -pl user-service,auth-service,hotel-service,media-service,booking-service,revenue-service,notification-service,regulations-service "-Dtest=*,!*ApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false" package.

Default ApplicationTests excluded to avoid protected developer infrastructure; packaged canonical apps started separately. Existing opt-in tests remain SKIPPED, not PASS. Unit/mock coverage includes profile/OTP/loyalty/commands/recovery, Hotel catalog/review/inventory, Booking compensation/cancellation/idempotency/UUID/outbox, Payment retry/expiry/VNPay callbacks/original receipt, Refund approve/reject/manual/internal, Revenue ordering/gaps/reports and Regulations versions/transactions.

Real five-service HTTP + PostgreSQL + owned RabbitMQ/Redis: 30 checks PASS. Positive-points booking, Hotel hold/User reservation/authoritative Regulations rates; same retry/UUIDv3/one item; HOTEL payment/inventory commit, duplicate receipt/one CREDIT; Loyalty commit; cancellation/release/reversal; ADMIN refund with4 concurrent internal executions + retry/one execution/audit/DEBIT, exact original receipt fields, zero net cash. Native Hotel two-transaction no-oversell PASS. [Checks](http-workflow-results.json), log (local only: `logs/http-acceptance.log`).

Real outage/restart: 14 checks PASS. Broker down source/snapshot/outbox PENDING, all5 JVMs restart, pending delivery, no duplicate seed, same event replay twice/no second DEBIT/execution. Test exchange alias batch5b.isolated bridges regulation.updated to hardcoded hotel.events cache subscribers solely inside owned broker. [Checks](restart-results.json), log (local only: `logs/restart-acceptance.log`).

Single-host five-process proof, not multi-node certification. Gateway/browser/real-provider/SMTP/Media/full positive Review→Booking gRPC NOT VERIFIED. VNPay callback/retry/expiry verified by unit/mock here; no external provider refund/mail.
