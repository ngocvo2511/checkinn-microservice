package com.example.hotelservice.security;

import com.example.hotelservice.Review.controller.ReviewController;
import com.example.hotelservice.Review.service.ReviewService;
import com.example.hotelservice.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ReviewController.class, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import(SecurityConfig.class)
class ReviewAuthorizationTest {
    private static final UUID ACTOR = UUID.randomUUID(), OTHER = UUID.randomUUID(), REVIEW = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockBean ReviewService reviews;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;

    @BeforeEach void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> Jwt.withTokenValue(call.getArgument(0))
                .header("alg", "HS256").subject(ACTOR.toString())
                .claim("role", "admin".equals(call.getArgument(0)) ? "ADMIN" : "CUSTOMER").build());
    }
    @Test void anonymousCannotDeleteEvenWithForgedHeaders() throws Exception {
        mvc.perform(delete("/api/v1/reviews/" + REVIEW).header("X-User-Id", OTHER)
                .header("X-Is-Admin", "true")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews);
    }
    @Test void deleteUsesVerifiedSubjectAndRole() throws Exception {
        mvc.perform(delete("/api/v1/reviews/" + REVIEW).header("Authorization", "Bearer customer")
                .header("X-User-Id", OTHER).header("X-Is-Admin", "true")).andExpect(status().isNoContent());
        verify(reviews).deleteReview(REVIEW, ACTOR, false);
    }
    @Test void adminCanDeleteWithoutIdentityHeader() throws Exception {
        mvc.perform(delete("/api/v1/reviews/" + REVIEW).header("Authorization", "Bearer admin"))
                .andExpect(status().isNoContent());
        verify(reviews).deleteReview(REVIEW, ACTOR, true);
    }
    @Test void anonymousCannotVoteAsAnotherUser() throws Exception {
        mvc.perform(post("/api/v1/reviews/" + REVIEW + "/helpful").header("X-User-Id", OTHER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews);
    }
    @Test void voteUsesVerifiedSubject() throws Exception {
        mvc.perform(post("/api/v1/reviews/" + REVIEW + "/helpful").header("Authorization", "Bearer customer")
                .header("X-User-Id", OTHER)).andExpect(status().isOk());
        verify(reviews).markReviewHelpful(REVIEW, ACTOR);
    }
    @Test void publicReadIgnoresForgedPersonalizationHeader() throws Exception {
        mvc.perform(get("/api/v1/reviews/" + REVIEW).header("X-User-Id", OTHER)).andExpect(status().isOk());
        verify(reviews).getReviewById(REVIEW, null);
    }
    @Test void bookingReviewLookupUsesVerifiedSubjectInsteadOfQuery() throws Exception {
        when(reviews.getReviewByBookingId(any(), any())).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/v1/reviews/check-booking/" + REVIEW).param("userId", OTHER.toString())
                .header("Authorization", "Bearer customer")).andExpect(status().isNotFound());
        verify(reviews).getReviewByBookingId(REVIEW, ACTOR);
    }
    @Test void anonymousOwnerListIsProtected() throws Exception {
        mvc.perform(get("/api/v1/reviews/owner/all").header("X-User-Id", OTHER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviews);
    }
}
