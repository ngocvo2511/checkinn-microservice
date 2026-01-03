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
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import lombok.RequiredArgsConstructor;
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
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final HotelAvailabilityClient availabilityClient;
    private final HotelEventPublisher eventPublisher;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
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

        HoldResponse holdResponse;
        try {
            holdResponse = availabilityClient.holdRooms(holdRequest);
        } catch (Exception e) {
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

        // Create booking entity with holdId
        Booking booking = Booking.builder()
                .userId(request.getUserId())
                .hotelId(request.getHotelId())
                .hotelName(request.getHotelName())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .adults(request.getAdults())
                .children(request.getChildren())
                .status(BookingStatus.PENDING)
                .totalAmount(totalAmount.subtract(voucherDiscount))
                .paidAmount(BigDecimal.ZERO)
                .voucherCode(request.getVoucherCode())
                .voucherDiscount(voucherDiscount)
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .specialRequests(request.getSpecialRequests())
                .holdId(holdResponse.holdId())
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        return toBookingResponse(booking);
    }

    public List<BookingResponse> getUserBookings(String userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponse updateBookingStatus(String bookingId, BookingStatus status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.setStatus(status);
        booking = bookingRepository.save(booking);
        publishStatusEvent(booking, status.name());
        return toBookingResponse(booking);
    }

    @Transactional
    public void cancelBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        
        if (booking.getStatus() == BookingStatus.CANCELLED || 
            booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw new IllegalArgumentException("Cannot cancel booking with status: " + booking.getStatus());
        }

        // Release hold if exists
        if (booking.getHoldId() != null) {
            try {
                availabilityClient.releaseHold(booking.getHoldId());
            } catch (Exception e) {
                // Log but don't fail the cancellation
                System.err.println("Failed to release hold: " + e.getMessage());
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        publishStatusEvent(booking, BookingStatus.CANCELLED.name());
    }

    public void confirmBookingHold(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() != null) {
            availabilityClient.confirmHold(booking.getHoldId());
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
        eventPublisher.publishBookingStatus(event);
        }
    public void releaseBookingHold(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getHoldId() != null) {
            availabilityClient.releaseHold(booking.getHoldId());
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
        LocalDateTime holdExpiresAt = null;
        if (holdId != null) {
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
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .contactPhone(booking.getContactPhone())
                .specialRequests(booking.getSpecialRequests())
                .holdId(holdId)
                .holdExpiresAt(holdExpiresAt)
                .items(itemResponses)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
