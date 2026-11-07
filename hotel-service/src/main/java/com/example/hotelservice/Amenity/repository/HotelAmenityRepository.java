package com.example.hotelservice.Amenity.repository;

import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelAmenityRepository extends JpaRepository<HotelAmenity, UUID> {
    interface CandidateFacet {
        UUID getHotelId();
        UUID getCategoryId();
        String getCategoryTitle();
        UUID getItemId();
        String getTitle();
    }

    @org.springframework.data.jpa.repository.Query("SELECT c.hotelId AS hotelId, c.id AS categoryId, c.title AS categoryTitle, "
            + "a.id AS itemId, a.title AS title FROM HotelAmenity a JOIN a.category c "
            + "WHERE c.hotelId IN :hotelIds AND c.isActive = true AND a.isActive = true")
    List<CandidateFacet> findCandidateFacets(@org.springframework.data.repository.query.Param("hotelIds") java.util.Collection<UUID> hotelIds);

    List<HotelAmenity> findAllByCategoryHotelId(UUID hotelId);
    void deleteAllByCategoryHotelId(UUID hotelId);
    void deleteAllByCategory(HotelAmenityCategory category);
}
