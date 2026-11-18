# AI Agent READ tool contract (Batch 9B, 17/11/2026)

Contract for a read-only AI agent over **existing** public hotel APIs. No API was added or changed. Place/Nearby APIs are out of scope (EXCLUDED — EXTERNAL OWNER). Verified at the hotel-service boundary by `AgentReadContractTest` and `AvailabilityReadValidationTest`; Gateway-level execution is NOT_RUN (see [Batch 9B report](../BATCH9B_AI_READ_CONTRACT_VERIFICATION_2026-11-17.md)).

## Rules for every tool

- Call through the Gateway (`NEXT_PUBLIC_API_BASE_URL`, default `http://localhost:8080`) **without** an `Authorization` header. Public reads do not need one and never return private data with one.
- Set a client deadline. Suggested: 10 s, matching the frontend `readJson` helper. The Gateway now cuts any upstream wait at 20 s (`GATEWAY_RESPONSE_TIMEOUT`) with a 504.
- Treat only HTTP 200 as data. An empty `content`/array on 200 means "no match". Never turn an error into "no results" or "sold out".
- Errors share one JSON envelope: `{timestamp, status, error, code, message, path, requestId, fieldErrors?}` plus `X-Request-ID` and `Cache-Control: no-store`. Log `requestId`; never show raw bodies to the model beyond `code`/`message`.
- Public hotel responses no longer carry `ownerId` (always null since 18/11). Do not pass `contactEmail`/`contactPhone` into model context unless the user asks for contact details (data minimisation; these remain public hotel contact data).
- Availability and prices are **observations at `checkedAt`**, not reservations.

| HTTP | `code` | Agent meaning |
|---|---|---|
| 400 | `INVALID_REQUEST` / `VALIDATION_ERROR` | Fix the arguments; do not retry unchanged |
| 404 | `NOT_FOUND` | Hotel/room not public or does not exist |
| 409 | `INVENTORY_APPROVAL_REQUIRED` | Availability check/booking: room cannot take new bookings yet — not "sold out" |
| 409 | `CONFLICT` | State changed; re-read |
| 5xx | `INTERNAL_ERROR` / `DEPENDENCY_UNAVAILABLE` / `DEPENDENCY_TIMEOUT` | Temporary failure; say the data is unavailable. A database outage is 503 `DEPENDENCY_UNAVAILABLE` at the service; through the Gateway it is usually a 504 first (pool waits 30 s, Gateway 20 s) |
| — | client timeout | Unknown; report unavailability, may retry a read once |

## Tools

### `search_hotels` → `GET /api/hotel/hotels/offers/page`

| Param | Type | Rule |
|---|---|---|
| `cityId` / `provinceId` | UUID | one of `cityId`, `provinceId`, `cityName`, `hotelName` is required |
| `cityName`, `hotelName` | string | free text |
| `checkIn`, `checkOut` | ISO date `YYYY-MM-DD` | both or neither; `checkIn` not before today in `Asia/Ho_Chi_Minh`; `checkOut > checkIn`; at most 30 nights |
| `rooms` (1), `adults` (1), `children` (0) | int | `rooms` 1–20, `adults` 1–40, `children` 0–40 (totals for the search) |
| `minPrice`, `maxPrice` | decimal VND | `≥ 0`, `min ≤ max`; applies to the matched room's base price |
| `accommodationTypes` | repeated code | from `list_accommodation_types` |
| `amenityCodes` | repeated `[A-Z][A-Z0-9_]*` | canonical codes from `list_amenity_catalog`; AND semantics |
| `page` (0), `size` (20) | int | `page ≥ 0`, `1 ≤ size ≤ 50` |
| `sort` | `DEFAULT`, `PRICE_ASC`, `PRICE_DESC`, `RATING_DESC` | |
| `starRatings` | repeated 1–5 | |
| `guestRatings` | repeated 0–10 | |

Response 200: `{content:[{hotel, offer:{roomTypeId, quantity, unitPrice, totalPerNight, availableRooms, checkIn, checkOut, checkedAt}, reviews:{hotelId,totalReviews,averageRating}}], page, size, totalElements, totalPages, sort, availableAmenities}`. Price, capacity and availability of a result come from the same room offer. Violating the limits above returns 400 `INVALID_REQUEST` with an English message (e.g. `Check-in date cannot be in the past`, `Stay cannot exceed 30 nights`).

### `get_hotel` → `GET /api/hotel/hotels/{hotelId}`
Published hotel detail with policies, FAQs, amenity categories, approved rooms and `lowestPrice` (null when no approved room). Order of nested lists is not guaranteed.

### `list_rooms` → `GET /api/hotel/api/v1/hotel/{hotelId}`; `get_room` → `GET /api/hotel/api/v1/{roomTypeId}`
Only active rooms with effective inventory approval in published hotels. `get_room` for an unapproved room returns 409 `INVENTORY_APPROVAL_REQUIRED` (kept: Booking admission depends on it); treat it as "not bookable yet", and never show such a room as a result.

### `check_availability` → `GET /api/hotel/api/v1/availability/check?roomTypeId&checkIn&checkOut&quantity`
All four required; `quantity` 1–20; same date rules as search (not before today in `Asia/Ho_Chi_Minh`, at most 30 nights). An unapproved room returns 409 `INVENTORY_APPROVAL_REQUIRED` (not sold out). Response `{available, availableRooms, checkedAt}` (`checkedAt` = server time, ISO-8601 UTC; absent on older servers — then record the call time).

### Catalogs
`GET /api/hotel/hotels/accommodation-types` → `[{code,label}]`; `GET /api/hotel/amenity-catalog` → active canonical amenities; `GET /api/hotel/hotels/amenities/categories`, `GET /api/hotel/hotels/policies/categories`; `GET /api/hotel/cities`, `GET /api/hotel/cities/search/locations?query=` → CITY/PROVINCE with IDs.

### Reviews and policy
`GET /api/hotel/api/v1/reviews/summaries?hotelIds=` (1–100 IDs) → `{hotelId,totalReviews,averageRating}`; `GET /api/hotel/api/v1/reviews/stats/hotel/{hotelId}`; `GET /api/bookings/cancellation-policy`.

## Never call from a READ agent

Preview (`/hotels/{id}/preview`, `/api/v1/**/preview`), owner/admin routes (`/hotels/owner`, `/hotels/pending`, `/admin/**`), service routes (`/internal/**`, `/api/v1/availability/hold/**`), any write method, and `/api/places/**`. The first three groups return 401/403 to anonymous and CUSTOMER callers (verified).
