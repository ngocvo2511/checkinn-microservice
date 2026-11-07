package com.example.authservice.service;

import com.checkinn.user.grpc.UserResponse;
import com.example.authservice.dto.LoginRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionTokenIssuanceTest {
    private JwtService signer() {
        var jwt=new JwtService();ReflectionTestUtils.setField(jwt,"secret","unit-only-session-signing-key-not-production-01234567890123456789");jwt.init();return jwt;
    }
    @Test void delayedCredentialResultRetainsItsCapturedFamilyInsteadOfGettingNewResetPrivileges() {
        UUID id=UUID.randomUUID();String captured=UUID.randomUUID().toString();var client=mock(UserGrpcClient.class);var jwt=signer();
        when(client.login("synthetic-user","synthetic-password")).thenReturn(UserResponse.newBuilder().setId(id.toString()).setRole("CUSTOMER").setSessionGeneration(captured).build());
        var auth=new AuthService(client,jwt,mock(TokenRevocationService.class));
        var request=new LoginRequestDto();request.setUsernameOrEmail("synthetic-user");request.setPassword("synthetic-password");
        var result=auth.login(request);
        assertTrue(captured.equals(jwt.parseToken(result.getToken()).getBody().get("sessionGeneration",String.class)));
    }
    @Test void separateLoginsHaveDifferentTokenIdsEvenWithinOneClockSecond() {
        var jwt=signer();UUID id=UUID.randomUUID();String family=UUID.randomUUID().toString();
        String first=jwt.generateToken(id,"CUSTOMER",family),second=jwt.generateToken(id,"CUSTOMER",family);
        assertFalse(first.equals(second));
        assertFalse(jwt.parseToken(first).getBody().getId().equals(jwt.parseToken(second).getBody().getId()));
    }
}
