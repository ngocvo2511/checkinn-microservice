package com.example.hotelservice.Room.repository;

import com.example.hotelservice.Room.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    List<RoomType> findByHotelId(UUID hotelId);

    boolean existsByIdAndHotelId(UUID roomTypeId, UUID hotelId);
}
