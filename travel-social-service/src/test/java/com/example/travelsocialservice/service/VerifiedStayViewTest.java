package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.AuthorClient;
import com.example.travelsocialservice.client.HotelClient;
import com.example.travelsocialservice.client.HotelClient.HotelRef;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostQueries;
import com.example.travelsocialservice.repository.PostQueries.Filter;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.PostDtos.PostView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class VerifiedStayViewTest {

    private HotelClient hotels;
    private PostAssembler assembler;

    private final Actor author = user();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hotels = mock(HotelClient.class);
        AuthorClient authors = mock(AuthorClient.class);
        when(authors.findAll(any())).thenReturn(Map.of());
        assembler = new PostAssembler(mock(PostMediaRepository.class), mock(InteractionQueries.class),
                mock(PlaceClient.class), hotels, authors);
    }

    private Post verifiedPost() {
        Post post = post(author.id(), ContentStatus.PUBLISHED);
        post.setHotelId(hotelId);
        post.setStayBookingId(bookingId);
        post.setVerifiedStay(true);
        return post;
    }

    @Test
    void publicViewShowsTheBadgeAndHotelButNeverTheBooking() throws Exception {
        Post post = verifiedPost();
        when(hotels.findAll(Set.of(hotelId))).thenReturn(Map.of(hotelId,
                new HotelRef(hotelId, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, "https://cdn.test/a.jpg")));

        for (Actor viewer : new Actor[]{null, user(), admin(), author}) {
            PostView view = assembler.toView(post, viewer);
            assertThat(view.verifiedStay()).isTrue();
            assertThat(view.hotelId()).isEqualTo(hotelId);
            assertThat(view.hotel().name()).isEqualTo("Pine Hill");
            assertThat(view.hotel().provinceName()).isEqualTo("Lâm Đồng");
            assertThat(view.stayBookingId()).isNull();
            String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(view);
            assertThat(json).doesNotContain(bookingId.toString()).doesNotContain("stayBookingId");
        }
    }

    @Test
    void onlyTheAuthorsOwnViewCarriesTheBooking() {
        Post post = verifiedPost();

        assertThat(assembler.toOwnView(post, author).stayBookingId()).isEqualTo(bookingId);
        assertThat(assembler.toViews(List.of(post), admin(), true).get(0).stayBookingId()).isNull();
    }

    @Test
    void anUnavailableHotelKeepsTheIdWithoutContext() {
        Post post = verifiedPost();
        when(hotels.findAll(any())).thenReturn(Map.of());

        PostView view = assembler.toView(post, null);

        assertThat(view.hotelId()).isEqualTo(hotelId);
        assertThat(view.hotel()).isNull();
    }

    @Test
    void onePageMakesOneHotelLookup() {
        Post first = verifiedPost();
        Post second = post(author.id(), ContentStatus.PUBLISHED);
        second.setHotelId(hotelId);
        Post third = post(author.id(), ContentStatus.PUBLISHED);

        assembler.toViews(List.of(first, second, third), null);

        verify(hotels, times(1)).findAll(Set.of(hotelId));
    }

    @Test
    void theHotelFeedFiltersByHotelAndRejectsAPlaceFilterAlongside() {
        PostQueries queries = mock(PostQueries.class);
        when(queries.page(any(), isNull(), anyInt())).thenReturn(List.of());
        FeedService feed = new FeedService(queries, mock(InteractionQueries.class), assembler);

        feed.recent(null, hotelId, null, null, null);
        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(queries).page(filter.capture(), isNull(), anyInt());
        assertThat(filter.getValue().hotelId()).isEqualTo(hotelId);
        assertThat(filter.getValue().status()).isEqualTo(ContentStatus.PUBLISHED);

        assertApiError(() -> feed.recent(UUID.randomUUID(), hotelId, null, null, null), HttpStatus.BAD_REQUEST);
    }
}
