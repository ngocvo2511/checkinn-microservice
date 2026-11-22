package com.example.travelsocialservice.service;

import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.PostDtos.LikeView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final PostService postService;
    private final InteractionQueries interactions;
    private final Clock clock;

    @Transactional
    public LikeView like(Actor actor, UUID postId) {
        postService.requirePublished(postId);
        interactions.like(postId, actor.id(), Paging.now(clock));
        return new LikeView(count(postId), true);
    }

    @Transactional
    public LikeView unlike(Actor actor, UUID postId) {
        postService.requirePublished(postId);
        interactions.unlike(postId, actor.id());
        return new LikeView(count(postId), false);
    }

    private long count(UUID postId) {
        return interactions.likeCounts(List.of(postId)).getOrDefault(postId, 0L);
    }
}
