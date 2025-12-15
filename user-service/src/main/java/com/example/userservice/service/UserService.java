package com.example.userservice.service;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.dto.UserProfileDto;
import com.example.userservice.dto.UpdateProfileDto;
import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import com.example.userservice.repository.UserProfileRepository;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

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
                .build();

        UserProfile profile = UserProfile.builder()
                .fullName(request.getFullName())
                .user(user)
                .build();

        user.setProfile(profile);

        return userRepository.save(user); // cascade sẽ tự lưu profile
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserLoginResult login(String usernameOrEmail, String password) {

        User user = userRepository
                .findByEmailOrUsername(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

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

    public class UserLoginResult {
        private User user;
        private UserProfile profile;

        public UserLoginResult(User user, UserProfile profile) {
            this.user = user;
            this.profile = profile;
        }

        public User getUser() { return user; }
        public UserProfile getProfile() { return profile; }
    }

}


