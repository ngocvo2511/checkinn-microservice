package com.example.userservice.service;

import com.example.userservice.model.Otp;
import com.example.userservice.repository.OtpRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpVerificationPolicyTest {
    @Test void fiveAttemptBudgetAppliesBeforeAndAfterIntermediateVerification() {
        for(boolean verified:List.of(false,true)) {
            var repository=mock(OtpRepository.class);var service=new OtpService(repository,mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
            var otp=Otp.builder().otpCode("123456").isVerified(verified).attemptCount(0).expiryTime(LocalDateTime.now().plusMinutes(5)).build();
            when(repository.findAllForUpdate("synthetic@example.invalid")).thenReturn(List.of(otp));
            for(int attempt=0;attempt<5;attempt++)assertFalse(service.verifyOtp("synthetic@example.invalid","incorrect"));
            assertEquals(5,otp.getAttemptCount());assertFalse(service.verifyOtp("synthetic@example.invalid","123456"));
            verify(repository).delete(otp);
            assertThrows(IllegalArgumentException.class,()->service.consumeVerifiedForReset("synthetic@example.invalid"));
        }
    }
    @Test void correctIntermediateVerificationCanContinueToResetWithoutDeletingTheProof() {
        var repository=mock(OtpRepository.class);var service=new OtpService(repository,mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        var otp=Otp.builder().otpCode("123456").isVerified(false).expiryTime(LocalDateTime.now().plusMinutes(5)).build();
        when(repository.findAllForUpdate("synthetic@example.invalid")).thenReturn(List.of(otp));
        assertTrue(service.verifyOtp("synthetic@example.invalid","123456"));assertTrue(otp.isVerified());
        assertTrue(service.verifyOtp("synthetic@example.invalid","123456"));
        verify(repository,never()).delete(any());verify(repository,times(1)).save(otp);
    }
}
