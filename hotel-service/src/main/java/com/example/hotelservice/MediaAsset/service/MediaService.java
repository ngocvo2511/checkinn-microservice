package com.example.hotelservice.MediaAsset.service;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.dto.request.ReorderMediaRequest;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MediaService {
    MediaAsset uploadMedia(MediaUploadRequest request);

    List<MediaAsset> getByTarget(UUID targetId, MediaTargetType targetType);

    void deleteMedia(UUID mediaId);
}
