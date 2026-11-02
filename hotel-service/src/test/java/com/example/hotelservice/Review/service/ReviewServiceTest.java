package com.example.hotelservice.Review.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Hotel.service.UserGrpcClient;
import com.example.hotelservice.Review.dto.request.CreateReviewRequest;
import com.example.hotelservice.Review.entity.HotelReview;
import com.example.hotelservice.Review.mapper.ReviewMapper;
import com.example.hotelservice.Review.repository.HotelReviewRepository;
import com.example.hotelservice.Review.repository.ReviewFeedbackRepository;
import com.example.hotelservice.Review.repository.ReviewResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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

class ReviewServiceTest {

    private HotelReviewRepository hotelReviewRepository;
    private HotelRepository hotelRepository;
    private ReviewResponseRepository reviewResponseRepository;
    private ReviewService reviewService;
    private ReviewEligibilityClient eligibility;

    @BeforeEach
    void setUp() {
        hotelReviewRepository = mock(HotelReviewRepository.class);
        reviewResponseRepository = mock(ReviewResponseRepository.class);
        ReviewFeedbackRepository reviewFeedbackRepository = mock(ReviewFeedbackRepository.class);
        hotelRepository = mock(HotelRepository.class);
        eligibility = mock(ReviewEligibilityClient.class);
        when(eligibility.isEligible(any(), any(), any())).thenReturn(true);
        reviewService = new ReviewService(
                hotelReviewRepository,
                reviewResponseRepository,
                reviewFeedbackRepository,
                new ReviewMapper(),
                mock(UserGrpcClient.class),
                hotelRepository,
                eligibility
        );
    }

    @Test
    void missingBookingCannotCreateReview() {
        var request = CreateReviewRequest.builder().hotelId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> reviewService.createReview(request, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(hotelReviewRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(eligibility);
    }

    @Test
    void ineligibleBookingCannotCreateReview() {
        UUID booking = UUID.randomUUID(), guest = UUID.randomUUID(), hotel = UUID.randomUUID();
        when(eligibility.isEligible(booking, guest, hotel)).thenReturn(false);
        var request = CreateReviewRequest.builder().bookingId(booking).hotelId(hotel).build();
        assertThatThrownBy(() -> reviewService.createReview(request, guest)).isInstanceOf(SecurityException.class);
        verify(hotelReviewRepository, never()).save(any());
    }

    @Test
    void bookingOutageCannotCreateReview() {
        when(eligibility.isEligible(any(), any(), any())).thenThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        var request = CreateReviewRequest.builder().bookingId(UUID.randomUUID()).hotelId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> reviewService.createReview(request, UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(hotelReviewRepository, never()).save(any());
    }

    @Test
    void createReviewRejectsDuplicateReviewForSameBooking() {
        UUID hotelId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CreateReviewRequest request = CreateReviewRequest.builder()
                .hotelId(hotelId)
                .bookingId(bookingId)
                .rating(BigDecimal.TEN)
                .title("Great")
                .content("Great hotel stay")
                .build();

        when(hotelReviewRepository.findByHotelIdAndGuestIdAndBookingId(hotelId, guestId, bookingId))
                .thenReturn(Optional.of(HotelReview.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> reviewService.createReview(request, guestId))
                .isInstanceOf(IllegalStateException.class);

        verify(hotelReviewRepository, never()).save(any());
    }

    @Test
    void createReviewPersistsPublishedReviewForFirstReview() {
        UUID hotelId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CreateReviewRequest request = CreateReviewRequest.builder()
                .hotelId(hotelId)
                .bookingId(bookingId)
                .rating(BigDecimal.valueOf(9))
                .title("Great")
                .content("Great hotel stay")
                .build();

        when(hotelReviewRepository.findByHotelIdAndGuestIdAndBookingId(hotelId, guestId, bookingId))
                .thenReturn(Optional.empty());
        when(hotelReviewRepository.save(any(HotelReview.class))).thenAnswer(invocation -> {
            HotelReview review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });

        var response = reviewService.createReview(request, guestId);

        assertThat(response.getGuestId()).isEqualTo(guestId);
        assertThat(response.getStatus().name()).isEqualTo("PUBLISHED");
        verify(hotelReviewRepository).save(any(HotelReview.class));
    }

    @Test
    void getReviewsByOwnerAndHotelRejectsHotelOutsideOwnerScope() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();

        when(hotelRepository.existsByIdAndOwnerId(hotelId, ownerId)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.getReviewsByOwnerAndHotel(ownerId, hotelId, PageRequest.of(0, 10)))
                .isInstanceOf(SecurityException.class);

        verify(hotelReviewRepository, never()).findByHotelIdAndStatusOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getReviewsByOwnerReturnsEmptyPageWhenOwnerHasNoHotels() {
        UUID ownerId = UUID.randomUUID();
        when(hotelRepository.findByOwnerId(ownerId)).thenReturn(List.of());

        var page = reviewService.getReviewsByOwner(ownerId, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        verify(hotelReviewRepository, never()).findByHotelIdInAndStatusOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getReviewsByOwnerReturnsPublishedReviewsForOwnedHotels() {
        UUID ownerId = UUID.randomUUID();
        UUID hotelId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        Hotel hotel = Hotel.builder().id(hotelId).ownerId(ownerId).name("RTM Hotel").build();
        HotelReview review = HotelReview.builder()
                .id(reviewId)
                .hotelId(hotelId)
                .guestId(UUID.randomUUID())
                .rating(BigDecimal.valueOf(8))
                .title("Nice")
                .content("Nice stay")
                .build();

        when(hotelRepository.findByOwnerId(ownerId)).thenReturn(List.of(hotel));
        when(hotelReviewRepository.findByHotelIdInAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        var page = reviewService.getReviewsByOwner(ownerId, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getHotelName()).isEqualTo("RTM Hotel");
    }
    @Test
    void anotherHotelOwnerCannotAddResponse() {
        UUID reviewId = UUID.randomUUID(), hotelId = UUID.randomUUID(), intruder = UUID.randomUUID();
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).hotelId(hotelId).build()));
        var request = new com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest();
        request.setContent("Thank you for your stay");
        assertThatThrownBy(() -> reviewService.addReviewResponse(reviewId, request, intruder))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void anotherGuestCannotDeleteReview() {
        UUID reviewId = UUID.randomUUID();
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).guestId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> reviewService.deleteReview(reviewId, UUID.randomUUID(), false))
                .isInstanceOf(SecurityException.class);
        verify(hotelReviewRepository, never()).deleteById(any());
    }
    @Test
    void actualHotelOwnerCanAddResponse() {
        UUID reviewId = UUID.randomUUID(), hotelId = UUID.randomUUID(), owner = UUID.randomUUID();
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).hotelId(hotelId).build()));
        when(hotelRepository.existsByIdAndOwnerId(hotelId, owner)).thenReturn(true);
        var repository = reviewResponseRepository;
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = new com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest();
        request.setContent("Thank you for your stay");
        var result = reviewService.addReviewResponse(reviewId, request, owner);
        assertThat(result.getOwnerId()).isEqualTo(owner);
        verify(repository).save(any());
    }

