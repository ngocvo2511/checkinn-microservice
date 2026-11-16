# Implementation inventory

38/38 approved persisted columns implemented: User3, Booking21, Revenue14. This count includes four Booking domain PKs and34 references; it does not count every DTO/signature edit as a new conversion. [Machine inventory](implementation-inventory.json) retains producer/consumer/query links to the historical impact matrix.

| Approved persisted field | Implemented Java/bind | Source declaration/writer |
|---|---|---|
| user.loyalty_commands.booking_id | UUID | `user-service/src/main/java/com/example/userservice/model/LoyaltyCommand.java` |
| user.points_reservations.booking_id | UUID | `user-service/src/main/java/com/example/userservice/model/PointsReservation.java` |
| user.points_transactions.booking_id | UUID | `user-service/src/main/java/com/example/userservice/model/PointsTransaction.java` |
| booking.bookings.id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.bookings.user_id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.bookings.hotel_id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.bookings.hold_id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.bookings.paid_reservation_payment_id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.bookings.inventory_reviewed_by | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/Booking.java` |
| booking.booking_items.id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/BookingItem.java` |
| booking.booking_items.booking_id | Booking association → UUID PK | `booking-service/src/main/java/com/example/bookingservice/booking/entity/BookingItem.java` |
| booking.booking_items.room_type_id | UUID | `booking-service/src/main/java/com/example/bookingservice/booking/entity/BookingItem.java` |
| booking.payments.id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/entity/Payment.java` |
| booking.payments.booking_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/entity/Payment.java` |
| booking.refund_requests.id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java` |
| booking.refund_requests.payment_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java` |
| booking.refund_requests.booking_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java` |
| booking.refund_requests.requested_by | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java` |
| booking.refund_requests.approved_by | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/RefundRequest.java` |
| revenue.booking_status_records.booking_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/BookingStatusRecord.java` |
| revenue.booking_status_records.hotel_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/BookingStatusRecord.java` |
| revenue.booking_status_records.room_type_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/BookingStatusRecord.java` |
| revenue.payment_records.booking_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/PaymentRecord.java` |
| revenue.payment_records.customer_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/PaymentRecord.java` |
| revenue.payment_records.hotel_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/PaymentRecord.java` |
| revenue.payment_records.room_type_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/entity/PaymentRecord.java` |
| booking.booking_event_outbox.booking_id | UUID | `booking-service/src/main/java/com/example/bookingservice/messaging/BookingEventOutbox.java` |
| booking.manual_refund_executions.request_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundService.java` |
| booking.manual_refund_executions.payment_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundService.java` |
| booking.manual_refund_executions.booking_id | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundService.java` |
| booking.manual_refund_executions.submitted_by | UUID | `booking-service/src/main/java/com/example/bookingservice/payment/refund/ManualRefundService.java` |
| revenue.revenue_cash_transactions.payment_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cash_transactions.booking_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cash_transactions.customer_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cash_transactions.hotel_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cash_transactions.room_type_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cashflow_gaps.booking_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |
| revenue.revenue_cashflow_gaps.hotel_id | UUID | `revenue-service/src/main/java/com/example/revenueservice/service/CashTransactionWriter.java` |

Repositories, services, scheduled recovery, REST/gRPC adapters, projections, SQL readback and JDBC binds were updated together. BookingItem.booking remains an association. SQL-only outbox/manual execution/cash/gap references use UUID arguments; old migration DDL is intentionally unchanged. Native tests verify representative mapping/binding behavior, not all38 columns in a canonical migrated schema.

New strict UUID boundary helpers in Booking/User/Revenue accept full UUIDv3/v4 and uppercase; transport IDs serialize as strings. Booking MVC property editor prevents Spring fallback from accepting shortened UUID groups. User malformed JSON maps to400 instead of the old generic RuntimeException401. Revenue validates before projection while preserving raw invalid receipt.

Booking catalog comparison now parses String request IDs to compare UUID identities. Scheduler keeps lexical UUID text ordering. Golden request/key hashing still consumes original input text, without normalization. Provider identifiers never enter the UUID helper.

Positive test aliases were replaced with deterministic valid UUID fixtures using test-only TestIds helpers. Negative malformed cases and assertions remain. No test assertions disabled. No Hotel/Auth/Media/Notification/Regulations production source or frontend edits required.
