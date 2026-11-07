package com.example.hotelservice.Amenity.controller;

import com.example.hotelservice.Amenity.service.AmenityCatalogService;
import com.example.hotelservice.Amenity.service.AmenityCatalogService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class AmenityCatalogController {
    private final AmenityCatalogService service;
    private Actor actor(Jwt jwt) {
        if(jwt==null) throw new SecurityException("Yêu cầu đăng nhập.");
        try { return new Actor(UUID.fromString(jwt.getSubject()),jwt.getClaimAsString("role")); }
        catch(IllegalArgumentException e) { throw new SecurityException("Danh tính không hợp lệ."); }
    }
    @GetMapping("/amenity-catalog") public List<CatalogEntry> catalog() { return service.catalog(false,null); }
    @GetMapping("/admin/amenity-catalog") public List<CatalogEntry> all(@AuthenticationPrincipal Jwt jwt) { return service.catalog(true,actor(jwt)); }
    @PostMapping("/admin/amenity-catalog") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CatalogEntry create(@Valid @RequestBody CatalogInput input,@AuthenticationPrincipal Jwt jwt) { return service.save(input,true,actor(jwt)); }
    @PutMapping("/admin/amenity-catalog/{code}") public CatalogEntry update(@PathVariable String code,@Valid @RequestBody CatalogInput input,@AuthenticationPrincipal Jwt jwt) {
        if(!code.equals(input.code())) throw new IllegalArgumentException("Không được thay đổi mã tiện nghi.");
        return service.save(input,false,actor(jwt));
    }
    @GetMapping("/hotels/{hotelId}/amenity-mappings") public List<Map<String,Object>> mappings(@PathVariable UUID hotelId,@AuthenticationPrincipal Jwt jwt) { return service.mappings(hotelId,actor(jwt)); }
    @PostMapping("/hotels/{hotelId}/amenity-mappings/{amenityId}/requests") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Object> propose(@PathVariable UUID hotelId,@PathVariable UUID amenityId,@Valid @RequestBody Proposal input,@AuthenticationPrincipal Jwt jwt) { return service.propose(hotelId,amenityId,input,actor(jwt)); }
    @GetMapping("/admin/amenity-mappings") public List<Map<String,Object>> pending(@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal Jwt jwt) { return service.pending(actor(jwt),page); }
    @GetMapping("/admin/amenity-mappings/history") public List<Map<String,Object>> history(@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal Jwt jwt) { return service.history(actor(jwt),page); }
    @PostMapping("/admin/amenity-mappings/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody Review input,@AuthenticationPrincipal Jwt jwt) { return service.review(id,input,actor(jwt)); }
}
