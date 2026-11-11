package com.example.hotelservice.MediaAsset.service;

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

    private final MediaMutationService mutations;

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

        return mutations.register(targetId, targetType, url, isThumbnail, sortOrder);
    }

    @Override
    public List<MediaAsset> getByTarget(UUID targetId, MediaTargetType targetType) {
        UUID hotelId = targetId;
        if (targetType == MediaTargetType.ROOM_TYPE) {
            var room = rooms.findById(targetId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Room not found"));
            if (!Boolean.TRUE.equals(room.getIsActive())) throw new jakarta.persistence.EntityNotFoundException("Room not found");
            hotelId = room.getHotelId();
        }
        var hotel = hotels.findById(hotelId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Hotel not found"));
        if (!Boolean.TRUE.equals(hotel.getIsActive()) || hotel.getApprovedStatus() != com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED)
            throw new jakarta.persistence.EntityNotFoundException("Hotel not found");
        return repository.findByTargetIdAndTargetTypeOrderBySortOrderAsc(targetId, targetType);
    }

    @Override public List<MediaAsset> getPublicRoomMediaByHotel(UUID hotelId) {
        return repository.findPublicRoomMediaByHotel(hotelId, MediaTargetType.ROOM_TYPE,
                com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED);
    }

    @Override public List<MediaAsset> getPreviewByTarget(UUID targetId, MediaTargetType targetType) {
        requireTargetAccess(targetId, targetType);
        return repository.findByTargetIdAndTargetTypeOrderBySortOrderAsc(targetId, targetType);
    }

    @Override
    public void delete(UUID id) {
        var location = repository.findLocationById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Media not found"));
        requireTargetAccess(location.getTargetId(), location.getTargetType());
        if (!mediaClient.deleteByUrl(location.getUrl())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Storage did not acknowledge deletion");
        }
        mutations.remove(id, location.getTargetId(), location.getTargetType(), location.getUrl());
    }

    @Override
    public MediaAsset updateThumbnail(UUID mediaId) {
        return mutations.selectThumbnail(mediaId);
    }
}
