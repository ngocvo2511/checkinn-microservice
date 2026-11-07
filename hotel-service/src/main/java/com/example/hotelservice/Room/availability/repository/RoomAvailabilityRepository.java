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

    // Peak combined load on a single night, not max(held) + max(booked) from different nights.
    // Missing dates have no counters; checkout is exclusive. Widen before adding integer counters.
    interface StayLoad {
        Long getPeakOccupied();
        Long getInvalidCounters();
    }

    @Query("SELECT COALESCE(MAX(CAST(ra.held AS long) + CAST(ra.booked AS long)), 0) AS peakOccupied, "
            + "COALESCE(SUM(CASE WHEN ra.held < 0 OR ra.booked < 0 THEN 1 ELSE 0 END), 0) AS invalidCounters "
            + "FROM RoomAvailability ra WHERE ra.roomTypeId = :roomTypeId "
            + "AND ra.date >= :checkIn AND ra.date < :checkOut")
    StayLoad findLoadForStay(@Param("roomTypeId") UUID roomTypeId,
                                 @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    interface OccupiedRoom extends StayLoad {
        UUID getRoomTypeId();
    }

    @Query("SELECT ra.roomTypeId AS roomTypeId, MAX(CAST(ra.held AS long) + CAST(ra.booked AS long)) AS peakOccupied, "
            + "SUM(CASE WHEN ra.held < 0 OR ra.booked < 0 THEN 1 ELSE 0 END) AS invalidCounters "
            + "FROM RoomAvailability ra WHERE ra.roomTypeId IN :roomIds "
            + "AND ra.date >= :checkIn AND ra.date < :checkOut GROUP BY ra.roomTypeId")
    java.util.List<OccupiedRoom> findPeakOccupiedForRooms(@Param("roomIds") java.util.Collection<UUID> roomIds,
            @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);
}
