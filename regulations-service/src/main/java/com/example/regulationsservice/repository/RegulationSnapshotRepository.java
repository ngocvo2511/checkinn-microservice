package com.example.regulationsservice.repository;

import com.example.regulationsservice.model.RegulationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegulationSnapshotRepository extends JpaRepository<RegulationSnapshot, Long> {

    List<RegulationSnapshot> findTop50ByOrderByAppliedAtDesc();

    List<RegulationSnapshot> findAllByRegulationKeyOrderByAppliedAtDesc(String regulationKey);
}
