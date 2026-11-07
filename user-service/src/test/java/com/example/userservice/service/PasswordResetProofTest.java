package com.example.userservice.service;

import com.example.userservice.repository.*;
import com.example.userservice.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetProofTest {
    @Test void resetRequiresProofBeforeChangingStoredPassword() {
        var users=mock(UserRepository.class);var otp=mock(OtpService.class);var user=User.builder().password("unchanged-fixture-hash").build();
        when(users.findByEmailForUpdate("fixture@example.invalid")).thenReturn(Optional.of(user));
        doThrow(new IllegalArgumentException("No verified proof")).when(otp).consumeVerifiedForReset("fixture@example.invalid");
        var service=new UserService(users,mock(UserProfileRepository.class),otp,mock(com.example.userservice.security.TokenRevocationService.class));
        assertThrows(IllegalArgumentException.class,()->service.resetPassword("fixture@example.invalid","synthetic-password"));
        assertEquals("unchanged-fixture-hash",user.getPassword());verify(users,never()).save(any());
    }
    @Test void expiredUnverifiedMissingOrAmbiguousProofIsNotConsumed() {
        var repository=mock(OtpRepository.class);var service=new OtpService(repository,mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        Otp valid=Otp.builder().isVerified(true).expiryTime(LocalDateTime.now().plusMinutes(5)).build();
        for(var rows:List.of(List.<Otp>of(),List.of(Otp.builder().isVerified(false).expiryTime(LocalDateTime.now().plusMinutes(5)).build()),
                List.of(Otp.builder().isVerified(true).expiryTime(LocalDateTime.now().minusMinutes(1)).build()),List.of(valid,valid),List.of(valid,Otp.builder().isVerified(false).expiryTime(LocalDateTime.now().plusMinutes(5)).build()))) {
            when(repository.findAllForUpdate("fixture@example.invalid")).thenReturn(rows);
            assertThrows(IllegalArgumentException.class,()->service.consumeVerifiedForReset("fixture@example.invalid"));
        }
        verify(repository,never()).deleteAll(anyIterable());
    }
    @Test void verifiedProofIsLockedConsumedAndCannotBeReplayed() {
        var repository=mock(OtpRepository.class);var service=new OtpService(repository,mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        var rows=List.of(Otp.builder().isVerified(true).expiryTime(LocalDateTime.now().plusMinutes(5)).build());
        when(repository.findAllForUpdate("fixture@example.invalid")).thenReturn(rows).thenReturn(List.of());
        service.consumeVerifiedForReset("fixture@example.invalid");
        assertThrows(IllegalArgumentException.class,()->service.consumeVerifiedForReset("fixture@example.invalid"));
        verify(repository,times(1)).deleteAll(rows);verify(repository,times(1)).flush();
    }
}
