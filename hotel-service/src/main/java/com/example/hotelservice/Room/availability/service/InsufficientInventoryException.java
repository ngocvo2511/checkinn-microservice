package com.example.hotelservice.Room.availability.service;

/** Known stock conflict, distinct from malformed input and an unavailable dependency. */
public class InsufficientInventoryException extends IllegalArgumentException {
    public InsufficientInventoryException(java.time.LocalDate date) { super("Not enough rooms on date: " + date); }
}
