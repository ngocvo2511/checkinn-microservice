package com.example.userservice.exception;

/**
 * A deliberate, user-facing business rejection (duplicate account, wrong password, locked account, ...).
 * Its message is written for end users and is the only exception text that may leave the service
 * (REST body or gRPC description). Any other exception is reported with a generic message (F-9F-1).
 */
public class UserBusinessException extends RuntimeException {
    public UserBusinessException(String message) { super(message); }

    /** Message safe to return to a client: the business message, otherwise {@code fallback}. */
    public static String publicMessage(Throwable failure, String fallback) {
        return failure instanceof UserBusinessException business && business.getMessage() != null ? business.getMessage() : fallback;
    }
}
