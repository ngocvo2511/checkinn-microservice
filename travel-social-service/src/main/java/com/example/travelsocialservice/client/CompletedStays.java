package com.example.travelsocialservice.client;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** booking-service is the only authority on who completed which stay. */
public interface CompletedStays {

    record CompletedStay(UUID userId, UUID hotelId, LocalDate checkInDate, LocalDate checkOutDate) {
    }

    class StayLookupUnavailableException extends RuntimeException {
        public StayLookupUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Empty when the booking does not exist or is not CHECKED_OUT.
     *
     * @throws StayLookupUnavailableException when booking-service cannot answer, so callers never
     *                                        mistake an outage for "no such stay"
     */
    Optional<CompletedStay> find(UUID bookingId);
}
