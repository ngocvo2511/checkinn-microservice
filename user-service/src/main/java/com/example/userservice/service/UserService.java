package com.example.userservice.service;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import com.example.userservice.repository.UserProfileRepository;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public User getUserById(Long id) {
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


