package com.example.bookingservice.grpc.interceptor;

import com.example.bookingservice.security.JwtService;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** gRPC has its own context; HTTP SecurityContext and x-user-id are not credentials. */
@GrpcGlobalServerInterceptor
public class UserContextInterceptor implements ServerInterceptor {
    public static final Context.Key<Authentication> ACTOR = Context.key("verifiedActor");
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private final JwtService jwtService;

    public UserContextInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String service = call.getMethodDescriptor().getServiceName();
        if (!"booking.BookingGrpcService".equals(service) && !"payment.PaymentGrpcService".equals(service)) {
            return next.startCall(call, headers);
        }
        Authentication actor = authenticate(headers.get(AUTHORIZATION));
        if (actor == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid bearer token required"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(ACTOR, actor), call, headers, next);
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        try {
            if (!jwtService.validateToken(token)) return null;
            String role = jwtService.extractRole(token);
            if (role == null || role.isBlank()) return null;
            return new UsernamePasswordAuthenticationToken(jwtService.extractUserId(token), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
