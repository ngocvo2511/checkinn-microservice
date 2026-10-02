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
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        hotelReviewRepository = mock(HotelReviewRepository.class);
        ReviewResponseRepository reviewResponseRepository = mock(ReviewResponseRepository.class);
        ReviewFeedbackRepository reviewFeedbackRepository = mock(ReviewFeedbackRepository.class);
        hotelRepository = mock(HotelRepository.class);
        reviewService = new ReviewService(
                hotelReviewRepository,
                reviewResponseRepository,
                reviewFeedbackRepository,
                new ReviewMapper(),
                mock(UserGrpcClient.class),
                hotelRepository
        );
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
                .isInstanceOf(IllegalArgumentException.class);

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
}
