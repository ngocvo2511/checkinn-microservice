package com.example.userservice.controller;

import com.example.userservice.dto.PublicProfileResponse;
import com.example.userservice.dto.PublicProfilesRequest;
import com.example.userservice.security.JwtService;
import com.example.userservice.service.PublicProfileService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service-only lookups. Not routed by the API Gateway; reachable only through Eureka.
 * Only the travel-social-service identity may call it.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    static final int MAX_IDS = 100;

    private final JwtService jwtService;
    private final PublicProfileService publicProfileService;

    @PostMapping("/public-profiles")
    public ResponseEntity<List<PublicProfileResponse>> publicProfiles(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PublicProfilesRequest request) {
        requireTravelSocialService(authorization);
        if (request == null || request.ids() == null || request.ids().isEmpty() || request.ids().contains(null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must contain 1-" + MAX_IDS + " user ids");
        }
        Set<UUID> ids = new LinkedHashSet<>(request.ids());
        if (ids.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must contain 1-" + MAX_IDS + " user ids");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(publicProfileService.findPublicProfiles(ids));
    }

    private void requireTravelSocialService(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required");
        }
        Claims actor;
        try {
            actor = jwtService.parseToken(authorization.substring(7)).getBody();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }
        if (!"travel-social-service".equals(actor.getSubject())
                || !"TRAVEL_SOCIAL_SERVICE".equals(actor.get("role", String.class))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Travel social service identity required");
        }
    }
}
