package com.example.placeservice.repository;

import com.example.placeservice.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaceRepository extends JpaRepository<Place, UUID> {

    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    Optional<Place> findByFsqId(String fsqId);

    List<Place> findByCityId(UUID cityId);

    List<Place> findByProvinceNameIgnoreCase(String provinceName);

    /** First page of a keyword search. {@code pattern} must already have LIKE wildcards escaped with '!'. */
    @Query(value = """
        SELECT p.* FROM places p
        WHERE p.name ILIKE :pattern ESCAPE '!'
        ORDER BY p.name ASC, p.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Place> searchByName(@Param("pattern") String pattern, @Param("limit") int limit);

    /** Keyset continuation of {@link #searchByName}: rows strictly after {@code (afterName, afterId)}. */
    @Query(value = """
        SELECT p.* FROM places p
        WHERE p.name ILIKE :pattern ESCAPE '!'
          AND (p.name, p.id) > (:afterName, :afterId)
        ORDER BY p.name ASC, p.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Place> searchByNameAfter(@Param("pattern") String pattern,
                                  @Param("afterName") String afterName,
                                  @Param("afterId") UUID afterId,
                                  @Param("limit") int limit);

    @Query(value = """
        SELECT p.*
        FROM places p
        WHERE (6371000.0 * acos(
                LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:targetLat)) * cos(radians(p.latitude)) * 
                    cos(radians(p.longitude) - radians(:targetLng)) + 
                    sin(radians(:targetLat)) * sin(radians(p.latitude))
                ))
            )) <= :radiusMeters
        ORDER BY (6371000.0 * acos(
                LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:targetLat)) * cos(radians(p.latitude)) * 
                    cos(radians(p.longitude) - radians(:targetLng)) + 
                    sin(radians(:targetLat)) * sin(radians(p.latitude))
                ))
            )) ASC
        """, nativeQuery = true)
    List<Place> findWithinDistanceMeters(
            @Param("targetLat") Double targetLat,
            @Param("targetLng") Double targetLng,
            @Param("radiusMeters") Double radiusMeters
    );

    @Query(value = """
        SELECT 
            p.id AS id,
            p.google_place_id AS googlePlaceId,
            p.name AS name,
            p.category AS category,
            p.address AS address,
            p.latitude AS latitude,
            p.longitude AS longitude,
            p.rating AS rating,
            p.user_ratings_total AS userRatingsTotal,
            p.cover_image_url AS coverImageUrl,
            p.detail_url AS detailUrl,
            p.province_name AS provinceName,
            (6371 * acos(
                LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:centerLat)) * cos(radians(p.latitude)) * 
                    cos(radians(p.longitude) - radians(:centerLng)) + 
                    sin(radians(:centerLat)) * sin(radians(p.latitude))
                ))
            )) AS distanceKm
        FROM places p
        WHERE (:category IS NULL OR :category = 'ALL' OR p.category = :category)
          AND p.latitude BETWEEN :minLat AND :maxLat
          AND p.longitude BETWEEN :minLng AND :maxLng
          AND (6371 * acos(
                LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:centerLat)) * cos(radians(p.latitude)) * 
                    cos(radians(p.longitude) - radians(:centerLng)) + 
                    sin(radians(:centerLat)) * sin(radians(p.latitude))
                ))
            )) <= :radiusKm
        ORDER BY distanceKm ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyPlaceProjection> findNearbyPlaces(
            @Param("centerLat") Double centerLat,
            @Param("centerLng") Double centerLng,
            @Param("radiusKm") Double radiusKm,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("category") String category,
            @Param("limit") int limit
    );
}
