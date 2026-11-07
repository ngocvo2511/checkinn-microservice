package com.example.authservice.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.example.authservice.controller.AuthController;
import com.example.authservice.dto.AuthResponseDto;
import com.example.authservice.dto.LoginRequestDto;
import com.example.authservice.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-memory DEBUG capture; all input markers are synthetic, never real credentials/data. */
class AuthLogPrivacyTest {
    final String email="synthetic-privacy@example.invalid",credential="SYNTHETIC-CREDENTIAL-MARKER",payload="SYNTHETIC-UPSTREAM-PAYLOAD";
    final Logger logger=(Logger)LoggerFactory.getLogger("com.example.authservice");
    final ListAppender<ILoggingEvent> captured=new ListAppender<>();
    Level oldLevel;boolean oldAdditive;
    @BeforeEach void capture() {
        oldLevel=logger.getLevel();oldAdditive=logger.isAdditive();logger.setLevel(Level.DEBUG);logger.setAdditive(false);
        captured.start();logger.addAppender(captured);MDC.put("traceId","synthetic-log-trace");
    }
    @AfterEach void restore() {
        logger.detachAppender(captured);captured.stop();logger.setLevel(oldLevel);logger.setAdditive(oldAdditive);MDC.remove("traceId");
    }
    LoginRequestDto request() {var request=new LoginRequestDto();request.setUsernameOrEmail(email);request.setPassword(credential);return request;}
    void safeLogs() {
        assertThat(captured.list).isNotEmpty();
        String text=captured.list.stream().map(event->event.getFormattedMessage()+(event.getThrowableProxy()==null?"":ThrowableProxyUtil.asString(event.getThrowableProxy()))).collect(Collectors.joining("\n"));
        assertThat(text).doesNotContain(email,credential,payload);
        assertThat(captured.list).allSatisfy(event->assertThat(event.getMDCPropertyMap()).containsEntry("traceId","synthetic-log-trace"));
    }
    @Test void successfulHttpLoginLogsOutcomeWithoutIdentityOrCredentials() {
        var auth=mock(AuthService.class);var request=request();
        when(auth.login(request)).thenReturn(AuthResponseDto.builder().userId(UUID.randomUUID()).email(email).token(credential).role("USER").build());
        assertThat(new AuthController(auth,mock(OtpService.class)).login(request).getStatusCode().value()).isEqualTo(200);
        safeLogs();
    }
    @Test void failedHttpLoginDoesNotLogExceptionMessagesOrNestedCredentialCauses() {
        var auth=mock(AuthService.class);var request=request();var failure=new IllegalStateException(payload+email,new RuntimeException(credential));
        when(auth.login(request)).thenThrow(failure);
        assertThatThrownBy(()->new AuthController(auth,mock(OtpService.class)).login(request)).isSameAs(failure);
        safeLogs();
    }
    @Test void debugServiceLoginKeepsTokenResponseButNotIdentityInLogs() {
        var users=mock(UserGrpcClient.class);var jwt=mock(JwtService.class);var request=request();UUID id=UUID.randomUUID();
        var user=com.checkinn.user.grpc.UserResponse.newBuilder().setId(id.toString()).setUsername(email).setEmail(email).setRole("USER").build();
        when(users.login(email,credential)).thenReturn(user);when(jwt.generateToken(id,"USER","")).thenReturn(credential);
        assertThat(new AuthService(users,jwt,mock(TokenRevocationService.class)).login(request).getToken()).isEqualTo(credential);
        safeLogs();
    }
    @Test void grpcLoginNeverLogsUsernameEmailOrReturnedToken() {
        var users=mock(UserGrpcClient.class);var jwt=mock(JwtService.class);UUID id=UUID.randomUUID();
        when(users.login(email,credential)).thenReturn(com.checkinn.user.grpc.UserResponse.newBuilder().setId(id.toString()).setEmail(email).setRole("USER").build());
        when(jwt.generateToken(id,"USER","")).thenReturn(credential);
        var observer=mock(io.grpc.stub.StreamObserver.class);
        new AuthGrpcService(users,jwt,mock(TokenRevocationService.class)).login(com.checkinn.auth.grpc.AuthLoginRequest.newBuilder().setUsernameOrEmail(email).setPassword(credential).build(),observer);
        verify(observer).onCompleted();safeLogs();
    }
    @Test void otpProxyDoesNotLogRecipientOrUpstreamResponsePayload() {
        var http=mock(RestTemplate.class);var otp=new OtpService(http);
        when(http.postForObject(any(java.net.URI.class),isNull(),eq(Map.class))).thenReturn(Map.of("success",true,"message",payload));
        org.springframework.test.util.ReflectionTestUtils.setField(otp,"userServiceUrl","http://synthetic-user.invalid");
        assertThat(otp.resendOtp(email)).containsEntry("success",true);
        safeLogs();
    }
    @Test void unexpectedAuthErrorKeeps500WithoutReflectingExceptionIntoBodyOrLogs() {
        var failure=new RuntimeException(payload+email,new IllegalStateException(credential));
        var response=new com.example.authservice.exception.GlobalExceptionHandler().handleGeneric(failure,new MockHttpServletRequest("POST","/login"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().get("message")).isEqualTo("Internal Server Error");
        safeLogs();
    }
    @Test void upstreamErrorWithNoReasonKeeps503AndHasSafeMessage() {
        var response=new com.example.authservice.exception.GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE),new MockHttpServletRequest("POST","/login"));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().get("message")).isEqualTo("Service Unavailable");safeLogs();
    }
}
