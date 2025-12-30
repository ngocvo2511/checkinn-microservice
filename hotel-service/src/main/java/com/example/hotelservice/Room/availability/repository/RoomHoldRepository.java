package com.example.hotelservice.Room.availability.repository;

import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomHoldRepository extends JpaRepository<RoomHold, UUID> {
    Optional<RoomHold> findByIdAndStatus(UUID id, HoldStatus status);

    @Query("SELECT h FROM RoomHold h WHERE h.status = 'HELD' AND h.expiresAt < ?1")
    List<RoomHold> findExpiredHolds(Instant now);
}
