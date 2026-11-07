package com.example.userservice.security;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import java.util.Set;
import java.util.UUID;

/** User RPC is a separate security boundary; HTTP OTP/controllers do not protect it. */
@GrpcGlobalServerInterceptor
public class UserRpcAccess implements ServerInterceptor {
    public record Actor(String subject,String role) {}
    public static final Context.Key<Actor> ACTOR=Context.key("verifiedUserRpcActor");
    private static final Metadata.Key<String> AUTH=Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER);
    private final JwtService jwt;
    public UserRpcAccess(JwtService jwt) { this.jwt=jwt; }
    private static boolean service(Actor actor,String subject,String role) {
        return actor!=null && subject.equals(actor.subject()) && role.equals(actor.role());
    }
    public static boolean canRead(String userId) {
        Actor actor=ACTOR.get();
        if(actor==null) return false;
        // Existing hotel admin/review enrichment is a trusted, read-only service call.
        if(service(actor,"hotel-service","HOTEL_SERVICE") || "ADMIN".equals(actor.role())) return true;
        if(!Set.of("CUSTOMER","OWNER").contains(actor.role())) return false;
        try { return UUID.fromString(userId).equals(UUID.fromString(actor.subject())); }
        catch(IllegalArgumentException ex) { return false; }
    }
    @Override public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT,RespT> call,Metadata headers,ServerCallHandler<ReqT,RespT> next) {
        if(!"UserService".equals(call.getMethodDescriptor().getServiceName())) return next.startCall(call,headers);
        Actor actor=null;String header=headers.get(AUTH);
        if(header!=null && header.startsWith("Bearer ")) {
            try {
                var claims=jwt.parseToken(header.substring(7)).getBody();
                String subject=claims.getSubject(),role=claims.get("role",String.class);
                if(subject!=null && !subject.isBlank() && role!=null && !role.isBlank()) actor=new Actor(subject,role);
            } catch(SessionStoreUnavailableException ex) {
                call.close(Status.UNAVAILABLE.withDescription("Session verification unavailable"),new Metadata());return new ServerCall.Listener<>(){};
            } catch(RuntimeException ex) { /* Reject unverifiable/revoked identity; never log credentials. */ }
        }
        if(actor==null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid bearer token required"),new Metadata());return new ServerCall.Listener<>(){};
        }
        String method=call.getMethodDescriptor().getBareMethodName();
        boolean allowed=switch(method) {
            case "RegisterUser","LoginUser","ResetPassword" -> service(actor,"auth-service","AUTH_SERVICE");
            case "GetUserById" -> service(actor,"hotel-service","HOTEL_SERVICE") || Set.of("ADMIN","CUSTOMER","OWNER").contains(actor.role());
            default -> false;
        };
        if(!allowed) {
            call.close(Status.PERMISSION_DENIED.withDescription("RPC not permitted for this identity"),new Metadata());return new ServerCall.Listener<>(){};
        }
        return Contexts.interceptCall(Context.current().withValue(ACTOR,actor),call,headers,next);
    }
}
