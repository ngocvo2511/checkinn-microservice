# User Stories - Tripto Hotel Booking System

| No. | User Story |
| :--- | :--- |
| 1 | As a new user, I want to create an account requiring email verification, so that I can ensure no one else can sign up using my identity |
| 2 | As a registered user, I want to log in within 500ms so that I can access the platform without frustrating delays. |
| 3 | As a user who forgot their password, I want to reset it via a secure multi-factor verification so that my account recovery process cannot be hijacked. |
| 4 | As a registered user, I want to log out from my current session so that my account cannot be reused from the same browser session, with the session token removed immediately after logout. |
| 5 | As a customer, I want to update my personal information so that my account profile stays accurate, with invalid phone numbers, passwords, or required fields rejected before saving. |
| 6 | As a customer, I want to search hotels by destination, stay dates, guests, and room quantity so that I can find suitable accommodation, with search results returned within 2 seconds under normal load. |
| 7 | As a customer, I want to view hotel details, room types, prices, amenities, policies, and reviews so that I can make a booking decision, with the hotel detail page remaining usable even if review data fails to load. |
| 8 | As a customer, I want room availability to be temporarily held during booking and payment so that another customer cannot take the same room, with expired holds automatically released or cancelled. |
| 9 | As a customer, I want booking creation and payment processing to remain consistent so that I am not charged for a room that is not reserved, with successful payments confirming the booking and failed payments leaving it unconfirmed. |
| 10 | As a customer, I want to use loyalty points during booking so that I can reduce the payable amount, with the system validating point balance and applying the discount accurately before payment confirmation. |
| 11 | As a customer, I want to pay through VNPay or pay at the hotel so that I can choose my preferred payment method, with sensitive online payment data handled outside Tripto’s main database. |
| 12 | As a customer, I want to view my booking history so that I can track previous and current reservations, with the history list loaded without noticeable slowing down other booking operations. |
| 13 | As a customer, I want to view booking details with a clear, printable UI so that I have a physical copy of my reservation. |
| 14 | As a customer, I want to submit a hotel review after completing my stay so that I can share my experience with other users, with duplicate reviews for the same booking prevented. |
| 15 | As a customer, I want to receive booking notifications reliably even during high traffic periods so that I never miss an update. |
| 16 | As a hotel owner, I want to register by uploading verified business documents so that the platform maintains high-quality listings. |
| 17 | As a hotel owner, I want to upload high-quality media for my listing without negatively impacting the page load performance, so that potential guests always have a fast and smooth browsing experience. |
| 18 | As a hotel owner, I want to update my hotel details seamlessly in the background, so that customers never encounter a broken page or a 'maintenance' message while I am making changes. |
| 19 | As a hotel owner, I want to hide a listing with the ability to restore it later so that I don't lose my data permanently. |
| 20 | As a hotel owner, I want to manage rooms with concurrency protection so that price updates remain consistent across all staff. |
| 21 | As a hotel owner, I want to view booking requests the moment they occur so that I can react to new business without manual refreshing. |
| 22 | As a hotel owner, I want to confirm orders with minimal steps so that the internal processing time is reduced to under 1 minute. |
| 23 | As a hotel owner, I want to update booking stay status to checked-in or checked-out so that the system reflects actual guest stay progress, with status transitions limited to valid business rules. |
| 24 | As a hotel owner, I want to view reviews aggregated into sentiment scores so that I can quickly understand the overall mood. |
| 25 | As a hotel owner, I want to respond to customer reviews so that I can address customer feedback publicly, with each response editable only by the owner who created it. |
| 26 | As a hotel owner, I want to view revenue reports restricted to authorized roles only so that sensitive financial data remains confidential. |
| 27 | As an administrator, I want to manage accounts with full accountability so that I can track every change made in the system. |
| 28 | As an administrator, I want to manage owner accounts automatically cross-checked for legitimacy so that I can ensure only verified businesses are on the platform. |
| 29 | As an administrator, I want to approve hotels without the interface becoming unresponsive, allowing me to continue other tasks while large assets are being processed. |
| 30 | As an administrator, I want to reject registrations with a mandatory reason field so that users receive actionable feedback to resolve their application. |
| 31 | As an administrator, I want to generate complex analytical reports without causing any slowdown or latency to the live booking system used by customers. |
| 32 | As an administrator, I want to update system policies so that changes take effect globally and immediately, ensuring zero downtime or service disruption for active users. |
| 33 | As a customer, I want the core searching and booking process to remain operational even if secondary features (like reviews or recommendations) encounter issues, so that my travel plans aren't interrupted by minor system glitches. |