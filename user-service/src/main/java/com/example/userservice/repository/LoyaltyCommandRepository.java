package com.example.userservice.repository;

import com.example.userservice.model.LoyaltyCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyCommandRepository extends JpaRepository<LoyaltyCommand, String> {}
