package com.example.hotelservice.MediaAsset.controller;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper;
import com.example.hotelservice.MediaAsset.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaAssetController {

    private final MediaService mediaAssetService;
    private final MediaAssetMapper mediaAssetMapper;

    // -------------------------------------------------------
    // 1. Upload file cho Hotel hoặc RoomType
    // -------------------------------------------------------
    @PostMapping
    public ResponseEntity<MediaAssetResponse> createMedia(
            @Valid @RequestBody MediaUploadRequest request
    ) {
        var asset = mediaAssetService.uploadMedia(request);
        return ResponseEntity.ok(mediaAssetMapper.toMediaAssetResponse(asset));
    }

    // 2. Get media by target
    // -------------------------------------------------------
    @GetMapping
    public ResponseEntity<?> getByTarget(
            @RequestParam UUID targetId,
            @RequestParam MediaTargetType targetType
    ) {
        var list = mediaAssetService.getByTarget(targetId, targetType)
                .stream()
                .map(mediaAssetMapper::toMediaAssetResponse)
                .toList();

        return ResponseEntity.ok(list);
    }

    // -------------------------------------------------------
    // 3. Delete media
    // -------------------------------------------------------
    @DeleteMapping("/{mediaId}")
    public ResponseEntity<?> deleteMedia(@PathVariable UUID mediaId) {
        mediaAssetService.deleteMedia(mediaId);
        return ResponseEntity.ok("Deleted");
    }
}