    @Test
    void reviewAuthorAndAdministratorCanDelete() {
        UUID reviewId = UUID.randomUUID(), author = UUID.randomUUID();
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).guestId(author).build()));
        reviewService.deleteReview(reviewId, author, false);
        reviewService.deleteReview(reviewId, UUID.randomUUID(), true);
        org.mockito.Mockito.verify(hotelReviewRepository, org.mockito.Mockito.times(2)).deleteById(reviewId);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void legacyUnrelatedResponseAuthorCannotModifyResponse(boolean delete) {
        UUID responseId = UUID.randomUUID(), reviewId = UUID.randomUUID(), hotelId = UUID.randomUUID(), actor = UUID.randomUUID();
        when(reviewResponseRepository.findById(responseId)).thenReturn(Optional.of(
                com.example.hotelservice.Review.entity.ReviewResponse.builder()
                        .id(responseId).reviewId(reviewId).ownerId(actor).build()));
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).hotelId(hotelId).build()));
        var request = new com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest();
        request.setContent("Synthetic updated response");
        assertThatThrownBy(() -> {
            if (delete) reviewService.deleteReviewResponse(responseId, actor);
            else reviewService.updateReviewResponse(responseId, request, actor);
        }).isInstanceOf(SecurityException.class);
        verify(reviewResponseRepository, never()).save(any());
        verify(reviewResponseRepository, never()).deleteById(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void currentHotelOwnerCanModifyOwnResponse(boolean delete) {
        UUID responseId = UUID.randomUUID(), reviewId = UUID.randomUUID(), hotelId = UUID.randomUUID(), actor = UUID.randomUUID();
        when(reviewResponseRepository.findById(responseId)).thenReturn(Optional.of(
                com.example.hotelservice.Review.entity.ReviewResponse.builder()
                        .id(responseId).reviewId(reviewId).ownerId(actor).build()));
        when(hotelReviewRepository.findById(reviewId)).thenReturn(Optional.of(
                HotelReview.builder().id(reviewId).hotelId(hotelId).build()));
        when(hotelRepository.existsByIdAndOwnerId(hotelId, actor)).thenReturn(true);
        when(reviewResponseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        if (delete) {
            reviewService.deleteReviewResponse(responseId, actor);
            verify(reviewResponseRepository).deleteById(responseId);
        } else {
            var request = new com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest();
            request.setContent("Synthetic updated response");
            assertThat(reviewService.updateReviewResponse(responseId, request, actor).getContent())
                    .isEqualTo(request.getContent());
        }
    }
}
