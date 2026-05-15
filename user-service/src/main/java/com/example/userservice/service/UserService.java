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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final OtpService otpService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User registerUser(RegisterRequest request, Role role) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

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

        // Generate and send OTP
        otpService.generateAndSendOtp(request.getEmail());

        return savedUser;
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserLoginResult login(String usernameOrEmail, String password) {

        User user = userRepository
                .findByEmailOrUsername(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email not verified. Please verify your email first");
        }
        if(!user.getIsActive()) {
            throw new RuntimeException("Account is locked. Please contact support");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        UserProfile profile = userProfileRepository
                .findByUserId(user.getId())
                .orElse(null); // profile có thể chưa tạo

        return new UserLoginResult(user, profile);
    }

    public UserProfileDto getUserProfile(UUID userId) {
        System.out.println("[UserService] getUserProfile - userId: " + userId);

        User user = getUserById(userId);
        System.out.println("[UserService] Found user: " + user.getUsername());

        // If profile missing (older accounts), create an empty one to avoid 401
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        System.out.println("[UserService] Using profile - fullName: " + profile.getFullName());

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
        System.out.println("[UserService] Profile missing, creating empty profile for userId=" + user.getId());
        UserProfile newProfile = UserProfile.builder()
                .fullName(user.getUsername())
                .user(user)
                .build();
        user.setProfile(newProfile);
        return userProfileRepository.save(newProfile);
    }

    public UserProfileDto updateUserProfile(UUID userId, UpdateProfileDto dto) {
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

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }

        // Validate new password
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void verifyUserEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public class UserLoginResult {
        private User user;
        private UserProfile profile;

        public UserLoginResult(User user, UserProfile profile) {
            this.user = user;
            this.profile = profile;
        }

        public User getUser() {
            return user;
        }

        public UserProfile getProfile() {
            return profile;
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
        User user = getUserById(userId);
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Admin: Unlock user account (set isActive = true)
     */
    @Transactional
    public void unlockAccount(UUID userId) {
        User user = getUserById(userId);
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


