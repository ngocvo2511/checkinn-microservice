package com.example.hotelservice.City.repository;

import com.example.hotelservice.City.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, UUID> {
    Optional<Province> findByName(String name);
    Optional<Province> findByCode(String code);
    boolean existsByName(String name);
}
