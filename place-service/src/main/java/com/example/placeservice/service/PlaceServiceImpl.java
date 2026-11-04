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

    @Override
    @Transactional(readOnly = true)
    public List<NearbyPlaceResponse> getNearbyPlaces(Double latitude, Double longitude, PlaceCategory category, Double radiusKm, Integer limit) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude coordinates must not be null");
        }

        double targetRadiusKm = (radiusKm != null && radiusKm > 0) ? Math.min(radiusKm, 30.0) : 10.0;
        int targetLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
        PlaceCategory targetCategory = (category != null) ? category : PlaceCategory.ALL;

        // Check Redis Cache
        String cacheKey = String.format(Locale.US, "nearby:coords:%.4f:%.4f:%s:%.1f:%d",
                latitude, longitude, targetCategory.name(), targetRadiusKm, targetLimit);
        List<NearbyPlaceResponse> cachedResponses = getFromCache(cacheKey);
        if (cachedResponses != null) {
            log.debug("[NEARBY_PLACES] Cache HIT for key {}", cacheKey);
            return cachedResponses;
        }

        // Query Local Database (Haversine formula in native SQL) with 1.25x expanded radius buffer
        double candidateRadiusKm = targetRadiusKm * 1.25;
        int candidateLimit = Math.max(targetLimit * 2, 25);
        List<NearbyPlaceProjection> projections = placeRepository.findNearbyPlaces(
                latitude,
                longitude,
                candidateRadiusKm,
                targetCategory.name(),
                candidateLimit
        );

        if (!projections.isEmpty()) {
            log.info("[NEARBY_PLACES] Found {} candidate places in DB for coordinates ({}, {}) within {} km (buffer: {} km). Enriching with road distances.",
                    projections.size(), latitude, longitude, targetRadiusKm, candidateRadiusKm);
            List<NearbyPlaceResponse> responses = buildResponsesWithRoadMetrics(latitude, longitude, projections, targetRadiusKm, targetLimit);
            saveToCache(cacheKey, responses);
            return responses;
        }

        log.info("[NEARBY_PLACES] Zero places found in DB within {} km for coordinates ({}, {}). Returning empty list.",
                candidateRadiusKm, latitude, longitude);
        saveToCache(cacheKey, Collections.emptyList());
        return Collections.emptyList();
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

    private List<NearbyPlaceResponse> buildResponsesWithRoadMetrics(
            Double originLat,
            Double originLng,
            List<NearbyPlaceProjection> projections,
            double targetRadiusKm,
            int limit
    ) {
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }

        Coordinate origin = new Coordinate(originLat, originLng);
        List<Coordinate> destinationCoordinates = projections.stream()
                .map(p -> new Coordinate(p.getLatitude(), p.getLongitude()))
                .collect(Collectors.toList());

        Map<Coordinate, RoadMetrics> roadMetricsMap = roadDistanceService.getRoadMetrics(origin, destinationCoordinates);

        List<UUID> placeIds = projections.stream().map(NearbyPlaceProjection::getId).collect(Collectors.toList());

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

        return projections.stream()
                .map(proj -> {
                    Coordinate dest = new Coordinate(proj.getLatitude(), proj.getLongitude());
                    RoadMetrics metrics = roadMetricsMap.get(dest);

                    Double roadDistKm;
                    Integer durationMin;
                    if (metrics != null) {
                        roadDistKm = metrics.getDistanceKm();
                        durationMin = metrics.getDurationMinutes();
                    } else {
                        roadDistKm = proj.getDistanceKm() != null ? Math.round(proj.getDistanceKm() * 1.35 * 10.0) / 10.0 : 0.0;
                        durationMin = NearbyPlaceResponse.calculateTravelTimeMinutes(roadDistKm);
                    }

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
                            .distanceKm(roadDistKm)
                            .estimatedTravelTimeMinutes(durationMin)
                            .photos(photosByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .reviews(reviewsByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .build();
                })
                .filter(res -> res.distanceKm() != null && res.distanceKm() <= targetRadiusKm)
                .sorted(Comparator.comparing(NearbyPlaceResponse::distanceKm))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<NearbyPlaceResponse> buildResponsesFromProjections(List<NearbyPlaceProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> placeIds = projections.stream().map(NearbyPlaceProjection::getId).collect(Collectors.toList());

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

        return projections.stream()
                .map(proj -> {
                    Double dist = proj.getDistanceKm() != null ? Math.round(proj.getDistanceKm() * 100.0) / 100.0 : 0.0;
                    Integer travelTime = NearbyPlaceResponse.calculateTravelTimeMinutes(dist);

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
                            .distanceKm(dist)
                            .estimatedTravelTimeMinutes(travelTime)
                            .photos(photosByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .reviews(reviewsByPlaceId.getOrDefault(proj.getId(), Collections.emptyList()))
                            .build();
                })
                .collect(Collectors.toList());
    }

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

    private void saveToCache(String cacheKey, List<NearbyPlaceResponse> responses) {
        try {
            String json = objectMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("[NEARBY_PLACES] Redis cache write failed for key {}: {}", cacheKey, ex.getMessage());
        }
    }
}
