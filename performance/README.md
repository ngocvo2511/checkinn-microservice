# Performance Checks

This folder contains lightweight performance scripts that verify quality targets from the architecture documents.

## Login Latency

Target: valid login should complete within 500ms under normal local/test load.

Run after the gateway, auth service, user service, database, and Eureka are running:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:LOGIN_USER="ngocvo2511"
$env:LOGIN_PASSWORD="123123"
$env:VUS="25"
$env:DURATION="1m"
k6 run .\performance\login-latency.k6.js
```

For a small normal-load smoke test:

```powershell
$env:VUS="5"
$env:DURATION="3m"
k6 run .\performance\login-latency.k6.js
```

Record the resulting `http_req_duration` p95 value in the test evidence for the 500ms login latency requirement.

## Booking Hold Concurrency

Target: concurrent attempts to reserve the last available room must not over-allocate inventory.

Prepare a test room type with only one available room for the selected date range, then run:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:HOTEL_ID="c1d2e3f4-a5b6-7890-cdef-111111111111"
$env:ROOM_TYPE_ID="d1e2f3a4-b5c6-7890-defa-111111111111"
$env:CHECK_IN_DATE="2026-06-01"
$env:CHECK_OUT_DATE="2026-06-02"
$env:ATTEMPTS="10"
$env:EXPECTED_SUCCESSES="1"
k6 run .\performance\booking-hold-concurrency.k6.js
```

The `successful_bookings` counter must stay at or below `EXPECTED_SUCCESSES`.
