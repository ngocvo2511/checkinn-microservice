package com.example.travelsocialservice.security;

import com.example.travelsocialservice.web.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The signed-in user, taken only from the verified token. */
public record Actor(UUID id, boolean admin) {

    /** Null for anonymous callers and for service tokens, whose subject is not a user id. */
    public static Actor optional(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return null;
        }
        try {
            return new Actor(UUID.fromString(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
        } catch (IllegalArgumentException notAUserSubject) {
            return null;
        }
    }

    public static Actor require(Jwt jwt) {
        Actor actor = optional(jwt);
        if (actor == null) {
            throw jwt == null ? ApiException.unauthorized() : ApiException.forbidden();
        }
        return actor;
    }

    public boolean is(UUID userId) {
        return id.equals(userId);
    }
}
