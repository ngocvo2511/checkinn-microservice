package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Preserve legacy delivery while preventing new onsite events from describing rolled-back writes. */
public final class OnsiteEvents {
    private OnsiteEvents() {}
    public static void afterCommit(Booking booking, Runnable publish) {
        if (booking.getOnsiteReservationStatus() != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }
}
