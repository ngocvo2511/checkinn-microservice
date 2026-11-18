package com.example.placeservice.service;

import com.example.placeservice.dto.response.PlaceSearchPage;
import com.example.placeservice.dto.response.PlaceSummary;
import com.example.placeservice.entity.Place;
import com.example.placeservice.enums.PlaceCategory;
import com.example.placeservice.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlaceCatalogServiceTest {

    private PlaceRepository repository;
    private PlaceCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlaceRepository.class);
        service = new PlaceCatalogService(repository);
    }

    private static Place place(String name) {
        return Place.builder().id(UUID.randomUUID()).name(name).category(PlaceCategory.RESTAURANT)
                .latitude(16.0).longitude(108.0).googlePlaceId("g-" + name).fsqId("f-" + name).build();
    }

    @Test
    void search_TrimsQueryEscapesWildcardsAndRequestsOneExtraRow() {
        when(repository.searchByName(anyString(), anyInt())).thenReturn(List.of());

        service.search("  50%_off! ", null, 10);

        verify(repository).searchByName("%50!%!_off!!%", 11);
    }

    @Test
    void search_FirstPageWithMoreRowsReturnsCursorForLastItem() {
        List<Place> rows = IntStream.range(0, 3).mapToObj(i -> place("Cafe " + i)).toList();
        when(repository.searchByName(anyString(), eq(3))).thenReturn(rows);

        PlaceSearchPage page = service.search("cafe", null, 2);

        assertThat(page.items()).extracting(PlaceSummary::name).containsExactly("Cafe 0", "Cafe 1");
        PlaceCatalogService.Cursor cursor = PlaceCatalogService.Cursor.decode(page.nextCursor());
        assertThat(cursor.name()).isEqualTo("Cafe 1");
        assertThat(cursor.id()).isEqualTo(rows.get(1).getId());
    }

    @Test
    void search_LastPageHasNoCursor() {
        when(repository.searchByName(anyString(), anyInt())).thenReturn(List.of(place("Cafe")));

        assertThat(service.search("cafe", null, 5).nextCursor()).isNull();
    }

    @Test
    void search_CursorContinuesStrictlyAfterNameAndId() {
        Place last = place("Quán | Bà Nà");
        String cursor = PlaceCatalogService.Cursor.of(last).encode();
        when(repository.searchByNameAfter(anyString(), anyString(), any(), anyInt())).thenReturn(List.of());

        service.search("bà", cursor, null);

        verify(repository).searchByNameAfter("%bà%", "Quán | Bà Nà", last.getId(), PlaceCatalogService.DEFAULT_LIMIT + 1);
        verify(repository, never()).searchByName(anyString(), anyInt());
    }

    @Test
    void search_RejectsBlankOrOverlongQuery() {
        assertBadRequest(() -> service.search(null, null, null));
        assertBadRequest(() -> service.search("   ", null, null));
        assertBadRequest(() -> service.search("x".repeat(101), null, null));
        verifyNoInteractions(repository);
    }

    @Test
    void search_RejectsOutOfRangeLimit() {
        assertBadRequest(() -> service.search("cafe", null, 0));
        assertBadRequest(() -> service.search("cafe", null, 51));
    }

    @Test
    void search_RejectsMalformedCursor() {
        assertBadRequest(() -> service.search("cafe", "%%%not-base64", null));
        String noUuid = Base64.getUrlEncoder().encodeToString("Cafe|not-a-uuid".getBytes());
        assertBadRequest(() -> service.search("cafe", noUuid, null));
    }

    @Test
    void summary_ExposesNoProviderIdentifiers() {
        assertThat(Arrays.stream(PlaceSummary.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("googlePlaceId", "fsqId", "dataSource", "reviews", "photos", "detailUrl");
    }

    @Test
    void batch_DeduplicatesIdsAndMapsFoundPlaces() {
        Place known = place("Cầu Rồng");
        UUID unknown = UUID.randomUUID();
        when(repository.findAllById(any())).thenReturn(List.of(known));

        List<PlaceSummary> result = service.batch(List.of(known.getId(), known.getId(), unknown));

        assertThat(result).extracting(PlaceSummary::id).containsExactly(known.getId());
        verify(repository).findAllById(Set.of(known.getId(), unknown));
    }

    @Test
    void batch_RejectsEmptyOrTooManyIds() {
        assertBadRequest(() -> service.batch(null));
        assertBadRequest(() -> service.batch(List.of()));
        List<UUID> tooMany = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();
        assertBadRequest(() -> service.batch(tooMany));
        verifyNoInteractions(repository);
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
