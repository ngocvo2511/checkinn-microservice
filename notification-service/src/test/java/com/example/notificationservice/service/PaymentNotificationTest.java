package com.example.notificationservice.service;

import com.example.notificationservice.model.BookingNotificationEvent;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentNotificationTest {
    @Test void unsettledReservationEmailDoesNotPromiseConfirmedRoom() throws Exception {
        assertThat(render("PENDING_PAYMENT")).contains("đặt phòng chưa được xác nhận").doesNotContain("Phòng đã được xác nhận.");
    }
    @Test void confirmedRoomEmailCanProvideCheckInConfirmation() throws Exception {
        assertThat(render("CONFIRMED")).contains("Phòng đã được xác nhận.");
    }
    @Test void legacyEventWithUnknownBookingStatusDoesNotInventConfirmation() throws Exception {
        assertThat(render(null)).contains("đặt phòng chưa được xác nhận");
    }
    private String render(String bookingStatus) throws Exception {
        JavaMailSender mail = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(message);
        var service = new EmailNotificationService(mail, mock(TemplateEngine.class));
        ReflectionTestUtils.setField(service, "fromEmail", "sender@example.invalid");
        ReflectionTestUtils.setField(service, "fromName", "Test sender");
        BookingNotificationEvent event = new BookingNotificationEvent();
        event.setBookingId("booking-1"); event.setBookingStatus(bookingStatus); event.setUserEmail("customer@example.invalid");
        event.setUserName("Test customer"); event.setHotelName("Test hotel");
        event.setCheckInDate(LocalDate.of(2027, 1, 10)); event.setCheckOutDate(LocalDate.of(2027, 1, 11));
        event.setAmount(BigDecimal.TEN); event.setPaymentMethod("VNPAY");
        service.sendPaymentSuccessNotification(event);
        verify(mail).send(message);
        return text(message);
    }
    private String text(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String value) return value;
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) result.append(text(multipart.getBodyPart(i)));
            return result.toString();
        }
        return "";
    }
}
