package com.example.hotelservice.grpc.interceptor;

import com.example.hotelservice.security.TokenRevocationService;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.util.Set;
import java.util.UUID;

/** Review identity is verified independently of legacy x-user-id metadata. */
@GrpcGlobalServerInterceptor
public class ReviewAuthenticationInterceptor implements ServerInterceptor {
    public static final Context.Key<Jwt> ACTOR = Context.key("verifiedReviewActor");
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Set<String> PUBLIC_READS = Set.of("GetHotelReviews", "GetReviewById", "GetReviewStats", "GetReviewResponse");
    private final JwtDecoder decoder;
    private final TokenRevocationService revocation;

    public ReviewAuthenticationInterceptor(JwtDecoder decoder, TokenRevocationService revocation) {
        this.decoder = decoder;
        this.revocation = revocation;
    }

    @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!"review.ReviewGrpcService".equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        String bearer = headers.get(AUTHORIZATION);
        Jwt actor = null;
        try {
            if (bearer != null) {
                if (!bearer.startsWith("Bearer ")) throw new IllegalArgumentException();
                String token = bearer.substring(7);
                if (revocation.isRevoked(token)) throw new IllegalArgumentException();
                actor = decoder.decode(token);
                UUID.fromString(actor.getSubject());
            }
            if (actor == null && !PUBLIC_READS.contains(call.getMethodDescriptor().getBareMethodName())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid bearer token required"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(ACTOR, actor), call, headers, next);
    }

    public static Jwt requireActor() {
        Jwt actor = ACTOR.get();
        if (actor == null) throw Status.UNAUTHENTICATED.withDescription("Valid bearer token required").asRuntimeException();
        return actor;
    }
    public static UUID actorId() {
        return UUID.fromString(requireActor().getSubject());
    }
}
