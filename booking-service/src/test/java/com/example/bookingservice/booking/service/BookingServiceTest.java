package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.dto.CreateBookingItemRequest;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingItemRepository;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.integration.hotel.HotelCatalogClient;
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
    private HotelCatalogClient catalogClient;
    private com.example.bookingservice.payment.refund.RefundRequestService refundRequests;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingItemRepository = mock(BookingItemRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        availabilityClient = mock(HotelAvailabilityClient.class);
        eventPublisher = mock(HotelEventPublisher.class);
        loyaltyPointsClient = mock(LoyaltyPointsClient.class);
        catalogClient = mock(HotelCatalogClient.class);
        refundRequests = mock(com.example.bookingservice.payment.refund.RefundRequestService.class);
        bookingService = new BookingService(
                bookingRepository,
                bookingItemRepository,
                paymentRepository,
                availabilityClient,
                eventPublisher,
                loyaltyPointsClient,
                catalogClient,
                new CancellationPolicy(),
                refundRequests
        );
    }

    @Test
    void reviewEligibilityRequiresMatchingGuestHotelAndCheckedOutBooking() {
        Booking booking = Booking.builder().id("booking").userId("guest").hotelId("hotel")
                .status(BookingStatus.CHECKED_OUT).build();
        when(bookingRepository.findById("booking")).thenReturn(Optional.of(booking));
        assertThat(bookingService.isReviewEligible("missing", "guest", "hotel")).isFalse();
        assertThat(bookingService.isReviewEligible("booking", "other", "hotel")).isFalse();
        assertThat(bookingService.isReviewEligible("booking", "guest", "other")).isFalse();
        assertThat(bookingService.isReviewEligible("booking", "guest", "hotel")).isTrue();
        for (BookingStatus status : BookingStatus.values()) {
            booking.setStatus(status);
            assertThat(bookingService.isReviewEligible("booking", "guest", "hotel"))
                    .as("status %s", status).isEqualTo(status == BookingStatus.CHECKED_OUT);
        }
        assertThat(bookingService.isReviewEligible("", "guest", "hotel")).isFalse();
        assertThat(bookingService.isReviewEligible("booking", "", "hotel")).isFalse();
        assertThat(bookingService.isReviewEligible("booking", "guest", "")).isFalse();
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
        request.setAcceptedCancellationPolicyVersion(CancellationPolicy.VERSION);

        LoyaltyPointsDTO points = new LoyaltyPointsDTO();
        points.setAvailablePoints(1_000L);

        when(availabilityClient.holdRooms(any())).thenReturn(new HoldResponse("hold-1", "HELD", expiresAt));
        when(catalogClient.getRoom(roomTypeId)).thenReturn(new HotelCatalogClient.Room(roomTypeId, "hotel-1", "Server Room",
                BigDecimal.valueOf(1_000_000), new HotelCatalogClient.Capacity(2, 0), true, 10));
        when(catalogClient.getHotel("hotel-1")).thenReturn(new HotelCatalogClient.Hotel("hotel-1", "Server Hotel", true, "APPROVED"));
        when(loyaltyPointsClient.getLoyaltyPoints("user-1")).thenReturn(points);
        when(loyaltyPointsClient.quote(any())).thenReturn(new com.example.bookingservice.integration.loyalty.PointsQuote(
                500L, new BigDecimal("500000"), new BigDecimal("1000")));
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
        assertThat(response.getUsedPoints()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getHotelName()).isEqualTo("Server Hotel");
        assertThat(response.getItems().get(0).getRoomTypeName()).isEqualTo("Server Room");
        assertThat(response.getItems().get(0).getRatePlanId()).isEqualTo(roomTypeId);
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("1000000");
        assertThat(response.getCancellationPolicyVersion()).isEqualTo(CancellationPolicy.VERSION);
        assertThat(java.time.Duration.between(response.getFreeCancellationUntil(), response.getCheckInAt()).toHours()).isEqualTo(24);
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

    @Test void nonPositiveRoomQuantityIsRejectedBeforeHoldingInventory() {
        var request = baseBookingRequest(List.of(item(UUID.randomUUID().toString(), 0, BigDecimal.TEN)));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void inconsistentItemDatesAreRejectedBeforeHoldingInventory() {
        var room = item(UUID.randomUUID().toString(), 1, BigDecimal.TEN);
        room.setCheckOutDate(LocalDate.now().plusDays(5));
        var request = baseBookingRequest(List.of(room));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void unsupportedVoucherIsRejectedBeforeHoldingInventory() {
        var request = baseBookingRequest(List.of(item(UUID.randomUUID().toString(), 1, BigDecimal.TEN)));
        request.setVoucherCode("TEST-VOUCHER");
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void negativePriceIsRejectedBeforeHoldingInventory() {
        var request = baseBookingRequest(List.of(item(UUID.randomUUID().toString(), 1, BigDecimal.ONE.negate())));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void changedCatalogPriceIsRejectedBeforeHoldingInventory() {
        String id = UUID.randomUUID().toString();
        var request = baseBookingRequest(List.of(item(id, 1, BigDecimal.ONE)));
        stubCatalog(id, "hotel-1", 2, true);
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price has changed");
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void roomFromAnotherHotelIsRejectedBeforeHoldingInventory() {
        String id = UUID.randomUUID().toString();
        stubCatalog(id, "different-hotel", 2, true);
        var request = baseBookingRequest(List.of(item(id, 1, new BigDecimal("1000000"))));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void oneRoomCannotUseCapacityOfTenPhysicalRooms() {
        String id = UUID.randomUUID().toString();
        stubCatalog(id, "hotel-1", 2, true);
        var request = baseBookingRequest(List.of(item(id, 1, new BigDecimal("1000000"))));
        request.setAdults(4);
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
        verify(availabilityClient, never()).holdRooms(any());
    }

    @Test void inactiveHotelOfferIsRejectedBeforeHoldingInventory() {
        String id = UUID.randomUUID().toString();
        stubCatalog(id, "hotel-1", 2, false);
        var request = baseBookingRequest(List.of(item(id, 1, new BigDecimal("1000000"))));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityClient, never()).holdRooms(any());
    }

    private void stubCatalog(String id, String hotelId, int adults, boolean active) {
        when(catalogClient.getRoom(id)).thenReturn(new HotelCatalogClient.Room(id, hotelId, "Server Room",
                new BigDecimal("1000000"), new HotelCatalogClient.Capacity(adults, 0), active, 10));
        when(catalogClient.getHotel("hotel-1")).thenReturn(new HotelCatalogClient.Hotel("hotel-1", "Server Hotel", active, "APPROVED"));
    }

    @Test void eligibleCancellationCreatesRefundRequestAndReleasesInventoryOnce() {
        Booking booking = cancellationFixture(true);
        bookingService.cancelBooking("booking-1", "customer-1");
        bookingService.cancelBooking("booking-1", "customer-1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getPaidAmount()).isEqualByComparingTo("1000000");
        verify(availabilityClient).cancelReservation("hold-1");
        verify(refundRequests).request("payment-1", "customer-1", "BOOKING_CANCELLED_STANDARD_24H");
        verify(paymentRepository, never()).save(any());
    }
    @Test void legacyPaidBookingRequiresReviewInsteadOfRetroactivePolicy() {
        Booking booking = cancellationFixture(true); booking.setCancellationPolicyVersion(null);
        assertThatThrownBy(() -> bookingService.cancelBooking("booking-1", "customer-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoCancellationSideEffects();
    }
    @Test void latePaidCancellationRequiresReview() {
        Booking booking = cancellationFixture(true); booking.setFreeCancellationUntil(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> bookingService.cancelBooking("booking-1", "customer-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoCancellationSideEffects();
    }
    @Test void administratorReviewedCancellationCanHandleHotelUnableToSupplyRoom() {
        Booking booking = cancellationFixture(true); booking.setFreeCancellationUntil(Instant.now().minusSeconds(1));
        bookingService.cancelBookingReviewed("booking-1", "administrator-1", "HOTEL_UNAVAILABLE");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(refundRequests).request("payment-1", "administrator-1", "HOTEL_UNAVAILABLE");
    }
    @Test void inventoryFailureDoesNotClaimCancellationSucceeded() {
        Booking booking = cancellationFixture(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("Inventory unavailable")).when(availabilityClient).cancelReservation("hold-1");
        assertThatThrownBy(() -> bookingService.cancelBooking("booking-1", "customer-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishBookingStatus(any());
    }
    @Test void genericStatusEndpointCannotBypassPaymentConfirmation() {
        cancellationFixture(false);
        assertThatThrownBy(() -> bookingService.updateBookingStatus("booking-1", BookingStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bookingRepository, never()).save(any());
    }
    @Test void databaseFailureAfterHoldTriggersInventoryCompensation() {
        String room = UUID.randomUUID().toString();
        stubCatalog(room, "hotel-1", 2, true);
        when(availabilityClient.holdRooms(any())).thenReturn(new HoldResponse("hold-1", "HELD", Instant.now().plusSeconds(900).toString()));
        when(bookingRepository.save(any())).thenThrow(new IllegalStateException("Test database failure"));
        var request = baseBookingRequest(List.of(item(room, 1, new BigDecimal("1000000"))));
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Test database failure");
        verify(availabilityClient).releaseHold("hold-1");
        verify(eventPublisher, never()).publishBookingStatus(any());
    }

    @Test void settlementDebitsSnapshotBeforeEarningAndUsesActualAward() {
        Booking booking = Booking.builder().id("booking-1").userId("customer-1").totalAmount(new BigDecimal("95000"))
                .usedPoints(10L).pointsDiscountAmount(new BigDecimal("5000")).build();
        when(loyaltyPointsClient.usePoints(any())).thenReturn(new BigDecimal("5000"));
        LoyaltyPointsDTO award = new LoyaltyPointsDTO(); award.setAwardedPoints(9L);
        when(loyaltyPointsClient.earnPoints("customer-1", "booking-1", new BigDecimal("95000"))).thenReturn(award);
        bookingService.settleLoyaltyPoints(booking);
        var order = org.mockito.Mockito.inOrder(loyaltyPointsClient);
        order.verify(loyaltyPointsClient).usePoints(org.mockito.ArgumentMatchers.argThat(request ->
                request.getPointsToUse() == 10 && request.getExpectedDiscountAmount().compareTo(new BigDecimal("5000")) == 0));
        order.verify(loyaltyPointsClient).earnPoints("customer-1", "booking-1", new BigDecimal("95000"));
        assertThat(booking.getEarnedPoints()).isEqualTo(9L);
        bookingService.settleLoyaltyPoints(booking);
        org.mockito.Mockito.verify(loyaltyPointsClient, org.mockito.Mockito.times(1)).earnPoints(any(), any(), any());
    }

    @Test void failedDebitDoesNotEarnOrSetSuccessfulMarker() {
        Booking booking = Booking.builder().id("booking-1").userId("customer-1").totalAmount(new BigDecimal("95000"))
                .usedPoints(10L).pointsDiscountAmount(new BigDecimal("5000")).build();
        when(loyaltyPointsClient.usePoints(any())).thenThrow(new IllegalStateException("Unavailable"));
        bookingService.settleLoyaltyPoints(booking);
        assertThat(booking.getEarnedPoints()).isNull();
        verify(loyaltyPointsClient, never()).earnPoints(any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test void cancellationReversesRedemptionEvenWithoutLocalEarnMarkerAndRetriesSafely() {
        Booking booking = cancellationFixture(false); booking.setUsedPoints(10L);
        bookingService.cancelBooking("booking-1", "customer-1");
        bookingService.cancelBooking("booking-1", "customer-1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        org.mockito.Mockito.verify(loyaltyPointsClient, org.mockito.Mockito.times(2)).refundPoints("customer-1", "booking-1");
        verify(availabilityClient).cancelReservation("hold-1");
        verify(eventPublisher).publishBookingStatus(any());
    }

    @Test void reversalOutageDoesNotReportCancellationFinished() {
        Booking booking = cancellationFixture(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("Unavailable")).when(loyaltyPointsClient).refundPoints(any(), any());
        assertThatThrownBy(() -> bookingService.cancelBooking("booking-1", "customer-1"))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(eventPublisher, never()).publishBookingStatus(any());
    }

    @Test void quoteOutageReleasesHoldAndDoesNotSilentlyRemoveRequestedDiscount() {
        String room = UUID.randomUUID().toString();
        stubCatalog(room, "hotel-1", 2, true);
        when(availabilityClient.holdRooms(any())).thenReturn(new HoldResponse("hold-1", "HELD", Instant.now().plusSeconds(900).toString()));
        when(loyaltyPointsClient.quote(any())).thenThrow(new IllegalStateException("Unavailable"));
        var request = baseBookingRequest(List.of(item(room, 1, new BigDecimal("1000000"))));
        request.setPointsToUse(100L);
        assertThatThrownBy(() -> bookingService.createBooking(request)).isInstanceOfSatisfying(
                org.springframework.web.server.ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        verify(availabilityClient).releaseHold("hold-1");
        verify(bookingRepository, never()).save(any());
    }
    private Booking cancellationFixture(boolean paid) {
        Booking booking = Booking.builder().id("booking-1").userId("customer-1").hotelId("hotel-1")
                .checkInDate(LocalDate.now().plusDays(3)).checkOutDate(LocalDate.now().plusDays(4))
                .paidAmount(paid ? new BigDecimal("1000000") : BigDecimal.ZERO).holdId("hold-1")
                .totalAmount(new BigDecimal("1000000")).status(paid ? BookingStatus.CONFIRMED : BookingStatus.PENDING).items(List.of()).build();
        new CancellationPolicy().snapshot(booking, CancellationPolicy.VERSION);
        when(bookingRepository.findByIdForUpdate("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var payment = com.example.bookingservice.payment.entity.Payment.builder().id("payment-1").bookingId("booking-1")
                .amount(new BigDecimal("1000000")).status(paid ? com.example.bookingservice.payment.enums.PaymentStatus.COMPLETED
                        : com.example.bookingservice.payment.enums.PaymentStatus.ONSITE_PENDING).build();
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc("booking-1")).thenReturn(Optional.of(payment));
        return booking;
    }
    private void verifyNoCancellationSideEffects() {
        org.mockito.Mockito.verifyNoInteractions(availabilityClient, refundRequests);
        verify(bookingRepository, never()).save(any());
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
