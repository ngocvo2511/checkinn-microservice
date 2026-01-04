package com.example.bookingservice.payment.service;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.dto.PaymentResponse;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.payment.vnpay.VnPayUtil;
import com.example.bookingservice.payment.dto.VnPayInitResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VnPayProperties vnPayProperties;
    private final BookingService bookingService;
    private final HotelEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Validate booking exists
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + request.getBookingId()));

        // Check if payment already exists
        paymentRepository.findByBookingId(request.getBookingId())
                .ifPresent(p -> {
                    throw new IllegalArgumentException("Payment already exists for this booking");
                });

        // Create payment
        Payment payment = Payment.builder()
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .transactionId(UUID.randomUUID().toString())
                .build();

        // For hotel payment, mark as onsite pending (not paid yet)
        if (request.getMethod() == PaymentMethod.HOTEL) {
            payment.setStatus(PaymentStatus.ONSITE_PENDING);

            // Update booking as confirmed/held but do NOT increase paid amount yet
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
        }

        payment = paymentRepository.save(payment);
        return toPaymentResponse(payment);
    }

        @Transactional
        public VnPayInitResponse initVnPayPayment(String bookingId, String clientIp) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        paymentRepository.findByBookingId(bookingId)
            .ifPresent(p -> { 
                // Allow retry if previous payment failed
                if (p.getStatus() != PaymentStatus.FAILED && p.getStatus() != PaymentStatus.CANCELLED) {
                    throw new IllegalArgumentException("Payment already exists for this booking"); 
                }
                // Delete old failed payment to create new one
                paymentRepository.delete(p);
            });

        // Mark booking as waiting for VNPay payment and extend hold expiry
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15));
        bookingRepository.save(booking);

        Payment payment = Payment.builder()
            .bookingId(bookingId)
            .amount(booking.getTotalAmount())
            .method(PaymentMethod.VNPAY)
            .status(PaymentStatus.PENDING)
            .transactionId(UUID.randomUUID().toString())
            .build();
        payment = paymentRepository.save(payment);

        // Build VNPay params
        String txnRef = payment.getId();
        payment.setVnpayOrderId(txnRef);

        LocalDateTime now = LocalDateTime.now();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(booking.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don dat phong " + booking.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        params.put("vnp_Locale", "vn");
        params.put("vnp_CreateDate", VnPayUtil.getCreateDate(now));
        params.put("vnp_ExpireDate", VnPayUtil.getExpireDate(now, 15));

        String query = VnPayUtil.buildQuery(params);
        String secureHash = VnPayUtil.hmacSHA512(vnPayProperties.getHashSecret(), query);
        String redirectUrl = vnPayProperties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;

        paymentRepository.save(payment);

        return VnPayInitResponse.builder()
            .redirectUrl(redirectUrl)
            .orderId(txnRef)
            .build();
        }

    @Transactional
    public PaymentResponse processVNPayCallback(String orderId, String responseCode, String transactionId) {
        Payment payment = paymentRepository.findByVnpayOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));

        // VNPay response codes: 00 = success
        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking
            Booking booking = bookingRepository.findById(payment.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPaidAmount(payment.getAmount());
            bookingRepository.save(booking);

            // Confirm hold with hotel service
            try {
                bookingService.confirmBookingHold(payment.getBookingId());
            } catch (Exception e) {
                System.err.println("Failed to confirm hold: " + e.getMessage());
            }

            PaymentEvent event = buildPaymentEvent(booking, payment, PaymentStatus.COMPLETED.name());
            eventPublisher.publishPaymentCompleted(event);
            BookingStatusEvent statusEvent = buildBookingStatusEvent(booking, BookingStatus.CONFIRMED.name());
            eventPublisher.publishBookingStatus(statusEvent);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking back to PENDING so user can retry payment or choose another method
            Booking booking = bookingRepository.findById(payment.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
            booking.setStatus(BookingStatus.PENDING);
            bookingRepository.save(booking);

            // Release hold on payment failure
            try {
                bookingService.releaseBookingHold(payment.getBookingId());
            } catch (Exception e) {
                System.err.println("Failed to release hold: " + e.getMessage());
            }
        }

        payment = paymentRepository.save(payment);
        return toPaymentResponse(payment);
    }

    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        return toPaymentResponse(payment);
    }

    public PaymentResponse getPaymentByBookingId(String bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking: " + bookingId));
        return toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only refund completed payments");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        
        // Update booking
        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        booking.setPaidAmount(BigDecimal.ZERO);
        bookingRepository.save(booking);

        payment = paymentRepository.save(payment);

        PaymentEvent event = buildPaymentEvent(booking, payment, PaymentStatus.REFUNDED.name());
        eventPublisher.publishPaymentRefunded(event);
        return toPaymentResponse(payment);
    }

        private PaymentEvent buildPaymentEvent(Booking booking, Payment payment, String statusLabel) {
        int rooms = booking.getItems() == null ? 1 : booking.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum();
        if (rooms <= 0) {
            rooms = 1;
        }
        String roomTypeId = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeId()
            : null;
        
        // Lấy room type name nếu có
        String roomType = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeName()
            : "Standard Room";
            
        int nights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        int numberOfGuests = (booking.getAdults() != null ? booking.getAdults() : 0) + 
                             (booking.getChildren() != null ? booking.getChildren() : 0);
        
        return PaymentEvent.builder()
            .bookingId(booking.getId())
            .hotelId(booking.getHotelId())
            .hotelName(booking.getHotelName())
            .roomTypeId(roomTypeId)
            .roomType(roomType)
            .userId(booking.getUserId())
            .userName(booking.getContactName())
            .userEmail(booking.getContactEmail())
            .checkInDate(booking.getCheckInDate())
            .checkOutDate(booking.getCheckOutDate())
            .nights(nights)
            .rooms(rooms)
            .numberOfGuests(numberOfGuests)
            .amount(payment.getAmount())
            .paymentStatus(statusLabel)
            .paymentMethod(payment.getMethod().name())
            .paidAt(payment.getPaidAt())
            .eventAt(LocalDateTime.now())
            .build();
        }

        private BookingStatusEvent buildBookingStatusEvent(Booking booking, String statusLabel) {
        int rooms = booking.getItems() == null ? 1 : booking.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum();
        if (rooms <= 0) {
            rooms = 1;
        }
        String roomTypeId = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeId()
            : null;
        int nights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        return BookingStatusEvent.builder()
            .bookingId(booking.getId())
            .hotelId(booking.getHotelId())
            .roomTypeId(roomTypeId)
            .checkInDate(booking.getCheckInDate())
            .checkOutDate(booking.getCheckOutDate())
            .nights(nights)
            .rooms(rooms)
            .bookingStatus(statusLabel)
            .eventAt(LocalDateTime.now())
            .build();
        }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .vnpayOrderId(payment.getVnpayOrderId())
                .vnpayResponseCode(payment.getVnpayResponseCode())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
