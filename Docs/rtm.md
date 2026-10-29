# Requirements Traceability Matrix - Tripto

Source: `checkinn/Docs/SRS.docx.txt`

## Notes

- This RTM is organized by use case and business rule coverage.
- `Req ID` uses the SRS business rule IDs/ranges. `Use Case ID` is kept as a separate column so each requirement remains traceable to its use case.
- SRS text currently contains duplicated IDs `BR185` to `BR189` in `UC09` and `UC10`, `BR329` in `UC15` and `UC20`, plus repeated `BR213` in `UC12` and `BR316` in `UC19`. The rows below keep the SRS IDs as written and disambiguate them by `Use Case ID`.
- Default execution status is `Not Run` because these are test designs prepared before execution.
- Backend automated tests are implemented as JUnit unit/service tests inside the Maven modules. UI-only or UAT-style cases are classified as `Manual`.

## Automation Run

```bash
cd checkinn/auth-service
.\mvnw.cmd -Dtest=AuthServiceTest test

cd ..\hotel-service
.\mvnw.cmd '-Dtest=RoomTypeServiceImplTest,ReviewServiceTest' test

cd ..\booking-service
..\auth-service\mvnw.cmd -f pom.xml '-Dtest=BookingServiceTest,BookingScheduledTaskTest,PaymentServiceTest' test

cd ..\regulations-service
..\auth-service\mvnw.cmd -f pom.xml -Dtest=RegulationServiceTest test
```

Full module `test` may still run pre-existing `contextLoads` tests that require local secrets/database configuration. The commands above run the backend unit/service tests that do not require a live frontend, API gateway, database, or seeded user accounts.

## Column Guide

| Column | Description |
|---|---|
| No | RTM row number |
| Use Case ID | Use case from SRS |
| Req ID | Covered SRS business rule ID or range |
| Req Desc | Requirement area covered by the test |
| TC ID | Test case identifier |
| TC Desc | Test case objective |
| Test Design | Positive, negative, boundary, state transition, integration, security, or performance |
| Test Level | UI, API, integration, unit, security, or performance |
| Test Designer | Person/team designing the test |
| UAT Test Req? | Whether this should be included in UAT |
| Test Env | Test environment execution status |
| UAT Env | UAT environment execution status |
| Prod Env | Production verification status |
| Defects? | Whether a defect was found |
| Defect ID | Linked defect ID |
| Defect Status | Current defect status |
| Req Coverage Status | Covered, partially covered, not covered, or blocked |

## RTM

