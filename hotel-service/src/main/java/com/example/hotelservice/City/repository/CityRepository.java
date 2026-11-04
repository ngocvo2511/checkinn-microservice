package com.example.hotelservice.City.repository;

import com.example.hotelservice.City.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CityRepository extends JpaRepository<City, UUID> {
    java.util.List<City> findByProvince_Id(UUID provinceId);
    Optional<City> findByName(String name);
    boolean existsByName(String name);
}
