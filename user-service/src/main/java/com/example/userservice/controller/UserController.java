package com.example.userservice.controller;

import com.example.userservice.dto.UserProfileDto;
import com.example.userservice.dto.UpdateProfileDto;
import com.example.userservice.service.UserService;
import com.example.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(@RequestHeader("Authorization") String authHeader) {
        System.out.println("[UserController] GET /profile - Auth header: " + authHeader);
        Long userId = extractUserIdFromToken(authHeader);
        System.out.println("[UserController] Extracted userId: " + userId);
        UserProfileDto profile = userService.getUserProfile(userId);
        System.out.println("[UserController] Retrieved profile: " + profile);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateProfileDto dto) {
        Long userId = extractUserIdFromToken(authHeader);
        return ResponseEntity.ok(userService.updateUserProfile(userId, dto));
    }

    private Long extractUserIdFromToken(String authHeader) {
        System.out.println("[UserController] extractUserIdFromToken - authHeader: " + authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("[UserController] Invalid auth header format");
            throw new RuntimeException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        System.out.println("[UserController] Extracted token: " + token);
        Long userId = jwtService.extractUserId(token);
        System.out.println("[UserController] Extracted userId: " + userId);
        return userId;
    }
}
