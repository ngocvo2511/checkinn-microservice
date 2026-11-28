package com.example.travelsocialservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Only the trigger fields of booking-service's event. The owner, hotel and dates are read from
 * booking-service, never trusted from the payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingStatusEvent(String bookingId, String bookingStatus, String eventId) {
}
