package com.example.userservice.service;

import com.example.userservice.controller.OtpController;
import com.example.userservice.dto.VerifyOtpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserOtpBoundaryTest {
    static final String EMAIL="synthetic-boundary+alias@example.invalid",PRIVATE="SYNTHETIC-PRIVATE-DEPENDENCY-DETAIL";
    static Stream<org.junit.jupiter.params.provider.Arguments> failures() {
        return Stream.of("verify","generate","resend").flatMap(action->Stream.of("storage","commit","delivery","unexpected")
                .map(kind->org.junit.jupiter.params.provider.Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("failures")
    void unknownDependencyOutcomesCannotBecomeBadInputOrExposeException(String action,String kind) {
        var otp=mock(OtpService.class);var controller=new OtpController(otp,mock(UserService.class));
        RuntimeException fault=switch(kind) {
            case "storage" -> new org.springframework.dao.DataAccessResourceFailureException(PRIVATE);
            case "commit" -> new org.springframework.transaction.TransactionSystemException(PRIVATE);
            case "delivery" -> new RuntimeException("Wrapper",new org.springframework.amqp.AmqpException(PRIVATE));
            default -> new RuntimeException(PRIVATE);
        };
        when(otp.verifyOtp(any(),any())).thenThrow(fault);when(otp.generateAndSendOtp(any())).thenThrow(fault);
        var response=switch(action) {
            case "verify" -> controller.verifyOtp(VerifyOtpRequest.builder().email(EMAIL).otpCode("synthetic-proof").build());
            case "generate" -> controller.generateAndSendOtp(EMAIL);
            default -> controller.resendOtp(EMAIL);
        };
        assertThat(response.getStatusCode().value()).isEqualTo(kind.equals("unexpected")?500:503);
        assertThat(response.getBody().toString()).doesNotContain(PRIVATE,"Wrapper");
        if(response.getBody() instanceof com.example.userservice.dto.OtpVerificationResponse body)assertThat(body.isVerified()).isFalse();
        else assertThat(((java.util.Map<?,?>)response.getBody()).get("success")).isEqualTo(false);
    }
    @Test void knownInvalidProofStaysFalseAndNeverMarksEmailVerified() {
        var users=mock(UserService.class);var controller=new OtpController(mock(OtpService.class),users);
        var response=controller.verifyOtp(VerifyOtpRequest.builder().email(EMAIL).otpCode("wrong-proof").build());
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody().isVerified()).isFalse();verifyNoInteractions(users);
    }
    @Test void malformedRequiredInputIsBadRequestBeforeAnyProofMutation() {
        var otp=mock(OtpService.class);var users=mock(UserService.class);var controller=new OtpController(otp,users);
        assertThat(controller.verifyOtp(null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.verifyOtp(VerifyOtpRequest.builder().email(EMAIL).build()).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.generateAndSendOtp(" ").getStatusCode().value()).isEqualTo(400);
        assertThat(controller.resendOtp(null).getStatusCode().value()).isEqualTo(400);verifyNoInteractions(otp,users);
    }
}
