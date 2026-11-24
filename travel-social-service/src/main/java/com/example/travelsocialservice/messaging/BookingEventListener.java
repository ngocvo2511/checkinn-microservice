package com.example.travelsocialservice.messaging;

import com.example.travelsocialservice.config.RabbitConfig;
import com.example.travelsocialservice.service.StayInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * A thrown exception is retried a bounded number of times, then dead-lettered to social.events.dlq, so an
 * event is never dropped silently. Malformed events are rejected without requeue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final StayInvitationService invitations;

    @RabbitListener(queues = RabbitConfig.BOOKING_QUEUE)
    public void onBookingStatus(BookingStatusEvent event) {
        UUID bookingId = parseBookingId(event);
        try {
            invitations.onBookingStatus(bookingId, event.bookingStatus());
        } catch (RuntimeException ex) {
            log.warn("[SOCIAL_BOOKING_EVENT] Deferred event {}: {}", event.eventId(), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static UUID parseBookingId(BookingStatusEvent event) {
        if (event == null || event.bookingId() == null) {
            throw new AmqpRejectAndDontRequeueException("Booking event without a booking id");
        }
        try {
            return UUID.fromString(event.bookingId());
        } catch (IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Booking event with a malformed booking id");
        }
    }
}
