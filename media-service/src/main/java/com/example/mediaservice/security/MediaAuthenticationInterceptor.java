package com.example.mediaservice.security;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Storage operations, including private signed URLs, are hotel-service only. */
@GrpcGlobalServerInterceptor
public class MediaAuthenticationInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private final JwtDecoder decoder;
    public MediaAuthenticationInterceptor(JwtDecoder decoder) { this.decoder = decoder; }
    @Override public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT,RespT> call, Metadata headers, ServerCallHandler<ReqT,RespT> next) {
        if (!"media.MediaGrpcService".equals(call.getMethodDescriptor().getServiceName())) return next.startCall(call,headers);
        String header = headers.get(AUTHORIZATION);
        try {
            if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException();
            var jwt = decoder.decode(header.substring(7));
            if (!"hotel-service".equals(jwt.getSubject()) || !"HOTEL_SERVICE".equals(jwt.getClaimAsString("role"))) {
                call.close(Status.PERMISSION_DENIED.withDescription("Hotel service identity required"),new Metadata());
                return new ServerCall.Listener<>() {};
            }
        } catch (RuntimeException ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid service bearer required"),new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call,headers);
    }
}
