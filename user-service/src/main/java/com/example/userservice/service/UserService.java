package com.example.userservice.service;
import com.example.userservice.dto.PagedResponse;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.dto.UserProfileDto;
import com.example.userservice.dto.UpdateProfileDto;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.dto.UserDetailResponse;
import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import com.example.userservice.repository.UserProfileRepository;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final OtpService otpService;
    private final com.example.userservice.security.TokenRevocationService sessions;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User registerUser(RegisterRequest request, Role role) {
        logger.info("[REGISTER] User registration attempt");
        try {
            if (userRepository.existsByUsername(request.getUsername())) {
            logger.warn("[REGISTER] Registration failed - username already exists");
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
            }

            if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("[REGISTER] Registration failed - email already exists");
            throw new RuntimeException("Email đã tồn tại");
            }

            validatePassword(request.getPassword());

            logger.debug("[REGISTER] Creating user account");

            User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .isEmailVerified(false)
                .build();

            UserProfile profile = UserProfile.builder()
                .fullName(request.getFullName())
                .user(user)
                .build();

            user.setProfile(profile);

            User savedUser = userRepository.save(user); // cascade sẽ tự lưu profile
            MDC.put("userId", savedUser.getId().toString());

            logger.info("[REGISTER] User account created successfully - userId: {}", savedUser.getId());

            // Generate and send OTP
            logger.debug("[REGISTER] Generating and sending OTP");
            otpService.generateAndSendOtp(request.getEmail());

            logger.info("[REGISTER] Registration completed successfully - userId: {}", savedUser.getId());

            return savedUser;
        } finally {
            MDC.remove("userId");
        }
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
    }

    @Transactional
    public UserLoginResult login(String usernameOrEmail, String password) {
        logger.info("[LOGIN] Login attempt");
        try {
            User user = userRepository
                    .findIdentityForUpdate(usernameOrEmail)
                    .orElseThrow(() -> {
                        logger.warn("[LOGIN] User not found");
                        return new RuntimeException("Tài khoản hoặc mật khẩu không đúng");
                    });

            MDC.put("userId", user.getId().toString());
            logger.debug("[LOGIN] User found - userId: {}", user.getId());

            if (!user.isEmailVerified()) {
                logger.warn("[LOGIN] Email not verified - userId: {}", user.getId());
                throw new RuntimeException("Email chưa được xác thực. Vui lòng xác thực email trước");
            }
            if(!user.getIsActive()) {
                logger.warn("[LOGIN] Account locked - userId: {}", user.getId());
                throw new RuntimeException("Account is locked. Please contact support");
            }

            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("[LOGIN] Password mismatch - userId: {}", user.getId());
                throw new RuntimeException("Tài khoản hoặc mật khẩu không đúng");
            }

            logger.info("[LOGIN] Password verified successfully - userId: {}", user.getId());

            UserProfile profile = userProfileRepository
                    .findByUserId(user.getId())
                    .orElse(null); // profile có thể chưa tạo

            logger.info("[LOGIN] Login successful - userId: {}", user.getId());

            return new UserLoginResult(user, profile, sessions.currentUserGeneration(user.getId()));
        } finally {
            MDC.remove("userId");
        }
    }

    public UserProfileDto getUserProfile(UUID userId) {
        logger.info("[PROFILE_GET] Fetch user profile request received - userId: {}", userId);

        User user = getUserById(userId);
        logger.info("[PROFILE_GET] User loaded - userId: {}", userId);

        // If profile missing (older accounts), create an empty one to avoid 401
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        logger.info("[PROFILE_GET] Using profile - userId: {}", userId);

        return UserProfileDto.builder()
                .id(user.getId())
                .fullName(profile.getFullName() != null ? profile.getFullName() : "")
                .email(user.getEmail())
                .phone(profile.getPhone() != null ? profile.getPhone() : "")
                .gender(profile.getGender() != null ? profile.getGender() : "")
                .birthday(profile.getBirthDate() != null ? profile.getBirthDate().toString() : "")
                .country(profile.getCountry() != null ? profile.getCountry() : "")
                .address(profile.getAddress() != null ? profile.getAddress() : "")
                .build();
    }

    private UserProfile createEmptyProfile(User user) {
        logger.info("[PROFILE_CREATE_EMPTY] Profile missing, creating empty profile - userId: {}", user.getId());
        UserProfile newProfile = UserProfile.builder()
                .fullName(user.getUsername())
                .user(user)
                .build();
        user.setProfile(newProfile);
        return userProfileRepository.save(newProfile);
    }

    public UserProfileDto updateUserProfile(UUID userId, UpdateProfileDto dto) {
        logger.info("[PROFILE_UPDATE] Update profile request received - userId: {}", userId);
        User user = getUserById(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        if (dto.getFullName() != null) {
            profile.setFullName(dto.getFullName());
        }
        if (dto.getPhone() != null) {
            profile.setPhone(dto.getPhone());
        }
        if (dto.getGender() != null) {
            profile.setGender(dto.getGender());
        }
        if (dto.getBirthday() != null && !dto.getBirthday().isEmpty()) {
            try {
                profile.setBirthDate(java.time.LocalDate.parse(dto.getBirthday()));
            } catch (Exception e) {
                // ignore invalid date format
            }
        }
        if (dto.getCountry() != null) {
            profile.setCountry(dto.getCountry());
        }
        if (dto.getAddress() != null) {
            profile.setAddress(dto.getAddress());
        }

        userProfileRepository.save(profile);
        logger.info("[PROFILE_UPDATE] Update profile success - userId: {}", userId);

        return UserProfileDto.builder()
                .id(user.getId())
                .fullName(profile.getFullName())
                .email(user.getEmail())
                .phone(profile.getPhone())
                .gender(profile.getGender())
                .birthday(profile.getBirthDate() != null ? profile.getBirthDate().toString() : "")
                .country(profile.getCountry())
                .address(profile.getAddress())
                .build();
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        logger.info("[PASSWORD_CHANGE] Change password request received - userId: {}", userId);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }

        validatePassword(newPassword);

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("[PASSWORD_CHANGE] Change password success - userId: {}", userId);
    }

    @Transactional
    public void verifyUserEmail(String email) {
        logger.info("[EMAIL_VERIFY] Verify user email request received");
        if (userRepository.markEmailVerified(email)!=1) throw new RuntimeException("Không tìm thấy người dùng");
        // Do not merge a detached account here: concurrent reset must retain its new password.
        logger.info("[EMAIL_VERIFY] Verify user email success");
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        logger.info("[PASSWORD_RESET] Password reset requested");
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        validatePassword(newPassword);

        otpService.consumeVerifiedForReset(email);
        sessions.rotateUserSessions(user.getId());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("[PASSWORD_RESET] Password reset completed");
    }

    public class UserLoginResult {
        private User user;
        private UserProfile profile;
        private final String sessionGeneration;

        public UserLoginResult(User user, UserProfile profile, String sessionGeneration) {
            this.sessionGeneration=sessionGeneration;
            this.user = user;
            this.profile = profile;
        }

        public String getSessionGeneration() { return sessionGeneration; }

        public User getUser() {
            return user;
        }

        public UserProfile getProfile() {
            return profile;
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }

    public long getTotalUsersCount() {
        return userRepository.count();
    }

    /**
     * Admin: Get paged users list
     */
    public PagedResponse<UserResponse> getUsersPage(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<User> userPage = userRepository.findAll(pageable);

        List<UserResponse> content = userPage.getContent().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return PagedResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    /**
     * Admin: Get all users list
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Admin: Get user detail by ID
     */
    public UserDetailResponse getUserDetail(UUID userId) {
        User user = getUserById(userId);
        return toUserDetailResponse(user);
    }

    /**
     * Admin: Lock user account (set isActive = false)
     */
    @Transactional
    public void lockAccount(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Admin: Unlock user account (set isActive = true)
     */
    @Transactional
    public void unlockAccount(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        user.setIsActive(true);
        userRepository.save(user);
    }

    private UserResponse toUserResponse(User user) {
        UserProfile profile = user.getProfile();
        String fullName = profile != null && profile.getFullName() != null
                ? profile.getFullName()
                : user.getUsername();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(fullName)
                .role(user.getRole().name())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserDetailResponse toUserDetailResponse(User user) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createEmptyProfile(user));

        return UserDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(profile.getFullName() != null ? profile.getFullName() : "")
                .phone(profile.getPhone() != null ? profile.getPhone() : "")
                .role(user.getRole().name())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}


