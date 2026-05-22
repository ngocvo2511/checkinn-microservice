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

    // Giới hạn giảm giá tối đa khi sử dụng điểm: 50% tổng tiền
    private static final BigDecimal MAX_DISCOUNT_PERCENTAGE = new BigDecimal("0.5");

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        log.debug("[CREATE_BOOKING] Creating booking for hotel: {}, checkIn: {}, checkOut: {}",
                request.getHotelId(), request.getCheckInDate(), request.getCheckOutDate());

        // Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Booking must have at least one item");
        }

        // Enforce same room type for multiple rooms
        if (request.getItems().size() > 1) {
            String firstRoomTypeId = request.getItems().get(0).getRoomTypeId();
            boolean allSameRoomType = request.getItems().stream()
                    .allMatch(item -> item.getRoomTypeId().equals(firstRoomTypeId));

            if (!allSameRoomType) {
                throw new IllegalArgumentException("All items must have the same room type");
            }
        }

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

        // Calculate total amount
        BigDecimal totalAmount = request.getItems().stream()
                .map(item -> {
                    long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
                    return item.getUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
                            .multiply(BigDecimal.valueOf(nights));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply voucher discount if provided
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isEmpty()) {
            // TODO: Call voucher service to validate and get discount
            voucherDiscount = BigDecimal.ZERO;
        }

        // Apply loyalty points discount if provided
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        Long usedPoints = null;
        if (request.getPointsToUse() != null && request.getPointsToUse() > 0) {
            try {
                // Validate user has enough points
                LoyaltyPointsDTO userPoints = loyaltyPointsClient.getLoyaltyPoints(request.getUserId());
                Long availablePoints = userPoints.getAvailablePoints();
                
                if (availablePoints < request.getPointsToUse()) {
                    throw new IllegalArgumentException(
                            String.format("Insufficient points. Available: %d, Requested: %d", 
                                    availablePoints, request.getPointsToUse()));
                }
                
                // Calculate discount: 1 point = 1000 VND
                pointsDiscount = BigDecimal.valueOf(request.getPointsToUse() * 1000);
                usedPoints = request.getPointsToUse();

                // Validate: discount cannot exceed 50% of total amount
                BigDecimal maxDiscount = totalAmount.multiply(MAX_DISCOUNT_PERCENTAGE);
                if (pointsDiscount.compareTo(maxDiscount) > 0) {
                    log.warn("Points discount {} exceeds {}% limit of {}. Capping to max discount.",
                            pointsDiscount, MAX_DISCOUNT_PERCENTAGE.multiply(new BigDecimal("100")).intValue(), maxDiscount);
                    pointsDiscount = maxDiscount;
                }

                log.debug("[CREATE_BOOKING] Successfully calculated {} points discount: {} VND (max allowed: {} VND)",
                    request.getPointsToUse(), pointsDiscount, maxDiscount);
            } catch (Exception e) {
                log.warn("Failed to calculate points discount: {}", e.getMessage());
                pointsDiscount = BigDecimal.ZERO;
                usedPoints = null; // Reset usedPoints on failure
            }
        }

        // Keep booking hold expiry aligned with hold-service response
        LocalDateTime holdExpiry = parseHoldExpiry(holdResponse);
        Booking booking = Booking.builder()
                .userId(request.getUserId())
                .hotelId(request.getHotelId())
                .hotelName(request.getHotelName())
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

        booking = bookingRepository.save(booking);
        final Booking bookingRef = booking;

        // Create booking items
        List<BookingItem> items = request.getItems().stream()
                .map(itemRequest -> {
                    long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
                    BigDecimal subtotal = itemRequest.getUnitPrice()
                            .multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                            .multiply(BigDecimal.valueOf(nights));

                    return BookingItem.builder()
                            .booking(bookingRef)
                            .roomTypeId(itemRequest.getRoomTypeId())
                            .roomTypeName(itemRequest.getRoomTypeName())
                            .ratePlanId(itemRequest.getRatePlanId())
                            .checkInDate(itemRequest.getCheckInDate())
                            .checkOutDate(itemRequest.getCheckOutDate())
                            .quantity(itemRequest.getQuantity())
                            .unitPrice(itemRequest.getUnitPrice())
                            .nights((int) nights)
                            .subtotal(subtotal)
                            .taxFee(BigDecimal.ZERO)
                            .cancellationPolicy(itemRequest.getCancellationPolicy())
                            .guestName(itemRequest.getGuestName())
                            .build();
                })
                .collect(Collectors.toList());

        bookingItemRepository.saveAll(items);
        booking.setItems(items);

        publishStatusEvent(booking, BookingStatus.PENDING.name());

        return toBookingResponse(booking);
    }

    public BookingResponse getBooking(String bookingId) {
        log.debug("[GET_BOOKING] Fetching booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        log.debug("[GET_BOOKING] Booking fetched successfully - bookingId: {}, status: {}", bookingId, booking.getStatus());
        return toBookingResponse(booking);
    }

    public List<BookingResponse> getUserBookings(String userId) {
        log.debug("[GET_USER_BOOKINGS] Fetching bookings for userId: {}", userId);
        List<BookingResponse> responses = bookingRepository.findByUserId(userId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
        log.debug("[GET_USER_BOOKINGS] Found {} bookings for userId: {}", responses.size(), userId);
        return responses;
    }

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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.setStatus(status);
        booking = bookingRepository.save(booking);
        publishStatusEvent(booking, status.name());
        log.debug("[UPDATE_BOOKING_STATUS] Booking status updated successfully - bookingId: {}, status: {}", bookingId, status);
        return toBookingResponse(booking);
    }

    @Transactional
    public void cancelBooking(String bookingId) {
        log.debug("[CANCEL_BOOKING] Cancelling booking - bookingId: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED ||
                booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw new IllegalArgumentException("Cannot cancel booking with status: " + booking.getStatus());
        }

        // Release hold if exists
        if (booking.getHoldId() != null) {
            try {
                log.debug("[CANCEL_BOOKING] Releasing hold - bookingId: {}, holdId: {}", bookingId, booking.getHoldId());
                availabilityClient.releaseHold(booking.getHoldId());
            } catch (Exception e) {
                // Log but don't fail the cancellation
                log.warn("[CANCEL_BOOKING] Failed to release hold - bookingId: {}, holdId: {}, error: {}",
                        bookingId, booking.getHoldId(), e.getMessage(), e);
            }
        }

        // Hoàn lại điểm khi hủy booking
        if (booking.getEarnedPoints() != null && booking.getEarnedPoints() > 0) {
            try {
                loyaltyPointsClient.refundPoints(booking.getUserId(), bookingId);
                log.debug("Successfully refunded points for cancelled booking: {}", bookingId);
            } catch (Exception e) {
                log.warn("Failed to refund points for booking {}: {}", bookingId, e.getMessage());
            }
        }

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

        // Tích điểm khi booking được confirm (hoàn thành thanh toán)
        try {
            Long earnedPoints = calculateEarnedPoints(booking.getTotalAmount().add(
                    booking.getVoucherDiscount() != null ? booking.getVoucherDiscount() : BigDecimal.ZERO
            ).add(
                    booking.getPointsDiscountAmount() != null ? booking.getPointsDiscountAmount() : BigDecimal.ZERO
            ));
            booking.setEarnedPoints(earnedPoints);
            bookingRepository.save(booking);

            loyaltyPointsClient.earnPoints(
                    booking.getUserId(),
                    bookingId,
                    booking.getTotalAmount().add(
                            booking.getVoucherDiscount() != null ? booking.getVoucherDiscount() : BigDecimal.ZERO
                    ).add(
                            booking.getPointsDiscountAmount() != null ? booking.getPointsDiscountAmount() : BigDecimal.ZERO
                    )
            );
            log.debug("Successfully earned {} points for user: {}", earnedPoints, booking.getUserId());
        } catch (Exception e) {
            log.warn("Failed to earn points for booking {}: {}", bookingId, e.getMessage());
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

    /**
     * Tính điểm từ tổng tiền (1000 VND = 1 điểm)
     */
    private Long calculateEarnedPoints(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        // Tỷ lệ tích điểm: 1000 VND = 1 điểm (cần phải đặt phòng nhiều để tích)
        BigDecimal earnConversionRate = new BigDecimal("1000");
        return totalAmount.divide(earnConversionRate, 0, java.math.RoundingMode.DOWN).longValue();
    }
}
