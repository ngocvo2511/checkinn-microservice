package com.example.hotelservice.Room.availability.repository;

import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomHoldRepository extends JpaRepository<RoomHold, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM RoomHold h WHERE h.id = ?1")
    Optional<RoomHold> findByIdForUpdate(UUID id);
    Optional<RoomHold> findByIdAndStatus(UUID id, HoldStatus status);

    @Query("SELECT h FROM RoomHold h WHERE h.status = 'HELD' AND h.expiresAt < ?1")
    List<RoomHold> findExpiredHolds(Instant now);
}
