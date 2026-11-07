package com.example.notificationservice.service;

import com.example.notificationservice.listener.BookingPaymentListener;
import com.example.notificationservice.listener.OtpEmailListener;
import com.example.notificationservice.model.BookingNotificationEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class NotificationDeliveryTest {
    // Deliberately synthetic markers; assertions must never print actual customer data.
    private static final String PRIVATE = "private-marker@example.invalid";
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final TemplateEngine template = mock(TemplateEngine.class);

    private EmailNotificationService service(boolean fail) {
        when(mail.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        when(template.process(eq("booking-confirmation"), any(org.thymeleaf.context.IContext.class)))
                .thenReturn("<p>Test confirmation</p>");
        if (fail) doThrow(new MailSendException(PRIVATE)).when(mail).send(any(MimeMessage.class));
        var service = new EmailNotificationService(mail, template);
        ReflectionTestUtils.setField(service, "fromEmail", "sender@example.invalid");
        ReflectionTestUtils.setField(service, "fromName", "Test sender");
        return service;
    }

    private BookingNotificationEvent event() {
        var event = new BookingNotificationEvent();
        event.setBookingId(PRIVATE); event.setUserEmail(PRIVATE); event.setUserName(PRIVATE);
        event.setHotelName(PRIVATE); event.setAmount(BigDecimal.TEN);
        event.setCheckInDate(LocalDate.of(2027, 1, 10)); event.setCheckOutDate(LocalDate.of(2027, 1, 11));
        event.setPaidAt(LocalDateTime.of(2027, 1, 1, 12, 0));
        return event;
    }

    @Test void paymentSmtpFailureMustReachListenerRetry(CapturedOutput output) {
        var listener = new BookingPaymentListener(service(true));
        assertThatThrownBy(() -> listener.handlePaymentCompletedEvent(event())).isInstanceOf(RuntimeException.class);
        verify(mail).send(any(MimeMessage.class));
        assertThat(output.getAll()).doesNotContain(PRIVATE).doesNotContain("EVENT_PROCESSED");
    }

    @Test void otpFailurePropagatesWithoutPrivateLogs(CapturedOutput output) {
        var listener = new OtpEmailListener(service(true));
        assertThatThrownBy(() -> listener.handleOtpEmail(Map.of("email", PRIVATE, "otp_code", "synthetic-proof",
                "event_type", "otp_verification"))).isInstanceOf(RuntimeException.class);
        assertThat(output.getAll()).doesNotContain(PRIVATE, "synthetic-proof");
    }

    @Test void confirmationFailurePropagatesWithoutPrivateLogs(CapturedOutput output) {
        assertThatThrownBy(() -> service(true).sendBookingConfirmation(event())).isInstanceOf(RuntimeException.class);
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }

    @Test void successfulPaymentAndOtpKeepPrivateDataOutOfLogs(CapturedOutput output) {
        var service = service(false);
        new BookingPaymentListener(service).handlePaymentCompletedEvent(event());
        new OtpEmailListener(service).handleOtpEmail(Map.of("email", PRIVATE, "otp_code", "synthetic-proof",
                "event_type", "otp_verification"));
        verify(mail, times(2)).send(any(MimeMessage.class));
        assertThat(output.getAll()).contains("EVENT_PROCESSED").doesNotContain(PRIVATE, "synthetic-proof");
    }

    @Test void unsupportedOtpEventRemainsSkippedWithoutEchoingInput(CapturedOutput output) {
        new OtpEmailListener(service(false)).handleOtpEmail(Map.of("email", PRIVATE, "event_type", PRIVATE));
        verify(mail, never()).send(any(MimeMessage.class));
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }
}
