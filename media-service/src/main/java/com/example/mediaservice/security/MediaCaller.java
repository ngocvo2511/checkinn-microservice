package com.example.mediaservice.security;

import io.grpc.Context;

import java.util.Set;

/** Verified service identities allowed to call media-service, and the RPCs each may use. */
public enum MediaCaller {
    HOTEL_SERVICE("hotel-service", "HOTEL_SERVICE", null),
    TRAVEL_SOCIAL_SERVICE("travel-social-service", "TRAVEL_SOCIAL_SERVICE", Set.of("UploadMedia", "DeleteMedia"));

    /** Set by {@link MediaAuthenticationInterceptor} for every authorized call. */
    public static final Context.Key<MediaCaller> CONTEXT_KEY = Context.key("media-caller");

    /** Storage folder for objects uploaded by travel-social-service. */
    public static final String SOCIAL_PREFIX = "social/";

    private final String subject;
    private final String role;
    private final Set<String> allowedMethods; // null = every MediaGrpcService method

    MediaCaller(String subject, String role, Set<String> allowedMethods) {
        this.subject = subject;
        this.role = role;
        this.allowedMethods = allowedMethods;
    }

    public static MediaCaller fromClaims(String subject, String role) {
        for (MediaCaller caller : values()) {
            if (caller.subject.equals(subject) && caller.role.equals(role)) {
                return caller;
            }
        }
        return null;
    }

    public boolean mayCall(String bareMethodName) {
        return allowedMethods == null || allowedMethods.contains(bareMethodName);
    }

    /** The caller of the current gRPC call; null outside an authorized call. */
    public static MediaCaller current() {
        return CONTEXT_KEY.get();
    }
}
