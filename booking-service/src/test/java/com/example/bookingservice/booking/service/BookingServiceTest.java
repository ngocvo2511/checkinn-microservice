package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.dto.CreateBookingItemRequest;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingItemRepository;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsDTO;
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private BookingRepository bookingRepository;
    private BookingItemRepository bookingItemRepository;
    private PaymentRepository paymentRepository;
    private HotelAvailabilityClient availabilityClient;
    private HotelEventPublisher eventPublisher;
    private LoyaltyPointsClient loyaltyPointsClient;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingItemRepository = mock(BookingItemRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        availabilityClient = mock(HotelAvailabilityClient.class);
        eventPublisher = mock(HotelEventPublisher.class);
        loyaltyPointsClient = mock(LoyaltyPointsClient.class);
        bookingService = new BookingService(
                bookingRepository,
                bookingItemRepository,
                paymentRepository,
                availabilityClient,
                eventPublisher,
                loyaltyPointsClient
        );
    }

    @Test
    void createBookingRejectsEmptyItemsBeforeHoldingRooms() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .hotelId("hotel-1")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .items(List.of())
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");

        verify(availabilityClient, never()).holdRooms(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingRejectsMultipleRoomTypesInOneBooking() {
        CreateBookingRequest request = baseBookingRequest(List.of(
                item(UUID.randomUUID().toString(), 1, BigDecimal.valueOf(1_000_000)),
                item(UUID.randomUUID().toString(), 1, BigDecimal.valueOf(1_000_000))
        ));

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same room type");

        verify(availabilityClient, never()).holdRooms(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingCapsPointsDiscountAtHalfOfTotalAmount() {
        String roomTypeId = UUID.randomUUID().toString();
        String expiresAt = Instant.now().plusSeconds(900).toString();
        CreateBookingRequest request = baseBookingRequest(List.of(
                item(roomTypeId, 1, BigDecimal.valueOf(1_000_000))
        ));
        request.setPointsToUse(900L);

        LoyaltyPointsDTO points = new LoyaltyPointsDTO();
        points.setAvailablePoints(1_000L);

        when(availabilityClient.holdRooms(any())).thenReturn(new HoldResponse("hold-1", "HELD", expiresAt));
        when(loyaltyPointsClient.getLoyaltyPoints("user-1")).thenReturn(points);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId("booking-1");
            return booking;
        });
        when(bookingItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc("booking-1")).thenReturn(Optional.empty());

        var response = bookingService.createBooking(request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo("500000");
        assertThat(response.getPointsDiscountAmount()).isEqualByComparingTo("500000");
        assertThat(response.getUsedPoints()).isEqualTo(900L);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void ensureActiveHoldCancelsBookingWhenHoldExpired() {
        Booking booking = Booking.builder()
                .id("booking-1")
                .userId("user-1")
                .hotelId("hotel-1")
                .hotelName("Tripto")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(1_000_000))
                .paidAmount(BigDecimal.ZERO)
                .holdId("hold-1")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1))
                .items(List.of())
                .build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> bookingService.ensureActiveHold("booking-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(availabilityClient).releaseHold("hold-1");
        verify(bookingRepository).save(booking);
    }

    private CreateBookingRequest baseBookingRequest(List<CreateBookingItemRequest> items) {
        return CreateBookingRequest.builder()
                .userId("user-1")
                .hotelId("hotel-1")
                .hotelName("Tripto Hotel")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .adults(2)
                .children(0)
                .contactName("RTM Customer")
                .contactEmail("rtm@example.com")
                .contactPhone("912345678")
                .items(items)
                .build();
    }

    private CreateBookingItemRequest item(String roomTypeId, int quantity, BigDecimal unitPrice) {
        return CreateBookingItemRequest.builder()
                .roomTypeId(roomTypeId)
                .roomTypeName("RTM Room")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .quantity(quantity)
                .unitPrice(unitPrice)
                .guestName("RTM Customer")
                .build();
    }
}
