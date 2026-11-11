package com.example.hotelservice.Review.service;

/** A duplicate review or owner reply is a resource conflict, not invalid input. */
public class ReviewConflictException extends IllegalStateException {
    public ReviewConflictException() {
        super("Review or response already exists");
    }
}
