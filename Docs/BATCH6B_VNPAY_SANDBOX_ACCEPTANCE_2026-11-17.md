# Batch6B — VNPay Sandbox acceptance completed

17/11/2026, Asia/Saigon. **DONE for the requested real Sandbox payment flow.**
The previous merchant code-71 blocker is resolved in the observed checkout.
This is a real call to VNPay Sandbox using the configured merchant, not a
locally generated success callback. Production stability is not claimed.

Docker was started with `docker/stack.ps1 -Environment Supabase -Action Up`.
A temporary verified customer, Hotel/Room and 10,000 VND Booking were prepared
under a unique private manifest. Existing province/destination data was reused.
The normal Payment API produced the checkout URL. The browser selected NCB,
entered the user-provided Sandbox card and OTP, and completed the test payment.
The Sandbox terms were accepted only after the user's explicit confirmation.
No real card, real-money payment or refund was used.

| Check | Result |
| --- | --- |
| Actual provider return | ResponseCode=00 / TransactionStatus=00 |
| Provider return HMAC against configured secret | PASS |
| Payment persisted by application | COMPLETED, 10,000 VND |
| Authenticated Booking API | CONFIRMED / V1_CONFIRMED |
| RabbitMQ → Revenue | Exactly one CREDIT |
| Three replays of the actual signed return | HTTP 200; paid amount unchanged; still one CREDIT |
| Invalid hash | HTTP 400, rejected |
| Correctly signed but mismatched amount | HTTP 400, rejected |

The browser return was deliberately opened without a NextAuth login. Its
conservative pending-booking message is not evidence that the payment failed:
the authenticated Booking API independently confirmed the booking. Full
authenticated browser return UX was not rechecked in this run.

Exact-ID cleanup removed the temporary account, Hotel/Room, Booking/payment,
loyalty/inventory/outbox/Revenue records and their test Mailpit messages.
Private preimages and the test manifest were retained under ignored `.local`.
All unselected rows were compared and preserved. No reference cities/provinces,
real accounts, existing assets, Flyway history, grants or schema were changed.
The temporary checkout bridge was stopped; the application Docker stack was
started again after cleanup.

Evidence:
[VNPay Sandbox result](evidence/batch-6b-supabase-local-integration/vnpay-sandbox-acceptance-2026-11-17.json).
Real Storage upload/private signing also passed in the
[Storage evidence](evidence/batch-6b-supabase-local-integration/storage-live-acceptance-2026-11-16.json).
This closes the two provider blockers for Batch6B local development.

Not certified here: externally reachable IPN webhook delivery, real provider
refund/query/reconciliation, provider-wide outage/load testing, production
deployment or production readiness. Other batches and standardization parent
findings retain their own scope/status.
