package com.example.travelsocialservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Likes, counts and rating aggregates, computed on read from the source rows. */
@Repository
public class InteractionQueries {

    public record RatingSummary(long count, Double average) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** Idempotent: the primary key absorbs repeated and concurrent likes. */
    public void like(UUID postId, UUID userId, Instant at) {
        entityManager.createNativeQuery("""
                        INSERT INTO post_likes (post_id, user_id, created_at) VALUES (:postId, :userId, :at)
                        ON CONFLICT (post_id, user_id) DO NOTHING
                        """)
                .setParameter("postId", postId).setParameter("userId", userId).setParameter("at", at)
                .executeUpdate();
    }

    public void unlike(UUID postId, UUID userId) {
        entityManager.createNativeQuery("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
                .setParameter("postId", postId).setParameter("userId", userId)
                .executeUpdate();
    }

    public Map<UUID, Long> likeCounts(Collection<UUID> postIds) {
        return countsByPost("SELECT post_id, count(*) FROM post_likes WHERE post_id IN (:postIds) GROUP BY post_id", postIds);
    }

    public Map<UUID, Long> publishedCommentCounts(Collection<UUID> postIds) {
        return countsByPost("""
                SELECT post_id, count(*) FROM comments
                WHERE post_id IN (:postIds) AND status = 'PUBLISHED' GROUP BY post_id
                """, postIds);
    }

    @SuppressWarnings("unchecked")
    public Set<UUID> likedBy(UUID userId, Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        List<Object> rows = entityManager.createNativeQuery(
                        "SELECT post_id FROM post_likes WHERE user_id = :userId AND post_id IN (:postIds)")
                .setParameter("userId", userId).setParameter("postIds", postIds)
                .getResultList();
        Set<UUID> liked = new HashSet<>();
        rows.forEach(row -> liked.add((UUID) row));
        return liked;
    }

    public RatingSummary placeRating(UUID placeId) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT count(*), avg(rating) FROM posts
                        WHERE place_id = :placeId AND status = 'PUBLISHED' AND rating IS NOT NULL
                        """)
                .setParameter("placeId", placeId)
                .getSingleResult();
        long count = ((Number) row[0]).longValue();
        return new RatingSummary(count, count == 0 ? null : ((Number) row[1]).doubleValue());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> countsByPost(String sql, Collection<UUID> postIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (postIds.isEmpty()) {
            return counts;
        }
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("postIds", postIds).getResultList();
        rows.forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).longValue()));
        return counts;
    }
}
