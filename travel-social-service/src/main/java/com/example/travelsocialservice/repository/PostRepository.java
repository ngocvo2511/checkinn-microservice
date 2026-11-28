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

    @org.springframework.data.jpa.repository.Query("""
            select count(p) > 0 from Post p
            where p.stayBookingId = :stayBookingId
              and p.status <> com.example.travelsocialservice.domain.ContentStatus.DELETED
              and (:editedPostId is null or p.id <> :editedPostId)
            """)
    boolean isStaySharedElsewhere(@org.springframework.data.repository.query.Param("stayBookingId") UUID stayBookingId,
                                  @org.springframework.data.repository.query.Param("editedPostId") UUID editedPostId);
}
