package com.example.hotelservice.Room.repository;

import com.example.hotelservice.Room.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomType r WHERE r.id = ?1")
    Optional<RoomType> findByIdForUpdate(UUID id);
    List<RoomType> findByHotelId(UUID hotelId);

    boolean existsByIdAndHotelId(UUID roomTypeId, UUID hotelId);
}
