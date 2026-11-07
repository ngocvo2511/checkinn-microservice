package com.example.userservice.security;

import com.checkinn.user.grpc.*;
import com.example.userservice.model.*;
import com.example.userservice.service.*;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRpcAccessTest {
    private final String secret="unit-only-user-rpc-signing-key-not-production-01234567890123456789";
    private final UserService users=mock(UserService.class);
    private final TokenRevocationService revoked=mock(TokenRevocationService.class);
    private final UUID id=UUID.randomUUID();
    private Server server;private ManagedChannel channel;private UserServiceGrpc.UserServiceBlockingStub stub;
    @BeforeEach void start() throws Exception {
        var profile=new UserProfile();profile.setFullName("Synthetic RPC fixture");
        when(users.getUserById(id)).thenReturn(User.builder().id(id).username("synthetic-user").email("synthetic@example.invalid").role(Role.CUSTOMER).profile(profile).build());
        server=ServerBuilder.forPort(0).addService(ServerInterceptors.intercept(new UserGrpcService(users),new UserRpcAccess(new JwtService(revoked,secret)))).build().start();
        channel=ManagedChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().build();
        stub=UserServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5,TimeUnit.SECONDS);
    }
    @AfterEach void stop() throws Exception {channel.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);server.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);}
    private String token(String subject,String role) {
        return Jwts.builder().setSubject(subject).claim("role",role).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis()+300000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),SignatureAlgorithm.HS256).compact();
    }
    private UserServiceGrpc.UserServiceBlockingStub as(String subject,String role) {
        Metadata headers=new Metadata();headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token(subject,role));
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
    @Test void anonymousAndForgedIdentityCannotResetOrReadAccount() {
        var request=ResetPasswordRequest.newBuilder().setEmail("synthetic@example.invalid").setNewPassword("synthetic-password").build();
        assertEquals(Status.Code.UNAUTHENTICATED,assertThrows(StatusRuntimeException.class,()->stub.resetPassword(request)).getStatus().getCode());
        Metadata forged=new Metadata();forged.put(Metadata.Key.of("x-user-id",Metadata.ASCII_STRING_MARSHALLER),id.toString());
        var pretend=stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(forged));
        assertEquals(Status.Code.UNAUTHENTICATED,assertThrows(StatusRuntimeException.class,()->pretend.getUserById(GetUserRequest.newBuilder().setId(id.toString()).build())).getStatus().getCode());
        verifyNoInteractions(users);
    }
    @Test void endUserAdminAndWrongServiceCannotBypassOtpThroughResetRpc() {
        var request=ResetPasswordRequest.newBuilder().setEmail("synthetic@example.invalid").setNewPassword("synthetic-password").build();
        for(String role:List.of("CUSTOMER","OWNER","ADMIN","AUTH_SERVICE")) {
            var caller=as(id.toString(),role);
            assertEquals(Status.Code.PERMISSION_DENIED,assertThrows(StatusRuntimeException.class,()->caller.resetPassword(request)).getStatus().getCode());
        }
        var hotel=as("hotel-service","HOTEL_SERVICE");
        assertEquals(Status.Code.PERMISSION_DENIED,assertThrows(StatusRuntimeException.class,()->hotel.resetPassword(request)).getStatus().getCode());
        verifyNoInteractions(users);
    }
    @Test void verifiedAuthServiceCanResetAndOtherMethodsRequireSameIdentity() {
        as("auth-service","AUTH_SERVICE").resetPassword(ResetPasswordRequest.newBuilder().setEmail("synthetic@example.invalid").setNewPassword("synthetic-password").build());
        verify(users).resetPassword("synthetic@example.invalid","synthetic-password");
        var caller=as(id.toString(),"CUSTOMER");
        assertEquals(Status.Code.PERMISSION_DENIED,assertThrows(StatusRuntimeException.class,()->caller.loginUser(LoginRequest.newBuilder().build())).getStatus().getCode());
        assertEquals(Status.Code.PERMISSION_DENIED,assertThrows(StatusRuntimeException.class,()->caller.registerUser(RegisterRequest.newBuilder().build())).getStatus().getCode());
    }
    @Test void profileReadAllowsSelfAdminAndExactHotelServiceButDeniesForeignUser() {
        var request=GetUserRequest.newBuilder().setId(id.toString()).build();
        assertEquals(id.toString(),as(id.toString(),"CUSTOMER").getUserById(request).getId());
        assertEquals(id.toString(),as(UUID.randomUUID().toString(),"ADMIN").getUserById(request).getId());
        assertEquals(id.toString(),as("hotel-service","HOTEL_SERVICE").getUserById(request).getId());
        var other=as(UUID.randomUUID().toString(),"CUSTOMER");
        assertEquals(Status.Code.PERMISSION_DENIED,assertThrows(StatusRuntimeException.class,()->other.getUserById(request)).getStatus().getCode());
        verify(users,times(3)).getUserById(id);
    }
    @Test void revokedServiceTokenNeverReachesPasswordMutation() {
        when(revoked.isRevoked(anyString())).thenReturn(true);
        var caller=as("auth-service","AUTH_SERVICE");
        assertEquals(Status.Code.UNAUTHENTICATED,assertThrows(StatusRuntimeException.class,()->caller.resetPassword(ResetPasswordRequest.newBuilder().build())).getStatus().getCode());
        verifyNoInteractions(users);
    }
}
