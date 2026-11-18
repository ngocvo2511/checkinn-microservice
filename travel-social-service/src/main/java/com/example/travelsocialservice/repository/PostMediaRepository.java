package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.PostMedia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    List<PostMedia> findByPostIdOrderByDisplayOrderAsc(UUID postId);

    List<PostMedia> findByPostIdInOrderByPostIdAscDisplayOrderAsc(Collection<UUID> postIds);

    long countByUploaderIdAndStatus(UUID uploaderId, MediaStatus status);

    List<PostMedia> findByStatusAndPendingSinceBeforeOrderByPendingSinceAsc(MediaStatus status, Instant cutoff, Pageable pageable);
}
