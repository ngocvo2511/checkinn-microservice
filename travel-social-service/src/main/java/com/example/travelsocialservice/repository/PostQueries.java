package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.service.KeysetCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Newest-first keyset pages over posts. Row comparison keeps pages stable while new posts arrive. */
@Repository
public class PostQueries {

    /**
     * @param status      only posts in this status; null means every status except DELETED
     * @param bodyPattern an ILIKE pattern whose wildcards in user input are already escaped with '!'
     */
    public record Filter(ContentStatus status, UUID placeId, UUID authorId, String bodyPattern) {

        public static Filter published() {
            return new Filter(ContentStatus.PUBLISHED, null, null, null);
        }

        public Filter atPlace(UUID placeId) {
            return new Filter(status, placeId, authorId, bodyPattern);
        }

        public Filter matchingBody(String bodyPattern) {
            return new Filter(status, placeId, authorId, bodyPattern);
        }

        public static Filter ownedBy(UUID authorId) {
            return new Filter(null, null, authorId, null);
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Post> page(Filter filter, KeysetCursor after, int limit) {
        StringBuilder sql = new StringBuilder("SELECT p.* FROM posts p WHERE ");
        Map<String, Object> params = new HashMap<>();
        if (filter.status() != null) {
            sql.append("p.status = :status");
            params.put("status", filter.status().name());
        } else {
            sql.append("p.status <> 'DELETED'");
        }
        if (filter.placeId() != null) {
            sql.append(" AND p.place_id = :placeId");
            params.put("placeId", filter.placeId());
        }
        if (filter.authorId() != null) {
            sql.append(" AND p.author_id = :authorId");
            params.put("authorId", filter.authorId());
        }
        if (filter.bodyPattern() != null) {
            sql.append(" AND p.body ILIKE :bodyPattern ESCAPE '!'");
            params.put("bodyPattern", filter.bodyPattern());
        }
        if (after != null) {
            sql.append(" AND (p.created_at, p.id) < (:afterCreatedAt, :afterId)");
            params.put("afterCreatedAt", after.createdAt());
            params.put("afterId", after.id());
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit");
        params.put("limit", limit);

        Query query = entityManager.createNativeQuery(sql.toString(), Post.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }
}
