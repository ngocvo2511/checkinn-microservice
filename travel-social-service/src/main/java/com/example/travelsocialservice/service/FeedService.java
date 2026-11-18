package com.example.travelsocialservice.service;

import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.repository.InteractionQueries.RatingSummary;
import com.example.travelsocialservice.repository.PostQueries;
import com.example.travelsocialservice.repository.PostQueries.Filter;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.PageResponse;
import com.example.travelsocialservice.web.dto.PostDtos.PostView;
import com.example.travelsocialservice.web.dto.PostDtos.RatingView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedService {

    static final int MAX_QUERY_LENGTH = 100;

    private final PostQueries postQueries;
    private final InteractionQueries interactions;
    private final PostAssembler assembler;

    @Transactional(readOnly = true)
    public PageResponse<PostView> recent(UUID placeId, String cursor, Integer limit, Actor viewer) {
        Filter filter = placeId == null ? Filter.published() : Filter.published().atPlace(placeId);
        return page(filter, cursor, limit, viewer);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostView> search(String query, String cursor, Integer limit, Actor viewer) {
        String term = query == null ? "" : query.strip();
        if (term.isEmpty() || term.length() > MAX_QUERY_LENGTH) {
            throw ApiException.invalid("q", "q must be 1-" + MAX_QUERY_LENGTH + " characters");
        }
        return page(Filter.published().matchingBody(Paging.likePattern(term)), cursor, limit, viewer);
    }

    /** The author's own posts, including hidden ones, so they can see and edit moderated content. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> ownedBy(Actor author, String cursor, Integer limit) {
        return page(Filter.ownedBy(author.id()), cursor, limit, author);
    }

    @Transactional(readOnly = true)
    public RatingView placeRating(UUID placeId) {
        RatingSummary summary = interactions.placeRating(placeId);
        return new RatingView(placeId, summary.count(), summary.average());
    }

    private PageResponse<PostView> page(Filter filter, String cursor, Integer limit, Actor viewer) {
        int pageSize = Paging.limit(limit);
        List<Post> rows = postQueries.page(filter, KeysetCursor.decode(cursor), pageSize + 1);
        return Paging.page(rows, pageSize, posts -> assembler.toViews(posts, viewer),
                post -> new KeysetCursor(post.getCreatedAt(), post.getId()));
    }
}