| No | Use Case ID | Req ID | Req Desc | TC ID | TC Desc | Test Design | Test Level | Test Designer | UAT Test Req? | Test Env | UAT Env | Prod Env | Defects? | Defect ID | Defect Status | Req Coverage Status |
|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | UC01 | BR1-BR3 | Login validation and error messages | TC-UC01-01 | Submit login with blank email/password and invalid credentials; verify MSG01/MSG02 are shown. | Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 2 | UC01 | BR4-BR6 | Token, response, and local session storage | TC-UC01-02 | Login with valid verified account; verify token, user fields, and localStorage session are created. | Positive | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 3 | UC01 | BR7 | Role-based redirect after login | TC-UC01-03 | Login as Admin, Owner/Host, and Customer; verify each role is redirected or reloaded correctly. | Decision table | UI/E2E | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 4 | UC02 | BR8-BR10 | Registration form validation and duplicate account messages | TC-UC02-01 | Register with missing fields, mismatched password, duplicate username, and duplicate email; verify corresponding messages. | Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 5 | UC02 | BR11-BR12 | Account creation and OTP generation | TC-UC02-02 | Register with valid data; verify account is created unverified and OTP has 6 digits, 10-minute expiry, attempt count 0. | Positive | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 6 | UC02 | BR13-BR15 | OTP verification validation and messages | TC-UC02-03 | Verify registration OTP with blank, non-6-digit, wrong, expired, max-attempt, and valid OTP values. | Boundary/Negative | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 7 | UC03 | BR16-BR18 | Forgot-password email request and OTP send result | TC-UC03-01 | Request password reset with blank, unknown, and valid email; verify validation, OTP generation, and messages. | Negative/Positive | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 8 | UC03 | BR19-BR21 | Reset OTP verification | TC-UC03-02 | Submit reset OTP with invalid format, wrong code, expired code, and valid code; verify flow and messages. | Boundary/Negative | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 9 | UC03 | BR22-BR24 | New password validation and reset completion | TC-UC03-03 | Submit missing, short, and valid new password after OTP verification; verify password is updated and login works with new password. | Negative/Positive | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 10 | UC04 | BR25-BR26 | Search input validation | TC-UC04-01 | Search without destination/hotel and without dates; verify required search messages. | Negative | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 11 | UC04 | BR27-BR30 | Search parameter handling and navigation | TC-UC04-02 | Search by destination/hotel, dates, guests, and quantity; verify search URL/state and result page navigation. | Positive | UI/E2E | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 12 | UC04 | BR31-BR34 | Search result retrieval, location matching, and empty/error states | TC-UC04-03 | Run searches with matching hotels, no hotels, no suitable hotels, and API failure; verify result count and messages. | Positive/Negative | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 13 | UC05 | BR47-BR49 | Booking entry authentication and room selection | TC-UC05-01 | Start booking as unauthenticated user, single room, same-type multi-room, and mixed-type multi-room; verify login or MSG31 behavior. | Decision table | UI/E2E | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 14 | UC05 | BR50-BR52 | Guest/contact validation and booking payload creation | TC-UC05-02 | Submit booking form with invalid guest name/email/phone and with valid contact data; verify validation messages and request payload. | Boundary/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 15 | UC05 | BR53-BR55 | Room hold and availability failure handling | TC-UC05-03 | Create booking with empty items, mixed room types, insufficient inventory, and valid inventory; verify hold creation or booking failure. | Integration | API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 16 | UC05 | BR56-BR59 | Amount, loyalty points, booking creation, and item persistence | TC-UC05-04 | Create booking with room quantity, nights, loyalty points under/over limits; verify total amount, discounts, status, event, and booking items. | Calculation/Integration | API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 17 | UC05 | BR60-BR66 | Payment validation and VNPay payment creation | TC-UC05-05 | Open payment for missing, expired, cancelled, confirmed, pending, and valid bookings; verify messages, payment attempt rules, and VNPay redirect URL. | State transition | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 18 | UC05 | BR67-BR75 | VNPay callback failure, hold retention, and retry behavior | TC-UC05-06 | Process failed/cancelled/expired VNPay callback; verify payment FAILED, booking PENDING, hold retained, retry allowed before expiry, and messages. | State transition | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 19 | UC05 | BR76-BR83 | VNPay success confirmation flow | TC-UC05-07 | Process successful first or retry VNPay callback; verify payment COMPLETED, hold confirmed, booking CONFIRMED, events published, points processed, and MSG44. | Positive/Integration | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 20 | UC05 | BR84-BR86 | Pay-at-hotel payment flow | TC-UC05-08 | Choose hotel payment for a valid active hold; verify ONSITE_PENDING payment, booking PENDING_PAYMENT, paidAmount 0, and MSG55. | Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 21 | UC06 | BR87-BR90 | Booking history navigation and authentication | TC-UC06-01 | Open booking history from navigation with and without local user data; verify route and MSG57 when unauthenticated. | Negative/Positive | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 22 | UC06 | BR91-BR94 | Booking retrieval, response mapping, and load failure | TC-UC06-02 | Load bookings successfully and with API failure; verify mapped fields or MSG58. | Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 23 | UC06 | BR95-BR100 | Sorting, images, review state, empty list, and list actions | TC-UC06-03 | Load multiple bookings with images, checked-out/reviewed states, empty list; verify sorting, actions, MSG59, and nonblocking image errors. | UI/E2E | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 24 | UC06 | BR101-BR103 | Booking detail retrieval and display | TC-UC06-04 | Open booking detail for valid and invalid booking ID; verify detail fields or MSG54. | Positive/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 25 | UC07 | BR104-BR109 | Review eligibility, navigation, context, and context errors | TC-UC07-01 | Try write-review action for non-checked-out, already reviewed, missing context, and valid checked-out booking; verify action availability and errors. | Decision table | UI/E2E | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 26 | UC07 | BR110-BR118 | Review form display, rating, and validation | TC-UC07-02 | Fill review form with missing title/content, short title/content, ratings, and valid data; verify validation messages and submit readiness. | Boundary/Negative | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 27 | UC07 | BR119-BR126 | Review submission, duplicate prevention, persistence, and post-submit behavior | TC-UC07-03 | Submit valid review, duplicate review, and API failure; verify PUBLISHED review, duplicate message, success message, and navigation/display update. | Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 28 | UC08 | BR127-BR130 | Personal information access control and redirect | TC-UC08-01 | Open profile without token, with invalid role, and with valid customer token; verify redirects and dashboard access. | Security/Negative | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 29 | UC08 | BR131-BR138 | Profile loading, identity, mapping, and load failure | TC-UC08-02 | Load profile with valid token, missing user, and service failure; verify displayed profile fields or MSG68. | Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 30 | UC08 | BR139-BR147 | Profile edit validation and save | TC-UC08-03 | Update profile with invalid required fields/phone and valid values; verify validation, saved fields, and MSG69/MSG70. | Boundary/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 31 | UC08 | BR148-BR162 | Password change validation, current password check, and result messages | TC-UC08-04 | Change password with missing current/new/confirm, short new password, mismatch, wrong current password, and valid change. | Boundary/Negative | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 32 | UC09 | BR163-BR170 | Owner review access and owned hotel loading | TC-UC09-01 | Open owner reviews without owner access, with missing token, and with valid owner; verify redirect, hotel loading, and MSG79 on failure. | Security/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 33 | UC09 | BR171-BR179 | Hotel filter, token, owner-scoped review API, and unauthorized hotel protection | TC-UC09-02 | Filter all hotels and one owned hotel, then attempt an unowned hotel; verify owner headers, scoped results, and MSG83/no data. | Security/Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 34 | UC09 | BR180-BR189 | Review list retrieval, enrichment, empty state, display, filters, and analytics | TC-UC09-03 | Load published reviews, empty reviews, and filtered/analytics tabs; verify enriched cards, MSG81/MSG82, counts, pagination, and updated view. | Positive/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 35 | UC10 | BR185-BR186 | Register hotel validation and create request | TC-UC10-01 | Submit hotel registration with missing required fields and with valid required data; verify validation and create-hotel API payload. | Boundary/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 36 | UC10 | BR187-BR192 | Hotel persistence, related data, media upload, thumbnail/gallery upload, and success response | TC-UC10-02 | Register hotel with thumbnail and gallery images; verify hotel awaits approval, related data saved, media uploaded, and success response shown. | Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 37 | UC11 | BR193-BR195 | Update hotel detail loading, form initialization, and validation | TC-UC11-01 | Open hotel edit page for existing hotel and submit invalid name/description/star/address/contact fields; verify prefill and validation messages. | Boundary/Negative | UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 38 | UC11 | BR196-BR200 | Update hotel request, extra information, image validation/upload, and active state | TC-UC11-02 | Update valid hotel info, extra fields, valid/invalid images, and active state; verify persisted values and success/error behavior. | Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 39 | UC12 | BR201-BR204 | Add room form, amenities, validation, and create request | TC-UC12-01 | Add room with missing/invalid name, price, amount, capacity, amenities, and valid room data; verify validation and request payload. | Boundary/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 40 | UC12 | BR205-BR213 | Hotel authorization, room type creation, serialization, image upload, and response | TC-UC12-02 | Create room as unauthorized owner, authorized owner, with valid images and image errors; verify authorization, serialized capacity/amenities, saved room, and upload handling. | Security/Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 41 | UC13 | BR214-BR217 | Update room form loading, inline edits, validation, and request | TC-UC13-01 | Open room edit page, edit fields/amenities/images, submit invalid and valid values; verify prefill, validation, and update payload. | Boundary/Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 42 | UC13 | BR218-BR226 | Owner authorization, room type persistence, serialization, image sync, and updated response | TC-UC13-02 | Update room as unauthorized and authorized owner; verify ownership check, persisted room fields, serialized capacity/amenities, image changes, and response. | Security/Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 43 | UC14 | BR227-BR229 | Room detail load and activate/deactivate action request | TC-UC14-01 | Open room detail and trigger deactivate/activate action; verify current state is loaded and correct toggle request is sent. | Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 44 | UC14 | BR230-BR236 | Room lookup, owner authorization, active-state persistence, UI refresh, and messages | TC-UC14-02 | Toggle existing/unowned/missing room; verify authorization, saved active flag, refreshed UI, and success/error messages. | Security/State transition | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 45 | UC15 | BR237-BR238, BR329, BR240-BR244 | Owner booking management entry, access, display, token, login message, and hotel loading | TC-UC15-01 | Open host booking management with missing token, invalid owner, and valid owner; verify redirect/message and owned hotel loading. | Security/Negative | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 46 | UC15 | BR245-BR255 | Owner-scoped booking retrieval, filters, search, empty state, and list display | TC-UC15-02 | Load bookings for all/one hotel, filter by status/search/date, and test empty result; verify scoped list and messages. | Integration/UI | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 47 | UC15 | BR256-BR265 | Booking detail, payment confirmation, and status transitions | TC-UC15-03 | Open booking detail, confirm hotel payment, and update stay status through valid and invalid transitions; verify messages and persisted status. | State transition | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 48 | UC15 | BR266-BR271 | Booking management refresh, error handling, and updated display | TC-UC15-04 | After payment/status action, reload list and detail; verify updated totals/status and error messages when action fails. | Integration | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 49 | UC16 | BR272-BR276 | Host revenue page loading, owner context, date defaults, initial fetch, and hotel selection | TC-UC16-01 | Open host revenue report with valid owner context; verify default date range, initial data fetch, and default hotel selection. | Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 50 | UC16 | BR277-BR284 | Hotel report filtering, validation, summary/series/top-room data, and export | TC-UC16-02 | Apply valid/invalid date filters, switch hotels, load report metrics, and export report; verify data and export failure handling. | Boundary/Integration | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 51 | UC17 | BR285-BR289 | Admin website report page, date input, validation, and fetch request | TC-UC17-01 | Open admin report page and apply invalid/valid date ranges; verify shell rendering, validation, and admin fetch parameters. | Boundary/Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 52 | UC17 | BR290-BR297 | Admin summary, revenue series, top hotels, status breakdown, charts, and export | TC-UC17-02 | Generate website report; verify summary metrics, charts, top hotels, booking status breakdown, loading/error states, and export behavior. | Integration | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 53 | UC18 | BR298-BR300 | Pending hotel list/detail loading and mapping | TC-UC18-01 | Load pending hotels and open hotel detail; verify list fields and mapped detail information. | Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 54 | UC18 | BR301-BR307 | Reject validation, action confirmation, approve/reject requests, authorization, state update, and messages | TC-UC18-02 | Approve pending hotel, reject without reason, reject with reason, and try non-pending/unauthorized action; verify state and messages. | Security/State transition | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 55 | UC19 | BR308-BR312 | Account list/detail loading, mapping, action display, and confirmation | TC-UC19-01 | Load user accounts, open detail, and verify lock/unlock action availability and confirmation dialog. | Positive/UI | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 56 | UC19 | BR313-BR319 | Account state validation, lock/unlock request, persistence, refresh, and messages | TC-UC19-02 | Lock active account, unlock locked account, repeat same-state action, and handle missing user/error; verify state and messages. | State transition/Security | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 57 | UC20 | BR320-BR323 | Regulation loading, editing, frontend validation, and update request | TC-UC20-01 | Open regulations page, edit service fees/pricing limits, submit invalid and valid values; verify validation and payload. | Boundary/Positive | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 58 | UC20 | BR324-BR330 | Authorization, server validation, persistence, snapshot, refresh, and messages | TC-UC20-02 | Update regulations as unauthorized user and admin; verify authorization, server validation, persisted config, snapshot/history, refreshed values, and messages. | Security/Integration | API/UI | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |

