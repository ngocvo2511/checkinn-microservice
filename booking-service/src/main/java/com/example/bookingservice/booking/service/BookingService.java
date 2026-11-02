package com.example.bookingservice.booking.service;

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
import com.example.bookingservice.integration.loyalty.ApplyPointsRequestDTO;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsDTO;
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

    // Giới hạn giảm giá tối đa khi sử dụng điểm: 50% tổng tiền
    private static final BigDecimal MAX_DISCOUNT_PERCENTAGE = new BigDecimal("0.5");

    @Transactional(readOnly = true)
    public boolean isReviewEligible(String bookingId, String guestId, String hotelId) {
        if (bookingId == null || bookingId.isBlank() || guestId == null || guestId.isBlank()
                || hotelId == null || hotelId.isBlank()) return false;
        return bookingRepository.findById(bookingId)
                .filter(booking -> guestId.equals(booking.getUserId()) && hotelId.equals(booking.getHotelId())
                        && booking.getStatus() == BookingStatus.CHECKED_OUT)
                .isPresent();
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        log.debug("[CREATE_BOOKING] Creating booking for hotel: {}, checkIn: {}, checkOut: {}",
                request.getHotelId(), request.getCheckInDate(), request.getCheckOutDate());

        // Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Booking must have at least one item");
        }

        validateBookingInput(request);

        // Enforce same room type for multiple rooms
        if (request.getItems().size() > 1) {
            String firstRoomTypeId = request.getItems().get(0).getRoomTypeId();
            boolean allSameRoomType = request.getItems().stream()
                    .allMatch(item -> item.getRoomTypeId().equals(firstRoomTypeId));

            if (!allSameRoomType) {
                throw new IllegalArgumentException("All items must have the same room type");
            }
        }

        HotelCatalogClient.Room catalogRoom = catalogClient.getRoom(request.getItems().get(0).getRoomTypeId());
        HotelCatalogClient.Hotel catalogHotel = catalogClient.getHotel(request.getHotelId());
        validateCatalogOffer(request, catalogRoom, catalogHotel);

        // Hold rooms before creating booking
        String roomTypeId = request.getItems().get(0).getRoomTypeId();
        int totalQuantity = request.getItems().stream()
                .mapToInt(item -> item.getQuantity())
                .sum();

        HoldRequest holdRequest = new HoldRequest(
                UUID.fromString(roomTypeId),
                request.getCheckInDate(),
                request.getCheckOutDate(),
                totalQuantity
        );

        log.debug("[CREATE_BOOKING] Attempting to hold rooms with request: roomTypeId={}, quantity={}", roomTypeId, totalQuantity);

        HoldResponse holdResponse;
        try {
            holdResponse = availabilityClient.holdRooms(holdRequest);
            log.debug("[CREATE_BOOKING] Successfully held rooms, holdId: {}", holdResponse.holdId());
        } catch (Exception e) {
            log.error("Failed to hold rooms", e);
            throw new IllegalStateException("Failed to hold rooms: " + e.getMessage());
        }

        try {
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

        // Apply loyalty points discount if provided
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        Long usedPoints = null;
        if (request.getPointsToUse() != null && request.getPointsToUse() > 0) {
            try {
                var quote = loyaltyPointsClient.quote(new com.example.bookingservice.integration.loyalty.PointsQuoteRequest(
                        request.getUserId(), request.getPointsToUse(), totalAmount.subtract(voucherDiscount)));
                BigDecimal maxDiscount = totalAmount.subtract(voucherDiscount).multiply(MAX_DISCOUNT_PERCENTAGE);
                if (quote == null || quote.redemptionRate() == null || quote.redemptionRate().signum() <= 0
                        || quote.pointsToUse() < 0 || quote.pointsToUse() > request.getPointsToUse()
                        || quote.discountAmount() == null || quote.discountAmount().signum() < 0
                        || quote.discountAmount().compareTo(maxDiscount) > 0
                        || quote.discountAmount().compareTo(quote.redemptionRate().multiply(BigDecimal.valueOf(quote.pointsToUse()))) != 0) {
                    throw new IllegalStateException("Invalid points quote");
                }
                pointsDiscount = quote.discountAmount();
                usedPoints = quote.pointsToUse() == 0 ? null : quote.pointsToUse();
            } catch (feign.FeignException e) {
                if (e.status() == 400 || e.status() == 409) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.valueOf(e.status()), "Points quote rejected");
                }
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Points quote unavailable; retry before booking");
            } catch (Exception e) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Points quote unavailable; retry before booking");
            }
        }

        // Keep booking hold expiry aligned with hold-service response
        LocalDateTime holdExpiry = parseHoldExpiry(holdResponse);
        Booking booking = Booking.builder()
                .userId(request.getUserId())
                .hotelId(request.getHotelId())
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
                            .roomTypeId(itemRequest.getRoomTypeId())
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

        publishStatusEvent(booking, BookingStatus.PENDING.name());

        return toBookingResponse(booking);
        } catch (RuntimeException failure) {
            try {
                availabilityClient.releaseHold(holdResponse.holdId());
            } catch (RuntimeException releaseFailure) {
                log.warn("Booking failed and hold release needs cleanup (type={})", releaseFailure.getClass().getSimpleName());
            }
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
            throw new IllegalArgumentException("Invalid hotel, stay dates or guest counts");
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
                throw new IllegalArgumentException("Invalid room quantity, price, dates or unsupported rate plan");
            }
            quantity += item.getQuantity();
            if (quantity > Integer.MAX_VALUE) throw new IllegalArgumentException("Room quantity is too large");
        }
    }

    private void validateCatalogOffer(CreateBookingRequest request, HotelCatalogClient.Room room, HotelCatalogClient.Hotel hotel) {
        if (room == null || hotel == null || !request.getHotelId().equals(room.hotelId())
                || !request.getHotelId().equals(hotel.id())
                || !request.getItems().get(0).getRoomTypeId().equals(room.id())
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
    public BookingResponse getBooking(String bookingId) {
        log.debug("[GET_BOOKING] Fetching booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        log.debug("[GET_BOOKING] Booking fetched successfully - bookingId: {}, status: {}", bookingId, booking.getStatus());
        return toBookingResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userId) {
        log.debug("[GET_USER_BOOKINGS] Fetching bookings for userId: {}", userId);
        List<BookingResponse> responses = bookingRepository.findByUserId(userId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
        log.debug("[GET_USER_BOOKINGS] Found {} bookings for userId: {}", responses.size(), userId);
        return responses;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getHotelBookings(String hotelId) {
        log.debug("[GET_HOTEL_BOOKINGS] Fetching bookings for hotelId: {}", hotelId);
        List<BookingResponse> responses = bookingRepository.findByHotelId(hotelId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
        log.debug("[GET_HOTEL_BOOKINGS] Found {} bookings for hotelId: {}", responses.size(), hotelId);
        return responses;
    }

    @Transactional
    public BookingResponse updateBookingStatus(String bookingId, BookingStatus status) {
        log.debug("[UPDATE_BOOKING_STATUS] Updating booking status - bookingId: {}, status: {}", bookingId, status);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == status) return toBookingResponse(booking);
        boolean stayTransition = (booking.getStatus() == BookingStatus.CONFIRMED && status == BookingStatus.CHECKED_IN)
                || (booking.getStatus() == BookingStatus.CHECKED_IN && status == BookingStatus.CHECKED_OUT)
                || (booking.getStatus() == BookingStatus.CONFIRMED && status == BookingStatus.NO_SHOW);
        if (!stayTransition) throw new IllegalArgumentException("Unsupported stay transition; use payment or cancellation workflow");
        booking.setStatus(status);
        booking = bookingRepository.save(booking);
        publishStatusEvent(booking, status.name());
        log.debug("[UPDATE_BOOKING_STATUS] Booking status updated successfully - bookingId: {}, status: {}", bookingId, status);
        return toBookingResponse(booking);
    }

    @Transactional
    public void cancelBooking(String bookingId, String actorId) {
        cancelBookingInternal(bookingId, actorId, false, "BOOKING_CANCELLED_STANDARD_24H");
    }

    @Transactional
    public void cancelBookingReviewed(String bookingId, String actorId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("A review reason is required");
        cancelBookingInternal(bookingId, actorId, true, reason);
    }

    private void cancelBookingInternal(String bookingId, String actorId, boolean reviewed, String reason) {
        log.debug("[CANCEL_BOOKING] Cancelling booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
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
        if (booking.getHoldId() != null) {
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

    public void confirmBookingHold(String bookingId) {
        log.debug("[CONFIRM_BOOKING_HOLD] Confirming booking hold for booking: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() == null) {
            log.error("Booking {} has no holdId, cannot confirm hold", bookingId);
            throw new IllegalStateException("Booking has no holdId");
        }

        log.debug("[CONFIRM_BOOKING_HOLD] Confirming hold {} for booking {}", booking.getHoldId(), bookingId);
        try {
            availabilityClient.confirmHold(booking.getHoldId());
            log.debug("Successfully confirmed hold {} for booking {}", booking.getHoldId(), bookingId);
        } catch (Exception e) {
            log.error("Failed to confirm hold {} for booking {}: {}", booking.getHoldId(), bookingId, e.getMessage(), e);
            throw new RuntimeException("Failed to confirm hold: " + e.getMessage(), e);
        }

        settleLoyaltyPoints(booking);
    }

    public void settleLoyaltyPoints(Booking booking) {
        try {
            if (booking.getUsedPoints() != null && booking.getUsedPoints() > 0) {
                BigDecimal discount = loyaltyPointsClient.usePoints(new ApplyPointsRequestDTO(booking.getUserId(),
                        booking.getUsedPoints(), booking.getId(), "Points for booking " + booking.getId(), booking.getPointsDiscountAmount()));
                if (discount == null || booking.getPointsDiscountAmount() == null
                        || discount.compareTo(booking.getPointsDiscountAmount()) != 0) {
                    throw new IllegalStateException("Points receipt does not match booking quote");
                }
            }
            if (booking.getEarnedPoints() == null) {
                var receipt = loyaltyPointsClient.earnPoints(booking.getUserId(), booking.getId(), booking.getTotalAmount());
                if (receipt == null || receipt.getAwardedPoints() == null) {
                    throw new IllegalStateException("Missing booking points receipt");
                }
                booking.setEarnedPoints(receipt.getAwardedPoints());
                bookingRepository.save(booking);
            }
        } catch (Exception e) {
            // Payment receipt and confirmed inventory survive a loyalty outage.
            // Marker remains null so a verified payment replay can retry the same commands.
            log.warn("Loyalty settlement requires retry for booking {} (type={})", booking.getId(), e.getClass().getSimpleName());
        }
    }

    private void reverseBookingPoints(Booking booking) {
        try {
            loyaltyPointsClient.refundPoints(booking.getUserId(), booking.getId());
        } catch (feign.FeignException ex) {
            if (ex.status() == 409) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Points ledger requires reconciliation before cancellation can finish");
            }
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Points reversal is not confirmed; retry cancellation");
        } catch (RuntimeException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Points reversal is not confirmed; retry cancellation");
        }
    }

    private void publishStatusEvent(Booking booking, String statusLabel) {
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

        BookingStatusEvent event = BookingStatusEvent.builder()
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
        try {
            log.debug("[PUBLISH_EVENT] Publishing booking.status.changed - bookingId: {}, status: {}", booking.getId(), statusLabel);
            eventPublisher.publishBookingStatus(event);
            log.debug("[PUBLISH_EVENT] Published booking.status.changed - bookingId: {}, status: {}", booking.getId(), statusLabel);
        } catch (Exception e) {
            log.error("[PUBLISH_EVENT] Failed to publish booking.status.changed for bookingId: {}: {}", booking.getId(), e.getMessage(), e);
        }
    }

    public void releaseBookingHold(String bookingId) {
        log.info("[RELEASE_BOOKING_HOLD] Releasing booking hold - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() != null) {
            log.info("[RELEASE_BOOKING_HOLD] Releasing hold - bookingId: {}, holdId: {}", bookingId, booking.getHoldId());
            availabilityClient.releaseHold(booking.getHoldId());
        }
    }

    @Transactional
    public Booking ensureActiveHold(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(LocalDateTime.now())) {
            if (booking.getHoldId() != null) {
                try {
                    availabilityClient.releaseHold(booking.getHoldId());
                } catch (Exception e) {
                    log.warn("Failed to release expired hold {} for booking {}: {}",
                            booking.getHoldId(), booking.getId(), e.getMessage());
                }
            }

            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            publishStatusEvent(booking, BookingStatus.CANCELLED.name());
            throw new IllegalStateException("Booking hold has expired");
        }

        if (hasUsableHold(booking)) {
            return booking;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        publishStatusEvent(booking, BookingStatus.CANCELLED.name());
        throw new IllegalStateException("Booking hold is no longer valid");
    }

    private boolean hasUsableHold(Booking booking) {
        if (booking.getHoldId() == null || booking.getHoldId().isBlank()) {
            return false;
        }

        try {
            HoldResponse hold = availabilityClient.getHold(booking.getHoldId());
            if (hold == null || !"HELD".equalsIgnoreCase(hold.status())) {
                return false;
            }

            LocalDateTime expiresAt = parseHoldExpiry(hold);
            if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
                availabilityClient.releaseHold(booking.getHoldId());
                return false;
            }

            LocalDateTime currentExpiry = booking.getHoldExpiresAt();
            if (currentExpiry == null || !currentExpiry.isEqual(expiresAt)) {
                booking.setHoldExpiresAt(expiresAt);
                bookingRepository.save(booking);
            }
            return true;
        } catch (Exception e) {
            log.warn("Existing hold {} is not usable for booking {}: {}",
                    booking.getHoldId(), booking.getId(), e.getMessage());
            return false;
        }
    }

    private LocalDateTime parseHoldExpiry(HoldResponse holdResponse) {
        if (holdResponse == null || holdResponse.expiresAt() == null || holdResponse.expiresAt().isBlank()) {
            return LocalDateTime.now().plusMinutes(15);
        }

        try {
            Instant expiresAt = Instant.parse(holdResponse.expiresAt());
            return LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
        } catch (Exception e) {
            log.warn("Unable to parse hold expiry '{}', using default 15 minutes", holdResponse.expiresAt());
            return LocalDateTime.now().plusMinutes(15);
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

        String holdId = booking.getHoldId();
        LocalDateTime holdExpiresAt = booking.getHoldExpiresAt();

        // If holdExpiresAt not set in DB, try fetching from availability service
        if (holdId != null && holdExpiresAt == null) {
            try {
                HoldResponse hr = availabilityClient.getHold(holdId);
                if (hr != null && hr.expiresAt() != null && !hr.expiresAt().isBlank()) {
                    Instant exp = Instant.parse(hr.expiresAt());
                    holdExpiresAt = LocalDateTime.ofInstant(exp, ZoneId.systemDefault());
                }
            } catch (Exception e) {
                // Swallow; countdown will fallback client-side
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
                .usedPoints(booking.getUsedPoints())
                .pointsDiscountAmount(booking.getPointsDiscountAmount())
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .contactPhone(booking.getContactPhone())
                .specialRequests(booking.getSpecialRequests())
                .paymentMethod(paymentMethod)
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
