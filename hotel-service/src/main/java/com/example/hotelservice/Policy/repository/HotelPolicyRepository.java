package com.example.hotelservice.Policy.repository;

import com.example.hotelservice.Policy.entity.HotelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelPolicyRepository extends JpaRepository<HotelPolicy, UUID> {
    @Query("SELECT p FROM HotelPolicy p JOIN FETCH p.category WHERE p.hotel.id = :hotelId")
    List<HotelPolicy> findAllByHotelId(@Param("hotelId") UUID id);
    
    void deleteAllByHotelId(UUID hotelId);
    
    Optional<HotelPolicy> findByIdAndHotelId(UUID policyId, UUID hotelId);
}