## Non-Functional Requirement Coverage

| No | Req ID | Req Desc | TC ID | TC Desc | Test Design | Test Level | Test Designer | UAT Test Req? | Test Env | UAT Env | Prod Env | Defects? | Defect ID | Defect Status | Req Coverage Status |
|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 59 | NFR-SEC-01 | Role-based access control for customer, owner, and admin functions | TC-NFR-SEC-01 | Attempt cross-role access to admin, host, and customer-only pages/APIs; verify access is denied. | Security | UI/API | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 60 | NFR-SEC-02 | Token/session validation on protected operations | TC-NFR-SEC-02 | Call protected APIs with missing, expired, malformed, and valid tokens; verify authorization behavior. | Security/Negative | API | QA Team | No | Not Run | N/A | N/A | No | N/A | N/A | Covered |
| 61 | NFR-PERF-01 | Login should complete within expected response time under normal load | TC-NFR-PERF-01 | Measure login API/UI response time for valid credentials under normal load. | Performance | Performance | QA Team | No | Not Run | N/A | N/A | No | N/A | N/A | Covered |
| 62 | NFR-PERF-02 | Hotel search should return results within expected response time under normal load | TC-NFR-PERF-02 | Measure hotel search response time using representative destination/date/guest filters. | Performance | Performance | QA Team | No | Not Run | N/A | N/A | No | N/A | N/A | Covered |
| 63 | NFR-REL-01 | Booking/payment consistency during payment success, failure, retry, and hold expiry | TC-NFR-REL-01 | Run end-to-end booking/payment scenarios and verify booking, payment, and hold states remain consistent. | Reliability/State transition | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |
| 64 | NFR-REL-02 | Expired holds and pending payments are cleaned up automatically | TC-NFR-REL-02 | Create expired holds/bookings and run scheduled cleanup; verify booking cancellation and room hold release. | Reliability | Integration | QA Team | No | Not Run | N/A | N/A | No | N/A | N/A | Covered |
| 65 | NFR-AVAIL-01 | Core search and booking remain usable when non-critical review data fails | TC-NFR-AVAIL-01 | Simulate review service failure while searching/viewing hotels/booking; verify core flows continue. | Fault tolerance | Integration | QA Team | Yes | Not Run | Not Run | N/A | No | N/A | N/A | Covered |

