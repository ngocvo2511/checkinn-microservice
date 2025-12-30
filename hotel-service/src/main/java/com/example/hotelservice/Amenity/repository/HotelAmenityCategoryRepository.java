package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelAmenityCategoryRepository extends JpaRepository<HotelAmenityCategory, UUID> {
    List<HotelAmenityCategory> findAllByHotelId(UUID hotelId);
}
