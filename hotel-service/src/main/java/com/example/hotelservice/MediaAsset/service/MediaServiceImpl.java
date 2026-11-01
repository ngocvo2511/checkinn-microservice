package com.example.hotelservice.MediaAsset.service;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetRepository repository;
    private final MediaClient mediaClient;
    private final com.example.hotelservice.Hotel.repository.HotelRepository hotels;
    private final com.example.hotelservice.Room.repository.RoomTypeRepository rooms;

    private void requireTargetAccess(UUID targetId, MediaTargetType targetType) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken actor)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Valid bearer token required");
        }
        UUID hotelId = targetType == MediaTargetType.ROOM_TYPE
                ? rooms.findById(targetId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Room not found")).getHotelId()
                : targetId;
        if ("ADMIN".equals(actor.getToken().getClaimAsString("role"))) {
            if (targetType == MediaTargetType.HOTEL && !hotels.existsById(hotelId)) {
                throw new jakarta.persistence.EntityNotFoundException("Hotel not found");
            }
            return;
        }
        if (!hotels.existsByIdAndOwnerId(hotelId, UUID.fromString(actor.getToken().getSubject()))) {
            throw new SecurityException("You do not own the media's hotel");
        }
    }

    @Override
    public MediaAsset uploadMultipart(
            UUID targetId,
            MediaTargetType targetType,
            Boolean isThumbnail,
            Integer sortOrder,
            MultipartFile file
    ) throws Exception {

        requireTargetAccess(targetId, targetType);

        // 1) Gửi ảnh qua media-service để upload lên MinIO
        String url = mediaClient.upload(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );

        // 2) Tạo record để lưu metadata
        MediaAsset asset = MediaAsset.builder()
                .targetId(targetId)
                .targetType(targetType)
                .url(url)
                .isThumbnail(isThumbnail != null ? isThumbnail : false)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build();

        return repository.save(asset);
    }

    @Override
    public List<MediaAsset> getByTarget(UUID targetId, MediaTargetType targetType) {
        return repository.findByTargetIdAndTargetTypeOrderBySortOrderAsc(targetId, targetType);
    }

    @Override
    public void delete(UUID id) {
        MediaAsset delete = repository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Media not found"));
        requireTargetAccess(delete.getTargetId(), delete.getTargetType());
        UUID targetId = delete.getTargetId();
        MediaTargetType targetType = delete.getTargetType();
        Integer deletedOrder = delete.getSortOrder();
        // 1) Xoá file khỏi MinIO qua media-service
        if (!mediaClient.deleteByUrl(delete.getUrl())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Storage did not acknowledge deletion");
        }

        // 2) Xoá metadata DB
        repository.delete(delete);

        //3) reorder sau khi xoá
        List<MediaAsset> assets = repository.findByTargetIdAndTargetType(targetId, targetType);

        for (MediaAsset asset : assets) {
            if (asset.getSortOrder() > deletedOrder) {
                asset.setSortOrder(asset.getSortOrder() - 1);
            }
        }

        repository.saveAll(assets);
    }

    @Override
    public MediaAsset updateThumbnail(UUID mediaId) {

        MediaAsset selected = repository.findById(mediaId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Media not found"));

        requireTargetAccess(selected.getTargetId(), selected.getTargetType());
        UUID targetId = selected.getTargetId();
        MediaTargetType targetType = selected.getTargetType();

        List<MediaAsset> allAssets = repository.findByTargetIdAndTargetType(targetId, targetType);

        for (MediaAsset asset : allAssets) {
            if (asset.getId().equals(mediaId)) {
                asset.setIsThumbnail(true);
            } else {
                asset.setIsThumbnail(false);
            }
        }

        repository.saveAll(allAssets);

        return selected;
    }
}
