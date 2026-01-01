package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelAmenityCategoryRepository extends JpaRepository<HotelAmenityCategory, UUID> {
    List<HotelAmenityCategory> findAllByHotelId(UUID hotelId);
    HotelAmenityCategory findByTitle(String title);
    Optional<HotelAmenityCategory> findByHotelIdAndTitle(UUID hotelId, String title);
    void deleteAllByHotelId(UUID hotelId);
}
