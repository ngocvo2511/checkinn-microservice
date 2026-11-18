package com.example.travelsocialservice.repository;

import com.example.travelsocialservice.domain.ModerationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModerationAuditRepository extends JpaRepository<ModerationAudit, UUID> {
}
