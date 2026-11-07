package com.example.authservice.service;

public class SessionStoreUnavailableException extends RuntimeException {
    public SessionStoreUnavailableException() {
        super("Session revocation store is temporarily unavailable");
    }
}
