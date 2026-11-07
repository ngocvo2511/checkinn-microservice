package com.example.userservice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.example.userservice.model.*;
import com.example.userservice.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceLogPrivacyTest {
    static final String EMAIL="synthetic-user-privacy@example.invalid",NAME="SYNTHETIC-PRIVATE-USERNAME",DETAIL="SYNTHETIC-PRIVATE-BROKER-DETAIL";
    @ParameterizedTest @ValueSource(strings={"registration","duplicate","missingLogin","login","profile","verifyEmail","otpSuccess","otpFailure"})
    void accountAndOtpEventsRetainMetadataWithoutRawIdentityOrThrowablePayload(String action) {
        Logger logger=(Logger)LoggerFactory.getLogger(action.startsWith("otp")?OtpService.class:UserService.class);
        Level old=logger.getLevel();boolean additive=logger.isAdditive();var captured=new ListAppender<ILoggingEvent>();
        logger.setLevel(Level.DEBUG);logger.setAdditive(false);captured.start();logger.addAppender(captured);
        var users=mock(UserRepository.class);var profiles=mock(UserProfileRepository.class);var otp=mock(OtpService.class);
        var sessions=mock(com.example.userservice.security.TokenRevocationService.class);var service=new UserService(users,profiles,otp,sessions);
        UUID id=UUID.randomUUID();User user=User.builder().id(id).email(EMAIL).username(NAME).role(Role.CUSTOMER).isEmailVerified(true).isActive(true).build();
        UserProfile profile=UserProfile.builder().fullName("SYNTHETIC-PRIVATE-FULL-NAME").user(user).build();user.setProfile(profile);
        when(users.save(any())).thenReturn(user);when(users.findById(id)).thenReturn(Optional.of(user));when(users.markEmailVerified(EMAIL)).thenReturn(1);
        org.slf4j.MDC.put("traceId","synthetic-privacy-trace");
        try {
            switch(action) {
                case "registration" -> service.registerUser(com.example.userservice.dto.RegisterRequest.builder().username(NAME).email(EMAIL).password("synthetic-long-password").fullName(profile.getFullName()).build(),Role.CUSTOMER);
                case "duplicate" -> {when(users.existsByUsername(NAME)).thenReturn(true);assertThatThrownBy(()->service.registerUser(com.example.userservice.dto.RegisterRequest.builder().username(NAME).email(EMAIL).build(),Role.CUSTOMER)).isInstanceOf(RuntimeException.class);}
                case "missingLogin" -> assertThatThrownBy(()->service.login(EMAIL,"synthetic-long-password")).isInstanceOf(RuntimeException.class);
                case "login" -> {user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("synthetic-long-password"));when(users.findIdentityForUpdate(EMAIL)).thenReturn(Optional.of(user));when(sessions.currentUserGeneration(id)).thenReturn("");service.login(EMAIL,"synthetic-long-password");}
                case "profile" -> {when(profiles.findByUserId(id)).thenReturn(Optional.of(profile));service.getUserProfile(id);}
                case "verifyEmail" -> service.verifyUserEmail(EMAIL);
                default -> {
                    var rabbit=mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
                    if(action.equals("otpFailure"))doThrow(new org.springframework.amqp.AmqpException(DETAIL)).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class));
                    var realOtp=new OtpService(mock(OtpRepository.class),rabbit);
                    if(action.equals("otpFailure"))assertThatThrownBy(()->realOtp.generateAndSendOtp(EMAIL)).isInstanceOf(RuntimeException.class);else realOtp.generateAndSendOtp(EMAIL);
                }
            }
            String logs=captured.list.stream().map(e->e.getFormattedMessage()+(e.getThrowableProxy()==null?"":ThrowableProxyUtil.asString(e.getThrowableProxy()))).collect(Collectors.joining("\n"));
            assertThat(logs).isNotBlank().doesNotContain(EMAIL,NAME,profile.getFullName(),DETAIL,"synthetic-long-password");
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("synthetic-privacy-trace");
        } finally {org.slf4j.MDC.clear();logger.detachAppender(captured);captured.stop();logger.setLevel(old);logger.setAdditive(additive);}
    }
}
