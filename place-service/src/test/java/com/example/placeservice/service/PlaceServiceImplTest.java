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

import java.time.Duration;
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
        String cacheKey = String.format(Locale.US, "nearby:v3:coords:%.4f:%.4f:%s:%.1f:%d",
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

        when(placeRepository.findNearbyPlaces(eq(16.0544), eq(108.2022), eq(10.3),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("ALL"), eq(300)))
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
        assertThat(result.get(0).distanceEstimated()).isFalse();

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void getNearbyPlaces_WhenRoadMetricsAreFallback_FlagsEstimateAndUsesShortCacheTtl() {
        NearbyPlaceProjection mockProj = mock(NearbyPlaceProjection.class);
        when(mockProj.getId()).thenReturn(UUID.randomUUID());
        when(mockProj.getName()).thenReturn("Chợ Hàn");
        when(mockProj.getCategory()).thenReturn("ATTRACTION");
        when(mockProj.getLatitude()).thenReturn(16.0680);
        when(mockProj.getLongitude()).thenReturn(108.2240);
        when(mockProj.getDistanceKm()).thenReturn(1.6);

        when(placeRepository.findNearbyPlaces(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt()))
                .thenReturn(List.of(mockProj));
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());
        when(roadDistanceService.getRoadMetrics(any(Coordinate.class), anyList()))
                .thenReturn(Map.of(new Coordinate(16.0680, 108.2240),
                        RoadMetrics.builder().distanceKm(2.2).durationMinutes(7).fallback(true).build()));

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distanceEstimated()).isTrue();
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void getNearbyPlaces_WhenRemainingCandidatesCannotBeatTopResults_StopsRoutingEarly() {
        List<NearbyPlaceProjection> candidates = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candidates.add(candidate(16.06 + i * 0.0001, 0.5 + i * 0.01));
        }
        candidates.add(candidate(16.20, 5.0));
        stubCandidates(candidates);
        stubRoadDistanceAsStraightLineTimes(1.2);

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 1);

        assertThat(result).hasSize(1);
        verify(roadDistanceService, times(1)).getRoadMetrics(any(Coordinate.class), anyList());
    }

    @Test
    void getNearbyPlaces_WhenLaterCandidateMayBeCloserByRoad_RoutesNextBatch() {
        List<NearbyPlaceProjection> candidates = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candidates.add(candidate(16.06 + i * 0.0001, 2.0 + i * 0.01));
        }
        NearbyPlaceProjection directRoute = candidate(16.30, 2.6);
        candidates.add(directRoute);
        stubCandidates(candidates);
        Coordinate directCoord = new Coordinate(16.30, 108.2022);
        when(roadDistanceService.getRoadMetrics(any(Coordinate.class), anyList())).thenAnswer(inv -> {
            List<Coordinate> dests = inv.getArgument(1);
            Map<Coordinate, RoadMetrics> out = new HashMap<>();
            for (Coordinate c : dests) {
                double km = c.equals(directCoord) ? 2.7 : 4.0;
                out.put(c, RoadMetrics.builder().distanceKm(km).durationMinutes(10).fallback(false).build());
            }
            return out;
        });

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.ALL, 10.0, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).latitude()).isEqualTo(16.30);
        verify(roadDistanceService, times(2)).getRoadMetrics(any(Coordinate.class), anyList());
    }

    private NearbyPlaceProjection candidate(double latitude, double straightKm) {
        NearbyPlaceProjection proj = mock(NearbyPlaceProjection.class);
        when(proj.getId()).thenReturn(UUID.randomUUID());
        when(proj.getName()).thenReturn("Place " + latitude);
        when(proj.getCategory()).thenReturn("ATTRACTION");
        when(proj.getLatitude()).thenReturn(latitude);
        when(proj.getLongitude()).thenReturn(108.2022);
        when(proj.getDistanceKm()).thenReturn(straightKm);
        return proj;
    }

    private void stubCandidates(List<NearbyPlaceProjection> candidates) {
        when(placeRepository.findNearbyPlaces(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt()))
                .thenReturn(candidates);
        when(placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(any())).thenReturn(Collections.emptyList());
        when(placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());
    }

    private void stubRoadDistanceAsStraightLineTimes(double factor) {
        when(roadDistanceService.getRoadMetrics(any(Coordinate.class), anyList())).thenAnswer(inv -> {
            List<Coordinate> dests = inv.getArgument(1);
            Map<Coordinate, RoadMetrics> out = new HashMap<>();
            for (Coordinate c : dests) {
                double km = Math.round(RoadDistanceService.haversineKm(16.0544, 108.2022, c.latitude(), c.longitude()) * factor * 10) / 10.0;
                out.put(c, RoadMetrics.builder().distanceKm(km).durationMinutes(5).fallback(false).build());
            }
            return out;
        });
    }

    @Test
    void getNearbyPlaces_WhenDatabaseEmpty_ReturnsEmptyListAndCachesResult() {
        when(placeRepository.findNearbyPlaces(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(16.0544, 108.2022, PlaceCategory.RESTAURANT, 5.0, 10);

        assertThat(result).isEmpty();
        verify(valueOperations).set(anyString(), eq("[]"), any());
        verify(placeRepository, times(1)).findNearbyPlaces(eq(16.0544), eq(108.2022), eq(5.3),
                doubleThat(minLat -> minLat < 16.0544), doubleThat(maxLat -> maxLat > 16.0544),
                doubleThat(minLng -> minLng < 108.2022), doubleThat(maxLng -> maxLng > 108.2022),
                eq("RESTAURANT"), eq(300));
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
        when(mockProj.getDistanceKm()).thenReturn(4.8);

        when(placeRepository.findNearbyPlaces(eq(16.0544), eq(108.2022), eq(5.3),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("ALL"), eq(300)))
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
