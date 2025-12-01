package com.example.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.userservice.model.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(Long userId);
}
