package com.example.bookingservice.messaging;

import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Public producer API now persists snapshots; broker I/O belongs to the committed outbox worker. */
@Component
@RequiredArgsConstructor
public class HotelEventPublisher {
    private final BookingEventOutbox outbox;
    public void publishPaymentCompleted(PaymentEvent event) { outbox.enqueue(event.getBookingId(),"payment.completed",event); }
    public void publishPaymentRefunded(PaymentEvent event) { outbox.enqueue(event.getBookingId(),"payment.refunded",event); }
    public void publishBookingStatus(BookingStatusEvent event) { outbox.enqueue(event.getBookingId(),"booking.status.changed",event); }
}
