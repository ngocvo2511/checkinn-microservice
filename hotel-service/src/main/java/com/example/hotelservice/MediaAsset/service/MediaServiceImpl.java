package com.example.hotelservice.MediaAsset.service;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetRepository repository;

    @Override
    public MediaAsset uploadMedia(MediaUploadRequest request) {
        MediaAsset asset = new MediaAsset();
        asset.setTargetId(request.targetId());
        asset.setTargetType(request.targetType());
        asset.setUrl(request.url());
        asset.setIsThumbnail(request.thumbnail());
        asset.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        return repository.save(asset);
    }

    @Override
    public List<MediaAsset> getByTarget(UUID targetId, MediaTargetType targetType) {
        return repository.findByTargetIdAndTargetTypeOrderBySortOrderAsc(targetId, targetType);
    }

    @Override
    public void deleteMedia(UUID mediaId) {
        repository.deleteById(mediaId);
    }
}
