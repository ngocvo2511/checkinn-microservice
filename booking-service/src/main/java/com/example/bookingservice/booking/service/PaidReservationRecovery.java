package com.example.bookingservice.booking.service;

import java.util.UUID;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.messaging.HotelEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

@Component
@Slf4j
public class PaidReservationRecovery {
    private final BookingRepository bookings;
    private final PaidReservationService reservations;
    private final HotelEventPublisher events;
    private final java.util.concurrent.Executor wakeups;

    public PaidReservationRecovery(BookingRepository bookings, PaidReservationService reservations, HotelEventPublisher events,
            @org.springframework.beans.factory.annotation.Qualifier("paidReservationWakeups") java.util.concurrent.Executor wakeups) {
        this.bookings = bookings; this.reservations = reservations; this.events = events; this.wakeups = wakeups;
    }

    @TransactionalEventListener
    public void accepted(PaidReservationService.Requested request) {
        // Do not block the committing request while opening another database transaction.
        // If the bounded wake-up queue is full, the durable DB scheduler still owns the intent.
        try { wakeups.execute(() -> attempt(request.bookingId())); }
        catch (java.util.concurrent.RejectedExecutionException busy) { log.debug("Paid reservation wake-up deferred to durable scheduler"); }
    }

    @Scheduled(fixedDelayString = "${paid.reservation.delay-ms:1000}", initialDelayString = "${paid.reservation.initial-delay-ms:10000}")
    public void recover() {
        for (UUID id : bookings.findDuePaidReservations(LocalDateTime.now(), PageRequest.of(0, 20))) attempt(id);
    }
    private void attempt(UUID id) {
        try {
            reservations.process(id); // source state and outbox snapshot commit atomically
        } catch (RuntimeException ex) {
            try { reservations.defer(id); }
            catch (RuntimeException unavailable) { log.warn("Paid reservation recovery deferred (type={})", unavailable.getClass().getSimpleName()); }
        }
    }
}
