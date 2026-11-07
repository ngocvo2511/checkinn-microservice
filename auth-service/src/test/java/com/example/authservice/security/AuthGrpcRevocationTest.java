package com.example.authservice.security;

import com.checkinn.auth.grpc.ValidateTokenRequest;
import com.checkinn.auth.grpc.ValidateTokenResponse;
import com.example.authservice.service.AuthGrpcService;
import com.example.authservice.service.JwtService;
import com.example.authservice.service.TokenRevocationService;
import com.example.authservice.service.UserGrpcClient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthGrpcRevocationTest {
    final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    final ValueOperations<String,String> values=mock(ValueOperations.class);
    final Map<String,String> store=new HashMap<>();
    final UUID user=UUID.randomUUID();
    final JwtService jwt=new JwtService();
    final TokenRevocationService revocations=new TokenRevocationService(redis);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(jwt,"secret","unit-only-auth-validation-signing-key-not-production-0123456789");
        ReflectionTestUtils.invokeMethod(jwt,"init");
        ReflectionTestUtils.setField(revocations,"keyPrefix","auth:revoked-token:");
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenAnswer(call->store.containsKey(call.getArgument(0)));
        when(values.get(anyString())).thenAnswer(call->store.get(call.getArgument(0)));
        doAnswer(call->{store.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
    }
    String key(){return "auth:session-generation:"+user;}
    String legacy(boolean expiry) {
        var builder=io.jsonwebtoken.Jwts.builder().setSubject(user.toString()).claim("role","CUSTOMER");
        if(expiry)builder.setExpiration(new Date(System.currentTimeMillis()+300000));
        return builder.signWith((Key)ReflectionTestUtils.getField(jwt,"key"),io.jsonwebtoken.SignatureAlgorithm.HS256).compact();
    }
    Recorder validate(String token) {
        var result=new Recorder();
        new AuthGrpcService(mock(UserGrpcClient.class),jwt,revocations).validateToken(ValidateTokenRequest.newBuilder().setToken(token).build(),result);
        return result;
    }
    void rejected(String token,Status.Code code) {
        var result=validate(token);assertThat(result.error).isNotNull();
        assertThat(Status.fromThrowable(result.error).getCode()).isEqualTo(code);assertThat(result.value).isNull();assertThat(result.complete).isFalse();
    }
    @Test void legacySessionIsAcceptedBeforeFirstRotation() {
        var result=validate(legacy(true));assertThat(result.error).isNull();assertThat(result.complete).isTrue();assertThat(result.value.getUserId()).isEqualTo(user.toString());
    }
    @Test void logoutRevocationCannotBeBypassedByValidationRpc() {
        String token=legacy(true);revocations.revoke(token,new Date(System.currentTimeMillis()+300000));
        rejected(token,Status.Code.UNAUTHENTICATED);
    }
    @Test void firstRotationInvalidatesLegacySession() {
        store.put(key(),UUID.randomUUID().toString());rejected(legacy(true),Status.Code.UNAUTHENTICATED);
    }
    @Test void previousFamilyCannotValidateAfterReset() {
        store.put(key(),UUID.randomUUID().toString());rejected(jwt.generateToken(user,"CUSTOMER",UUID.randomUUID().toString()),Status.Code.UNAUTHENTICATED);
    }
    @Test void currentFamilyIsAccepted() {
        String family=UUID.randomUUID().toString();store.put(key(),family);
        var result=validate(jwt.generateToken(user,"CUSTOMER",family));assertThat(result.error).isNull();assertThat(result.complete).isTrue();
    }
    @Test void userValidationFailsClosedWhenRedisIsUnavailable() {
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("Synthetic unavailable store"));
        rejected(legacy(true),Status.Code.UNAVAILABLE);
    }
    @Test void invalidStoredFamilyIsUnavailableNotAValidLegacySession() {
        store.put(key(),"not-a-generation");rejected(legacy(true),Status.Code.UNAVAILABLE);
    }
    @Test void malformedTokenIsUnauthenticatedWithoutAnAccountStoreLookup() {
        rejected("not-a-token",Status.Code.UNAUTHENTICATED);verify(redis,never()).hasKey(anyString());
    }
    @Test void signedUnknownRoleIsNotAnAuthenticatedUserContract() {
        rejected(jwt.generateToken(user,"UNKNOWN_ROLE",""),Status.Code.UNAUTHENTICATED);
    }
    @Test void tokenWithoutExpiryCannotValidate() {
        rejected(legacy(false),Status.Code.UNAUTHENTICATED);
    }
    @Test void invalidGenerationClaimCannotValidate() {
        rejected(jwt.generateToken(user,"CUSTOMER","not-a-generation"),Status.Code.UNAUTHENTICATED);
    }
    static class Recorder implements StreamObserver<ValidateTokenResponse> {
        ValidateTokenResponse value;Throwable error;boolean complete;
        public void onNext(ValidateTokenResponse value){this.value=value;}
        public void onError(Throwable error){this.error=error;}
        public void onCompleted(){complete=true;}
    }
}
