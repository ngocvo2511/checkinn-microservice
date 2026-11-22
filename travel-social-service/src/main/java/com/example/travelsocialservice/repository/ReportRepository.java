package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.Report;
import com.example.travelsocialservice.domain.ReportStatus;
import com.example.travelsocialservice.domain.TargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /** A repeat report by the same user on the same target is a no-op, so concurrent duplicates cannot fail. */
    @Modifying
    @Query(value = """
            INSERT INTO reports (id, reporter_id, target_type, target_id, reason, note, status, created_at)
            VALUES (:id, :reporterId, :targetType, :targetId, :reason, CAST(:note AS text), 'OPEN', :createdAt)
            ON CONFLICT (reporter_id, target_type, target_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("reporterId") UUID reporterId,
                       @Param("targetType") String targetType, @Param("targetId") UUID targetId,
                       @Param("reason") String reason, @Param("note") String note,
                       @Param("createdAt") Instant createdAt);

    List<Report> findByStatusOrderByCreatedAtAscIdAsc(ReportStatus status, Pageable pageable);

    List<Report> findByTargetTypeAndTargetIdAndStatus(TargetType targetType, UUID targetId, ReportStatus status);

    @Modifying
    @Query("delete from Report r where r.status <> com.example.travelsocialservice.domain.ReportStatus.OPEN and r.closedAt < :cutoff")
    int deleteClosedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Removes reports, open or closed, whose post or comment has been purged. Without this, open reports
     * on purged content would stay in the moderation queue forever with nothing left to act on.
     */
    @Modifying
    @Query(value = """
            DELETE FROM reports r
            WHERE (r.target_type = 'POST' AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.id = r.target_id))
               OR (r.target_type = 'COMMENT' AND NOT EXISTS (SELECT 1 FROM comments c WHERE c.id = r.target_id))
            """, nativeQuery = true)
    int deleteOrphaned();
}
