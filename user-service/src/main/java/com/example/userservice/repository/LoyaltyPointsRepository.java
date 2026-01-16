package com.example.userservice.repository;

import com.example.userservice.model.LoyaltyPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyPointsRepository extends JpaRepository<LoyaltyPoints, UUID> {
    Optional<LoyaltyPoints> findByUserId(UUID userId);
}
