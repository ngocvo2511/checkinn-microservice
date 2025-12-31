package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.HotelAmenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelAmenityRepository extends JpaRepository<HotelAmenity, UUID> {
    List<HotelAmenity> findAllByHotelId(UUID hotelId);
    void deleteAllByHotelId(UUID hotelId);
    void deleteAllByCategory(com.example.hotelservice.Amenity.entity.HotelAmenityCategory category);
}
