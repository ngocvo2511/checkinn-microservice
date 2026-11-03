package com.example.userservice.repository;

import com.example.userservice.model.PointsReservation;
import com.example.userservice.model.PointsReservationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PointsReservationRepository extends JpaRepository<PointsReservation, String> {
    @Query("select coalesce(sum(r.points), 0) from PointsReservation r where r.userId = ?1 and r.state = com.example.userservice.model.PointsReservationState.HELD")
    long sumHeldByUserId(UUID userId);
    List<PointsReservation> findTop20ByStateAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(PointsReservationState state, Instant now);
}
