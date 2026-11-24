package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.CompletedStays;
import com.example.travelsocialservice.client.CompletedStays.CompletedStay;
import com.example.travelsocialservice.client.CompletedStays.StayLookupUnavailableException;
import com.example.travelsocialservice.client.HotelClient;
import com.example.travelsocialservice.client.HotelClient.HotelRef;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.InvitationStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.StayInvitation;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.StayInvitationRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.PostDtos.PostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VerifiedStayPostTest {

    private PostRepository posts;
    private HotelClient hotels;
    private CompletedStays stays;
    private StayInvitationRepository invitations;
    private PostService service;

    private final Actor author = user();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID otherHotelId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        hotels = mock(HotelClient.class);
        stays = mock(CompletedStays.class);
        invitations = mock(StayInvitationRepository.class);
        PostMediaRepository media = mock(PostMediaRepository.class);
        when(media.findAllById(any())).thenReturn(List.of());
        when(hotels.require(hotelId)).thenReturn(Optional.of(hotel(hotelId)));
        when(hotels.require(otherHotelId)).thenReturn(Optional.of(hotel(otherHotelId)));
        when(invitations.findByBookingIdAndUserId(any(), any())).thenReturn(Optional.empty());
        service = new PostService(posts, media, mock(ModerationAuditRepository.class), mock(PlaceClient.class),
                hotels, stays, invitations, metrics(), PROPERTIES, CLOCK);
    }

    private static HotelRef hotel(UUID id) {
        return new HotelRef(id, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, null);
    }

    private void completed(UUID owner, UUID hotel) {
        when(stays.find(bookingId)).thenReturn(Optional.of(
                new CompletedStay(owner, hotel, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))));
    }

    private Post stored(Post post) {
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void tagsAPublishedHotelWithoutAStay() {
        Post post = service.create(author, new PostRequest("Lovely lobby", null, null, null, hotelId, null));

        assertThat(post.getHotelId()).isEqualTo(hotelId);
        assertThat(post.isVerifiedStay()).isFalse();
        assertThat(post.getStayBookingId()).isNull();
        verifyNoInteractions(stays);
    }

    @Test
    void rejectsAnUnpublishedHotel() {
        UUID draft = UUID.randomUUID();
        when(hotels.require(draft)).thenReturn(Optional.empty());

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, draft, null)),
                HttpStatus.BAD_REQUEST);
        assertThat(ex.getFields()).containsKey("hotelId");
        verify(posts, never()).save(any());
    }

    @Test
    void hotelCatalogOutageFailsThePublish() {
        UUID hotel = UUID.randomUUID();
        when(hotels.require(hotel)).thenThrow(ApiException.unavailable("HOTEL_SERVICE_UNAVAILABLE", "down"));

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotel, null)),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCode()).isEqualTo("HOTEL_SERVICE_UNAVAILABLE");
        verify(posts, never()).save(any());
    }

    @Test
    void aHotelPostCannotCarryARating() {
        UUID placeId = UUID.randomUUID();
        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", placeId, 4, null, hotelId, null)),
                HttpStatus.BAD_REQUEST);
        assertThat(ex.getFields()).containsKey("rating");
        verifyNoInteractions(hotels, stays);
    }

    @Test
    void aStayNeedsItsHotel() {
        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, null, bookingId)),
                HttpStatus.BAD_REQUEST);
        assertThat(ex.getFields()).containsKey("stayBookingId");
        verifyNoInteractions(stays);
    }

    @Test
    void linksTheAuthorsCompletedStayAndUsesTheOpenInvitation() {
        completed(author.id(), hotelId);
        StayInvitation invitation = new StayInvitation();
        invitation.setBookingId(bookingId);
        invitation.setUserId(author.id());
        invitation.setStatus(InvitationStatus.OPEN);
        when(invitations.findByBookingIdAndUserId(bookingId, author.id())).thenReturn(Optional.of(invitation));

        Post post = service.create(author, new PostRequest("We loved it", null, null, null, hotelId, bookingId));

        assertThat(post.isVerifiedStay()).isTrue();
        assertThat(post.getStayBookingId()).isEqualTo(bookingId);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
        assertThat(invitation.getPostId()).isEqualTo(post.getId());
        assertThat(invitation.getStatusChangedAt()).isEqualTo(NOW);
        verify(posts).flush();
    }

    @Test
    void everyStayMismatchGetsTheSameAnswer() {
        String[] messages = new String[3];

        completed(UUID.randomUUID(), hotelId);
        messages[0] = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.BAD_REQUEST).getFields().get("stayBookingId");

        completed(author.id(), otherHotelId);
        messages[1] = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.BAD_REQUEST).getFields().get("stayBookingId");

        when(stays.find(bookingId)).thenReturn(Optional.empty());
        messages[2] = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.BAD_REQUEST).getFields().get("stayBookingId");

        assertThat(messages).doesNotContainNull().containsOnly(messages[0]);
        verify(posts, never()).save(any());
    }

    @Test
    void bookingOutageFailsTheVerifiedPublish() {
        when(stays.find(bookingId)).thenThrow(new StayLookupUnavailableException("down", null));

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCode()).isEqualTo("BOOKING_SERVICE_UNAVAILABLE");
        verify(posts, never()).save(any());
    }

    @Test
    void aStayAlreadySharedInALivePostIsAConflict() {
        completed(author.id(), hotelId);
        when(posts.isStaySharedElsewhere(bookingId, null)).thenReturn(true);

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("STAY_ALREADY_SHARED");
        verify(posts, never()).save(any());
    }

    @Test
    void aConcurrentShareCaughtByTheUniqueIndexIsAConflict() {
        completed(author.id(), hotelId);
        doThrow(new DataIntegrityViolationException("ux_posts_live_stay")).when(posts).flush();

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("x", null, null, null, hotelId, bookingId)),
                HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("STAY_ALREADY_SHARED");
    }

    @Test
    void anUnchangedStayIsNotVerifiedAgainOnEdit() {
        Post post = stored(verifiedPost());

        service.update(author, post.getId(), new PostRequest("Edited", null, null, null, hotelId, bookingId));

        assertThat(post.getBody()).isEqualTo("Edited");
        assertThat(post.isVerifiedStay()).isTrue();
        verifyNoInteractions(stays);
        verify(posts, never()).flush();
    }

    @Test
    void removingTheStayOrTheHotelRemovesTheBadge() {
        Post withoutStay = stored(verifiedPost());
        service.update(author, withoutStay.getId(), new PostRequest("Edited", null, null, null, hotelId, null));
        assertThat(withoutStay.isVerifiedStay()).isFalse();
        assertThat(withoutStay.getStayBookingId()).isNull();

        Post withoutHotel = stored(verifiedPost());
        service.update(author, withoutHotel.getId(), new PostRequest("Edited", null, null, null, null, null));
        assertThat(withoutHotel.isVerifiedStay()).isFalse();
        assertThat(withoutHotel.getHotelId()).isNull();
        verifyNoInteractions(stays);
    }

    @Test
    void changingTheHotelReVerifiesTheStayWithoutCountingThePostItself() {
        Post post = stored(verifiedPost());
        completed(author.id(), hotelId);

        ApiException ex = assertApiError(() -> service.update(author, post.getId(),
                new PostRequest("Edited", null, null, null, otherHotelId, bookingId)), HttpStatus.BAD_REQUEST);
        assertThat(ex.getFields()).containsKey("stayBookingId");
        assertThat(post.getHotelId()).isEqualTo(hotelId);
        verify(stays).find(bookingId);
    }

    @Test
    void anAdministratorEditVerifiesTheStayAgainstThePostAuthor() {
        Post post = stored(post(author.id(), ContentStatus.PUBLISHED));
        post.setHotelId(hotelId);
        completed(author.id(), hotelId);

        service.update(admin(), post.getId(), new PostRequest("Edited", null, null, null, hotelId, bookingId));

        assertThat(post.isVerifiedStay()).isTrue();
        verify(posts).isStaySharedElsewhere(bookingId, post.getId());
    }

    @Test
    void editingAHiddenVerifiedPostKeepsItHidden() {
        Post post = stored(verifiedPost());
        post.setStatus(ContentStatus.HIDDEN);

        service.update(author, post.getId(), new PostRequest("Edited", null, null, null, hotelId, bookingId));

        assertThat(post.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        verify(invitations, never()).findByBookingIdAndUserId(eq(bookingId), any());
    }

    private Post verifiedPost() {
        Post post = post(author.id(), ContentStatus.PUBLISHED);
        post.setHotelId(hotelId);
        post.setStayBookingId(bookingId);
        post.setVerifiedStay(true);
        return post;
    }
}
