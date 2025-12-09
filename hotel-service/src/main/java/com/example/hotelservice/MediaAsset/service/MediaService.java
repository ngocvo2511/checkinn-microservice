package com.example.hotelservice.MediaAsset.service;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MediaService {
    MediaAsset uploadMultipart(UUID targetId,
                               MediaTargetType targetType,
                               Boolean isThumbnail,
                               Integer sortOrder,
                               MultipartFile file) throws Exception;

    List<MediaAsset> getByTarget(UUID targetId, MediaTargetType targetType);

    void delete(UUID mediaId);
    MediaAsset updateThumbnail(UUID mediaId);
}
