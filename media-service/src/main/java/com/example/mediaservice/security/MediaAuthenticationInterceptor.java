package com.example.mediaservice.security;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@GrpcGlobalServerInterceptor
public class MediaAuthenticationInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private final JwtDecoder decoder;
    public MediaAuthenticationInterceptor(JwtDecoder decoder) { this.decoder = decoder; }
    @Override public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT,RespT> call, Metadata headers, ServerCallHandler<ReqT,RespT> next) {
        MethodDescriptor<ReqT,RespT> method = call.getMethodDescriptor();
        if (!"media.MediaGrpcService".equals(method.getServiceName())) return next.startCall(call,headers);
        String header = headers.get(AUTHORIZATION);
        MediaCaller caller;
        try {
            if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException();
            var jwt = decoder.decode(header.substring(7));
            caller = MediaCaller.fromClaims(jwt.getSubject(), jwt.getClaimAsString("role"));
        } catch (RuntimeException ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid service bearer required"),new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (caller == null || !caller.mayCall(method.getBareMethodName())) {
            call.close(Status.PERMISSION_DENIED.withDescription("Service identity not allowed for this operation"),new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(MediaCaller.CONTEXT_KEY, caller), call, headers, next);
    }
}
