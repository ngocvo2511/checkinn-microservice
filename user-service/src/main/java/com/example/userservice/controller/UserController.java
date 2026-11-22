package com.example.userservice.controller;

import com.example.userservice.dto.PagedResponse;
import com.example.userservice.dto.UserProfileDto;
import com.example.userservice.dto.UpdateProfileDto;
import com.example.userservice.dto.ChangePasswordRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.dto.UserDetailResponse;
import com.example.userservice.service.UserService;
import com.example.userservice.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(@RequestHeader("Authorization") String authHeader) {
        log.info("[USER_PROFILE_GET] GET /profile request received");
        UUID userId = extractUserIdFromToken(authHeader);
        log.info("[USER_PROFILE_GET] Extracted userId: {}", userId);
        UserProfileDto profile = userService.getUserProfile(userId);
        log.info("[USER_PROFILE_GET] Retrieved profile for userId: {}", userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateProfileDto dto) {
        UUID userId = extractUserIdFromToken(authHeader);
        return ResponseEntity.ok(userService.updateUserProfile(userId, dto));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest request) {
        UUID userId = extractUserIdFromToken(authHeader);
        userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getTotalUsersCount() {
        long count = userService.getTotalUsersCount();
        return ResponseEntity.ok(count);
    }

    /**
     * Admin: Get paged users list
     */
    @GetMapping("/admin/users")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            UUID adminId = extractUserIdFromToken(authHeader);
            String role = extractRoleFromToken(authHeader);
            
            if (!"ADMIN".equalsIgnoreCase(role)) {
                return fail(HttpStatus.FORBIDDEN, "Access denied", "Only admins can view users list");
            }

            PagedResponse<UserResponse> users = userService.getUsersPage(page, size);
            return ResponseEntity.ok(users);
        } catch (com.example.userservice.exception.UserBusinessException e) {
            // Only deliberate business rejections are 401 with their message; outages/bugs reach the advice (503/500).
            return fail(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
        }
    }

    /**
     * Admin: Get user detail by ID
     */
    @GetMapping("/admin/users/{userId}")
    public ResponseEntity<?> getUserDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        try {
            UUID adminId = extractUserIdFromToken(authHeader);
            String role = extractRoleFromToken(authHeader);
            
            if (!"ADMIN".equalsIgnoreCase(role)) {
                return fail(HttpStatus.FORBIDDEN, "Access denied", "Only admins can view user details");
            }

            UserDetailResponse userDetail = userService.getUserDetail(userId);
            return ResponseEntity.ok(userDetail);
        } catch (com.example.userservice.exception.UserBusinessException e) {
            return fail(HttpStatus.NOT_FOUND, "Not found", "User not found");
        }
    }

    /**
     * Admin: Lock user account
     */
    @PostMapping("/admin/users/{userId}/lock")
    public ResponseEntity<?> lockAccount(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        try {
            UUID adminId = extractUserIdFromToken(authHeader);
            String role = extractRoleFromToken(authHeader);
            
            if (!"ADMIN".equalsIgnoreCase(role)) {
                return fail(HttpStatus.FORBIDDEN, "Access denied", "Only admins can lock accounts");
            }

            userService.lockAccount(userId);
            return ResponseEntity.ok(new SuccessResponse("Tài khoản đã được khóa thành công"));
        } catch (com.example.userservice.exception.UserBusinessException e) {
            log.warn("[USER_ACCOUNT_LOCK_REJECTED] lockAccount rejected");
            return fail(HttpStatus.BAD_REQUEST, "Error", e.getMessage());
        }
    }

    /**
     * Admin: Unlock user account
     */
    @PostMapping("/admin/users/{userId}/unlock")
    public ResponseEntity<?> unlockAccount(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        try {
            UUID adminId = extractUserIdFromToken(authHeader);
            String role = extractRoleFromToken(authHeader);
            
            if (!"ADMIN".equalsIgnoreCase(role)) {
                return fail(HttpStatus.FORBIDDEN, "Access denied", "Only admins can unlock accounts");
            }

            userService.unlockAccount(userId);
            return ResponseEntity.ok(new SuccessResponse("Tài khoản đã được mở khóa thành công"));
        } catch (com.example.userservice.exception.UserBusinessException e) {
            log.warn("[USER_ACCOUNT_UNLOCK_REJECTED] unlockAccount rejected");
            return fail(HttpStatus.BAD_REQUEST, "Error", e.getMessage());
        }
    }

    private UUID extractUserIdFromToken(String authHeader) {
        log.debug("[USER_TOKEN_EXTRACT] Extracting userId from authorization header");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[USER_TOKEN_INVALID] Invalid authorization header format");
            throw new com.example.userservice.exception.UserBusinessException("Header xác thực không hợp lệ");
        }

        String token = authHeader.substring(7);
        log.debug("[USER_TOKEN_EXTRACT] Token extracted successfully");
        try {
            UUID userId = jwtService.extractUserId(token);
            log.debug("[USER_TOKEN_EXTRACT] userId extracted successfully: {}", userId);
            return userId;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã đăng xuất", e);
        }
    }

    private String extractRoleFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new com.example.userservice.exception.UserBusinessException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        try {
            return jwtService.extractRole(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is invalid or revoked", e);
        }
    }

    /** Controller-level rejections use the standard envelope; the original error label and message are kept. */
    private static ResponseEntity<java.util.Map<String, Object>> fail(HttpStatus status, String error, String message) {
        var body = com.example.userservice.api.ApiErrorSupport.body(status.value(), null, message);
        body.put("error", error);
        return com.example.userservice.api.ApiErrorSupport.withHeader(status.value(), body);
    }

    // Helper response DTOs

    public static class SuccessResponse {
        public String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}
