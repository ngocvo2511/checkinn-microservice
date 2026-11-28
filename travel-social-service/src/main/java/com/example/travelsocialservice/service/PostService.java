package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.CompletedStays;
import com.example.travelsocialservice.client.CompletedStays.CompletedStay;
import com.example.travelsocialservice.client.CompletedStays.StayLookupUnavailableException;
import com.example.travelsocialservice.client.HotelClient;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.InvitationStatus;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.ModerationAction;
import com.example.travelsocialservice.domain.ModerationAudit;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.domain.TargetType;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.StayInvitationRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.PostDtos.PostRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostMediaRepository mediaRepository;
    private final ModerationAuditRepository auditRepository;
    private final PlaceClient placeClient;
    private final HotelClient hotelClient;
    private final CompletedStays completedStays;
    private final StayInvitationRepository invitationRepository;
    private final SocialMetrics metrics;
    private final SocialProperties properties;
    private final Clock clock;

    @Transactional
    public Post create(Actor actor, PostRequest request) {
        Content content = validate(request);
        requireKnownPlace(content.placeId());
        requirePublishedHotel(content.hotelId());
        if (content.stayBookingId() != null) {
            requireShareableStay(actor.id(), content.hotelId(), content.stayBookingId(), null);
        }

        Instant now = Paging.now(clock);
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setAuthorId(actor.id());
        post.setBody(content.body());
        post.setPlaceId(content.placeId());
        post.setRating(content.rating());
        post.setHotelId(content.hotelId());
        linkStay(post, content.stayBookingId());
        post.setStatus(ContentStatus.PUBLISHED);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        post.setStatusChangedAt(now);
        postRepository.save(post);
        claimStay(post, now);

        applyMedia(actor, post, content.mediaIds(), List.of());
        return post;
    }

    /** Editing never changes moderation status: a hidden post stays hidden. */
    @Transactional
    public Post update(Actor actor, UUID postId, PostRequest request) {
        Post post = requireEditable(actor, postId);
        Content content = validate(request);
        if (!Objects.equals(content.placeId(), post.getPlaceId())) {
            requireKnownPlace(content.placeId());
        }
        if (!Objects.equals(content.hotelId(), post.getHotelId())) {
            requirePublishedHotel(content.hotelId());
        }
        boolean stayChanged = !Objects.equals(content.stayBookingId(), post.getStayBookingId())
                || !Objects.equals(content.hotelId(), post.getHotelId());
        if (stayChanged && content.stayBookingId() != null) {
            requireShareableStay(post.getAuthorId(), content.hotelId(), content.stayBookingId(), post.getId());
        }

        Instant now = Paging.now(clock);
        post.setBody(content.body());
        post.setPlaceId(content.placeId());
        post.setRating(content.rating());
        post.setHotelId(content.hotelId());
        linkStay(post, content.stayBookingId());
        post.setUpdatedAt(now);
        if (stayChanged) {
            claimStay(post, now);
        }
        applyMedia(actor, post, content.mediaIds(), mediaRepository.findByPostIdOrderByDisplayOrderAsc(postId));
        return post;
    }

    @Transactional
    public void delete(Actor actor, UUID postId) {
        Post post = requireEditable(actor, postId);
        Instant now = Paging.now(clock);
        post.changeStatus(ContentStatus.DELETED, now);
        if (!actor.is(post.getAuthorId())) {
            auditRepository.save(new ModerationAudit(actor.id(), ModerationAction.DELETE, TargetType.POST, postId, null, now));
            log.info("[SOCIAL_MODERATION] action=DELETE target=POST:{} actor={}", postId, actor.id());
        }
    }

    /** Hidden and deleted posts do not exist for anyone but administrators. */
    @Transactional(readOnly = true)
    public Post requireVisible(Actor viewer, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(ApiException::notFound);
        if (!post.isPublished() && (viewer == null || !viewer.admin())) {
            throw ApiException.notFound();
        }
        return post;
    }

    /**
     * The author's own post in any status but DELETED, so a post hidden by moderation can still be
     * loaded for editing. Anyone else gets 404, exactly as for a post that does not exist.
     */
    @Transactional(readOnly = true)
    public Post requireOwn(Actor author, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(ApiException::notFound);
        if (post.getStatus() == ContentStatus.DELETED || !author.is(post.getAuthorId())) {
            throw ApiException.notFound();
        }
        return post;
    }

    @Transactional(readOnly = true)
    public Post requirePublished(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(ApiException::notFound);
        if (!post.isPublished()) {
            throw ApiException.notFound();
        }
        return post;
    }

    private Post requireEditable(Actor actor, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(ApiException::notFound);
        boolean owner = actor.is(post.getAuthorId());
        if (post.getStatus() == ContentStatus.DELETED) {
            throw ApiException.notFound();
        }
        if (owner || actor.admin()) {
            return post;
        }
        // A stranger must not learn that a hidden post exists.
        throw post.isPublished() ? ApiException.forbidden() : ApiException.notFound();
    }

    private record Content(String body, UUID placeId, Integer rating, List<UUID> mediaIds,
                           UUID hotelId, UUID stayBookingId) {
    }

    private Content validate(PostRequest request) {
        if (request == null) {
            throw ApiException.badRequest("A request body is required");
        }
        String body = request.body() == null ? "" : request.body().strip();
        if (body.isEmpty() || body.length() > properties.limits().postBodyMax()) {
            throw ApiException.invalid("body", "body must be 1-" + properties.limits().postBodyMax() + " characters");
        }
        Integer rating = request.rating();
        if (rating != null && (rating < 1 || rating > 5)) {
            throw ApiException.invalid("rating", "rating must be a whole number from 1 to 5");
        }
        if (rating != null && request.placeId() == null) {
            throw ApiException.invalid("rating", "A rating needs a place");
        }
        if (rating != null && request.hotelId() != null) {
            throw ApiException.invalid("rating", "A post that tags a hotel cannot have a rating");
        }
        if (request.stayBookingId() != null && request.hotelId() == null) {
            throw ApiException.invalid("stayBookingId", "A stay can only be linked together with its hotel");
        }
        List<UUID> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        if (mediaIds.stream().anyMatch(Objects::isNull) || new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw ApiException.invalid("mediaIds", "mediaIds must be distinct photo ids");
        }
        if (mediaIds.size() > properties.limits().maxMediaPerPost()) {
            throw ApiException.invalid("mediaIds", "A post can have at most " + properties.limits().maxMediaPerPost() + " photos");
        }
        return new Content(body, request.placeId(), rating, mediaIds, request.hotelId(), request.stayBookingId());
    }

    private void requireKnownPlace(UUID placeId) {
        if (placeId != null && placeClient.require(placeId).isEmpty()) {
            throw ApiException.invalid("placeId", "The selected place does not exist");
        }
    }

    private void requirePublishedHotel(UUID hotelId) {
        if (hotelId != null && hotelClient.require(hotelId).isEmpty()) {
            throw ApiException.invalid("hotelId", "The selected hotel is not available");
        }
    }

    /**
     * Every mismatch gets the same answer, so a caller cannot probe whether another user's booking exists.
     */
    private void requireShareableStay(UUID authorId, UUID hotelId, UUID stayBookingId, UUID editedPostId) {
        CompletedStay stay;
        try {
            stay = completedStays.find(stayBookingId).orElse(null);
        } catch (StayLookupUnavailableException ex) {
            metrics.verifiedPost("unavailable");
            log.warn("[SOCIAL_STAY] Completed-stay lookup unavailable: {}", ex.getMessage());
            throw ApiException.unavailable("BOOKING_SERVICE_UNAVAILABLE", "Your stay cannot be verified right now. Try again later.");
        }
        if (stay == null || !authorId.equals(stay.userId()) || !hotelId.equals(stay.hotelId())) {
            metrics.verifiedPost("rejected");
            throw ApiException.invalid("stayBookingId", "This stay cannot be linked to the post");
        }
        if (postRepository.isStaySharedElsewhere(stayBookingId, editedPostId)) {
            throw stayAlreadyShared();
        }
        metrics.verifiedPost("verified");
    }

    private static void linkStay(Post post, UUID stayBookingId) {
        post.setStayBookingId(stayBookingId);
        post.setVerifiedStay(stayBookingId != null);
    }

    /**
     * Flushes so that a concurrent post for the same stay hits the live-stay unique index here and becomes
     * a 409, then closes the author's open invitation for that stay.
     */
    private void claimStay(Post post, Instant now) {
        if (post.getStayBookingId() == null) {
            return;
        }
        try {
            postRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw stayAlreadyShared();
        }
        invitationRepository.findByBookingIdAndUserId(post.getStayBookingId(), post.getAuthorId())
                .filter(invitation -> invitation.getStatus() == InvitationStatus.OPEN)
                .ifPresent(invitation -> {
                    invitation.changeStatus(InvitationStatus.USED, now);
                    invitation.setPostId(post.getId());
                });
    }

    private static ApiException stayAlreadyShared() {
        return ApiException.conflict("STAY_ALREADY_SHARED", "This stay is already shared in another post");
    }

    /**
     * Sets the post's photos to exactly {@code desiredIds}, in that order. A photo can be added only if
     * the actor uploaded it and it is not attached anywhere; removed photos return to PENDING and are
     * collected by the cleanup job.
     */
    private void applyMedia(Actor actor, Post post, List<UUID> desiredIds, List<PostMedia> attached) {
        Map<UUID, PostMedia> attachedById = attached.stream().collect(Collectors.toMap(PostMedia::getId, Function.identity()));
        Map<UUID, PostMedia> candidates = mediaRepository.findAllById(desiredIds).stream()
                .collect(Collectors.toMap(PostMedia::getId, Function.identity()));

        for (int order = 0; order < desiredIds.size(); order++) {
            UUID mediaId = desiredIds.get(order);
            PostMedia media = candidates.get(mediaId);
            boolean alreadyOnThisPost = attachedById.containsKey(mediaId);
            boolean attachable = media != null && media.getStatus() == MediaStatus.PENDING && actor.is(media.getUploaderId());
            if (!alreadyOnThisPost && !attachable) {
                throw ApiException.invalid("mediaIds", "A photo is unknown, already used, or not uploaded by you");
            }
            media.attachTo(post.getId(), order);
        }
        Instant now = Paging.now(clock);
        attached.stream().filter(media -> !desiredIds.contains(media.getId())).forEach(media -> media.detach(now));
    }
}
