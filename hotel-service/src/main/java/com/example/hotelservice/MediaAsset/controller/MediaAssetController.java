package com.example.hotelservice.MediaAsset.controller;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper;
import com.example.hotelservice.MediaAsset.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/medias")
@RequiredArgsConstructor
@Slf4j
public class MediaAssetController {

    private final MediaService mediaAssetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAsset> uploadMedia(
            @RequestParam UUID targetId,
            @RequestParam MediaTargetType targetType,
            @RequestParam(required = false, defaultValue = "false") Boolean isThumbnail,
            @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        log.info("[MEDIA_CONTROLLER_UPLOAD] Upload media request received - targetId: {}, targetType: {}, isThumbnail: {}, sortOrder: {}, fileName: {}, fileSize: {}",
            targetId, targetType, isThumbnail, sortOrder, file.getOriginalFilename(), file.getSize());

        var asset = mediaAssetService.uploadMultipart(
                targetId,
                targetType,
                isThumbnail,
                sortOrder,
                file
        );

        log.info("[MEDIA_CONTROLLER_UPLOAD] Upload media success - mediaId: {}, targetId: {}, targetType: {}", asset.getId(), targetId, targetType);
        return ResponseEntity.ok(asset);
    }

    @GetMapping
    public ResponseEntity<?> getByTarget(
            @RequestParam UUID targetId,
            @RequestParam MediaTargetType targetType
    ) {
        log.info("[MEDIA_CONTROLLER_GET_BY_TARGET] Fetch media by target request received - targetId: {}, targetType: {}", targetId, targetType);
        var assets = mediaAssetService.getByTarget(targetId, targetType);
        log.info("[MEDIA_CONTROLLER_GET_BY_TARGET] Fetch media by target success - targetId: {}, targetType: {}, count: {}", targetId, targetType, assets.size());
        return ResponseEntity.ok(assets);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        log.info("[MEDIA_CONTROLLER_DELETE] Delete media request received - mediaId: {}", id);
        mediaAssetService.delete(id);
        log.info("[MEDIA_CONTROLLER_DELETE] Delete media success - mediaId: {}", id);
        return ResponseEntity.ok("Deleted");
    }

    @PatchMapping("/{mediaId}/thumbnail")
    public ResponseEntity<?> updateThumbnail(@PathVariable UUID mediaId) {
        log.info("[MEDIA_CONTROLLER_THUMBNAIL] Update thumbnail request received - mediaId: {}", mediaId);
        var updated = mediaAssetService.updateThumbnail(mediaId);
        log.info("[MEDIA_CONTROLLER_THUMBNAIL] Update thumbnail success - mediaId: {}", mediaId);
        return ResponseEntity.ok(updated);
    }
}
