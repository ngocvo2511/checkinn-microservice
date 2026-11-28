package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.CompletedStays;
import com.example.travelsocialservice.client.CompletedStays.CompletedStay;
import com.example.travelsocialservice.client.CompletedStays.StayLookupUnavailableException;
import com.example.travelsocialservice.client.HotelClient;
import com.example.travelsocialservice.client.HotelClient.HotelRef;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.InvitationStatus;
import com.example.travelsocialservice.domain.StayInvitation;
import com.example.travelsocialservice.repository.StayInvitationRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InvitationDtos.InvitationView;
import com.example.travelsocialservice.web.dto.PostDtos.HotelView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StayInvitationService {

    static final String CHECKED_OUT = "CHECKED_OUT";

    private final StayInvitationRepository invitations;
    private final CompletedStays completedStays;
    private final HotelClient hotelClient;
    private final SocialProperties properties;
    private final SocialMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public enum Outcome { CREATED, DUPLICATE, IGNORED_STATUS, NOT_COMPLETED, HOTEL_UNPUBLISHED }

    /**
     * Creates the invitation for a booking that booking-service confirms is CHECKED_OUT. Lookups run
     * outside the transaction; an outage is rethrown so the broker redelivers the event.
     */
    public Outcome onBookingStatus(UUID bookingId, String bookingStatus) {
        if (!CHECKED_OUT.equals(bookingStatus)) {
            return record(Outcome.IGNORED_STATUS);
        }
        Optional<CompletedStay> found;
        Optional<HotelRef> hotel;
        try {
            found = completedStays.find(bookingId);
            if (found.isEmpty()) {
                return record(Outcome.NOT_COMPLETED);
            }
            hotel = hotelClient.require(found.get().hotelId());
        } catch (StayLookupUnavailableException | ApiException ex) {
            metrics.stayInvitation("lookup_failed");
            throw ex;
        }
        if (hotel.isEmpty()) {
            return record(Outcome.HOTEL_UNPUBLISHED);
        }
        CompletedStay stay = found.get();
        Instant now = Paging.now(clock);
        Integer inserted = transactions.execute(tx -> invitations.insertIfAbsent(bookingId, stay.userId(), stay.hotelId(),
                stay.checkOutDate(), now, now.plus(properties.invitations().ttl())));
        return record(inserted != null && inserted > 0 ? Outcome.CREATED : Outcome.DUPLICATE);
    }

    public List<InvitationView> openFor(Actor owner) {
        Instant now = Paging.now(clock);
        List<StayInvitation> open = invitations.findByUserIdAndStatusAndExpiresAtAfterOrderByCheckOutDateDescBookingIdDesc(
                owner.id(), InvitationStatus.OPEN, now, PageRequest.of(0, properties.invitations().listMax()));
        if (open.isEmpty()) {
            return List.of();
        }
        Map<UUID, HotelRef> hotels = hotelClient.findAll(open.stream().map(StayInvitation::getHotelId).collect(Collectors.toSet()));
        return open.stream().map(invitation -> new InvitationView(
                invitation.getBookingId(),
                hotelView(hotels.get(invitation.getHotelId())),
                YearMonth.from(invitation.getCheckOutDate()).toString(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt())).toList();
    }

    /** Idempotent: an invitation that is already closed stays as it is. */
    public void dismiss(Actor owner, UUID invitationId) {
        transactions.executeWithoutResult(tx -> {
            StayInvitation invitation = invitations.findByBookingIdAndUserId(invitationId, owner.id())
                    .orElseThrow(ApiException::notFound);
            if (invitation.getStatus() == InvitationStatus.OPEN) {
                invitation.changeStatus(InvitationStatus.DISMISSED, Paging.now(clock));
            }
        });
    }

    private Outcome record(Outcome outcome) {
        metrics.stayInvitation(outcome.name().toLowerCase());
        log.debug("[SOCIAL_STAY_INVITATION] result={}", outcome);
        return outcome;
    }

    private static HotelView hotelView(HotelRef hotel) {
        return hotel == null ? null : new HotelView(hotel.id(), hotel.name(), hotel.cityName(), hotel.provinceName(),
                hotel.starRating(), hotel.coverImageUrl());
    }
}
