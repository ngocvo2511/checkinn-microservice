package com.example.hotelservice.Policy.repository;

import com.example.hotelservice.Policy.entity.HotelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelPolicyRepository extends JpaRepository<HotelPolicy, UUID> {
    List<HotelPolicy> findAllByHotelId(UUID id);
    void deleteAllByHotelId(UUID hotelId);
    Optional<HotelPolicy> findByIdAndHotelId(UUID policyId, UUID hotelId);
}
