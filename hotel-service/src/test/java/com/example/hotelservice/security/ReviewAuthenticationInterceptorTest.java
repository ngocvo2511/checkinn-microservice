package com.example.hotelservice.security;

import com.example.hotelservice.grpc.interceptor.ReviewAuthenticationInterceptor;
import io.grpc.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewAuthenticationInterceptorTest {
    final JwtDecoder decoder = mock(JwtDecoder.class);
    final TokenRevocationService revocation = mock(TokenRevocationService.class);
    final ReviewAuthenticationInterceptor interceptor = new ReviewAuthenticationInterceptor(decoder, revocation);
    final ServerCall<String, String> call = mock(ServerCall.class);
    final ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
    final Metadata headers = new Metadata();

    void method(String name) {
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("review.ReviewGrpcService/" + name)
                .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class)).build());
    }
    void bearer(String token) { headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token); }
    void denied() {
        interceptor.interceptCall(call, headers, next);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }
    @Test void forgedUserMetadataDoesNotAuthenticate() {
        method("DeleteReview");
        headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        denied();
    }
    @Test void revokedBearerDoesNotReachService() {
        method("CreateReview"); bearer("revoked"); when(revocation.isRevoked("revoked")).thenReturn(true);
        when(decoder.decode("revoked")).thenReturn(Jwt.withTokenValue("revoked").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).build());
        denied(); verify(decoder).decode("revoked");
    }
    @Test void invalidSignatureDoesNotReachServiceOrSessionStore() {
        method("AddReviewResponse"); bearer("invalid"); when(decoder.decode("invalid")).thenThrow(new JwtException("Invalid"));
        denied(); verifyNoInteractions(revocation);
    }
    @Test void sessionOutageIsUnavailableRatherThanInvalidIdentity() {
        method("DeleteReview"); bearer("valid");
        when(decoder.decode("valid")).thenReturn(Jwt.withTokenValue("valid").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).build());
        when(revocation.isRevoked("valid")).thenThrow(new SessionStoreUnavailableException());
        interceptor.interceptCall(call, headers, next);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAVAILABLE), any());
        verifyNoInteractions(next);
    }
    @Test void publicReadRemainsAnonymous() {
        method("GetHotelReviews"); when(next.startCall(any(), any())).thenReturn(new ServerCall.Listener<>() {});
        interceptor.interceptCall(call, headers, next);
        verify(next).startCall(call, headers); verify(call, never()).close(any(), any());
    }
    @Test void verifiedIdentityPropagatesWithoutLeakingContext() {
        method("DeleteReview"); bearer("valid");
        UUID subject = UUID.randomUUID();
        when(decoder.decode("valid")).thenReturn(Jwt.withTokenValue("valid").header("alg", "HS256")
                .subject(subject.toString()).claim("role", "CUSTOMER").build());
        when(next.startCall(any(), any())).thenAnswer(invocation -> {
            assertThat(ReviewAuthenticationInterceptor.actorId()).isEqualTo(subject);
            return new ServerCall.Listener<String>() {
                @Override public void onHalfClose() { assertThat(ReviewAuthenticationInterceptor.actorId()).isEqualTo(subject); }
            };
        });
        interceptor.interceptCall(call, headers, next).onHalfClose();
        assertThat(ReviewAuthenticationInterceptor.ACTOR.get()).isNull();
        verify(call, never()).close(any(), any());
    }
}
