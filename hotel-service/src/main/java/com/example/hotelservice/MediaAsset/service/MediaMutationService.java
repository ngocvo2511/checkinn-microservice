package com.example.hotelservice.MediaAsset.service;

import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import java.util.UUID;

/** Database-only mutations. Storage calls must finish before entering these transactions. */
@Service
@RequiredArgsConstructor
public class MediaMutationService {
    private final MediaAssetRepository assets;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;

    private JwtAuthenticationToken actor() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken actor) return actor;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid bearer token required");
    }

    private void lockTarget(UUID targetId, MediaTargetType type, JwtAuthenticationToken actor) {
        UUID hotelId = type == MediaTargetType.ROOM_TYPE
                ? rooms.findHotelIdById(targetId).orElseThrow(() -> new EntityNotFoundException("Room not found")) : targetId;
        // Lock the parent before the child; draft deletion uses the same parent lock.
        var hotel = hotels.findForUpdate(hotelId).orElseThrow(() -> new EntityNotFoundException("Hotel not found"));
        if (!"ADMIN".equals(actor.getToken().getClaimAsString("role"))
                && !Objects.equals(hotel.getOwnerId(), UUID.fromString(actor.getToken().getSubject())))
            throw new SecurityException("You do not own the media's hotel");
        if (type == MediaTargetType.ROOM_TYPE) {
            var room = rooms.findByIdForUpdate(targetId).orElseThrow(() -> new EntityNotFoundException("Room not found"));
            if (!Objects.equals(room.getHotelId(), hotelId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Media target changed");
        }
    }

    @Transactional
    public MediaAsset register(UUID targetId, MediaTargetType type, String url, Boolean thumbnail, Integer sortOrder) {
        lockTarget(targetId, type, actor());
        if (Boolean.TRUE.equals(thumbnail)) {
            var peers = assets.findByTargetIdAndTargetType(targetId, type);
            peers.forEach(peer -> peer.setIsThumbnail(false));
            assets.saveAll(peers);
        }
        return assets.saveAndFlush(MediaAsset.builder().targetId(targetId).targetType(type).url(url)
                .isThumbnail(Boolean.TRUE.equals(thumbnail)).sortOrder(sortOrder == null ? 0 : sortOrder).build());
    }

    @Transactional
    public MediaAsset selectThumbnail(UUID id) {
        var actor = actor();
        var location = assets.findLocationById(id).orElseThrow(() -> new EntityNotFoundException("Media not found"));
        lockTarget(location.getTargetId(), location.getTargetType(), actor);
        var selected = current(id, location.getTargetId(), location.getTargetType(), location.getUrl());
        var peers = assets.findByTargetIdAndTargetType(selected.getTargetId(), selected.getTargetType());
        peers.forEach(peer -> peer.setIsThumbnail(peer.getId().equals(id)));
        selected.setIsThumbnail(true);
        assets.saveAll(peers);
        assets.flush();
        return selected;
    }

    @Transactional
    public void remove(UUID id, UUID targetId, MediaTargetType type, String url) {
        lockTarget(targetId, type, actor());
        var selected = current(id, targetId, type, url);
        Integer order = selected.getSortOrder();
        assets.delete(selected);
        assets.flush();
        var peers = assets.findByTargetIdAndTargetType(targetId, type);
        for (var peer : peers) {
            if (order != null && peer.getSortOrder() != null && peer.getSortOrder() > order)
                peer.setSortOrder(peer.getSortOrder() - 1);
        }
        assets.saveAll(peers);
        assets.flush();
    }

    private MediaAsset current(UUID id, UUID targetId, MediaTargetType type, String url) {
        var media = assets.findById(id).orElseThrow(() -> new EntityNotFoundException("Media not found"));
        if (!Objects.equals(media.getTargetId(), targetId) || media.getTargetType() != type || !Objects.equals(media.getUrl(), url))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media target changed");
        return media;
    }
}
