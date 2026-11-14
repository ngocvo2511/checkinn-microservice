package com.example.bookingservice.payment.service;

import static com.example.bookingservice.contract.UuidContract.*;
import java.util.UUID;
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
    private final PaymentCreationPolicyFactory paymentCreationPolicyFactory;
    private final org.springframework.context.ApplicationEventPublisher applicationEvents;
    private final com.example.bookingservice.payment.refund.RefundRequestService refundRequests;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("[CREATE_PAYMENT] Request received - bookingId: {}, method: {}", request.getBookingId(), request.getMethod());
        validateHotelPaymentRequest(request);

        // Validate booking exists
        Booking booking = bookingRepository.findByIdForUpdate(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + request.getBookingId()));
        log.info("[CREATE_PAYMENT] Booking loaded - bookingId: {}, userId: {}", booking.getId(), booking.getUserId());
        var existingOnsite = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);
        if (booking.getOnsiteReservationStatus() != null && booking.getPaidAmount() != null && booking.getPaidAmount().signum() == 0 && existingOnsite != null
                && existingOnsite.getMethod() == PaymentMethod.HOTEL && existingOnsite.getStatus() == PaymentStatus.ONSITE_PENDING
                && java.util.Set.of(BookingStatus.PENDING, BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                    BookingStatus.CHECKED_IN).contains(booking.getStatus())) {
            if (existingOnsite.getAmount() == null || request.getAmount() == null || request.getAmount().compareTo(existingOnsite.getAmount()) != 0)
                throw new IllegalArgumentException("Payment amount must equal the accepted server total");
            applicationEvents.publishEvent(new com.example.bookingservice.booking.service.OnsiteReservationService.Requested(booking.getId()));
            return toPaymentResponse(existingOnsite);
        }
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

        booking.setOnsiteReservationStatus("V1_PENDING");
        booking.setOnsiteNextAttemptAt(LocalDateTime.now());
        booking.setOnsiteLastError(null);
        bookingRepository.save(booking);
        payment = paymentRepository.save(payment);
        applicationEvents.publishEvent(new com.example.bookingservice.booking.service.OnsiteReservationService.Requested(booking.getId()));
        eventPublisher.publishBookingStatus(com.example.bookingservice.messaging.BookingStatusSnapshots.from(booking));
        log.info("[CREATE_PAYMENT] Payment created - paymentId: {}, bookingId: {}, method: {}", payment.getId(), payment.getBookingId(), payment.getMethod());
        return toPaymentResponse(payment);
    }

    @Transactional
    public VnPayInitResponse initVnPayPayment(UUID bookingId, String clientIp) {
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
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        payment.setVnpayCreateDate(VnPayUtil.getCreateDate(now));
        creationPolicy.updateBookingAfterPaymentCreated(booking);
        if (creationPolicy.shouldPersistBooking()) {
            bookingRepository.save(booking);
        }
        payment = paymentRepository.save(payment);

        // Build VNPay params
        String txnRef = text(payment.getId());
        payment.setVnpayOrderId(txnRef);

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
        params.put("vnp_CreateDate", payment.getVnpayCreateDate());
        params.put("vnp_ExpireDate", VnPayUtil.getExpireDate(now, 15));

        String query = VnPayUtil.buildQuery(params);
        String secureHash = VnPayUtil.hmacSHA512(vnPayProperties.getHashSecret(), query);
        String redirectUrl = vnPayProperties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;

        paymentRepository.save(payment);
        eventPublisher.publishBookingStatus(com.example.bookingservice.messaging.BookingStatusSnapshots.from(booking));
        log.info("[INIT_VNPAY] VNPay init completed - bookingId: {}, orderId: {}", bookingId, txnRef);

        return VnPayInitResponse.builder()
            .redirectUrl(redirectUrl)
            .orderId(txnRef)
            .build();
        }

    @Transactional
    public PaymentResponse processVNPayCallback(String orderId, String responseCode, String transactionId) {
        return processVNPayCallback(orderId, responseCode, transactionId, null);
    }

    @Transactional
    public PaymentResponse processVNPayCallback(String orderId, String responseCode, String transactionId,
                                               java.time.Instant transactionAt) {
        log.info("[VNPAY_CALLBACK] Received callback - orderId: {}, responseCode: {}, transactionId: {}", orderId, responseCode, transactionId);
        UUID bookingId = paymentRepository.findBookingIdByVnpayOrderId(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        // All mutations acquire booking before payment to avoid lock-order inversions with cancellation/refund.
        Booking lockedBooking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        Payment payment = paymentRepository.findByVnpayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));

        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.REFUNDED) {
            validateVnPayReplay(payment, responseCode, transactionId);
            if (transactionAt != null && payment.getTransactionOccurredAt() != null
                    && !transactionAt.equals(payment.getTransactionOccurredAt())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Stored transaction time requires reconciliation");
            }
            if (payment.getStatus() == PaymentStatus.COMPLETED && "V1_PENDING".equals(lockedBooking.getPaidReservationStatus()))
                applicationEvents.publishEvent(new com.example.bookingservice.booking.service.PaidReservationService.Requested(lockedBooking.getId()));
            if (payment.getStatus() == PaymentStatus.COMPLETED && pointsSettlementAllowed(lockedBooking)) {
                bookingService.settleLoyaltyPoints(lockedBooking);
            }
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
            payment.setTransactionOccurredAt(transactionAt);
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking
            Booking booking = lockedBooking;
            BigDecimal previouslyPaid = booking.getPaidAmount() == null ? BigDecimal.ZERO : booking.getPaidAmount();
            booking.setPaidAmount(previouslyPaid.add(payment.getAmount()));
            booking.setPaidReservationStatus("V1_PENDING");
            booking.setPaidReservationPaymentId(payment.getId());
            booking.setPaidReservationNextAttemptAt(LocalDateTime.now());
            booking.setPaidReservationLastError(null);
            bookingRepository.save(booking);
            applicationEvents.publishEvent(new com.example.bookingservice.booking.service.PaidReservationService.Requested(booking.getId()));

            PaymentEvent event=buildPaymentEvent(booking,payment,PaymentStatus.COMPLETED.name());
            eventPublisher.publishPaymentCompleted(event);

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(transactionId);

            // Update booking back to PENDING so user can retry payment or choose another method
            Booking booking = lockedBooking;
            if ((booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                    && (booking.getPaidAmount() == null || booking.getPaidAmount().signum() == 0)) {
                boolean changed=booking.getStatus()!=BookingStatus.PENDING;
                booking.setStatus(BookingStatus.PENDING);
                bookingRepository.save(booking);
                if(changed) eventPublisher.publishBookingStatus(com.example.bookingservice.messaging.BookingStatusSnapshots.from(booking));
            }

            log.info("[VNPAY_CALLBACK] Payment failed for orderId: {}, bookingId: {}, responseCode: {}", orderId, booking.getId(), responseCode);
            // Keep the room hold until holdExpiresAt so the user can retry payment.
            // If the customer does not retry in time, BookingScheduledTask cancels the booking.
    }

        payment = paymentRepository.save(payment);
        return toPaymentResponse(payment);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        return toPaymentResponse(payment);
    }

    public PaymentResponse getPaymentByBookingId(UUID bookingId) {
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking: " + bookingId));
        return toPaymentResponse(payment);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate refundJdbc;
    @org.springframework.beans.factory.annotation.Value("${refund.internal.enabled:false}")
    private boolean sandboxRefundEnabled;

    @Transactional
    public PaymentResponse refundPayment(UUID paymentId) {
        log.info("[REFUND_PAYMENT] Request received - paymentId: {}", paymentId);
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (actor == null) actor = com.example.bookingservice.grpc.interceptor.UserContextInterceptor.ACTOR.get();
        if (sandboxRefundEnabled && (actor == null || !actor.isAuthenticated() || actor.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"ADMIN required");
        // Lock order matches cancellation and callback. Replay must not call completed-only validation.
        if (sandboxRefundEnabled) {
            UUID bookingId=paymentRepository.findBookingIdById(paymentId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Payment not found"));
            Booking b=bookingRepository.findByIdForUpdate(bookingId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Booking not found"));
            Payment p=paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            var r=refundRequests.get(paymentId);
            if (r.getStatus()==com.example.bookingservice.payment.refund.RefundRequest.Status.REFUNDED) {
                if (!paymentId.equals(r.getPaymentId()) || !bookingId.equals(r.getBookingId()) || r.getAmount()==null || r.getAmount().compareTo(p.getAmount())!=0 || b.getStatus()!=BookingStatus.CANCELLED || p.getStatus()!=PaymentStatus.REFUNDED || !"SANDBOX_INTERNAL".equals(r.getExecutionMode()))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Refund requires review");
                return toPaymentResponse(p);
            }
            refundRequests.validateExecution(paymentId);
            var old=refundJdbc.queryForList("SELECT state FROM manual_refund_executions WHERE payment_id=? FOR UPDATE",paymentId);
            if (!old.isEmpty() && !"PENDING_MANUAL_REFUND".equals(old.getFirst().get("state")))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Existing manual evidence requires review");
            java.time.Instant completed=java.time.Instant.now().truncatedTo(ChronoUnit.MICROS);
            refundJdbc.update("INSERT INTO manual_refund_executions(id,request_id,payment_id,booking_id,amount,currency,provider,state,execution_method) VALUES (?,?,?,?,?,'VND',?,'REFUNDED','SANDBOX_INTERNAL') ON CONFLICT(payment_id) DO UPDATE SET state='REFUNDED',execution_method='SANDBOX_INTERNAL',updated_at=now()",
                java.util.UUID.randomUUID(),r.getId(),paymentId,bookingId,r.getAmount(),p.getMethod().name());
            refundJdbc.update("INSERT INTO manual_refund_audit(id,execution_id,action,actor_id) SELECT ?,id,'SANDBOX_INTERNAL_REFUNDED',? FROM manual_refund_executions WHERE payment_id=?",java.util.UUID.randomUUID(),actor.getName(),paymentId);
            r.setStatus(com.example.bookingservice.payment.refund.RefundRequest.Status.REFUNDED);
            r.setExecutionMode("SANDBOX_INTERNAL");r.setRefundedAt(completed);
            p.setStatus(PaymentStatus.REFUNDED);
            b.setPaidAmount(b.getPaidAmount().subtract(r.getAmount()));
            var event=buildPaymentEvent(b,p,PaymentStatus.REFUNDED.name()).toBuilder()
                .transactionId("sandbox-refund:"+r.getId()).transactionAt(completed)
                .executionMode("SANDBOX_INTERNAL").currency("VND").build();
            eventPublisher.publishPaymentRefunded(event);
            return toPaymentResponse(p);
        }
        refundRequests.validateExecution(paymentId);
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Internal refund disabled; payment unchanged");
    }

    @Transactional
    public PaymentResponse confirmHotelPayment(UUID bookingId) {
        log.info("[CONFIRM_HOTEL_PAYMENT] Request received - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        var previous = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId).orElse(null);
        if (previous != null && previous.getMethod() == PaymentMethod.HOTEL && previous.getStatus() == PaymentStatus.COMPLETED) {
            if (pointsSettlementAllowed(booking)) bookingService.settleLoyaltyPoints(booking);
            return toPaymentResponse(previous);
        }

        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.ONSITE_PENDING)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for booking: " + bookingId));
        if (payment.getAmount() == null || booking.getTotalAmount() == null || payment.getAmount().signum() <= 0
                || payment.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Stored onsite payment amount requires reconciliation");
        }

        if (booking.getOnsiteReservationStatus() != null && (!"V1_CONFIRMED".equals(booking.getOnsiteReservationStatus())
                || !java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT).contains(booking.getStatus()))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Reservation must be confirmed before an onsite receipt is accepted");
        }
        // Mark payment as completed
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionOccurredAt(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        payment = paymentRepository.save(payment);

        // Update booking paid amount
        booking.setPaidAmount(booking.getTotalAmount());
        if(confirmReservation(booking)) eventPublisher.publishBookingStatus(com.example.bookingservice.messaging.BookingStatusSnapshots.from(booking));
        if (pointsSettlementAllowed(booking)) bookingService.settleLoyaltyPoints(booking);
        bookingRepository.save(booking);

        // Publish payment event
        PaymentEvent event = buildPaymentEvent(booking, payment, PaymentStatus.COMPLETED.name());
        eventPublisher.publishPaymentCompleted(event);

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

    private boolean pointsSettlementAllowed(Booking booking) {
        return (booking.getPaidReservationStatus() == null || "V1_CONFIRMED".equals(booking.getPaidReservationStatus()))
                && (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.CHECKED_IN
                    || booking.getStatus() == BookingStatus.CHECKED_OUT);
    }

    private PaymentEvent buildPaymentEvent(Booking booking, Payment payment, String statusLabel) {
        int rooms = booking.getItems() == null ? 0 : booking.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum();
        UUID roomTypeId = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeId()
            : null;
        
        // Lấy room type name nếu có
        String roomType = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeName()
            : null;
            
        int nights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        int numberOfGuests = (booking.getAdults() != null ? booking.getAdults() : 0) + 
                             (booking.getChildren() != null ? booking.getChildren() : 0);
        
        return PaymentEvent.builder()
            .bookingId(text(booking.getId()))
            .customerId(text(booking.getUserId()))
            .hotelId(text(booking.getHotelId()))
            .hotelName(booking.getHotelName())
            .roomTypeId(text(roomTypeId))
            .roomType(roomType)
            .userId(text(booking.getUserId()))
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
            .paymentId(text(payment.getId()))
            .transactionId(!PaymentStatus.COMPLETED.name().equals(statusLabel)?null:payment.getMethod()==PaymentMethod.HOTEL?text(payment.getId()):payment.getVnpayTransactionNo())
            .transactionAt(!PaymentStatus.COMPLETED.name().equals(statusLabel)?null:payment.getTransactionOccurredAt())
            .build();
    }

    private BookingStatusEvent buildBookingStatusEvent(Booking booking, String statusLabel) {
        int rooms = booking.getItems() == null ? 0 : booking.getItems().stream()
            .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
            .sum();
        UUID roomTypeId = booking.getItems() != null && !booking.getItems().isEmpty()
            ? booking.getItems().get(0).getRoomTypeId()
            : null;
        int nights = (int) ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        return BookingStatusEvent.builder()
            .bookingId(text(booking.getId()))
            .hotelId(text(booking.getHotelId()))
            .roomTypeId(text(roomTypeId))
            .checkInDate(booking.getCheckInDate())
            .checkOutDate(booking.getCheckOutDate())
            .nights(nights)
            .rooms(rooms)
            .bookingStatus(statusLabel)
            .eventAt(LocalDateTime.now())
            .build();
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
                .refundExecutionMode(payment.getStatus()==PaymentStatus.REFUNDED ? refundRequests.get(payment.getId()).getExecutionMode() : null)
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
