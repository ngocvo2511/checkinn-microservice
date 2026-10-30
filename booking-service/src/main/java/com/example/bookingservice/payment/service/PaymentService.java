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
import com.example.bookingservice.payment.service.factory.PaymentCreationPolicy;
import com.example.bookingservice.payment.service.factory.PaymentCreationPolicyFactory;
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.payment.vnpay.VnPayUtil;
import com.example.bookingservice.payment.dto.VnPayInitResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VnPayProperties vnPayProperties;
    private final BookingService bookingService;
    private final HotelEventPublisher eventPublisher;
    private final LoyaltyPointsClient loyaltyPointsClient;
    private final PaymentCreationPolicyFactory paymentCreationPolicyFactory;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("[CREATE_PAYMENT] Request received - bookingId: {}, method: {}", request.getBookingId(), request.getMethod());
        validateHotelPaymentRequest(request);

        // Validate booking exists
        Booking booking = bookingRepository.findByIdForUpdate(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + request.getBookingId()));
        log.info("[CREATE_PAYMENT] Booking loaded - bookingId: {}, userId: {}", booking.getId(), booking.getUserId());
        validateBookingCanStartPayment(booking);

        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount must equal the server booking total");
        }

        // Keep previous failed/cancelled attempts as payment history, but prevent duplicate active payments.
        paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(request.getBookingId())
                .ifPresent(this::validateCanCreateNewPayment);

        booking = bookingService.ensureActiveHold(request.getBookingId());

        PaymentCreationPolicy creationPolicy = paymentCreationPolicyFactory.getPolicy(request.getMethod());
        CreatePaymentRequest pricedRequest = CreatePaymentRequest.builder().bookingId(booking.getId())
                .amount(booking.getTotalAmount()).method(request.getMethod()).build();
        Payment payment = creationPolicy.createPayment(pricedRequest);
        creationPolicy.updateBookingAfterPaymentCreated(booking);
        if (creationPolicy.shouldPersistBooking()) {
            bookingRepository.save(booking);
        }

        payment = paymentRepository.save(payment);
        log.info("[CREATE_PAYMENT] Payment created - paymentId: {}, bookingId: {}, method: {}", payment.getId(), payment.getBookingId(), payment.getMethod());
        return toPaymentResponse(payment);
    }

    @Transactional
    public VnPayInitResponse initVnPayPayment(String bookingId, String clientIp) {
        log.info("[INIT_VNPAY] Request received - bookingId: {}, clientIp: {}", bookingId, clientIp);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        validateBookingCanStartPayment(booking);
        String providerAmount = providerAmount(booking.getTotalAmount());

        paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
            .ifPresent(this::validateCanCreateNewPayment);

        booking = bookingService.ensureActiveHold(bookingId);

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
            .bookingId(bookingId)
            .amount(booking.getTotalAmount())
            .method(PaymentMethod.VNPAY)
            .build();

        PaymentCreationPolicy creationPolicy = paymentCreationPolicyFactory.getPolicy(PaymentMethod.VNPAY);
        Payment payment = creationPolicy.createPayment(paymentRequest);
        creationPolicy.updateBookingAfterPaymentCreated(booking);
        if (creationPolicy.shouldPersistBooking()) {
            bookingRepository.save(booking);
        }
        payment = paymentRepository.save(payment);

        // Build VNPay params
        String txnRef = payment.getId();
        payment.setVnpayOrderId(txnRef);

        LocalDateTime now = LocalDateTime.now();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", providerAmount);
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
        log.info("[INIT_VNPAY] VNPay init completed - bookingId: {}, orderId: {}", bookingId, txnRef);

        return VnPayInitResponse.builder()
            .redirectUrl(redirectUrl)
            .orderId(txnRef)
            .build();
        }

    @Transactional
    public PaymentResponse processVNPayCallback(String orderId, String responseCode, String transactionId) {
        log.info("[VNPAY_CALLBACK] Received callback - orderId: {}, responseCode: {}, transactionId: {}", orderId, responseCode, transactionId);
        String bookingId = paymentRepository.findBookingIdByVnpayOrderId(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        // All mutations acquire booking before payment to avoid lock-order inversions with cancellation/refund.
        Booking lockedBooking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        Payment payment = paymentRepository.findByVnpayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));

        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
            validateVnPayReplay(payment, responseCode, transactionId);
            log.info("[VNPAY_CALLBACK_REPLAY] Returning existing result - orderId: {}, paymentStatus: {}",
                    orderId, payment.getStatus());
            return toPaymentResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && !(payment.getStatus() == PaymentStatus.CANCELLED && payment.getMethod() == PaymentMethod.VNPAY)) {
            throw new IllegalArgumentException("Không thể xử lý callback VNPay cho thanh toán có trạng thái: " + payment.getStatus());
        }

        validateVnPayTransactionNotUsedByAnotherPayment(payment, transactionId);

        // VNPay response codes: 00 = success
        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking
            Booking booking = lockedBooking;
            BigDecimal previouslyPaid = booking.getPaidAmount() == null ? BigDecimal.ZERO : booking.getPaidAmount();
            booking.setPaidAmount(previouslyPaid.add(payment.getAmount()));
            boolean reservationConfirmed = previouslyPaid.signum() == 0
                    && payment.getAmount().compareTo(booking.getTotalAmount()) == 0
                    && confirmReservation(booking);
            bookingRepository.save(booking);

            // Handle loyalty points: trừ điểm đã sử dụng và tích lũy điểm mới
            if (reservationConfirmed && booking.getUserId() != null) {
                try {
                    String userId = booking.getUserId().toString();
                    
                    // Trừ điểm đã sử dụng (nếu có) - chỉ trừ khi payment success
                    if (booking.getUsedPoints() != null && booking.getUsedPoints() > 0) {
                        log.info("Deducting {} points for user: {}", booking.getUsedPoints(), userId);
                        loyaltyPointsClient.usePoints(
                            new com.example.bookingservice.integration.loyalty.ApplyPointsRequestDTO(
                                userId,
                                booking.getUsedPoints(),
                                booking.getId(),
                                "Sử dụng điểm cho booking #" + booking.getId()
                            )
                        );
                    }
                    
                    // Tích lũy điểm mới từ thanh toán
                    // Pass totalAmount to the API - it will calculate points internally
                    if (booking.getEarnedPoints() == null) {
                        log.info("Earning points for user: {} from booking: {} with total amount: {}", 
                            userId, booking.getId(), booking.getTotalAmount());
                        loyaltyPointsClient.earnPoints(
                                userId,
                                booking.getId(),
                                booking.getTotalAmount() // Pass total amount, not pre-calculated points
                        );
                        booking.setEarnedPoints(calculateEarnedPoints(booking.getTotalAmount()));
                        bookingRepository.save(booking);
                        log.info("Successfully processed loyalty points");
                    } else {
                        log.info("Skipping loyalty point earning for booking {} because it was already awarded", booking.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to process loyalty points: {}", e.getMessage(), e);
                    // Don't fail the payment if earning points fails
                }
            }
            PaymentEvent event = buildPaymentEvent(booking, payment, PaymentStatus.COMPLETED.name());
            try {
                log.info("[PUBLISH_EVENT] Publishing payment.completed for paymentId: {}, bookingId: {}", payment.getId(), booking.getId());
                eventPublisher.publishPaymentCompleted(event);
                log.info("[PUBLISH_EVENT] Published payment.completed for paymentId: {}", payment.getId());
            } catch (Exception e) {
                log.error("[PUBLISH_EVENT] Failed to publish payment.completed for paymentId: {}: {}", payment.getId(), e.getMessage(), e);
            }

            if (reservationConfirmed) {
            BookingStatusEvent statusEvent = buildBookingStatusEvent(booking, BookingStatus.CONFIRMED.name());
            try {
                log.info("[PUBLISH_EVENT] Publishing booking.status.changed for bookingId: {}, status: {}", booking.getId(), BookingStatus.CONFIRMED.name());
                eventPublisher.publishBookingStatus(statusEvent);
                log.info("[PUBLISH_EVENT] Published booking.status.changed for bookingId: {}", booking.getId());
            } catch (Exception e) {
                log.error("[PUBLISH_EVENT] Failed to publish booking.status.changed for bookingId: {}: {}", booking.getId(), e.getMessage(), e);
            }
            }
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking back to PENDING so user can retry payment or choose another method
            Booking booking = lockedBooking;
            if ((booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                    && (booking.getPaidAmount() == null || booking.getPaidAmount().signum() == 0)) {
                booking.setStatus(BookingStatus.PENDING);
                bookingRepository.save(booking);
            }

            log.info("[VNPAY_CALLBACK] Payment failed for orderId: {}, bookingId: {}, responseCode: {}", orderId, booking.getId(), responseCode);
            // Keep the room hold until holdExpiresAt so the user can retry payment.
            // If the customer does not retry in time, BookingScheduledTask cancels the booking.
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
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking: " + bookingId));
        return toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(String paymentId) {
        log.info("[REFUND_PAYMENT] Request received - paymentId: {}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only refund completed payments");
        }

        // Financial success requires a verified provider refund result. A database update is not a refund.
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Refund execution is unavailable until the provider integration is verified; payment is unchanged");
    }

    @Transactional
    public PaymentResponse confirmHotelPayment(String bookingId) {
        log.info("[CONFIRM_HOTEL_PAYMENT] Request received - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.ONSITE_PENDING)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking: " + bookingId));
        if (payment.getAmount() == null || booking.getTotalAmount() == null || payment.getAmount().signum() <= 0
                || payment.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Stored onsite payment amount requires reconciliation");
        }

        // Mark payment as completed
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        // Update booking paid amount
        booking.setPaidAmount(booking.getTotalAmount());
        confirmReservation(booking);
        bookingRepository.save(booking);

        // Publish payment event
        PaymentEvent event = buildPaymentEvent(booking, payment, PaymentStatus.COMPLETED.name());
        try {
            log.info("[PUBLISH_EVENT] Publishing payment.completed for hotel-confirmed payment - bookingId: {}", bookingId);
            eventPublisher.publishPaymentCompleted(event);
            log.info("[PUBLISH_EVENT] Published payment.completed for bookingId: {}", bookingId);
        } catch (Exception e) {
            log.error("[PUBLISH_EVENT] Failed to publish payment.completed for bookingId: {}: {}", bookingId, e.getMessage(), e);
        }

        log.info("Hotel payment confirmed for booking: {}", bookingId);
        return toPaymentResponse(payment);
    }

    private boolean confirmReservation(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.PENDING_PAYMENT) return false;
        try {
            bookingService.confirmBookingHold(booking.getId());
            booking.setStatus(BookingStatus.CONFIRMED);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Payment received but reservation needs reconciliation (type={})", ex.getClass().getSimpleName());
            return false;
        }
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
            .customerId(booking.getUserId())
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
            .bookingStatus(booking.getStatus().name())
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

    private Long calculateEarnedPoints(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return totalAmount.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.DOWN).longValue();
    }

    private void validateCanCreateNewPayment(Payment payment) {
        if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Payment already exists for this booking");
        }
    }

    private void validateVnPayReplay(Payment payment, String responseCode, String transactionId) {
        if (payment.getVnpayResponseCode() != null && !payment.getVnpayResponseCode().equals(responseCode)) {
            throw new IllegalArgumentException("Mã phản hồi VNPay lặp lại không khớp với callback ban đầu");
        }

        if (transactionId != null
                && payment.getVnpayTransactionNo() != null
                && !payment.getVnpayTransactionNo().equals(transactionId)) {
            throw new IllegalArgumentException("Mã giao dịch VNPay lặp lại không khớp với callback ban đầu");
        }
    }

    private void validateVnPayTransactionNotUsedByAnotherPayment(Payment payment, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return;
        }

        paymentRepository.findByVnpayTransactionNo(transactionId)
                .filter(existing -> !existing.getId().equals(payment.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Mã giao dịch VNPay đã được sử dụng cho thanh toán khác");
                });
    }

    private void validateBookingCanStartPayment(Booking booking) {
        if (booking.getTotalAmount() == null || booking.getTotalAmount().signum() <= 0) {
            throw new IllegalArgumentException("Booking total must be positive");
        }
        if (booking.getPaidAmount() != null && booking.getPaidAmount().signum() != 0) {
            throw new IllegalArgumentException("Booking already has a received payment; reconciliation is required");
        }
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Cannot start payment for booking with status: " + booking.getStatus());
        }
    }

    private void validateHotelPaymentRequest(CreatePaymentRequest request) {
        if (request.getMethod() != PaymentMethod.HOTEL) {
            throw new IllegalArgumentException("Use initVnPayPayment to create VNPay payments");
        }
    }

    private String providerAmount(BigDecimal amount) {
        try {
            String encoded = amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString();
            if (encoded.length() > 12) throw new IllegalArgumentException("Amount exceeds VNPay supported range");
            return encoded;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount cannot be represented exactly by VNPay");
        }
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
                .vnpayTransactionNo(payment.getVnpayTransactionNo())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
