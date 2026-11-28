package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.City.entity.Province;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HotelSummaryServiceTest {
    private static final UUID PUBLISHED = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DRAFT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private final HotelRepository hotels = mock(HotelRepository.class);
    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final HotelSummaryService service = new HotelSummaryService(hotels, media);

    @Test
    void returnsOnlyPublishedHotelsInRequestOrderWithPublicFields() {
        Province province = new Province();
        province.setName("Lâm Đồng");
        City city = new City();
        city.setName("Đà Lạt");
        city.setProvince(province);
        Hotel hotel = new Hotel();
        hotel.setId(PUBLISHED);
        hotel.setName("Pine Hill");
        hotel.setStarRating((short) 4);
        hotel.setCity(city);
        hotel.setContactEmail("private@example.test");
        when(hotels.findByIdInAndIsActiveTrueAndApprovedStatus(anyCollection(), eq(HotelApprovalStatus.APPROVED)))
                .thenReturn(List.of(hotel));
        when(media.findByTargetTypeAndTargetIdIn(eq(MediaTargetType.HOTEL), anyCollection())).thenReturn(List.of(
                asset(PUBLISHED, "https://cdn.test/second.jpg", false, 0),
                asset(PUBLISHED, "https://cdn.test/thumb.jpg", true, 5)));

        var result = service.summarize(List.of(UNKNOWN, PUBLISHED, DRAFT, PUBLISHED));

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(PUBLISHED);
            assertThat(summary.name()).isEqualTo("Pine Hill");
            assertThat(summary.cityName()).isEqualTo("Đà Lạt");
            assertThat(summary.provinceName()).isEqualTo("Lâm Đồng");
            assertThat(summary.starRating()).isEqualTo((short) 4);
            assertThat(summary.coverImageUrl()).isEqualTo("https://cdn.test/thumb.jpg");
        });
        verify(hotels).findByIdInAndIsActiveTrueAndApprovedStatus(List.of(UNKNOWN, PUBLISHED, DRAFT), HotelApprovalStatus.APPROVED);
    }

    @Test
    void hotelWithoutMediaHasNoCover() {
        Hotel hotel = new Hotel();
        hotel.setId(PUBLISHED);
        hotel.setName("No Photos");
        when(hotels.findByIdInAndIsActiveTrueAndApprovedStatus(anyCollection(), any())).thenReturn(List.of(hotel));
        when(media.findByTargetTypeAndTargetIdIn(any(), anyCollection())).thenReturn(List.of());

        assertThat(service.summarize(List.of(PUBLISHED))).singleElement()
                .satisfies(summary -> assertThat(summary.coverImageUrl()).isNull());
    }

    @Test
    void noPublishedHotelsSkipsMediaLookup() {
        when(hotels.findByIdInAndIsActiveTrueAndApprovedStatus(anyCollection(), any())).thenReturn(List.of());

        assertThat(service.summarize(List.of(DRAFT))).isEmpty();
        verifyNoInteractions(media);
    }

    @Test
    void rejectsEmptyNullAndOversizedRequestsWithoutQuerying() {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) tooMany.add(UUID.randomUUID());
        List<UUID> withNull = new ArrayList<>(List.of(PUBLISHED));
        withNull.add(null);

        assertThatThrownBy(() -> service.summarize(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.summarize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.summarize(withNull)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.summarize(tooMany)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hotels, media);
    }

    @Test
    void fiftyDistinctIdsWithDuplicatesAreAccepted() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) ids.add(UUID.randomUUID());
        ids.addAll(ids.subList(0, 10));
        when(hotels.findByIdInAndIsActiveTrueAndApprovedStatus(anyCollection(), any())).thenReturn(Collections.emptyList());

        assertThat(service.summarize(ids)).isEmpty();
    }

    private static MediaAsset asset(UUID hotelId, String url, boolean thumbnail, int sortOrder) {
        MediaAsset asset = new MediaAsset();
        asset.setTargetId(hotelId);
        asset.setTargetType(MediaTargetType.HOTEL);
        asset.setUrl(url);
        asset.setIsThumbnail(thumbnail);
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
