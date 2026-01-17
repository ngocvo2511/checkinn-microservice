package com.example.userservice.repository;

import com.example.userservice.model.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<Otp, UUID> {
    Optional<Otp> findByEmailAndIsVerifiedFalse(String email);
    Optional<Otp> findByEmail(String email);
    void deleteByEmail(String email);
}
