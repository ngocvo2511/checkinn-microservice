package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelAmenityRepository extends JpaRepository<HotelAmenity, UUID> {
    List<HotelAmenity> findAllByCategoryHotelId(UUID hotelId);
    void deleteAllByCategoryHotelId(UUID hotelId);
    void deleteAllByCategory(HotelAmenityCategory category);
}
