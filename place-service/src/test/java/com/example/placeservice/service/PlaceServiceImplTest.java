package com.example.placeservice.service;

import com.example.placeservice.dto.response.NearbyPlaceResponse;
import com.example.placeservice.enums.PlaceCategory;
import com.example.placeservice.repository.NearbyPlaceProjection;
import com.example.placeservice.repository.PlacePhotoRepository;
import com.example.placeservice.repository.PlaceRepository;
import com.example.placeservice.repository.PlaceReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlaceServiceImplTest {

    private PlaceRepository placeRepository;
    private PlacePhotoRepository placePhotoRepository;
    private PlaceReviewRepository placeReviewRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;

    private PlaceServiceImpl placeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        placePhotoRepository = mock(PlacePhotoRepository.class);
        placeReviewRepository = mock(PlaceReviewRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        objectMapper = new ObjectMapper();

        placeService = new PlaceServiceImpl(
                placeRepository,
                placePhotoRepository,
                placeReviewRepository,
                redisTemplate,
                objectMapper
        );
    }

    @Test
    void getNearbyPlaces_WhenCacheHit_ReturnsCachedResultsWithoutQueryingDb() {
        String cacheKey = String.format(Locale.US, "nearby:coords:%.4f:%.4f:%s:%.1f:%d",
                16.0544, 108.2022, PlaceCategory.ALL.name(), 10.0, 10);
        String cachedJson = "[{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Cầu Rồng\",\"distanceKm\":1.2}]";
        when(valueOperations.get(cacheKey)).thenReturn(cachedJson);

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Cầu Rồng");

        verifyNoInteractions(placeRepository);
    }

    @Test
    void getNearbyPlaces_WhenDatabaseHasPlaces_ReturnsPlacesFromDatabase() {
        NearbyPlaceProjection mockProj = mock(NearbyPlaceProjection.class);
        UUID placeId = UUID.randomUUID();
        when(mockProj.getId()).thenReturn(placeId);
        when(mockProj.getName()).thenReturn("Bán đảo Sơn Trà");
        when(mockProj.getCategory()).thenReturn("ATTRACTION");
        when(mockProj.getDistanceKm()).thenReturn(4.5);
        when(mockProj.getRating()).thenReturn(4.8);

        when(placeRepository.findNearbyPlaces(eq(16.0544), eq(108.2022), eq(10.0), eq("ALL"), eq(10)))
                .thenReturn(List.of(mockProj));
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bán đảo Sơn Trà");

        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    void getNearbyPlaces_WhenDatabaseEmpty_ReturnsEmptyListAndCachesResult() {
        when(placeRepository.findNearbyPlaces(anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.RESTAURANT, 5.0, 10);

        assertThat(result).isEmpty();
        verify(valueOperations).set(anyString(), eq("[]"), any());
        // Verify exactly 1 query with requested radius 5.0km - no auto-expansion to 50km
        verify(placeRepository, times(1)).findNearbyPlaces(eq(16.0544), eq(108.2022), eq(5.0), eq("RESTAURANT"), eq(10));
    }

    @Test
    void getPlacesByProvince_WhenValidProvince_ReturnsMappedPlaces() {
        com.example.placeservice.entity.Place place = com.example.placeservice.entity.Place.builder()
                .id(UUID.randomUUID())
                .name("Hồ Gươm")
                .provinceName("Hà Nội")
                .category(PlaceCategory.ATTRACTION)
                .latitude(21.0285)
                .longitude(105.8542)
                .rating(4.7)
                .userRatingsTotal(1500)
                .coverImageUrl("https://example.com/hoguom.jpg")
                .detailUrl("https://example.com/hoguom")
                .build();

        when(placeRepository.findByProvinceNameIgnoreCase("Hà Nội"))
                .thenReturn(List.of(place));
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        List<NearbyPlaceResponse> result = placeService.getPlacesByProvince("Hà Nội", PlaceCategory.ALL, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Hồ Gươm");
        assertThat(result.get(0).provinceName()).isEqualTo("Hà Nội");
        assertThat(result.get(0).detailUrl()).isEqualTo("https://example.com/hoguom");
    }
}
