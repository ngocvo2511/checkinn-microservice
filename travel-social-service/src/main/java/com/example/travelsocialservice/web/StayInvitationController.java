package com.example.travelsocialservice.web;

import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.service.StayInvitationService;
import com.example.travelsocialservice.web.dto.InvitationDtos.InvitationList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/social/me/stay-invitations")
@RequiredArgsConstructor
public class StayInvitationController {

    private final StayInvitationService invitations;

    @GetMapping
    public InvitationList open(@AuthenticationPrincipal Jwt jwt) {
        return new InvitationList(invitations.openFor(Actor.require(jwt)));
    }

    @PostMapping("/{invitationId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID invitationId, @AuthenticationPrincipal Jwt jwt) {
        invitations.dismiss(Actor.require(jwt), invitationId);
        return ResponseEntity.noContent().build();
    }
}
