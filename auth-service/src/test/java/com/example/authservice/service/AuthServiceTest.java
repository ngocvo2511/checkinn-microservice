package com.example.authservice.service;

import com.example.authservice.dto.RegisterRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthServiceTest {

    private UserGrpcClient userGrpcClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userGrpcClient = mock(UserGrpcClient.class);
        JwtService jwtService = mock(JwtService.class);
        TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
        authService = new AuthService(userGrpcClient, jwtService, tokenRevocationService);
    }

    @Test
    void unavailableUserServiceIsNotAnInvalidPassword() {
        var request=new com.example.authservice.dto.LoginRequestDto();request.setUsernameOrEmail("synthetic@example.invalid");request.setPassword("synthetic-password");
        for(var status:java.util.List.of(io.grpc.Status.UNAVAILABLE,io.grpc.Status.DEADLINE_EXCEEDED)) {
            org.mockito.Mockito.doThrow(status.asRuntimeException()).when(userGrpcClient).login(request.getUsernameOrEmail(),request.getPassword());
            var failure=org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,()->authService.login(request));
            org.junit.jupiter.api.Assertions.assertEquals(503,failure.getStatusCode().value());
        }
    }
    @Test
    void realCredentialFailureRemainsUnauthorized() {
        var request=new com.example.authservice.dto.LoginRequestDto();request.setUsernameOrEmail("synthetic@example.invalid");request.setPassword("synthetic-password");
        org.mockito.Mockito.when(userGrpcClient.login(request.getUsernameOrEmail(),request.getPassword())).thenThrow(io.grpc.Status.UNAUTHENTICATED.asRuntimeException());
        var failure=org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,()->authService.login(request));
        org.junit.jupiter.api.Assertions.assertEquals(401,failure.getStatusCode().value());
    }
    @Test
    void registerRejectsPasswordShorterThanPolicy() {
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("rtm-user");
        request.setEmail("rtm@example.com");
        request.setPassword("short");
        request.setFullName("RTM User");

        assertThatThrownBy(() -> authService.register(request, com.checkinn.user.grpc.UserRole.USER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("12");

        verify(userGrpcClient, never()).register(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void resetPasswordRejectsPasswordShorterThanPolicy() {
        assertThatThrownBy(() -> authService.resetPassword("rtm@example.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("12");

        verify(userGrpcClient, never()).resetPassword(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
