# UC06 - Booking History Business Rules

| Activity | BR Code | Description |
|---|---|---|
| (1) | BR1 | Navigation Rules:<br>Customer opens booking history from navigation/sidebar. Frontend routes to `/booking/history` (Components: `Header.tsx`, `UserSidebar.tsx`, Route: `app/booking/history/page.tsx`). |
| (2) | BR2 | Login State Rules:<br>The booking history page reads `localStorage.getItem("user")` on mount (View: `app/booking/history/page.tsx`). It parses the stored user into `AuthResponse`. |
| (3) | BR3 | Authentication Check Rules:<br>If stored user data is missing, the customer is treated as not logged in. If stored user exists and has `userId`, booking history loading can continue. |
| (4) | BR4 | Message Rules:<br>If the customer is not logged in, the system shows MSG57 and a login button (View: `app/booking/history/page.tsx`). |
| (5) | BR5 | Booking History Request Rules:<br>The frontend calls `bookingApi.getUserBookings(String(user.userId))` (API client: `lib/api/booking.ts`). The API sends `GET /api/bookings/user/{userId}` with auth headers when a token exists. |
| (6) | BR6 | Booking Retrieval Rules:<br>`BookingController.getUserBookings(userId)` calls `BookingService.getUserBookings(userId)`. The service reads bookings using `BookingRepository.findByUserId(userId)`. |
| (7) | BR7 | Booking Response Mapping Rules:<br>Each `Booking` is converted by `BookingService.toBookingResponse(booking)`. The response includes booking status, hotel info, dates, guest counts, total amount, paid amount, contact fields, hold info, items, and latest payment method when available. |
| (8) | BR8 | Load Result Rules:<br>If `bookingApi.getUserBookings()` resolves, booking history is considered loaded. If it throws, loading fails and the list is not displayed. |
| (9) | BR9 | Message Rules:<br>If booking history loading fails, the page shows MSG58 from `setError(err?.message || "...")` (View: `app/booking/history/page.tsx`). |
| (10) | BR10 | Sorting Rules:<br>The frontend sorts bookings by `createdAt` descending before display (View: `app/booking/history/page.tsx`, Memo: `sorted`). |
| (11) | BR11 | Hotel Image Rules:<br>When bookings exist, the frontend extracts unique `hotelId` values without cached images and calls `hotelApi.getHotelById(hotelId)`. If the hotel has `mediaAssets`, the first asset URL is stored in `hotelImages`. Image load errors are logged and do not block booking history display. |
| (12) | BR12 | Review State Rules:<br>If a token exists, the frontend filters bookings with status `CHECKED_OUT` and calls `reviewApi.hasBookingBeenReviewed(bookingId, token)`. That method calls `reviewApi.getReviewByBookingId(bookingId, token)`, which sends `GET /api/v1/reviews/check-booking/{bookingId}?userId=...`. A 200 response marks the booking as reviewed; a 404 or request error is treated as not reviewed. |
| (13) | BR13 | Empty List Check Rules:<br>After loading and sorting, the system checks `sorted.length`. If it is `0`, the booking history is empty. Otherwise, the booking cards are rendered. |
| (14) | BR14 | Message Rules:<br>If there are no bookings, the page shows MSG59 (View: `app/booking/history/page.tsx`). |
| (15) | BR15 | Display Booking List Rules:<br>For each booking, the page displays status, booking ID, hotel name, first room type, check-in/check-out dates, guest counts, created date, total amount, and paid amount. If status is `PENDING` or `PENDING_PAYMENT`, the page shows a payment action. If status is `CHECKED_OUT` and not reviewed, it shows a write-review action. If status is `CHECKED_OUT` and reviewed, it shows a view-review action. All bookings show a view-detail action. |
| (16) | BR16 | View Detail Navigation Rules:<br>When the customer selects view detail, the frontend calls `router.push("/booking/{booking.id}")` (View: `app/booking/history/page.tsx`). |
| (17) | BR17 | Booking Detail Request Rules:<br>The booking detail page calls `bookingApi.getBooking(id)` (View: `app/booking/[id]/page.tsx`, API client: `lib/api/booking.ts`). The API sends `GET /api/bookings/{id}`. |
| (18) | BR18 | Booking Detail Retrieval Rules:<br>`BookingController.getBooking(id)` calls `BookingService.getBooking(id)`, which retrieves data using `BookingRepository.findById(id)`. |
| (19) | BR19 | Detail Load Result Rules:<br>If `BookingRepository.findById(id)` finds a booking, the detail load succeeds. If not, `BookingService.getBooking(id)` throws `IllegalArgumentException`, the controller returns 404, and the frontend treats the request as failed. |
| (20) | BR20 | Message Rules:<br>If booking detail loading fails, the detail page shows MSG54 (View: `app/booking/[id]/page.tsx`). |
| (21) | BR21 | Display Detail Rules:<br>When detail loading succeeds, the detail page displays booking status, hotel name, dates, first booking item, total amount, paid amount, and booking metadata (View: `app/booking/[id]/page.tsx`). |
