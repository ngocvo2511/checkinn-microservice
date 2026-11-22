package com.example.travelsocialservice.service;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.CommentRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Removes photos nobody attached and purges soft-deleted content after the retention window.
 * A stored object is always deleted before its row, so a storage failure leaves the row for the next run
 * instead of orphaning the file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final PostRepository postRepository;
    private final PostMediaRepository mediaRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final MediaService mediaService;
    private final SocialProperties properties;
    private final SocialMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${social.cleanup.delay-ms}", initialDelayString = "${social.cleanup.initial-delay-ms}")
    public void run() {
        try {
            Instant now = clock.instant();
            int media = removeUnattachedMedia(now.minus(properties.retention().pendingMedia()));
            Instant retentionCutoff = now.minus(properties.retention().deletedContent());
            int posts = purgeDeletedPosts(retentionCutoff);
            int comments = count(transactions.execute(tx -> commentRepository.deleteByStatusAndStatusChangedAtBefore(ContentStatus.DELETED, retentionCutoff)));
            int reports = count(transactions.execute(tx -> reportRepository.deleteClosedBefore(retentionCutoff)));
            // Runs after the purges above, so reports on content purged in this run go too.
            int orphanedReports = count(transactions.execute(tx -> reportRepository.deleteOrphaned()));
            metrics.cleanupRemoved("unattached_media", media);
            metrics.cleanupRemoved("deleted_posts", posts);
            metrics.cleanupRemoved("deleted_comments", comments);
            metrics.cleanupRemoved("closed_reports", reports);
            metrics.cleanupRemoved("orphaned_reports", orphanedReports);
            if (media + posts + comments + reports + orphanedReports > 0) {
                log.info("[SOCIAL_CLEANUP] unattachedMedia={} purgedPosts={} purgedComments={} purgedReports={} orphanedReports={}",
                        media, posts, comments, reports, orphanedReports);
            }
        } catch (RuntimeException ex) {
            log.error("[SOCIAL_CLEANUP] Cleanup run failed: {}", ex.getMessage(), ex);
        }
    }

    private static int count(Integer rows) {
        return rows == null ? 0 : rows;
    }

    int removeUnattachedMedia(Instant cutoff) {
        List<PostMedia> stale = mediaRepository.findByStatusAndPendingSinceBeforeOrderByPendingSinceAsc(
                MediaStatus.PENDING, cutoff, PageRequest.of(0, properties.cleanup().batchSize()));
        int removed = 0;
        for (PostMedia media : stale) {
            if (mediaService.deleteStoredObject(media)) {
                mediaRepository.deleteById(media.getId());
                removed++;
            }
        }
        return removed;
    }

    int purgeDeletedPosts(Instant cutoff) {
        List<Post> expired = postRepository.findByStatusAndStatusChangedAtBeforeOrderByStatusChangedAtAsc(
                ContentStatus.DELETED, cutoff, PageRequest.of(0, properties.cleanup().batchSize()));
        int purged = 0;
        for (Post post : expired) {
            boolean storageCleared = mediaRepository.findByPostIdOrderByDisplayOrderAsc(post.getId()).stream()
                    .allMatch(mediaService::deleteStoredObject);
            if (storageCleared) {
                postRepository.deleteById(post.getId());
                purged++;
            }
        }
        return purged;
    }
}
