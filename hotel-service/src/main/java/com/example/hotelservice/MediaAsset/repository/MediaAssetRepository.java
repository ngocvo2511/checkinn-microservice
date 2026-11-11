package com.example.hotelservice.MediaAsset.repository;

import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    interface Location {
        UUID getTargetId();
        MediaTargetType getTargetType();
        String getUrl();
    }
    @org.springframework.data.jpa.repository.Query("SELECT m.targetId AS targetId, m.targetType AS targetType, m.url AS url FROM MediaAsset m WHERE m.id = ?1")
    java.util.Optional<Location> findLocationById(UUID id);

    @org.springframework.data.jpa.repository.Query("""
        SELECT m FROM MediaAsset m, RoomType r, Hotel h
        WHERE m.targetId=r.id AND m.targetType=:targetType AND r.hotelId=h.id
          AND h.id=:hotelId AND h.isActive=true AND h.approvedStatus=:approval AND r.isActive=true
        ORDER BY m.sortOrder ASC, m.id ASC
        """)
    List<MediaAsset> findPublicRoomMediaByHotel(
            @org.springframework.data.repository.query.Param("hotelId") UUID hotelId,
            @org.springframework.data.repository.query.Param("targetType") MediaTargetType targetType,
            @org.springframework.data.repository.query.Param("approval") com.example.hotelservice.Hotel.enums.HotelApprovalStatus approval);

    List<MediaAsset> findByTargetIdAndTargetTypeOrderBySortOrderAsc(
            UUID targetId,
            MediaTargetType targetType
    );
    List<MediaAsset> findByTargetIdAndTargetType(
            UUID targetId,
            MediaTargetType targetType
    );
    MediaAsset findFirstByTargetIdAndTargetTypeAndIsThumbnailTrue(UUID targetId, MediaTargetType targetType);
}
