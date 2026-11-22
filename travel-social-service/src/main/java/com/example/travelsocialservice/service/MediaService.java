package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.MediaStorage;
import com.example.travelsocialservice.client.MediaStorage.MediaStorageException;
import com.example.travelsocialservice.client.MediaStorage.StoredObject;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InteractionDtos.MediaUploadView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final PostMediaRepository mediaRepository;
    private final MediaStorage storage;
    private final SocialProperties properties;
    private final SocialMetrics metrics;
    private final Clock clock;

    public MediaUploadView upload(Actor actor, byte[] data) {
        if (data == null || data.length == 0) {
            metrics.mediaUpload("rejected");
            throw ApiException.invalid("file", "A photo file is required");
        }
        if (data.length > properties.limits().maxMediaBytes()) {
            metrics.mediaUpload("rejected");
            throw ApiException.invalid("file", "The photo exceeds the size limit");
        }
        ImageType type = ImageType.detect(data);
        if (type == null) {
            metrics.mediaUpload("rejected");
            throw ApiException.invalid("file", "Only JPEG, PNG and WebP photos are accepted");
        }
        if (mediaRepository.countByUploaderIdAndStatus(actor.id(), MediaStatus.PENDING)
                >= properties.limits().maxPendingMediaPerUser()) {
            metrics.mediaUpload("too_many_pending");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_UPLOADS",
                    "Too many unattached photos. Attach or wait before uploading more.", null);
        }

        StoredObject stored;
        try {
            stored = storage.upload("photo." + type.extension(), type.mimeType(), data);
        } catch (MediaStorageException ex) {
            metrics.mediaUpload("storage_failure");
            log.error("[SOCIAL_MEDIA] Upload failed for uploader {}: {}", actor.id(), ex.getMessage());
            throw ApiException.unavailable("MEDIA_SERVICE_UNAVAILABLE", "Photos cannot be uploaded right now. Try again later.");
        }

        PostMedia media = new PostMedia();
        media.setId(UUID.randomUUID());
        media.setUploaderId(actor.id());
        media.setUrl(stored.url());
        media.setObjectName(stored.objectName());
        media.setMimeType(type.mimeType());
        media.setSizeBytes(data.length);
        media.setStatus(MediaStatus.PENDING);
        media.setCreatedAt(Paging.now(clock));
        media.setPendingSince(media.getCreatedAt());
        try {
            mediaRepository.save(media);
        } catch (RuntimeException ex) {
            deleteQuietly(stored.objectName());
            throw ex;
        }
        metrics.mediaUpload("stored");
        return new MediaUploadView(media.getId(), media.getUrl());
    }

    /** Returns false when storage could not be reached, so the caller keeps the row and retries later. */
    public boolean deleteStoredObject(PostMedia media) {
        try {
            storage.delete(media.getObjectName());
            return true;
        } catch (MediaStorageException ex) {
            log.warn("[SOCIAL_MEDIA] Storage delete failed for media {}: {}", media.getId(), ex.getMessage());
            return false;
        }
    }

    private void deleteQuietly(String objectName) {
        try {
            storage.delete(objectName);
        } catch (MediaStorageException ex) {
            log.warn("[SOCIAL_MEDIA] Could not remove an upload whose record failed to save: {}", ex.getMessage());
        }
    }
}
