# UC15 - Booking Management Business Rules

## Use Case Check

The submitted use case is mostly correct, but one precondition needs adjustment to match the current code:

- The owner booking page requires the user to be authenticated with the `OWNER` role.
- The page loads hotels by owner through `GET /api/v1/hotels/owner`. The backend uses `HotelService.getByOwner(ownerId)` and `hotelRepository.findByOwnerId(ownerId)`, so the implemented condition is that the owner has at least one hotel. The current flow does not check that the hotel is approved before displaying the booking management page.
- The implemented processing actions are: view booking list/detail, search/filter locally, confirm onsite hotel payment, and move stay status from `CONFIRMED` to `CHECKED_IN`, then from `CHECKED_IN` to `CHECKED_OUT`.

Suggested corrected English use case:

| Field | Corrected Content |
|---|---|
| Use Case ID | UC15 |
| Use Case Name | Booking Management |
| Description | The hotel owner monitors customer bookings for hotels they own, views booking details, confirms onsite hotel payments, and updates stay progress when allowed by the booking status. |
| Actors | Hotel Owner |
| Trigger | The hotel owner selects `Booking Management` in the host management menu. |
| Preconditions | The hotel owner is logged in with the `OWNER` role and has at least one hotel associated with their account. |
| Postconditions | The owner can view, search, and filter bookings for the selected hotel. If the owner confirms onsite payment or updates the stay status, the booking and payment records are updated to the corresponding status. |

