package com.example.bookingservice.booking.service;

import static com.example.bookingservice.contract.UuidContract.*;
import com.example.bookingservice.booking.dto.BookingItemResponse;
import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.entity.BookingItem;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingItemRepository;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.integration.hotel.HotelCatalogClient;
import com.example.bookingservice.integration.hotel.dto.HoldRequest;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final PaymentRepository paymentRepository;
    private final HotelAvailabilityClient availabilityClient;
    private final HotelEventPublisher eventPublisher;
    private final LoyaltyPointsClient loyaltyPointsClient;
    private final HotelCatalogClient catalogClient;
    private final CancellationPolicy cancellationPolicy;
    private final com.example.bookingservice.payment.refund.RefundRequestService refundRequests;
    private final BookingCreationLock creationLock;

    // Giới hạn giảm giá tối đa khi sử dụng điểm: 50% tổng tiền
    private static final BigDecimal MAX_DISCOUNT_PERCENTAGE = new BigDecimal("0.5");

    @Transactional(readOnly = true)
    public String getLoyaltyBookingState(UUID bookingId, UUID userId) {
        if (bookingId == null || userId == null) return "UNKNOWN";
        if (!creationLock.tryAcquire(bookingId)) return "UNKNOWN";
        return bookingRepository.findById(bookingId).map(booking -> {
            if (!userId.equals(booking.getUserId())) return "UNKNOWN";
            boolean unusedOnsiteNoShow = booking.getStatus() == BookingStatus.NO_SHOW && booking.getOnsiteReservationStatus() != null
                    && booking.getPaidAmount() != null && booking.getPaidAmount().signum() == 0;
            return booking.getStatus() == BookingStatus.CANCELLED || unusedOnsiteNoShow ? "CANCELLED" : "ACTIVE";
        }).orElse("MISSING");
    }

    @Transactional(readOnly = true)
    public boolean isReviewEligible(UUID bookingId, UUID guestId, UUID hotelId) {
        if (bookingId == null || guestId == null
                || hotelId == null) return false;
        return bookingRepository.findById(bookingId)
                .filter(booking -> guestId.equals(booking.getUserId()) && hotelId.equals(booking.getHotelId())
                        && booking.getStatus() == BookingStatus.CHECKED_OUT)
                .isPresent();
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        com.example.bookingservice.validation.InputValidation.require(request);
        String keyHash = BookingIdempotency.keyHash(request);
        String requestHash = keyHash == null ? null : BookingIdempotency.requestHash(request);
        if (keyHash != null) {
            // Transaction-scoped PostgreSQL lock also serializes the first insert across processes.
            bookingRepository.lockCreationCommand(keyHash);
            Booking accepted = bookingRepository.findByIdempotencyKeyHash(keyHash).orElse(null);
            if (accepted != null) {
                if (!required(request.getUserId()).equals(accepted.getUserId()) || !requestHash.equals(accepted.getIdempotencyRequestHash())) {
                    throw holdConflict("Idempotency key was already used for different booking input");
                }
                return toBookingResponse(accepted);
            }
        }
        log.debug("[CREATE_BOOKING] Creating booking for hotel: {}, checkIn: {}, checkOut: {}",
                request.getHotelId(), request.getCheckInDate(), request.getCheckOutDate());

        // Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Booking must have at least one item");
        }

        validateBookingInput(request);

        // Enforce same room type for multiple rooms
        if (request.getItems().size() > 1) {
            UUID firstRoomTypeId = required(request.getItems().get(0).getRoomTypeId());
            boolean allSameRoomType = request.getItems().stream()
                    .allMatch(item -> required(item.getRoomTypeId()).equals(firstRoomTypeId));

            if (!allSameRoomType) {
                throw new IllegalArgumentException("All items must have the same room type");
            }
        }

        HotelCatalogClient.Room catalogRoom = catalogClient.getRoom(request.getItems().get(0).getRoomTypeId());
        HotelCatalogClient.Hotel catalogHotel = catalogClient.getHotel(request.getHotelId());
        validateCatalogOffer(request, catalogRoom, catalogHotel);

        // Hold rooms before creating booking
        UUID roomTypeId = required(request.getItems().get(0).getRoomTypeId());
        int totalQuantity = request.getItems().stream()
                .mapToInt(item -> item.getQuantity())
                .sum();

        HoldRequest holdRequest = new HoldRequest(
                roomTypeId,
                request.getCheckInDate(),
                request.getCheckOutDate(),
                totalQuantity,
                keyHash == null ? null : BookingIdempotency.holdCommand(keyHash)
        );

        log.debug("[CREATE_BOOKING] Attempting to hold rooms with request: roomTypeId={}, quantity={}", roomTypeId, totalQuantity);

        HoldResponse holdResponse;
        try {
            holdResponse = availabilityClient.holdRooms(holdRequest);
            log.debug("[CREATE_BOOKING] Inventory hold request returned");
        } catch (Exception e) {
            log.error("Failed to hold rooms", e);
            if (e instanceof org.springframework.web.client.HttpClientErrorException conflict
                    && conflict.getStatusCode().value() == 409) {
                throw holdConflict("Requested inventory cannot be reserved; refresh availability or request review");
            }
            throw holdUnavailable();
        }

        var compensation=new BookingCreationCompensation(availabilityClient,loyaltyPointsClient,holdResponse,required(request.getUserId()));
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(compensation);
        try {
        requireHoldResponse(holdResponse, null);
        // Calculate total amount
        BigDecimal totalAmount = request.getItems().stream()
                .map(item -> {
                    long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
                    return catalogRoom.basePrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
                            .multiply(BigDecimal.valueOf(nights));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply voucher discount if provided
        BigDecimal voucherDiscount = BigDecimal.ZERO;

        // Start with canonical gross amount; reserve points only after the booking has a correlation ID.
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        Long usedPoints = null;

        // Keep booking hold expiry aligned with hold-service response
        LocalDateTime holdExpiry = parseHoldExpiry(holdResponse);
        Booking booking = Booking.builder()
                .id(keyHash == null ? null : BookingIdempotency.bookingId(keyHash))
                .idempotencyKeyHash(keyHash)
                .idempotencyRequestHash(requestHash)
                .userId(required(request.getUserId()))
                .hotelId(required(request.getHotelId()))
                .hotelName(catalogHotel.name())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .adults(request.getAdults())
                .children(request.getChildren())
                .status(BookingStatus.PENDING)
                .totalAmount(totalAmount.subtract(voucherDiscount).subtract(pointsDiscount))
                .paidAmount(BigDecimal.ZERO)
                .voucherCode(request.getVoucherCode())
                .voucherDiscount(voucherDiscount)
                .usedPoints(usedPoints)
                .pointsDiscountAmount(pointsDiscount)
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .specialRequests(request.getSpecialRequests())
                .holdId(holdResponse.holdId())
                .holdExpiresAt(holdExpiry)
                .build();

        cancellationPolicy.snapshot(booking, request.getAcceptedCancellationPolicyVersion());
        booking = bookingRepository.save(booking);
        if (request.getPointsToUse() != null && request.getPointsToUse() > 0) {
            try {
                if (!creationLock.tryAcquire(booking.getId())) throw new IllegalStateException("Booking creation is already in progress");
                Instant deadline = Instant.parse(holdResponse.expiresAt()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                compensation.reservedPointsFor(booking.getId()); // Set before RPC: timeout may hide committed points.
                var quote = loyaltyPointsClient.reserve(new com.example.bookingservice.integration.loyalty.ReservePointsRequestDTO(
                        required(request.getUserId()), booking.getId(), request.getPointsToUse(), totalAmount.subtract(voucherDiscount), deadline));
                BigDecimal maxDiscount = totalAmount.subtract(voucherDiscount).multiply(MAX_DISCOUNT_PERCENTAGE);
                if (quote == null || !booking.getId().equals(quote.bookingId()) || !"HELD".equals(quote.state())
                        || !deadline.equals(quote.holdExpiresAt()) || quote.redemptionRate() == null || quote.redemptionRate().signum() <= 0
                        || quote.pointsToUse() < 0 || quote.pointsToUse() > request.getPointsToUse()
                        || quote.discountAmount() == null || quote.discountAmount().signum() < 0
                        || quote.discountAmount().compareTo(maxDiscount) > 0
                        || quote.discountAmount().compareTo(quote.redemptionRate().multiply(BigDecimal.valueOf(quote.pointsToUse()))) != 0) {
                    throw new IllegalStateException("Invalid points reservation receipt");
                }
                booking.setUsedPoints(quote.pointsToUse() == 0 ? null : quote.pointsToUse());
                booking.setPointsDiscountAmount(quote.discountAmount());
                booking.setTotalAmount(totalAmount.subtract(voucherDiscount).subtract(quote.discountAmount()));
            } catch (feign.FeignException ex) {
                if (ex.status() == 400 || ex.status() == 409) {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.valueOf(ex.status()),
                            "Points reservation rejected");
                }
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Points reservation unavailable; retry before booking");
            } catch (Exception ex) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Points reservation unavailable; retry before booking");
            }
        }
        final Booking bookingRef = booking;

        // Create booking items
        List<BookingItem> items = request.getItems().stream()
                .map(itemRequest -> {
                    long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
                    BigDecimal subtotal = catalogRoom.basePrice()
                            .multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                            .multiply(BigDecimal.valueOf(nights));

                    return BookingItem.builder()
                            .booking(bookingRef)
                            .roomTypeId(required(itemRequest.getRoomTypeId()))
                            .roomTypeName(catalogRoom.name())
                            .ratePlanId(catalogRoom.id())
                            .checkInDate(itemRequest.getCheckInDate())
                            .checkOutDate(itemRequest.getCheckOutDate())
                            .quantity(itemRequest.getQuantity())
                            .unitPrice(catalogRoom.basePrice())
                            .nights((int) nights)
                            .subtotal(subtotal)
                            .taxFee(BigDecimal.ZERO)
                            .cancellationPolicy(CancellationPolicy.VERSION.equals(request.getAcceptedCancellationPolicyVersion())
                                    ? "STANDARD_24H_V1: Hủy miễn phí trước giờ nhận phòng ít nhất 24 giờ; hoàn tiền cần ADMIN duyệt. Trong 24 giờ hoặc đã nhận phòng: liên hệ hỗ trợ." : null)
                            .guestName(itemRequest.getGuestName())
                            .build();
                })
                .collect(Collectors.toList());

        bookingItemRepository.saveAll(items);
        booking.setItems(items);

        if (!holdExpiry.isAfter(LocalDateTime.now())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Inventory hold expired during booking creation");
        }
        publishStatusEvent(booking, BookingStatus.PENDING.name());

        return toBookingResponse(booking);
        } catch (RuntimeException failure) {
            compensation.run();
            throw failure;
        }
    }

    private void validateBookingInput(CreateBookingRequest request) {
        if (request.getAcceptedCancellationPolicyVersion() != null && !request.getAcceptedCancellationPolicyVersion().isBlank()
                && !CancellationPolicy.VERSION.equals(request.getAcceptedCancellationPolicyVersion())) {
            throw new IllegalArgumentException("Unsupported cancellation policy version");
        }
        if (request.getHotelId() == null || request.getHotelId().isBlank()
                || request.getCheckInDate() == null || request.getCheckOutDate() == null
                || !request.getCheckOutDate().isAfter(request.getCheckInDate())
                || request.getCheckInDate().isBefore(cancellationPolicy.today())
                || request.getAdults() == null || request.getAdults() < 1
                || request.getChildren() == null || request.getChildren() < 0) {
            throw new com.example.bookingservice.validation.InputValidation.InvalidInput(java.util.Map.of("checkOutDate","Stay dates must be ordered and arrival must not be in the past"));
        }
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            throw new IllegalArgumentException("Dịch vụ mã giảm giá hiện chưa khả dụng");
        }
        long quantity = 0;
        for (var item : request.getItems()) {
            if (item == null || item.getRoomTypeId() == null || item.getRoomTypeId().isBlank()
                    || item.getQuantity() == null || item.getQuantity() < 1
                    || item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0
                    || !request.getCheckInDate().equals(item.getCheckInDate())
                    || !request.getCheckOutDate().equals(item.getCheckOutDate())
                    || (item.getRatePlanId() != null && !item.getRatePlanId().isBlank()
                        && !item.getRatePlanId().equals(item.getRoomTypeId()))) {
                throw new com.example.bookingservice.validation.InputValidation.InvalidInput(java.util.Map.of("items","Room dates must match the stay and rate plan must match the room"));
            }
            quantity += item.getQuantity();
            if (quantity > Integer.MAX_VALUE) throw new IllegalArgumentException("Room quantity is too large");
        }
    }

    private void validateCatalogOffer(CreateBookingRequest request, HotelCatalogClient.Room room, HotelCatalogClient.Hotel hotel) {
        if (room == null || hotel == null || !required(request.getHotelId()).equals(room.hotelId())
                || !required(request.getHotelId()).equals(optional(hotel.id()))
                || !required(request.getItems().get(0).getRoomTypeId()).equals(optional(room.id()))
                || !Boolean.TRUE.equals(room.isActive()) || !Boolean.TRUE.equals(hotel.isActive())
                || !"APPROVED".equals(hotel.approvedStatus())
                || room.basePrice() == null || room.basePrice().signum() <= 0) {
            throw new IllegalArgumentException("Room does not belong to an active, approved hotel offer");
        }
        long quantity = request.getItems().stream().mapToLong(item -> item.getQuantity()).sum();
        if (room.totalRooms() == null || room.totalRooms() < quantity || room.capacity() == null
                || room.capacity().adults() == null || room.capacity().adults() < 1
                || room.capacity().children() == null || room.capacity().children() < 0
                || quantity * room.capacity().adults() < request.getAdults()
                || quantity * room.capacity().children() < request.getChildren()) {
            throw new IllegalArgumentException("Room quantity or capacity cannot satisfy the requested stay");
        }
        for (var item : request.getItems()) {
            if (item.getUnitPrice().compareTo(room.basePrice()) != 0) {
                throw new IllegalArgumentException("Room price has changed; refresh the offer before booking");
            }
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID bookingId) {
        log.debug("[GET_BOOKING] Fetching booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        log.debug("[GET_BOOKING] Booking fetched successfully - bookingId: {}, status: {}", bookingId, booking.getStatus());
        return toBookingResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(UUID userId) {
        log.debug("[GET_USER_BOOKINGS] Fetching bookings for userId: {}", userId);
        List<BookingResponse> responses = bookingRepository.findByUserId(userId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
        log.debug("[GET_USER_BOOKINGS] Found {} bookings for userId: {}", responses.size(), userId);
        return responses;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getHotelBookings(UUID hotelId) {
        log.debug("[GET_HOTEL_BOOKINGS] Fetching bookings for hotelId: {}", hotelId);
        List<BookingResponse> responses = bookingRepository.findByHotelId(hotelId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
        log.debug("[GET_HOTEL_BOOKINGS] Found {} bookings for hotelId: {}", responses.size(), hotelId);
        return responses;
    }

    @Transactional
    public BookingResponse updateBookingStatus(UUID bookingId, BookingStatus status) {
        log.debug("[UPDATE_BOOKING_STATUS] Updating booking status - bookingId: {}, status: {}", bookingId, status);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == status) return toBookingResponse(booking);
        boolean stayTransition = (booking.getStatus() == BookingStatus.CONFIRMED && status == BookingStatus.CHECKED_IN)
                || (booking.getStatus() == BookingStatus.CHECKED_IN && status == BookingStatus.CHECKED_OUT)
                || (booking.getStatus() == BookingStatus.CONFIRMED && status == BookingStatus.NO_SHOW);
        if (!stayTransition) throw new IllegalArgumentException("Unsupported stay transition; use payment or cancellation workflow");
        if (status == BookingStatus.NO_SHOW && booking.getOnsiteReservationStatus() != null) {
            if (booking.getCheckInDate() == null || booking.getCheckInDate().isAfter(cancellationPolicy.today()))
                throw new IllegalArgumentException("Cannot record no-show before the arrival date");
            queueOnsiteRelease(booking);
            var payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId).orElse(null);
            if (payment != null && payment.getStatus() == com.example.bookingservice.payment.enums.PaymentStatus.ONSITE_PENDING) {
                payment.setStatus(com.example.bookingservice.payment.enums.PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                reverseBookingPoints(booking);
            }
        }
        booking.setStatus(status);
        booking = bookingRepository.save(booking);
        publishStatusEvent(booking, status.name());
        log.debug("[UPDATE_BOOKING_STATUS] Booking status updated successfully - bookingId: {}, status: {}", bookingId, status);
        return toBookingResponse(booking);
    }

    @Transactional
    public void cancelBooking(UUID bookingId, UUID actorId) {
        cancelBookingInternal(bookingId, actorId, false, "BOOKING_CANCELLED_STANDARD_24H");
    }

    @Transactional
    public void cancelBookingReviewed(UUID bookingId, UUID actorId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("A review reason is required");
        cancelBookingInternal(bookingId, actorId, true, reason);
    }

    private void cancelBookingInternal(UUID bookingId, UUID actorId, boolean reviewed, String reason) {
        log.debug("[CANCEL_BOOKING] Cancelling booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            if (reviewed) retryCancelledInventoryAfterReview(booking, actorId, reason);
            reverseBookingPoints(booking);
            return;
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN || booking.getStatus() == BookingStatus.CHECKED_OUT
                || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalArgumentException("Cannot cancel booking with status: " + booking.getStatus());
        }
        var payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId).orElse(null);
        boolean paid = (booking.getPaidAmount() != null && booking.getPaidAmount().signum() > 0)
                || (payment != null && payment.getStatus() == com.example.bookingservice.payment.enums.PaymentStatus.COMPLETED);
        if (!reviewed && ((paid && !cancellationPolicy.freeCancellationAllowed(booking))
                || (!paid && !cancellationPolicy.beforeCheckIn(booking)))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Cancellation requires review: deadline passed or this booking has no accepted policy snapshot");
        }
        if (paid) {
            if (payment == null || payment.getStatus() != com.example.bookingservice.payment.enums.PaymentStatus.COMPLETED) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Payment needs reconciliation");
            }
            refundRequests.request(payment.getId(), actorId, reason);
        }
        if (booking.getHoldId() == null && (paid || booking.getStatus() == BookingStatus.CONFIRMED)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Missing inventory reservation; manual reconciliation required");
        }
        if (booking.getPaidReservationStatus() != null) {
            booking.setPaidReservationStatus("V1_PENDING");
            booking.setPaidReservationNextAttemptAt(LocalDateTime.now());
            booking.setPaidReservationLastError(null);
        } else if (booking.getOnsiteReservationStatus() != null) {
            queueOnsiteRelease(booking);
        } else if (booking.getHoldId() != null) {
            try {
                availabilityClient.cancelReservation(booking.getHoldId());
            } catch (Exception e) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Inventory cancellation is not confirmed; retry or request review");
            }
        }
        if (payment != null && (payment.getStatus() == com.example.bookingservice.payment.enums.PaymentStatus.PENDING
                || payment.getStatus() == com.example.bookingservice.payment.enums.PaymentStatus.ONSITE_PENDING)) {
            payment.setStatus(com.example.bookingservice.payment.enums.PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
        }

        // Reverse both earning and redemption, including unknown remote outcomes.
        reverseBookingPoints(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        publishStatusEvent(booking, BookingStatus.CANCELLED.name());
        log.debug("[CANCEL_BOOKING] Booking cancelled successfully - bookingId: {}", bookingId);
    }

    /** Existing ADMIN-only reviewed workflow may retry the same reconciled reference, never guess a new hold. */
    private void retryCancelledInventoryAfterReview(Booking booking, UUID actorId, String reason) {
        boolean paidReview="V1_REVIEW_REQUIRED".equals(booking.getPaidReservationStatus());
        boolean onsiteReview="V1_REVIEW_REQUIRED".equals(booking.getOnsiteReservationStatus());
        if (!paidReview && !onsiteReview) return;
        if (booking.getHoldId()==null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Original inventory reference must be reconciled before retry");
        booking.setInventoryReviewedBy(actorId);booking.setInventoryReviewReason(reason.strip());booking.setInventoryReviewedAt(Instant.now());
        if (paidReview) {
            booking.setPaidReservationStatus("V1_PENDING");booking.setPaidReservationNextAttemptAt(LocalDateTime.now());booking.setPaidReservationLastError(null);
        }
        if (onsiteReview) queueOnsiteRelease(booking);
        bookingRepository.save(booking);
    }

    private void queueOnsiteRelease(Booking booking) {
        booking.setOnsiteReservationStatus("V1_PENDING");
        booking.setOnsiteNextAttemptAt(LocalDateTime.now());
        booking.setOnsiteLastError(null);
    }

    // Inventory failure must not mark the caller's payment receipt transaction rollback-only.
    @Transactional(noRollbackFor = HoldConfirmationException.class)
    public void confirmBookingHold(UUID bookingId) {
        log.debug("[CONFIRM_BOOKING_HOLD] Confirming booking hold for booking: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() == null) {
            log.error("Booking {} has no holdId, cannot confirm hold", bookingId);
            throw new HoldConfirmationException(new IllegalStateException("Booking has no holdId"));
        }

        log.debug("[CONFIRM_BOOKING_HOLD] Confirming hold {} for booking {}", booking.getHoldId(), bookingId);
        try {
            availabilityClient.confirmHold(booking.getHoldId());
            log.debug("Successfully confirmed hold {} for booking {}", booking.getHoldId(), bookingId);
        } catch (Exception e) {
            log.error("Failed to confirm hold {} for booking {}: {}", booking.getHoldId(), bookingId, e.getMessage(), e);
            throw new HoldConfirmationException(e);
        }

        settleLoyaltyPoints(booking);
    }

    public static class HoldConfirmationException extends RuntimeException {
        public HoldConfirmationException(Throwable cause) {
            super("Failed to confirm hold", cause);
        }
    }

    /** Persist intent in the caller's booking/payment transaction; no remote ledger effect before commit. */
    public void settleLoyaltyPoints(Booking booking) {
        if ("SETTLED".equals(booking.getLoyaltyStatus()) || "REVIEW_REQUIRED".equals(booking.getLoyaltyStatus())) return;
        queueLoyalty(booking);
    }

    private void reverseBookingPoints(Booking booking) {
        if ("REVERSED".equals(booking.getLoyaltyStatus())) return;
        queueLoyalty(booking);
    }

    private void queueLoyalty(Booking booking) {
        booking.setLoyaltyStatus("PENDING");
        booking.setLoyaltyNextAttemptAt(LocalDateTime.now());
        booking.setLoyaltyLastError(null);
        bookingRepository.save(booking);
    }

    private void publishStatusEvent(Booking booking, String statusLabel) {
        eventPublisher.publishBookingStatus(com.example.bookingservice.messaging.BookingStatusSnapshots.from(booking));
    }

    public void releaseBookingHold(UUID bookingId) {
        log.info("[RELEASE_BOOKING_HOLD] Releasing booking hold - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() != null) {
            log.info("[RELEASE_BOOKING_HOLD] Releasing hold - bookingId: {}, holdId: {}", bookingId, booking.getHoldId());
            availabilityClient.releaseHold(booking.getHoldId());
        }
    }

    /** Payment eligibility check only. Cancellation belongs to the cancellation/expiry workflows.
     * Never emit a cancellation before throwing from a transaction that will roll back.
     */
    @Transactional(readOnly = true)
    public Booking ensureActiveHold(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getHoldId() == null) {
            throw holdConflict("Booking has no inventory hold; reconciliation required");
        }
        if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(LocalDateTime.now())) {
            throw holdConflict("Booking hold has expired");
        }

        HoldResponse hold;
        try {
            hold = availabilityClient.getHold(booking.getHoldId());
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw holdConflict("Inventory hold no longer exists");
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException ex) {
            throw holdUnavailable();
        }
        requireHoldResponse(hold, booking.getHoldId());
        LocalDateTime expiry = parseHoldExpiry(hold);
        if (!expiry.isAfter(LocalDateTime.now())) throw holdConflict("Booking hold has expired");
        return booking;
    }

    private void requireHoldResponse(HoldResponse hold, UUID expectedId) {
        if (hold == null || hold.holdId() == null
                || (expectedId != null && !expectedId.equals(hold.holdId())) || hold.status() == null) {
            throw holdUnavailable();
        }
        switch (hold.status()) {
            case "HELD" -> { }
            case "RELEASED", "EXPIRED", "CANCELLED", "CONFIRMED" ->
                    throw holdConflict("Inventory hold is not available for a new payment");
            default -> throw holdUnavailable();
        }
    }

    private org.springframework.web.server.ResponseStatusException holdUnavailable() {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Inventory hold could not be verified; retry or request reconciliation");
    }

    private org.springframework.web.server.ResponseStatusException holdConflict(String reason) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, reason);
    }

    private LocalDateTime parseHoldExpiry(HoldResponse holdResponse) {
        if (holdResponse == null || holdResponse.expiresAt() == null || holdResponse.expiresAt().isBlank()) {
            throw holdUnavailable();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(holdResponse.expiresAt()), ZoneId.systemDefault());
        } catch (java.time.format.DateTimeParseException ex) {
            throw holdUnavailable();
        }
    }

    private BookingResponse toBookingResponse(Booking booking) {
        List<BookingItemResponse> itemResponses = booking.getItems().stream()
                .map(item -> BookingItemResponse.builder()
                        .id(item.getId())
                        .roomTypeId(item.getRoomTypeId())
                        .roomTypeName(item.getRoomTypeName())
                        .ratePlanId(item.getRatePlanId())
                        .checkInDate(item.getCheckInDate())
                        .checkOutDate(item.getCheckOutDate())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .nights(item.getNights())
                        .subtotal(item.getSubtotal())
                        .taxFee(item.getTaxFee())
                        .cancellationPolicy(item.getCancellationPolicy())
                        .guestName(item.getGuestName())
                        .build())
                .collect(Collectors.toList());

        UUID holdId = booking.getHoldId();
        // Existing timestamp storage keeps the deployment's local clock convention.
        // The wire value must carry an offset so browsers do not reinterpret it in their own zone.
        Instant holdExpiresAt = booking.getHoldExpiresAt() == null ? null
                : booking.getHoldExpiresAt().atZone(ZoneId.systemDefault()).toInstant();

        // If holdExpiresAt not set in DB, try fetching from availability service
        if (holdId != null && holdExpiresAt == null) {
            try {
                HoldResponse hr = availabilityClient.getHold(holdId);
                if (hr != null && hr.expiresAt() != null && !hr.expiresAt().isBlank()) {
                    if (holdId.equals(hr.holdId())) holdExpiresAt = Instant.parse(hr.expiresAt());
                }
            } catch (Exception e) {
                // Unknown remains null; clients must not manufacture a local TTL.
            }
        }

        // Get payment method from Payment entity
        String paymentMethod = null;
        try {
            Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(booking.getId()).orElse(null);
            if (payment != null && payment.getMethod() != null) {
                paymentMethod = payment.getMethod().name();
            }
        } catch (Exception e) {
            log.debug("Could not fetch payment method for booking: {}", booking.getId());
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .hotelId(booking.getHotelId())
                .hotelName(booking.getHotelName())
                .checkInDate(booking.getCheckInDate())
                .checkOutDate(booking.getCheckOutDate())
                .adults(booking.getAdults())
                .children(booking.getChildren())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .paidAmount(booking.getPaidAmount())
                .voucherCode(booking.getVoucherCode())
                .voucherDiscount(booking.getVoucherDiscount())
                .earnedPoints(booking.getEarnedPoints())
                .loyaltyStatus(booking.getLoyaltyStatus())
                .usedPoints(booking.getUsedPoints())
                .pointsDiscountAmount(booking.getPointsDiscountAmount())
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .contactPhone(booking.getContactPhone())
                .specialRequests(booking.getSpecialRequests())
                .paymentMethod(paymentMethod)
                .onsiteReservationStatus(booking.getOnsiteReservationStatus())
                .paidReservationStatus(booking.getPaidReservationStatus())
                .holdId(holdId)
                .holdExpiresAt(holdExpiresAt)
                .cancellationPolicyVersion(booking.getCancellationPolicyVersion())
                .checkInAt(booking.getCheckInAt())
                .freeCancellationUntil(booking.getFreeCancellationUntil())
                .items(itemResponses)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    public long getTotalBookingsCount() {
        return bookingRepository.count();
    }

    public long getTodayBookingsCount() {
        return countToday();
    }

    private long countToday() {
        LocalDate today = LocalDate.now();
        return bookingRepository.countBookingsCreatedToday(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );
    }

}
