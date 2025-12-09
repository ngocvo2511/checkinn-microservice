package com.example.hotelservice.MediaAsset.controller;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper;
import com.example.hotelservice.MediaAsset.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/medias")
@RequiredArgsConstructor
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

        var asset = mediaAssetService.uploadMultipart(
                targetId,
                targetType,
                isThumbnail,
                sortOrder,
                file
        );

        return ResponseEntity.ok(asset);
    }

    @GetMapping
    public ResponseEntity<?> getByTarget(
            @RequestParam UUID targetId,
            @RequestParam MediaTargetType targetType
    ) {
        return ResponseEntity.ok(mediaAssetService.getByTarget(targetId, targetType));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        mediaAssetService.delete(id);
        return ResponseEntity.ok("Deleted");
    }

    @PatchMapping("/{mediaId}/thumbnail")
    public ResponseEntity<?> updateThumbnail(@PathVariable UUID mediaId) {
        var updated = mediaAssetService.updateThumbnail(mediaId);
        return ResponseEntity.ok(updated);
    }
}
