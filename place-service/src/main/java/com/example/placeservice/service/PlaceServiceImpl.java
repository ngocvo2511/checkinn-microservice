package com.example.placeservice.service;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.RoadMetrics;
import com.example.placeservice.dto.response.NearbyPlaceResponse;
import com.example.placeservice.dto.response.PlacePhotoDto;
import com.example.placeservice.dto.response.PlaceReviewDto;
import com.example.placeservice.entity.Place;
import com.example.placeservice.enums.PlaceCategory;
import com.example.placeservice.repository.NearbyPlaceProjection;
import com.example.placeservice.repository.PlacePhotoRepository;
import com.example.placeservice.repository.PlaceRepository;
import com.example.placeservice.repository.PlaceReviewRepository;
import com.example.placeservice.service.osrm.RoadDistanceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {

    private final PlaceRepository placeRepository;
    private final PlacePhotoRepository placePhotoRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RoadDistanceService roadDistanceService;

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    // Short enough that results recover soon after OSRM comes back, long enough to avoid hammering it while down.
    private static final Duration ESTIMATED_CACHE_TTL = Duration.ofMinutes(5);
    // OSRM snaps both ends to the nearest road, which can make a routed distance slightly shorter than the straight line.
    private static final double SNAP_TOLERANCE_KM = 0.3;
    private static final double KM_PER_DEGREE_LAT = 111.32;
    private static final int MAX_CANDIDATES = 300;
    private static final int ROUTING_BATCH_SIZE = 50;

    @Override
    @Transactional(readOnly = true)
    public List<NearbyPlaceResponse> getNearbyPlaces(Double latitude, Double longitude, PlaceCategory category, Double radiusKm, Integer limit) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude coordinates must not be null");
        }

        double targetRadiusKm = (radiusKm != null && radiusKm > 0) ? Math.min(radiusKm, 30.0) : 10.0;
        int targetLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
        PlaceCategory targetCategory = (category != null) ? category : PlaceCategory.ALL;

        // Bump the version segment whenever the estimation model changes, so stale cached values are not served.
        String cacheKey = String.format(Locale.US, "nearby:v3:coords:%.4f:%.4f:%s:%.1f:%d",
                latitude, longitude, targetCategory.name(), targetRadiusKm, targetLimit);
        List<NearbyPlaceResponse> cachedResponses = getFromCache(cacheKey);
        if (cachedResponses != null) {
            log.debug("[NEARBY_PLACES] Cache HIT for key {}", cacheKey);
            return cachedResponses;
        }

        double searchRadiusKm = targetRadiusKm + SNAP_TOLERANCE_KM;
        double latSpan = searchRadiusKm / KM_PER_DEGREE_LAT;
        double lngSpan = searchRadiusKm / (KM_PER_DEGREE_LAT * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));
        List<NearbyPlaceProjection> candidates = placeRepository.findNearbyPlaces(
                latitude,
                longitude,
                searchRadiusKm,
                latitude - latSpan,
                latitude + latSpan,
                longitude - lngSpan,
                longitude + lngSpan,
                targetCategory.name(),
                MAX_CANDIDATES
        );

        List<RankedCandidate> ranked = rankByRoadDistance(new Coordinate(latitude, longitude), candidates, targetRadiusKm, targetLimit);
        log.info("[NEARBY_PLACES] {} of {} candidates within {} km road distance of ({}, {})",
                ranked.size(), candidates.size(), targetRadiusKm, latitude, longitude);

        List<NearbyPlaceResponse> responses = toNearbyResponses(ranked);
        boolean anyEstimated = responses.stream().anyMatch(r -> Boolean.TRUE.equals(r.distanceEstimated()));
        saveToCache(cacheKey, responses, anyEstimated ? ESTIMATED_CACHE_TTL : CACHE_TTL);
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public NearbyPlaceResponse getPlaceById(UUID id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found with id: " + id));

        List<PlacePhotoDto> photoDtos = placePhotoRepository.findByPlaceIdOrderByDisplayOrderAsc(place.getId()).stream()
                .map(p -> PlacePhotoDto.builder()
                        .id(p.getId())
                        .photoUrl(p.getPhotoUrl())
                        .displayOrder(p.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());

        List<PlaceReviewDto> reviewDtos = placeReviewRepository.findByPlaceIdOrderByCreatedAtDesc(place.getId()).stream()
                .map(r -> PlaceReviewDto.builder()
                        .id(r.getId())
                        .authorName(r.getAuthorName())
                        .authorAvatarUrl(r.getAuthorAvatarUrl())
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .relativeTimeDescription(r.getRelativeTimeDescription())
                        .publishTime(r.getPublishTime())
                        .build())
                .collect(Collectors.toList());

        return NearbyPlaceResponse.builder()
                .id(place.getId())
                .googlePlaceId(place.getGooglePlaceId())
                .name(place.getName())
                .category(place.getCategory() != null ? place.getCategory().name() : "ATTRACTION")
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .rating(place.getRating())
                .userRatingsTotal(place.getUserRatingsTotal())
                .coverImageUrl(place.getCoverImageUrl())
                .detailUrl(place.getDetailUrl())
                .provinceName(place.getProvinceName())
                .distanceKm(0.0)
                .estimatedTravelTimeMinutes(1)
                .photos(photoDtos)
                .reviews(reviewDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyPlaceResponse> getPlacesByCityId(UUID cityId, PlaceCategory category, Integer limit) {
        if (cityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cityId must not be null");
        }

        int targetLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 20;
        List<Place> places = placeRepository.findByCityId(cityId);

        if (category != null && category != PlaceCategory.ALL) {
            places = places.stream()
                    .filter(p -> p.getCategory() == category)
                    .toList();
        }

        if (places.size() > targetLimit) {
            places = places.subList(0, targetLimit);
        }

        return places.stream()
                .map(place -> NearbyPlaceResponse.builder()
                        .id(place.getId())
                        .googlePlaceId(place.getGooglePlaceId())
                        .name(place.getName())
                        .category(place.getCategory() != null ? place.getCategory().name() : "ATTRACTION")
                        .address(place.getAddress())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .rating(place.getRating())
                        .userRatingsTotal(place.getUserRatingsTotal())
                        .coverImageUrl(place.getCoverImageUrl())
                        .detailUrl(place.getDetailUrl())
                        .provinceName(place.getProvinceName())
                        .distanceKm(0.0)
                        .estimatedTravelTimeMinutes(1)
                        .photos(Collections.emptyList())
                        .reviews(Collections.emptyList())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyPlaceResponse> getPlacesByProvince(String provinceName, PlaceCategory category, Integer limit) {
        if (provinceName == null || provinceName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provinceName must not be blank");
        }

        int targetLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 20;
        List<Place> places = placeRepository.findByProvinceNameIgnoreCase(provinceName.trim());

        if (category != null && category != PlaceCategory.ALL) {
            places = places.stream()
                    .filter(p -> p.getCategory() == category)
                    .toList();
        }

        if (places.size() > targetLimit) {
            places = places.subList(0, targetLimit);
        }

        List<UUID> placeIds = places.stream().map(Place::getId).toList();

        Map<UUID, List<PlacePhotoDto>> photosByPlaceId = placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(placeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getPlace().getId(),
                        Collectors.mapping(photo -> PlacePhotoDto.builder()
                                .id(photo.getId())
                                .photoUrl(photo.getPhotoUrl())
                                .displayOrder(photo.getDisplayOrder())
                                .build(), Collectors.toList())
                ));

        Map<UUID, List<PlaceReviewDto>> reviewsByPlaceId = placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(placeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        review -> review.getPlace().getId(),
                        Collectors.mapping(review -> PlaceReviewDto.builder()
                                .id(review.getId())
                                .authorName(review.getAuthorName())
                                .authorAvatarUrl(review.getAuthorAvatarUrl())
                                .rating(review.getRating())
                                .comment(review.getComment())
                                .relativeTimeDescription(review.getRelativeTimeDescription())
                                .publishTime(review.getPublishTime())
                                .build(), Collectors.toList())
                ));

        return places.stream()
                .map(place -> NearbyPlaceResponse.builder()
                        .id(place.getId())
                        .googlePlaceId(place.getGooglePlaceId())
                        .name(place.getName())
                        .category(place.getCategory() != null ? place.getCategory().name() : "ATTRACTION")
                        .address(place.getAddress())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .rating(place.getRating())
                        .userRatingsTotal(place.getUserRatingsTotal())
                        .coverImageUrl(place.getCoverImageUrl())
                        .detailUrl(place.getDetailUrl())
                        .provinceName(place.getProvinceName())
                        .distanceKm(0.0)
                        .estimatedTravelTimeMinutes(1)
                        .photos(photosByPlaceId.getOrDefault(place.getId(), Collections.emptyList()))
                        .reviews(reviewsByPlaceId.getOrDefault(place.getId(), Collections.emptyList()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<RankedCandidate> rankByRoadDistance(
            Coordinate origin,
            List<NearbyPlaceProjection> candidates,
            double targetRadiusKm,
            int limit
    ) {
        List<RankedCandidate> withinRadius = new ArrayList<>();
        for (int start = 0; start < candidates.size(); start += ROUTING_BATCH_SIZE) {
            int end = Math.min(start + ROUTING_BATCH_SIZE, candidates.size());
            List<NearbyPlaceProjection> batch = candidates.subList(start, end);
            Map<Coordinate, RoadMetrics> metrics = roadDistanceService.getRoadMetrics(origin,
                    batch.stream().map(PlaceServiceImpl::coordinateOf).toList());

            for (NearbyPlaceProjection candidate : batch) {
                Coordinate dest = coordinateOf(candidate);
                RoadMetrics m = metrics.get(dest);
                if (m == null) {
                    m = roadDistanceService.calculateCircuityFallback(origin, dest);
                }
                if (m.getDistanceKm() != null && m.getDistanceKm() <= targetRadiusKm) {
                    withinRadius.add(new RankedCandidate(candidate, m));
                }
            }

            if (end < candidates.size() && withinRadius.size() >= limit) {
                withinRadius.sort(BY_ROAD_DISTANCE);
                double kthRoadKm = withinRadius.get(limit - 1).metrics().getDistanceKm();
                Double nextStraightKm = candidates.get(end).getDistanceKm();
                // Road distance is never shorter than the straight line (minus snapping slack), and candidates
                // arrive in straight-line order, so no remaining candidate can displace the current top results.
                if (nextStraightKm != null && nextStraightKm - SNAP_TOLERANCE_KM >= kthRoadKm) {
                    break;
                }
            }
        }
        withinRadius.sort(BY_ROAD_DISTANCE);
        return withinRadius.size() > limit ? withinRadius.subList(0, limit) : withinRadius;
    }

    private List<NearbyPlaceResponse> toNearbyResponses(List<RankedCandidate> ranked) {
        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> placeIds = ranked.stream().map(r -> r.place().getId()).toList();

        Map<UUID, List<PlacePhotoDto>> photosByPlaceId = placePhotoRepository.findByPlace_IdInOrderByDisplayOrderAsc(placeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getPlace().getId(),
                        Collectors.mapping(photo -> PlacePhotoDto.builder()
                                .id(photo.getId())
                                .photoUrl(photo.getPhotoUrl())
                                .displayOrder(photo.getDisplayOrder())
                                .build(), Collectors.toList())
                ));

        Map<UUID, List<PlaceReviewDto>> reviewsByPlaceId = placeReviewRepository.findByPlace_IdInOrderByCreatedAtDesc(placeIds)
                .stream()
                .collect(Collectors.groupingBy(
                        review -> review.getPlace().getId(),
                        Collectors.mapping(review -> PlaceReviewDto.builder()
                                .id(review.getId())
                                .authorName(review.getAuthorName())
                                .authorAvatarUrl(review.getAuthorAvatarUrl())
                                .rating(review.getRating())
                                .comment(review.getComment())
                                .relativeTimeDescription(review.getRelativeTimeDescription())
                                .publishTime(review.getPublishTime())
                                .build(), Collectors.toList())
                ));

        return ranked.stream()
                .map(r -> {
                    NearbyPlaceProjection proj = r.place();
                    return NearbyPlaceResponse.builder()
                            .id(proj.getId())
                            .googlePlaceId(proj.getGooglePlaceId())
                            .name(proj.getName())
                            .category(proj.getCategory())
                            .address(proj.getAddress())
                            .latitude(proj.getLatitude())
                            .longitude(proj.getLongitude())
                            .rating(proj.getRating() != null ? Math.round(proj.getRating() * 10.0) / 10.0 : null)
                            .userRatingsTotal(proj.getUserRatingsTotal())
                            .coverImageUrl(proj.getCoverImageUrl())
                            .detailUrl(proj.getDetailUrl())
                            .provinceName(proj.getProvinceName())
                            .distanceKm(r.metrics().getDistanceKm())
                            .estimatedTravelTimeMinutes(r.metrics().getDurationMinutes())
                            .distanceEstimated(r.metrics().isFallback())
                            .photos(photosByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .reviews(reviewsByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static Coordinate coordinateOf(NearbyPlaceProjection place) {
        return new Coordinate(place.getLatitude(), place.getLongitude());
    }

    private record RankedCandidate(NearbyPlaceProjection place, RoadMetrics metrics) {
    }

    private static final Comparator<RankedCandidate> BY_ROAD_DISTANCE =
            Comparator.comparing((RankedCandidate r) -> r.metrics().getDistanceKm());

    private List<NearbyPlaceResponse> getFromCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<List<NearbyPlaceResponse>>() {});
            }
        } catch (Exception ex) {
            log.warn("[NEARBY_PLACES] Redis cache read failed for key {}: {}", cacheKey, ex.getMessage());
        }
        return null;
    }

    private void saveToCache(String cacheKey, List<NearbyPlaceResponse> responses, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (Exception ex) {
            log.warn("[NEARBY_PLACES] Redis cache write failed for key {}: {}", cacheKey, ex.getMessage());
        }
    }
}
