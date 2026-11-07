package com.example.userservice.security;
public class SessionStoreUnavailableException extends IllegalStateException {
    public SessionStoreUnavailableException() { super("Session verification storage is unavailable"); }
}
