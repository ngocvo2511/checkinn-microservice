package com.example.userservice.security;

import com.example.userservice.config.SecurityConfig;
import com.example.userservice.controller.InternalUserController;
import com.example.userservice.dto.PublicProfileResponse;
import com.example.userservice.service.PublicProfileService;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = InternalUserController.class, properties = {"spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false"})
@Import({SecurityConfig.class, JwtMdcFilter.class})
class PublicProfileAuthorizationTest {

    static final UUID USER = UUID.randomUUID();
    static final String PATH = "/internal/users/public-profiles";

    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean PublicProfileService profiles;

    /** token value → (subject, role) */
    static final Map<String, String[]> TOKENS = Map.of(
            "social", new String[]{"travel-social-service", "TRAVEL_SOCIAL_SERVICE"},
            "forged-role", new String[]{UUID.randomUUID().toString(), "TRAVEL_SOCIAL_SERVICE"},
            "forged-subject", new String[]{"travel-social-service", "CUSTOMER"},
            "booking", new String[]{"booking-service", "BOOKING_SERVICE"},
            "admin", new String[]{UUID.randomUUID().toString(), "ADMIN"},
            "customer", new String[]{UUID.randomUUID().toString(), "CUSTOMER"});

    @BeforeEach
    void tokens() {
        when(jwt.parseToken(anyString())).thenAnswer(call -> {
            String[] identity = TOKENS.get(call.<String>getArgument(0));
            if (identity == null) throw new JwtException("invalid");
            Claims claims = Jwts.claims().setSubject(identity[0]);
            claims.put("role", identity[1]);
            Jws<Claims> signed = mock(Jws.class);
            when(signed.getBody()).thenReturn(claims);
            return signed;
        });
        when(jwt.extractUserId(anyString())).thenThrow(new JwtException("not a user token"));
    }

    private static String body(Collection<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "{\"ids\":[", "]}"));
    }

    @Test
    void anonymousAndInvalidTokensAreUnauthorized() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body(List.of(USER))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType("application/json").content(body(List.of(USER)))
                .header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void otherIdentitiesAreForbidden() throws Exception {
        for (String token : List.of("forged-role", "forged-subject", "booking", "admin", "customer")) {
            mvc.perform(post(PATH).contentType("application/json").content(body(List.of(USER)))
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(profiles);
    }

    @Test
    void travelSocialServiceGetsOnlyPublicFields() throws Exception {
        when(profiles.findPublicProfiles(any())).thenReturn(List.of(new PublicProfileResponse(USER, "An N.", null)));

        mvc.perform(post(PATH).contentType("application/json").content(body(List.of(USER, USER)))
                        .header("Authorization", "Bearer social"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(USER.toString()))
                .andExpect(jsonPath("$[0].displayName").value("An N."))
                .andExpect(jsonPath("$[0].avatarUrl").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].username").doesNotExist());

        verify(profiles).findPublicProfiles(Set.of(USER));
    }

    @Test
    void rejectsEmptyOrOversizedBatches() throws Exception {
        List<UUID> tooMany = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList();
        for (String content : List.of("{\"ids\":[]}", "{}", "{\"ids\":[null]}", body(tooMany))) {
            mvc.perform(post(PATH).contentType("application/json").content(content)
                    .header("Authorization", "Bearer social")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(profiles);
    }
}
