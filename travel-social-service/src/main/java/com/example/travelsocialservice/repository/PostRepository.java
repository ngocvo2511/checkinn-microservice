package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findByStatusAndStatusChangedAtBeforeOrderByStatusChangedAtAsc(ContentStatus status, Instant cutoff, Pageable pageable);
}
