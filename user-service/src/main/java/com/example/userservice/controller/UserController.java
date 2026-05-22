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
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Access denied", "Only admins can view users list"));
            }

            PagedResponse<UserResponse> users = userService.getUsersPage(page, size);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("[USER_ADMIN_LIST_ERROR] Error in getAllUsers: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Unauthorized", e.getMessage()));
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Access denied", "Only admins can view user details"));
            }

            UserDetailResponse userDetail = userService.getUserDetail(userId);
            return ResponseEntity.ok(userDetail);
        } catch (Exception e) {
            log.error("[USER_ADMIN_DETAIL_ERROR] Error in getUserDetail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Not found", "User not found"));
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Access denied", "Only admins can lock accounts"));
            }

            userService.lockAccount(userId);
            return ResponseEntity.ok(new SuccessResponse("Tài khoản đã được khóa thành công"));
        } catch (Exception e) {
            log.error("[USER_ACCOUNT_LOCK_ERROR] Error in lockAccount: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Error", e.getMessage()));
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Access denied", "Only admins can unlock accounts"));
            }

            userService.unlockAccount(userId);
            return ResponseEntity.ok(new SuccessResponse("Tài khoản đã được mở khóa thành công"));
        } catch (Exception e) {
            log.error("[USER_ACCOUNT_UNLOCK_ERROR] Error in unlockAccount: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Error", e.getMessage()));
        }
    }

    private UUID extractUserIdFromToken(String authHeader) {
        log.debug("[USER_TOKEN_EXTRACT] Extracting userId from authorization header");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[USER_TOKEN_INVALID] Invalid authorization header format");
            throw new RuntimeException("Header xác thực không hợp lệ");
        }

        String token = authHeader.substring(7);
        log.debug("[USER_TOKEN_EXTRACT] Token extracted successfully");
        UUID userId = jwtService.extractUserId(token);
        log.debug("[USER_TOKEN_EXTRACT] userId extracted successfully: {}", userId);
        return userId;
    }

    private String extractRoleFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        return jwtService.extractRole(token);
    }

    // Helper response DTOs
    public static class ErrorResponse {
        public String error;
        public String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class SuccessResponse {
        public String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}
