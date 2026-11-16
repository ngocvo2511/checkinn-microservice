# Golden vectors — verified before and after conversion

Legacy golden test PASS before production changes (`legacy-golden.log`); same golden assertions PASS in final Booking suite. Expected UUID/MD5-version bits and SHA-256 vectors computed independently of the new helper; source hashing method bodies/prefixes retained.

| Input/output | Exact vector |
|---|---|
| user | `11111111-1111-4111-8111-111111111111` |
| hotel | `22222222-2222-4222-8222-222222222222` |
| command key | `ABCDEFAB-CDEF-4ABC-8DEF-ABCDEFABCDEF` |
| keyHash | `768586b8602ad6f6316db9120e2295c7851bdea2054b75dc4876db264b8de242` |
| Booking | `d3616870-e674-310a-b1ee-6a282806ec94` |
| Hold | `de0936d6-9eec-3ef7-a53c-91cb97898ab0` |
| requestHash (remaining request fields null; exact fixture in test) | `34c9ca60917ecf61fcea5ce75c922562ec8c14217233bb91b009b66184afd6f4` |

`UUID.nameUUIDFromBytes(("booking-id-v1:" + keyHash).getBytes(UTF_8))` and hold prefix `booking-hold-v1:` unchanged. The return type becomes UUID, not the resulting identity. Non-idempotent Booking remains randomUUID; generated Item/Payment/Refund remain v4. No v4-only validator.

Loyalty command shape canonical UUID + `|EARN/USE/CANCEL` unchanged; native EARN command test verifies exact key. Booking retry/duplicate/response-loss tests remain in BookingServiceTest; no replacement of frontend idempotencyKey with Booking ID.
