package com.example.hotelservice.Policy.repository;

import com.example.hotelservice.Policy.entity.HotelPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelPolicyRepository extends JpaRepository<HotelPolicy, String> {
    List<HotelPolicy> findAllByHotelId(UUID id);
}