| Activity | BR Code | Description |
|---|---|---|
| (1) | BR1 | Booking Management Entry Rules:<br>The hotel owner starts UC15 from the host menu and opens the booking management page for host users (View: `HostMenu`, Route: `/host/bookings`). |
| (2) | BR2 | Owner Access Rules:<br>The booking management page is only available when the current user passes the `OWNER` protected route check (Hook: `useProtectedRoute("OWNER")`). |
| (3) | BR3 | Access Redirect Rules:<br>If the user is not authenticated, the system redirects to `/host`; if the user is authenticated but does not have owner access, the system redirects to `/` (View: `app/host/bookings/page.tsx`). |
| (4) | BR4 | Booking Page Display Rules:<br>After owner access is confirmed, the system displays the booking management page with the hotel selector area, statistics area, and booking list area (View: `HostBookingsPage`). |
| (5) | BR5 | Owner Hotel Token Rules:<br>The page checks [token] in local storage before loading hotels. Hotel loading does not continue when [token] is missing (View: `HostBookingsPage`). |
| (6) | BR6 | Login Required Message Rules:<br>If [token] is missing, the page displays MSG84 and stops the booking management flow (View: `HostBookingsPage`). |
| (7) | BR7 | Owner Hotel Loading Rules:<br>The system loads the list of hotels associated with the current owner before loading bookings, because booking retrieval depends on [selectedHotelId] (Frontend API: `hotelApi.getHotelsByOwner(token)`). |
| (8) | BR8 | Owner Identity Rules:<br>The backend identifies [ownerId] from the authenticated JWT subject rather than from a manually entered owner id (Controller: `HotelController.getHotelsByOwner()`). |
| (9) | BR9 | Owned Hotel Retrieval Rules:<br>The hotel list contains hotels whose [ownerId] matches the authenticated owner. The implemented query does not filter by hotel approval status (Service: `HotelService.getByOwner()`, Repository: `hotelRepository.findByOwnerId()`). |
| (10) | BR10 | Owner Hotel Response Rules:<br>The returned hotels are stored in [hotels]. If at least one hotel exists, the first hotel id becomes the initial [selectedHotelId] (View: `HostBookingsPage`). |
| (11) | BR11 | Hotel Availability Rules:<br>The booking list can be loaded only when [hotels.length] is greater than zero and [selectedHotelId] is available. |
| (12) | BR12 | No Hotel Message Rules:<br>If the owner has no hotels, the page displays MSG87 and MSG88 with an action to create a hotel (View: `HostBookingsPage`). |
| (13) | BR13 | Hotel Selection Rules:<br>The owner can use the hotel selector to change [selectedHotelId]. The booking list is scoped to that selected hotel (View: `HostBookingsPage`). |
| (14) | BR14 | Hotel Booking Request Rules:<br>When [selectedHotelId] is set or changed, the frontend requests bookings for that hotel only (Frontend API: `bookingApi.getHotelBookings(selectedHotelId)`). |
| (15) | BR15 | Hotel Booking Retrieval Rules:<br>The backend retrieves bookings by [hotelId] using the booking service. The current booking endpoint does not perform an additional owner check; the frontend scopes [hotelId] from the owner hotel list (Controller: `BookingController.getHotelBookings()`, Service: `BookingService.getHotelBookings()`). |
| (16) | BR16 | Booking List Response Rules:<br>The backend returns [BookingResponse] items for the selected hotel, including contact information, dates, amounts, status, payment method, and room items (Service: `BookingService.toBookingResponse()`). |
| (17) | BR17 | Booking Loading Result Rules:<br>The booking list is considered loaded when `bookingApi.getHotelBookings()` returns successfully. Errors are handled as a separate user-visible outcome. |
| (18) | BR18 | Booking Loading Error Rules:<br>If bookings cannot be loaded, the page displays MSG86 or the backend error message when available (View: `HostBookingsPage`). |
| (19) | BR19 | Booking Overview Display Rules:<br>When bookings are loaded, the system displays total bookings, confirmed count, pending count, total revenue, search input, status filter, and booking table (View: `HostBookingsPage`, Component: `BookingsList`). |
| (20) | BR20 | Search And Filter Input Rules:<br>The owner may search by [booking.id], [contactEmail], [contactName], or [contactPhone], and may filter by normalized status (Component: `BookingsList`). |
| (21) | BR21 | Local Filtering Rules:<br>The frontend filters [bookings] locally into [filteredBookings]. Statuses `PENDING` and `PENDING_PAYMENT` are normalized to `AWAITING_PAYMENT` for display filtering (Component: `BookingsList`). |
| (22) | BR22 | Visible Booking Availability Rules:<br>Visible bookings are available when [filteredBookings.length] is greater than zero. Empty filtered results are not backend errors. |
| (23) | BR23 | Empty Booking Message Rules:<br>If there are no visible bookings, the page displays MSG90 and then either MSG91 for unmatched search/filter criteria or MSG92 for an empty booking list (Component: `BookingsList`). |
| (24) | BR24 | Booking Selection Rules:<br>Selecting a booking row stores that row as [selectedBooking] and opens the booking detail modal (Component: `BookingsList`). |
| (25) | BR25 | Booking Detail Display Rules:<br>The booking detail modal displays booking id, status, dates, guest contact data, booked room items, payment method, paid amount, debt amount, and special requests when present (Component: `BookingDetailModal`). |
| (26) | BR26 | Processing Action Rules:<br>From booking detail, the owner may confirm onsite payment, update stay status, or close the modal without modifying records. Available actions depend on [booking.status], [paymentMethod], and [checkInDate] (Component: `BookingDetailModal`). |
| (27) | BR27 | Hotel Payment Eligibility Rules:<br>Hotel payment confirmation is available only when [paymentMethod] is `HOTEL` and [booking.status] is `PENDING` or `PENDING_PAYMENT` (Function: `canConfirmPayment()`). |
| (28) | BR28 | Hotel Payment Confirmation Request Rules:<br>When the owner confirms payment, the frontend sends the booking id to the payment confirmation endpoint (Frontend API: `bookingApi.confirmPayment()`, Controller: `PaymentController.confirmHotelPayment()`). |
| (29) | BR29 | Hotel Payment Completion Rules:<br>The system marks the latest onsite pending payment as `COMPLETED`, sets the booking [paidAmount] to [totalAmount], changes [booking.status] to `CONFIRMED`, confirms the held room, and publishes the payment completion event (Service: `PaymentService.confirmHotelPayment()`, `BookingService.confirmBookingHold()`). |
| (30) | BR30 | Payment Success Refresh Rules:<br>After hotel payment confirmation succeeds, the modal displays MSG93, closes after a short delay, and reloads the booking list for the selected hotel (Component: `BookingDetailModal`). |
| (31) | BR31 | Stay Status Eligibility Rules:<br>Status update is available only for `CONFIRMED` and `CHECKED_IN` bookings. A `CONFIRMED` booking can move to `CHECKED_IN` only on or after [checkInDate]; a `CHECKED_IN` booking can move to `CHECKED_OUT` (Functions: `canChangeStatus()`, `getNextStatus()`). |
| (32) | BR32 | Booking Status Update Request Rules:<br>When status update is allowed, the frontend sends the next status to the booking status endpoint (Frontend API: `bookingApi.updateBookingStatus()`, Controller: `BookingController.updateBookingStatus()`). |
| (33) | BR33 | Booking Status Persistence Rules:<br>The system saves the new [booking.status] and publishes a booking status event so downstream services can consume the status change (Service: `BookingService.updateBookingStatus()`, `HotelEventPublisher.publishBookingStatus()`). |
| (34) | BR34 | Status Success Refresh Rules:<br>After status update succeeds, the modal displays MSG94, closes after a short delay, and reloads the booking list for the selected hotel (Component: `BookingDetailModal`). |
| (35) | BR35 | View Only Close Rules:<br>If no processing action is selected or no action is available, closing the booking detail modal does not update booking or payment data (Component: `BookingDetailModal`). |
