package com.example.authservice.security;

import com.example.authservice.controller.AuthController;
import com.example.authservice.dto.OtpVerificationResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthFailureBoundaryTest {
    final String email="synthetic-boundary@example.invalid",detail="SYNTHETIC-PRIVATE-UPSTREAM-DETAIL";
    OtpService proxy(RestTemplate http) {
        var otp=new OtpService(http);ReflectionTestUtils.setField(otp,"userServiceUrl","http://synthetic-user.invalid");return otp;
    }
    @Test void otpTransportFailureIs503RatherThanAnInvalidChallenge() {
        var http=mock(RestTemplate.class);
        when(http.postForObject(anyString(),any(),eq(OtpVerificationResponse.class))).thenThrow(new ResourceAccessException(detail));
        assertThatThrownBy(()->proxy(http).verifyOtp(email,"synthetic-challenge"))
                .isInstanceOfSatisfying(ResponseStatusException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getReason()).doesNotContain(detail);});
    }
    @Test void missingVerifyResponseIsUnknownRatherThanNullOrFalse() {
        assertThatThrownBy(()->proxy(mock(RestTemplate.class)).verifyOtp(email,"synthetic-challenge"))
                .isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
    @ParameterizedTest @ValueSource(strings={"resend","generate"})
    void missingCommandResponseIsNotManufacturedAsAFalseBusinessResult(String operation) {
        var otp=proxy(mock(RestTemplate.class));
        assertThatThrownBy(()->{if(operation.equals("resend"))otp.resendOtp(email);else otp.generateAndSendOtp(email);})
                .isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
    @ParameterizedTest @ValueSource(strings={"resend","generate"})
    void plusAliasSurvivesTheActualRestTemplateUriEncoding(String operation) {
        var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();var otp=proxy(http);
        String alias="synthetic+alias@example.invalid";
        server.expect(request->assertThat(URLDecoder.decode(request.getURI().getRawQuery(),StandardCharsets.UTF_8)).isEqualTo("email="+alias))
                .andRespond(withSuccess("{\"success\":true}",MediaType.APPLICATION_JSON));
        Map<String,Object> reply=operation.equals("resend")?otp.resendOtp(alias):otp.generateAndSendOtp(alias);
        assertThat(reply).containsEntry("success",true);server.verify();
    }
    @Test void backendValidationStays400WithoutLeakingItsResponseBody() {
        var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();var otp=proxy(http);
        server.expect(request->{}).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(detail));
        assertThatThrownBy(()->otp.verifyOtp(email,"synthetic-challenge"))
                .isInstanceOfSatisfying(ResponseStatusException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(400);assertThat(error.getReason()).doesNotContain(detail);});
        server.verify();
    }
    @Test void forgotPasswordDoesNotClaimSuccessWhenOtpGenerationReportsFailure() {
        var otp=mock(OtpService.class);when(otp.generateAndSendOtp(email)).thenReturn(Map.of("success",false,"message","Known refusal"));
        var response=new AuthController(mock(AuthService.class),otp).forgotPassword(email);
        assertThat(response.getBody()).containsEntry("success",false);
    }
    @Test void forgotPasswordPreserves503WithoutReflectingInternalDetails() {
        var otp=mock(OtpService.class);when(otp.generateAndSendOtp(email)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,detail));
        var response=new AuthController(mock(AuthService.class),otp).forgotPassword(email);
        assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody()).containsEntry("success",false);
        assertThat(response.getBody().get("message").toString()).doesNotContain(detail);
    }
    @Test void resetDependencyFailureKeeps503AndDoesNotCallPasswordMutation() {
        var otp=mock(OtpService.class);var auth=mock(AuthService.class);
        when(otp.verifyOtp(email,"synthetic-challenge")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,detail));
        var response=new AuthController(auth,otp).resetPassword(Map.of("email",email,"otpCode","synthetic-challenge","newPassword","synthetic-unused-marker"));
        assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody()).containsEntry("success",false);
        assertThat(response.getBody().get("message").toString()).doesNotContain(detail);verifyNoInteractions(auth);
    }
    @Test void unexpectedResetFailureKeeps500AndDoesNotExposeTheCause() {
        var otp=mock(OtpService.class);when(otp.verifyOtp(any(),any())).thenThrow(new RuntimeException(detail));
        var response=new AuthController(mock(AuthService.class),otp).resetPassword(Map.of("email",email,"otpCode","synthetic-challenge","newPassword","synthetic-unused-marker"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);assertThat(response.getBody()).containsEntry("success",false);
        assertThat(response.getBody().get("message").toString()).doesNotContain(detail);
    }
    @Test void knownInvalidOtpKeepsExistingFalseResultAndNeverResetsPassword() {
        var otp=mock(OtpService.class);var auth=mock(AuthService.class);
        when(otp.verifyOtp(any(),any())).thenReturn(OtpVerificationResponse.builder().verified(false).message("Invalid OTP").build());
        var response=new AuthController(auth,otp).resetPassword(Map.of("email",email,"otpCode","synthetic-challenge","newPassword","synthetic-unused-marker"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody()).containsEntry("success",false);verifyNoInteractions(auth);
    }
    @ParameterizedTest @ValueSource(strings={"login","register","reset"})
    void unexpectedAccountFailureIsNotClientValidationOrBadCredentials(String action) {
        var users=mock(com.example.authservice.service.UserGrpcClient.class);
        var auth=new AuthService(users,mock(com.example.authservice.service.JwtService.class),mock(com.example.authservice.service.TokenRevocationService.class));
        doThrow(new IllegalStateException(detail)).when(users).login(any(),any());
        doThrow(new IllegalStateException(detail)).when(users).register(any(),any(),any(),any(),any());
        doThrow(new IllegalStateException(detail)).when(users).resetPassword(any(),any());
        var login=new com.example.authservice.dto.LoginRequestDto();login.setUsernameOrEmail(email);login.setPassword("synthetic-unused-marker");
        var registration=new com.example.authservice.dto.RegisterRequestDto();registration.setEmail(email);registration.setUsername("synthetic-boundary");registration.setPassword("synthetic-unused-marker");
        assertThatThrownBy(()->{
            switch(action) {
                case "login" -> auth.login(login);
                case "register" -> auth.register(registration,com.checkinn.user.grpc.UserRole.USER);
                case "reset" -> auth.resetPassword(email,"synthetic-unused-marker");
                default -> throw new AssertionError("Unknown action");
            }
        }).isInstanceOfSatisfying(ResponseStatusException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getReason()).doesNotContain(detail);});
    }
    @ParameterizedTest @ValueSource(strings={"request","identity","password"})
    void missingLoginFieldsAreClientValidationWithoutAccountCalls(String missing) {
        var users=mock(com.example.authservice.service.UserGrpcClient.class);
        var auth=new AuthService(users,mock(com.example.authservice.service.JwtService.class),mock(com.example.authservice.service.TokenRevocationService.class));
        var request=new com.example.authservice.dto.LoginRequestDto();request.setUsernameOrEmail(email);request.setPassword("synthetic-unused-marker");
        if(missing.equals("identity"))request.setUsernameOrEmail(null);
        if(missing.equals("password"))request.setPassword(null);
        var input=missing.equals("request")?null:request;
        assertThatThrownBy(()->auth.login(input)).isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(users);
    }
    @Test void loginDoesNotApplyNewRegistrationPasswordLengthToLegacyCredentials() {
        var users=mock(com.example.authservice.service.UserGrpcClient.class);var jwt=mock(com.example.authservice.service.JwtService.class);
        var request=new com.example.authservice.dto.LoginRequestDto();request.setUsernameOrEmail(email);request.setPassword("legacy");
        var id=java.util.UUID.randomUUID();when(users.login(email,"legacy")).thenReturn(com.checkinn.user.grpc.UserResponse.newBuilder().setId(id.toString()).setRole("USER").build());
        when(jwt.generateToken(id,"USER","")).thenReturn("synthetic-token-marker");
        assertThat(new AuthService(users,jwt,mock(com.example.authservice.service.TokenRevocationService.class)).login(request).getToken()).isEqualTo("synthetic-token-marker");
    }
    @ParameterizedTest @ValueSource(strings={"INTERNAL","UNKNOWN"})
    void accountServerFaultIsNotAnInvalidCredentialResponse(String code) {
        var users=mock(com.example.authservice.service.UserGrpcClient.class);
        var auth=new AuthService(users,mock(com.example.authservice.service.JwtService.class),mock(com.example.authservice.service.TokenRevocationService.class));
        var request=new com.example.authservice.dto.LoginRequestDto();request.setUsernameOrEmail(email);request.setPassword("synthetic-unused-marker");
        var status=io.grpc.Status.fromCode(io.grpc.Status.Code.valueOf(code)).withDescription(detail);
        when(users.login(any(),any())).thenThrow(status.asRuntimeException());
        assertThatThrownBy(()->auth.login(request)).isInstanceOfSatisfying(ResponseStatusException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getReason()).doesNotContain(detail);});
    }
}
