package com.example.travelsocialservice.web;

import com.example.travelsocialservice.config.SecurityConfig;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.security.TokenRevocationService;
import com.example.travelsocialservice.service.CommentService;
import com.example.travelsocialservice.service.FeedService;
import com.example.travelsocialservice.service.LikeService;
import com.example.travelsocialservice.service.MediaService;
import com.example.travelsocialservice.service.ModerationService;
import com.example.travelsocialservice.service.PostAssembler;
import com.example.travelsocialservice.service.PostService;
import com.example.travelsocialservice.service.ReportService;
import com.example.travelsocialservice.web.dto.PageResponse;
import com.example.travelsocialservice.web.dto.PostDtos.PostRequest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PostController.class, MediaReportController.class, AdminModerationController.class},
        properties = {"jwt.secret=" + SocialApiSecurityTest.SECRET, "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false", "spring.boot.admin.client.enabled=false"})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class SocialApiSecurityTest {

    static final String SECRET = "test-only-secret-key-with-at-least-32-bytes!!";
    static final UUID USER = UUID.randomUUID();
    static final UUID ADMIN = UUID.randomUUID();
    static final UUID POST = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockBean TokenRevocationService revocations;
    @MockBean PostService postService;
    @MockBean FeedService feedService;
    @MockBean LikeService likeService;
    @MockBean CommentService commentService;
    @MockBean PostAssembler assembler;
    @MockBean MediaService mediaService;
    @MockBean ReportService reportService;
    @MockBean ModerationService moderationService;

    static String token(String secret, String subject, String role, long expiresInSeconds) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(subject).claim("role", role)
                .expirationTime(Date.from(Instant.now().plusSeconds(expiresInSeconds))).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String subject, String role) throws Exception {
        return request.header("Authorization", "Bearer " + token(SECRET, subject, role, 300));
    }

    static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) throws Exception {
        return as(request, USER.toString(), "CUSTOMER");
    }

    static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return as(request, ADMIN.toString(), "ADMIN");
    }

    @BeforeEach
    void stubs() {
        when(feedService.recent(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(), null));
        Post post = new Post();
        post.setId(POST);
        post.setAuthorId(USER);
        post.setStatus(ContentStatus.PUBLISHED);
        when(postService.create(any(), any())).thenReturn(post);
    }

    @Test
    void anonymousVisitorsCanReadPublicContent() throws Exception {
        mvc.perform(get("/api/social/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/social/posts/" + POST)).andExpect(status().isOk());
        mvc.perform(get("/api/social/posts/" + POST + "/comments")).andExpect(status().isOk());
        mvc.perform(get("/api/social/posts/search").param("q", "beach")).andExpect(status().isOk());
        mvc.perform(get("/api/social/places/" + UUID.randomUUID() + "/rating")).andExpect(status().isOk());
        verify(feedService).recent(isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void anonymousMutationsAreUnauthorizedWithTheStandardErrorBody() throws Exception {
        List<MockHttpServletRequestBuilder> mutations = List.of(
                post("/api/social/posts").contentType("application/json").content("{\"body\":\"x\"}"),
                put("/api/social/posts/" + POST).contentType("application/json").content("{\"body\":\"x\"}"),
                delete("/api/social/posts/" + POST),
                put("/api/social/posts/" + POST + "/like"),
                delete("/api/social/posts/" + POST + "/like"),
                post("/api/social/posts/" + POST + "/comments").contentType("application/json").content("{\"body\":\"x\"}"),
                delete("/api/social/comments/" + UUID.randomUUID()),
                post("/api/social/reports").contentType("application/json").content("{}"),
                multipart("/api/social/media").file(new MockMultipartFile("file", new byte[]{1})),
                get("/api/social/me/posts"),
                get("/api/social/me/posts/" + POST),
                get("/api/social/admin/reports"));
        for (MockHttpServletRequestBuilder mutation : mutations) {
            mvc.perform(mutation).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
        verifyNoInteractions(postService, likeService, commentService, reportService, mediaService, moderationService);
    }

    @Test
    void forgedExpiredAndRevokedTokensAreUnauthorized() throws Exception {
        String forged = token("another-secret-key-that-is-also-32-bytes-long", USER.toString(), "ADMIN", 300);
        String expired = token(SECRET, USER.toString(), "CUSTOMER", -300);
        String revoked = token(SECRET, USER.toString(), "CUSTOMER", 300);
        when(revocations.isRevoked(revoked)).thenReturn(true);

        for (String bearer : List.of(forged, expired, revoked, "not-a-jwt")) {
            mvc.perform(put("/api/social/posts/" + POST + "/like").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(likeService);
        // Tokens that fail signature or expiry checks are rejected before the revocation store is consulted.
        verify(revocations, never()).isRevoked(forged);
        verify(revocations, never()).isRevoked(expired);
        verify(revocations, never()).isRevoked("not-a-jwt");
    }

    @Test
    void anUnavailableRevocationStoreIsUnauthorizedNotAServerError() throws Exception {
        when(revocations.isRevoked(anyString())).thenThrow(new IllegalStateException("Token revocation storage is unavailable"));

        mvc.perform(asUser(get("/api/social/posts")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(asUser(put("/api/social/posts/" + POST + "/like"))).andExpect(status().isUnauthorized());
        // The anonymous retry the frontend makes still works.
        mvc.perform(get("/api/social/posts")).andExpect(status().isOk());
        verifyNoInteractions(likeService);
    }

    @Test
    void authorIdentityComesFromTheTokenNotFromTheBodyOrHeaders() throws Exception {
        UUID victim = UUID.randomUUID();
        String body = "{\"body\":\"Hello\",\"authorId\":\"" + victim + "\",\"userId\":\"" + victim + "\",\"status\":\"HIDDEN\"}";

        mvc.perform(asUser(post("/api/social/posts")).header("x-user-id", victim.toString()).header("x-user-role", "ADMIN")
                .contentType("application/json").content(body)).andExpect(status().isCreated());

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<PostRequest> request = ArgumentCaptor.forClass(PostRequest.class);
        verify(postService).create(actor.capture(), request.capture());
        assertThat(actor.getValue()).isEqualTo(new Actor(USER, false));
        assertThat(request.getValue().body()).isEqualTo("Hello");
    }

    @Test
    void moderationIsAdminOnly() throws Exception {
        List<MockHttpServletRequestBuilder> adminRoutes = List.of(
                get("/api/social/admin/reports"),
                post("/api/social/admin/posts/" + POST + "/hide"),
                post("/api/social/admin/posts/" + POST + "/restore"),
                post("/api/social/admin/comments/" + POST + "/hide"),
                post("/api/social/admin/comments/" + POST + "/restore"),
                post("/api/social/admin/reports/close").contentType("application/json").content("{}"));
        for (MockHttpServletRequestBuilder route : adminRoutes) {
            mvc.perform(asUser(route)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
        verifyNoInteractions(moderationService);

        mvc.perform(asAdmin(post("/api/social/admin/posts/" + POST + "/hide").contentType("application/json")
                .content("{\"reason\":\"spam\"}"))).andExpect(status().isOk());
        verify(moderationService).hidePost(new Actor(ADMIN, true), POST, "spam");
        mvc.perform(asAdmin(get("/api/social/admin/reports"))).andExpect(status().isOk());
    }

    @Test
    void aForgedAdminRoleHeaderDoesNotGrantModeration() throws Exception {
        mvc.perform(asUser(post("/api/social/admin/posts/" + POST + "/hide")).header("x-user-role", "ADMIN").header("role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderationService);
    }

    @Test
    void serviceTokensAreNotUsers() throws Exception {
        mvc.perform(as(put("/api/social/posts/" + POST + "/like"), "booking-service", "BOOKING_SERVICE"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/social/admin/posts/" + POST + "/hide"), "booking-service", "BOOKING_SERVICE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(likeService, moderationService);
    }

    @Test
    void signedInReadsPassTheViewerForLikeState() throws Exception {
        mvc.perform(asUser(get("/api/social/posts"))).andExpect(status().isOk());
        verify(feedService).recent(isNull(), isNull(), isNull(), eq(new Actor(USER, false)));
    }

    @Test
    void serviceErrorsUseTheStandardErrorBody() throws Exception {
        when(postService.requireVisible(any(), eq(POST))).thenThrow(ApiException.notFound());
        when(postService.create(any(), any())).thenThrow(ApiException.invalid("body", "body must be 1-5000 characters"));

        mvc.perform(get("/api/social/posts/" + POST)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND")).andExpect(jsonPath("$.fields").doesNotExist());
        mvc.perform(asUser(post("/api/social/posts")).contentType("application/json").content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.body").exists());
        mvc.perform(asUser(post("/api/social/posts")).contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/social/posts/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(asUser(post("/api/social/reports")).contentType("application/json")
                .content("{\"targetType\":\"HOTEL\",\"targetId\":\"" + POST + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadPassesTheFileBytesForTheSignedInUser() throws Exception {
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        mvc.perform(asUser(multipart("/api/social/media").file(new MockMultipartFile("file", "x.jpg", "image/jpeg", bytes))))
                .andExpect(status().isCreated());
        verify(mediaService).upload(new Actor(USER, false), bytes);
    }
}
