package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.CompletedStays;
import com.example.travelsocialservice.client.CompletedStays.CompletedStay;
import com.example.travelsocialservice.client.CompletedStays.StayLookupUnavailableException;
import com.example.travelsocialservice.client.HotelClient;
import com.example.travelsocialservice.client.HotelClient.HotelRef;
import com.example.travelsocialservice.domain.InvitationStatus;
import com.example.travelsocialservice.domain.StayInvitation;
import com.example.travelsocialservice.messaging.BookingEventListener;
import com.example.travelsocialservice.messaging.BookingStatusEvent;
import com.example.travelsocialservice.repository.StayInvitationRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.service.StayInvitationService.Outcome;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InvitationDtos.InvitationView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StayInvitationServiceTest {

    private StayInvitationRepository repository;
    private CompletedStays stays;
    private HotelClient hotels;
    private SimpleMeterRegistry registry;
    private StayInvitationService service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final LocalDate checkOut = LocalDate.of(2026, 11, 3);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(StayInvitationRepository.class);
        stays = mock(CompletedStays.class);
        hotels = mock(HotelClient.class);
        registry = new SimpleMeterRegistry();
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        doAnswer(call -> {
            call.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        service = new StayInvitationService(repository, stays, hotels, PROPERTIES, new SocialMetrics(registry), transactions, CLOCK);
    }

    private void completedStay() {
        when(stays.find(bookingId)).thenReturn(Optional.of(new CompletedStay(guestId, hotelId, LocalDate.of(2026, 11, 1), checkOut)));
    }

    private void publishedHotel() {
        when(hotels.require(hotelId)).thenReturn(Optional.of(new HotelRef(hotelId, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, null)));
    }

    private double count(String result) {
        return registry.counter("social.stay_invitations", "result", result).count();
    }

    @Test
    void aConfirmedCheckOutCreatesOneOpenInvitationForTheBookingOwner() {
        completedStay();
        publishedHotel();
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(service.onBookingStatus(bookingId, "CHECKED_OUT")).isEqualTo(Outcome.CREATED);

        verify(repository).insertIfAbsent(bookingId, guestId, hotelId, checkOut, NOW, NOW.plus(Duration.ofDays(30)));
        assertThat(count("created")).isEqualTo(1);
    }

    @Test
    void aReplayedEventDoesNotCreateOrResetAnything() {
        completedStay();
        publishedHotel();
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(service.onBookingStatus(bookingId, "CHECKED_OUT")).isEqualTo(Outcome.DUPLICATE);
        assertThat(service.onBookingStatus(bookingId, "CHECKED_OUT")).isEqualTo(Outcome.DUPLICATE);
        assertThat(count("duplicate")).isEqualTo(2);
        verify(repository, never()).save(any());
    }

    @Test
    void otherStatusesAreAcknowledgedWithoutLookups() {
        for (String status : new String[]{"CONFIRMED", "CHECKED_IN", "CANCELLED", "NO_SHOW", "PENDING", null}) {
            assertThat(service.onBookingStatus(bookingId, status)).isEqualTo(Outcome.IGNORED_STATUS);
        }
        verifyNoInteractions(stays, hotels, repository);
    }

    @Test
    void aStaleEventCreatesNothing() {
        when(stays.find(bookingId)).thenReturn(Optional.empty());

        assertThat(service.onBookingStatus(bookingId, "CHECKED_OUT")).isEqualTo(Outcome.NOT_COMPLETED);
        verifyNoInteractions(hotels, repository);
    }

    @Test
    void anUnpublishedHotelGetsNoInvitation() {
        completedStay();
        when(hotels.require(hotelId)).thenReturn(Optional.empty());

        assertThat(service.onBookingStatus(bookingId, "CHECKED_OUT")).isEqualTo(Outcome.HOTEL_UNPUBLISHED);
        verifyNoInteractions(repository);
    }

    @Test
    void outagesAreRethrownSoTheEventIsRedelivered() {
        when(stays.find(bookingId)).thenThrow(new StayLookupUnavailableException("down", null));
        assertThatThrownBy(() -> service.onBookingStatus(bookingId, "CHECKED_OUT")).isInstanceOf(StayLookupUnavailableException.class);

        reset(stays);
        completedStay();
        when(hotels.require(hotelId)).thenThrow(ApiException.unavailable("HOTEL_SERVICE_UNAVAILABLE", "down"));
        assertThatThrownBy(() -> service.onBookingStatus(bookingId, "CHECKED_OUT")).isInstanceOf(ApiException.class);

        assertThat(count("lookup_failed")).isEqualTo(2);
        verifyNoInteractions(repository);
    }

    @Test
    void theListenerRejectsMalformedEventsAndPropagatesOutages() {
        BookingEventListener listener = new BookingEventListener(service);
        assertThatThrownBy(() -> listener.onBookingStatus(new BookingStatusEvent(null, "CHECKED_OUT", "e1")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onBookingStatus(new BookingStatusEvent("not-a-uuid", "CHECKED_OUT", "e2")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onBookingStatus(null)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(stays);

        when(stays.find(bookingId)).thenThrow(new StayLookupUnavailableException("down", null));
        assertThatThrownBy(() -> listener.onBookingStatus(new BookingStatusEvent(bookingId.toString(), "CHECKED_OUT", "e3")))
                .isInstanceOf(StayLookupUnavailableException.class);
    }

    @Test
    void ownersSeeOnlyTheirOpenUnexpiredInvitationsWithPublicHotelData() {
        Actor owner = new Actor(guestId, false);
        StayInvitation invitation = invitation(InvitationStatus.OPEN);
        when(repository.findByUserIdAndStatusAndExpiresAtAfterOrderByCheckOutDateDescBookingIdDesc(
                guestId, InvitationStatus.OPEN, NOW, PageRequest.of(0, 20))).thenReturn(List.of(invitation));
        when(hotels.findAll(Set.of(hotelId))).thenReturn(Map.of(hotelId,
                new HotelRef(hotelId, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, null)));

        List<InvitationView> views = service.openFor(owner);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(bookingId);
            assertThat(view.hotel().name()).isEqualTo("Pine Hill");
            assertThat(view.checkOutMonth()).isEqualTo("2026-11");
        });
    }

    @Test
    void noOpenInvitationsSkipsTheHotelLookup() {
        when(repository.findByUserIdAndStatusAndExpiresAtAfterOrderByCheckOutDateDescBookingIdDesc(any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.openFor(new Actor(guestId, false))).isEmpty();
        verifyNoInteractions(hotels);
    }

    @Test
    void dismissIsIdempotentAndLeavesClosedInvitationsAlone() {
        Actor owner = new Actor(guestId, false);
        StayInvitation open = invitation(InvitationStatus.OPEN);
        when(repository.findByBookingIdAndUserId(bookingId, guestId)).thenReturn(Optional.of(open));

        service.dismiss(owner, bookingId);
        service.dismiss(owner, bookingId);
        assertThat(open.getStatus()).isEqualTo(InvitationStatus.DISMISSED);
        assertThat(open.getStatusChangedAt()).isEqualTo(NOW);

        StayInvitation used = invitation(InvitationStatus.USED);
        when(repository.findByBookingIdAndUserId(bookingId, guestId)).thenReturn(Optional.of(used));
        service.dismiss(owner, bookingId);
        assertThat(used.getStatus()).isEqualTo(InvitationStatus.USED);
    }

    @Test
    void anotherUsersInvitationIsNotFound() {
        when(repository.findByBookingIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertApiError(() -> service.dismiss(user(), bookingId), HttpStatus.NOT_FOUND);
    }

    private StayInvitation invitation(InvitationStatus status) {
        StayInvitation invitation = new StayInvitation();
        invitation.setBookingId(bookingId);
        invitation.setUserId(guestId);
        invitation.setHotelId(hotelId);
        invitation.setCheckOutDate(checkOut);
        invitation.setStatus(status);
        invitation.setCreatedAt(NOW.minusSeconds(60));
        invitation.setExpiresAt(Instant.parse("2026-12-12T10:00:00Z"));
        invitation.setStatusChangedAt(NOW.minusSeconds(60));
        return invitation;
    }
}
