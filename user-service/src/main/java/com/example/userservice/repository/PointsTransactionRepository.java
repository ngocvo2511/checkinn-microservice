package com.example.userservice.repository;

import com.example.userservice.model.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, UUID> {
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.userId = :userId ORDER BY pt.createdAt DESC")
    List<PointsTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<PointsTransaction> findByBookingId(String bookingId);
}
