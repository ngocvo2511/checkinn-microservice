package com.example.userservice.repository;

import com.example.userservice.model.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, UUID> {
    @Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM points_transactions mine
          WHERE mine.user_id = :userId AND mine.transaction_type IN ('EARN','USE')
            AND EXISTS (SELECT 1 FROM points_transactions other
              WHERE other.booking_id = mine.booking_id AND other.transaction_type = mine.transaction_type
                AND other.id <> mine.id)
        )
        """, nativeQuery = true)
    boolean hasAmbiguousEarningOrUse(UUID userId);
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.userId = :userId ORDER BY pt.createdAt DESC")
    List<PointsTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<PointsTransaction> findByBookingId(UUID bookingId);
}
