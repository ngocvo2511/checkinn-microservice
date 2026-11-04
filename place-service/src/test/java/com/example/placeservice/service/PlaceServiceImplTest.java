package com.example.placeservice.service;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.RoadMetrics;
import com.example.placeservice.dto.response.NearbyPlaceResponse;
import com.example.placeservice.enums.PlaceCategory;
import com.example.placeservice.repository.NearbyPlaceProjection;
import com.example.placeservice.repository.PlacePhotoRepository;
import com.example.placeservice.repository.PlaceRepository;
import com.example.placeservice.repository.PlaceReviewRepository;
import com.example.placeservice.service.osrm.RoadDistanceService;
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
    private RoadDistanceService roadDistanceService;

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
        roadDistanceService = mock(RoadDistanceService.class);

        placeService = new PlaceServiceImpl(
                placeRepository,
                placePhotoRepository,
                placeReviewRepository,
                redisTemplate,
                objectMapper,
                roadDistanceService
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
    void getNearbyPlaces_WhenDatabaseHasPlaces_EnrichesWithRoadDistanceAndReturnsSortedPlaces() {
        NearbyPlaceProjection mockProj = mock(NearbyPlaceProjection.class);
        UUID placeId = UUID.randomUUID();
        when(mockProj.getId()).thenReturn(placeId);
        when(mockProj.getName()).thenReturn("Bán đảo Sơn Trà");
        when(mockProj.getCategory()).thenReturn("ATTRACTION");
        when(mockProj.getLatitude()).thenReturn(16.1000);
        when(mockProj.getLongitude()).thenReturn(108.2500);
        when(mockProj.getDistanceKm()).thenReturn(4.5);
        when(mockProj.getRating()).thenReturn(4.8);

        // Expanded radius: 10.0 * 1.25 = 12.5 km, candidate limit = 25
        when(placeRepository.findNearbyPlaces(eq(16.0544), eq(108.2022), eq(12.5), eq("ALL"), eq(25)))
                .thenReturn(List.of(mockProj));
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        Coordinate destCoord = new Coordinate(16.1000, 108.2500);
        RoadMetrics roadMetrics = RoadMetrics.builder()
                .distanceKm(6.2)
                .durationMinutes(14)
                .fallback(false)
                .build();
        when(roadDistanceService.getRoadMetrics(any(Coordinate.class), anyList()))
                .thenReturn(Map.of(destCoord, roadMetrics));

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bán đảo Sơn Trà");
        assertThat(result.get(0).distanceKm()).isEqualTo(6.2);
        assertThat(result.get(0).estimatedTravelTimeMinutes()).isEqualTo(14);

        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    void getNearbyPlaces_WhenDatabaseEmpty_ReturnsEmptyListAndCachesResult() {
        when(placeRepository.findNearbyPlaces(anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.RESTAURANT, 5.0, 10);

        assertThat(result).isEmpty();
        verify(valueOperations).set(anyString(), eq("[]"), any());
        // Candidate radius: 5.0 * 1.25 = 6.25, candidate limit = 25
        verify(placeRepository, times(1)).findNearbyPlaces(eq(16.0544), eq(108.2022), eq(6.25), eq("RESTAURANT"), eq(25));
    }


    @Test
    void getNearbyPlaces_WhenCandidateRoadDistanceExceedsTargetRadius_FiltersOutPlace() {
        NearbyPlaceProjection mockProj = mock(NearbyPlaceProjection.class);
        UUID placeId = UUID.randomUUID();
        when(mockProj.getId()).thenReturn(placeId);
        when(mockProj.getName()).thenReturn("Đỉnh Bàn Cờ");
        when(mockProj.getCategory()).thenReturn("ATTRACTION");
        when(mockProj.getLatitude()).thenReturn(16.1200);
        when(mockProj.getLongitude()).thenReturn(108.2800);
        when(mockProj.getDistanceKm()).thenReturn(4.8); // Straight-line candidate is within 5.0 * 1.25 = 6.25 km

        // Target radius: 5.0 km, Candidate radius: 6.25 km
        when(placeRepository.findNearbyPlaces(eq(16.0544), eq(108.2022), eq(6.25), eq("ALL"), eq(25)))
                .thenReturn(List.of(mockProj));
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        Coordinate destCoord = new Coordinate(16.1200, 108.2800);
        // Actual road distance is 7.5 km, which exceeds targetRadiusKm (5.0 km)
        RoadMetrics roadMetrics = RoadMetrics.builder()
                .distanceKm(7.5)
                .durationMinutes(18)
                .fallback(false)
                .build();
        when(roadDistanceService.getRoadMetrics(any(Coordinate.class), anyList()))
                .thenReturn(Map.of(destCoord, roadMetrics));

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 5.0, 10);

        // Must be filtered out because road distance 7.5 > 5.0
        assertThat(result).isEmpty();
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
