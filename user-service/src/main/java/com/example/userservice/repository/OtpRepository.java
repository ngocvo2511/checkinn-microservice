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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Otp o where o.email=:email order by o.createdAt desc")
    java.util.List<Otp> findAllForUpdate(@org.springframework.data.repository.query.Param("email") String email);
}
