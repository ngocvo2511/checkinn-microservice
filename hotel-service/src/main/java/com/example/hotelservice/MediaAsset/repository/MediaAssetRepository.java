package com.example.hotelservice.MediaAsset.repository;

import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
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
