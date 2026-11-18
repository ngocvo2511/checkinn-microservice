package com.example.travelsocialservice.web;

import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.service.ModerationService;
import com.example.travelsocialservice.web.dto.InteractionDtos.CloseReportsRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.ModerationRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.ModerationResult;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportGroupView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every route here is restricted to role ADMIN by SecurityConfig. */
@RestController
@RequestMapping("/api/social/admin")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ModerationService moderationService;

    @GetMapping("/reports")
    public List<ReportGroupView> openReports(@RequestParam(required = false) Integer limit) {
        return moderationService.openReportQueue(limit);
    }

    @PostMapping("/reports/close")
    public Map<String, Integer> closeReports(@RequestBody(required = false) CloseReportsRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return Map.of("closed", moderationService.closeReports(Actor.require(jwt), request));
    }

    @PostMapping("/posts/{postId}/hide")
    public ModerationResult hidePost(@PathVariable UUID postId, @RequestBody(required = false) ModerationRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return moderationService.hidePost(Actor.require(jwt), postId, reason(request));
    }

    @PostMapping("/posts/{postId}/restore")
    public ModerationResult restorePost(@PathVariable UUID postId, @RequestBody(required = false) ModerationRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return moderationService.restorePost(Actor.require(jwt), postId, reason(request));
    }

    @PostMapping("/comments/{commentId}/hide")
    public ModerationResult hideComment(@PathVariable UUID commentId, @RequestBody(required = false) ModerationRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return moderationService.hideComment(Actor.require(jwt), commentId, reason(request));
    }

    @PostMapping("/comments/{commentId}/restore")
    public ModerationResult restoreComment(@PathVariable UUID commentId, @RequestBody(required = false) ModerationRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return moderationService.restoreComment(Actor.require(jwt), commentId, reason(request));
    }

    private static String reason(ModerationRequest request) {
        return request == null ? null : request.reason();
    }
}