## Backend Automation / Manual Classification

Use this section as the executable-test classification for the RTM. `Backend Automated` means JUnit test code exists in the backend module. `Manual` means the case is better verified through UI/UAT, seeded data, visual behavior, email/OTP delivery, upload flows, or live integration setup.

| TC ID | Type | Backend Test Code / Manual Reason |
|---|---|---|
| TC-UC01-01 | Manual | Login form validation and displayed messages are UI-facing. |
| TC-UC01-02 | Manual | Token storage and session persistence are frontend/localStorage behavior. |
| TC-UC01-03 | Manual | Role redirect is frontend routing behavior. |
| TC-UC02-01 | Backend Automated | `auth-service/src/test/java/com/example/authservice/service/AuthServiceTest.java` validates registration password policy before calling user-service. |
| TC-UC02-02 | Manual | Account creation plus OTP email delivery needs seeded user-service/email/UAT verification. |
| TC-UC02-03 | Manual | OTP value, expiry, and max-attempt flow should be verified with OTP test data or UAT email inbox. |
| TC-UC03-01 | Manual | Forgot-password request and OTP email delivery need live email/OTP setup. |
| TC-UC03-02 | Manual | OTP verification outcomes need controlled OTP records. |
| TC-UC03-03 | Backend Automated | `auth-service/src/test/java/com/example/authservice/service/AuthServiceTest.java` validates reset-password password policy before calling user-service. |
| TC-UC04-01 | Manual | Search input message behavior is frontend/UI. |
| TC-UC04-02 | Manual | Search URL/navigation state is frontend/UI. |
| TC-UC04-03 | Manual | Search result display and empty/error copy are UI/UAT; backend search can be covered later with repository/service integration tests. |
| TC-UC05-01 | Manual | Booking entry login prompt and room selection UI behavior are frontend. |
| TC-UC05-02 | Manual | Guest/contact validation messages are frontend form behavior. |
| TC-UC05-03 | Backend Automated | `booking-service/src/test/java/com/example/bookingservice/booking/service/BookingServiceTest.java` validates empty item, mixed room type, and hold failure paths. |
| TC-UC05-04 | Backend Automated | `BookingServiceTest.java` validates amount calculation, points discount cap, booking status, and item persistence flow. |
| TC-UC05-05 | Backend Automated | `booking-service/src/test/java/com/example/bookingservice/payment/service/PaymentServiceTest.java` validates payment creation and VNPay init rules. |
| TC-UC05-06 | Backend Automated | `PaymentServiceTest.java` and `BookingServiceTest.java` cover VNPay replay/failure-safe behavior and expired hold cancellation. |
| TC-UC05-07 | Backend Automated | `PaymentServiceTest.java` validates successful VNPay callback idempotency, booking confirmation, and event publication. |
| TC-UC05-08 | Backend Automated | `PaymentServiceTest.java` validates hotel payment creation and `ONSITE_PENDING` behavior. |
| TC-UC06-01 | Manual | Navigation/login state is frontend/localStorage behavior. |
| TC-UC06-02 | Manual | Booking history API can be added later, but current case includes frontend mapping/load failure display. |
| TC-UC06-03 | Manual | Sorting, images, review action visibility, and empty list are UI-facing. |
| TC-UC06-04 | Manual | Booking detail display is UI-facing; backend get-by-id can be added later as service test. |
| TC-UC07-01 | Manual | Review action availability/navigation context is frontend behavior. |
| TC-UC07-02 | Manual | Review form validation and rating widget behavior are frontend. |
| TC-UC07-03 | Backend Automated | `hotel-service/src/test/java/com/example/hotelservice/Review/service/ReviewServiceTest.java` validates duplicate prevention and published review persistence. |
| TC-UC08-01 | Manual | Profile route guard/redirect is frontend behavior. |
| TC-UC08-02 | Manual | Profile display/mapping is UI-facing. |
| TC-UC08-03 | Manual | Profile edit validation and success/error messages are UI-facing unless user-service tests are added. |
| TC-UC08-04 | Manual | Password-change UI messages are manual; backend password policy is partly covered by `AuthServiceTest.java`. |
| TC-UC09-01 | Manual | Owner review page access and hotel loading display are UI-facing. |
| TC-UC09-02 | Backend Automated | `ReviewServiceTest.java` validates owner-hotel scope protection. |
| TC-UC09-03 | Backend Automated | `ReviewServiceTest.java` validates owner review retrieval and hotel-name enrichment. |
| TC-UC10-01 | Manual | Register-hotel form validation and request composition are frontend/manual. |
| TC-UC10-02 | Manual | Hotel persistence plus media upload should be tested manually or by later integration tests. |
| TC-UC11-01 | Manual | Edit form loading and validation are frontend/manual. |
| TC-UC11-02 | Manual | Hotel update plus image upload/active state is better as manual/integration test. |
| TC-UC12-01 | Manual | Add-room form and amenity input behavior are frontend/manual. |
| TC-UC12-02 | Backend Automated | `hotel-service/src/test/java/com/example/hotelservice/Room/service/RoomTypeServiceImplTest.java` validates owner authorization and room amount persistence rules. |
| TC-UC13-01 | Manual | Room edit form loading and inline edit behavior are frontend/manual. |
| TC-UC13-02 | Backend Automated | `RoomTypeServiceImplTest.java` validates update owner authorization and room amount cannot decrease. |
| TC-UC14-01 | Manual | Room detail loading and UI toggle request are frontend/manual. |
| TC-UC14-02 | Backend Automated | `RoomTypeServiceImplTest.java` validates activate/deactivate service behavior and owner authorization. |
| TC-UC15-01 | Manual | Host menu entry, protected route, and page display are frontend/manual. |
| TC-UC15-02 | Manual | Booking filters/search/list display are frontend/manual. |
| TC-UC15-03 | Manual | Owner status-transition workflow should be manual unless booking transition service rules are hardened. |
| TC-UC15-04 | Manual | Refresh/error display is frontend/manual. |
| TC-UC16-01 | Manual | Host revenue page defaults and hotel selection are frontend/manual. |
| TC-UC16-02 | Manual | Report filtering/export is UAT/manual unless revenue-service unit tests are added. |
| TC-UC17-01 | Manual | Admin report page/date validation/fetch UI is manual. |
| TC-UC17-02 | Manual | Chart/export verification is manual/UAT. |
| TC-UC18-01 | Manual | Pending hotel list/detail display is admin UI/manual. |
| TC-UC18-02 | Manual | Approve/reject workflow should be manual or later hotel-service integration test. |
| TC-UC19-01 | Manual | Account list/detail display and confirmation dialog are admin UI/manual. |
| TC-UC19-02 | Manual | Lock/unlock flow should be manual or later user-service integration test. |
| TC-UC20-01 | Backend Automated | `regulations-service/src/test/java/com/example/regulationsservice/service/RegulationServiceTest.java` validates listing and upsert mapping. |
| TC-UC20-02 | Backend Automated | `RegulationServiceTest.java` validates active/invalid regulation handling and snapshot fallback. |
| TC-NFR-SEC-01 | Backend Automated | `ReviewServiceTest.java`, `RoomTypeServiceImplTest.java`, and `AuthServiceTest.java` cover backend ownership/password security rules. |
| TC-NFR-SEC-02 | Manual | Full token/session validation requires security filter/API integration setup. |
| TC-NFR-PERF-01 | Manual | Login latency requires running environment/load measurement. |
| TC-NFR-PERF-02 | Manual | Search latency requires running environment/load measurement. |
| TC-NFR-REL-01 | Backend Automated | `BookingServiceTest.java`, `BookingScheduledTaskTest.java`, and `PaymentServiceTest.java` cover booking/payment/hold consistency. |
| TC-NFR-REL-02 | Backend Automated | `BookingScheduledTaskTest.java` covers expired pending payment/hold cleanup behavior. |
| TC-NFR-AVAIL-01 | Manual | Service-failure tolerance is best verified in integration/UAT with one service intentionally unavailable. |
