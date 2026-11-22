package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.Comment;
import com.example.travelsocialservice.domain.ContentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("""
            select c from Comment c
            where c.postId = :postId and c.status = com.example.travelsocialservice.domain.ContentStatus.PUBLISHED
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findPublishedByPost(@Param("postId") UUID postId, Pageable pageable);

    @Query("""
            select c from Comment c
            where c.postId = :postId and c.status = com.example.travelsocialservice.domain.ContentStatus.PUBLISHED
              and (c.createdAt > :afterCreatedAt or (c.createdAt = :afterCreatedAt and c.id > :afterId))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findPublishedByPostAfter(@Param("postId") UUID postId,
                                           @Param("afterCreatedAt") Instant afterCreatedAt,
                                           @Param("afterId") UUID afterId,
                                           Pageable pageable);

    @Modifying
    @Query("delete from Comment c where c.status = :status and c.statusChangedAt < :cutoff")
    int deleteByStatusAndStatusChangedAtBefore(@Param("status") ContentStatus status, @Param("cutoff") Instant cutoff);
}
