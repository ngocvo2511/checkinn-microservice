package com.example.regulationsservice.service;

import com.example.regulationsservice.model.Regulation;
import com.example.regulationsservice.model.RegulationSnapshot;

import java.util.List;
import java.util.Optional;

public interface RegulationProvider {

    List<Regulation> getAllRegulations();

    Optional<Regulation> getRegulation(String regulationKey);

    Regulation saveRegulation(Regulation regulation, String changedBy);

    List<RegulationSnapshot> getSnapshots();

    void refreshCache();
}
