package com.example.hotelservice.Hotel.controller;
import com.example.hotelservice.Hotel.enums.AccommodationType;
import com.example.hotelservice.Hotel.entity.AccommodationTypeChange;
import com.example.hotelservice.Hotel.service.AccommodationTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController @RequestMapping("/hotels") @RequiredArgsConstructor
public class AccommodationTypeController {
    private final AccommodationTypeService types;
    public record Option(String code,String label) {}
    public record Proposal(AccommodationType accommodationType) {}
    public record Decision(String reason) {}
    private UUID actor(Jwt jwt) { if (jwt==null) throw new AccessDeniedException("Authentication required"); return UUID.fromString(jwt.getSubject()); }
    private boolean admin(Jwt jwt) { return jwt!=null && "ADMIN".equals(jwt.getClaimAsString("role")); }
    @GetMapping("/accommodation-types")
    public List<Option> catalog() { return Arrays.stream(AccommodationType.values()).map(t -> new Option(t.name(),t.getLabel())).toList(); }
    @GetMapping("/{hotelId}/accommodation-type-changes")
    public ResponseEntity<List<AccommodationTypeChange>> history(@PathVariable UUID hotelId,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(types.history(hotelId,actor(jwt),admin(jwt)));
    }
    @PostMapping("/{hotelId}/accommodation-type-changes")
    public ResponseEntity<AccommodationTypeChange> propose(@PathVariable UUID hotelId,@RequestBody Proposal proposal,@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(types.propose(hotelId,proposal.accommodationType(),actor(jwt)));
    }
    @GetMapping("/accommodation-type-changes/pending")
    public ResponseEntity<List<AccommodationTypeChange>> pending(@AuthenticationPrincipal Jwt jwt) {
        actor(jwt); return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(types.pending(admin(jwt)));
    }
    @PostMapping("/accommodation-type-changes/{id}/approve")
    public AccommodationTypeChange approve(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return types.review(id,true,null,actor(jwt),admin(jwt)); }
    @PostMapping("/accommodation-type-changes/{id}/reject")
    public AccommodationTypeChange reject(@PathVariable UUID id,@RequestBody Decision decision,@AuthenticationPrincipal Jwt jwt) { return types.review(id,false,decision.reason(),actor(jwt),admin(jwt)); }
}
