package com.example.notificationservice.service;

import com.example.notificationservice.model.BookingNotificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;

@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.from-email}")
    private String fromEmail;

    @Value("${notification.from-name}")
    private String fromName;

    public EmailNotificationService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendBookingConfirmation(BookingNotificationEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(event.getUserEmail());
            helper.setSubject("Xác nhận đặt phòng - Booking #" + event.getBookingId());

            Context context = new Context();
            context.setVariable("userName", event.getUserName());
            context.setVariable("bookingId", event.getBookingId());
            context.setVariable("hotelName", event.getHotelName());
            context.setVariable("checkInDate", event.getCheckInDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            context.setVariable("checkOutDate", event.getCheckOutDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            context.setVariable("numberOfGuests", event.getNumberOfGuests());
            context.setVariable("numberOfRooms", event.getRooms());
            context.setVariable("roomType", event.getRoomType());
            context.setVariable("totalAmount", String.format("%,.0f VNĐ", event.getAmount()));
            context.setVariable("paymentDate", event.getPaidAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

            String htmlContent = templateEngine.process("booking-confirmation", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Đã gửi email xác nhận booking #{} tới {}", event.getBookingId(), event.getUserEmail());

        } catch (Exception e) {
            logger.error("Lỗi khi gửi email cho booking #{}: {}", event.getBookingId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send booking confirmation email", e);
        }
    }

    public void sendPaymentSuccessNotification(BookingNotificationEvent event) {
        try {
            logger.info("Bắt đầu gửi email thanh toán thành công cho booking #{}", event.getBookingId());
            
            // Simple text email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(event.getUserEmail());
            helper.setSubject("Thanh toán thành công - CheckInn Booking #" + event.getBookingId());

            String content = String.format("""
                    <html>
                    <body style='font-family: Arial, sans-serif;'>
                        <h2 style='color: #4CAF50;'>Thanh toán thành công!</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Cảm ơn bạn đã đặt phòng tại <strong>%s</strong>.</p>
                        
                        <div style='background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin: 20px 0;'>
                            <h3>Thông tin đặt phòng:</h3>
                            <ul style='list-style: none; padding: 0;'>
                                <li><strong>Mã đặt phòng:</strong> #%s</li>
                                <li><strong>Khách sạn:</strong> %s</li>
                                <li><strong>Ngày nhận phòng:</strong> %s</li>
                                <li><strong>Ngày trả phòng:</strong> %s</li>
                                <li><strong>Số đêm:</strong> %d đêm</li>
                                <li><strong>Số khách:</strong> %d người</li>
                                <li><strong>Số phòng:</strong> %d</li>
                                <li><strong>Loại phòng:</strong> %s</li>
                                <li><strong>Tổng tiền:</strong> %,.0f VNĐ</li>
                                <li><strong>Phương thức thanh toán:</strong> %s</li>
                            </ul>
                        </div>
                        
                        <p style='color: #666;'>Chúng tôi đã gửi xác nhận đến email của bạn. Vui lòng mang theo email này khi check-in.</p>
                        <p style='color: #666;'>Nếu có bất kỳ thắc mắc nào, vui lòng liên hệ với chúng tôi.</p>
                        
                        <hr style='margin: 30px 0; border: none; border-top: 1px solid #ddd;'>
                        <p style='color: #999; font-size: 12px;'>Đây là email tự động, vui lòng không trả lời email này.</p>
                    </body>
                    </html>
                    """,
                    event.getUserName(),
                    event.getHotelName(),
                    event.getBookingId(),
                    event.getHotelName(),
                    event.getCheckInDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    event.getCheckOutDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    event.getNights(),
                    event.getNumberOfGuests(),
                    event.getRooms(),
                    event.getRoomType() != null ? event.getRoomType() : "Standard Room",
                    event.getAmount(),
                    event.getPaymentMethod()
            );

            helper.setText(content, true);
            mailSender.send(message);
            
            logger.info("✓ Đã gửi email thanh toán thành công cho booking #{} tới {}", 
                    event.getBookingId(), event.getUserEmail());

        } catch (Exception e) {
            logger.error("✗ Lỗi khi gửi email thanh toán cho booking #{}: {}", 
                    event.getBookingId(), e.getMessage(), e);
        }
    }
}
