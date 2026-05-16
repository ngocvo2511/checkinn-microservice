package com.example.regulationsservice.repository;

import com.example.regulationsservice.model.Regulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegulationRepository extends JpaRepository<Regulation, Long> {

    Optional<Regulation> findByRegulationKey(String regulationKey);
}
