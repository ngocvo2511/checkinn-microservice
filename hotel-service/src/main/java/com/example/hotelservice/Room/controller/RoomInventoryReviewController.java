package com.example.hotelservice.Room.controller;

import com.example.hotelservice.Room.service.RoomInventoryReviewService;
import com.example.hotelservice.Room.service.RoomInventoryReviewService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class RoomInventoryReviewController {
    private final RoomInventoryReviewService service;
    private Actor actor(Jwt jwt) {
        if(jwt==null)throw new SecurityException("Yêu cầu đăng nhập.");
        try{return new Actor(UUID.fromString(jwt.getSubject()),jwt.getClaimAsString("role"));}
        catch(IllegalArgumentException e){throw new SecurityException("Danh tính không hợp lệ.");}
    }
    @GetMapping("/api/v1/{roomId}/inventory-review")
    public State state(@PathVariable UUID roomId,@AuthenticationPrincipal Jwt jwt){return service.state(roomId,actor(jwt));}
    @PostMapping("/api/v1/{roomId}/inventory-review")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Object> propose(@PathVariable UUID roomId,@Valid @RequestBody Proposal input,@AuthenticationPrincipal Jwt jwt){return service.propose(roomId,input,actor(jwt));}
    @GetMapping("/admin/inventory-reviews")
    public List<Map<String,Object>> pending(@RequestParam(defaultValue="0")int page,@AuthenticationPrincipal Jwt jwt){return service.queue(actor(jwt),page,false);}
    @GetMapping("/admin/inventory-reviews/history")
    public List<Map<String,Object>> history(@RequestParam(defaultValue="0")int page,@AuthenticationPrincipal Jwt jwt){return service.queue(actor(jwt),page,true);}
    @PostMapping("/admin/inventory-reviews/{id}/review")
    public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody Review input,@AuthenticationPrincipal Jwt jwt){return service.review(id,input,actor(jwt));}
}
