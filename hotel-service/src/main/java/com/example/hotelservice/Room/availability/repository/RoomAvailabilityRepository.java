package com.example.hotelservice.Room.availability.repository;

import com.example.hotelservice.Room.availability.entity.RoomAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomAvailabilityRepository extends JpaRepository<RoomAvailability, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoomAvailability> findByRoomTypeIdAndDate(UUID roomTypeId, LocalDate date);

    // Read-only version without locking for read operations
    @Query("SELECT ra FROM RoomAvailability ra WHERE ra.roomTypeId = :roomTypeId AND ra.date = :date")
    Optional<RoomAvailability> findByRoomTypeIdAndDateReadOnly(@Param("roomTypeId") UUID roomTypeId, @Param("date") LocalDate date);
}
