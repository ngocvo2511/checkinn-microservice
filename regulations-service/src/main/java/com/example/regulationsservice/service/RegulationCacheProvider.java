package com.example.regulationsservice.service;

import com.example.regulationsservice.messaging.RegulationEvent;
import com.example.regulationsservice.messaging.RegulationEventPublisher;
import com.example.regulationsservice.model.Regulation;
import com.example.regulationsservice.model.RegulationSnapshot;
import com.example.regulationsservice.repository.RegulationRepository;
import com.example.regulationsservice.repository.RegulationSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegulationCacheProvider implements RegulationProvider {

    private static final Logger logger = LoggerFactory.getLogger(RegulationCacheProvider.class);

    private final RegulationRepository regulationRepository;
    private final RegulationSnapshotRepository snapshotRepository;
    private final RegulationEventPublisher eventPublisher;

    public RegulationCacheProvider(RegulationRepository regulationRepository,
                                   RegulationSnapshotRepository snapshotRepository,
                                   RegulationEventPublisher eventPublisher) {
        this.regulationRepository = regulationRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void initializeCache() {
        refreshCache();
        seedDefaultRegulations();
    }

    @Override
    public List<Regulation> getAllRegulations() {
        return regulationRepository.findAll();
    }

    @Override
    public Optional<Regulation> getRegulation(String regulationKey) {
        return regulationRepository.findByRegulationKey(regulationKey);
    }

    @Override
    @Transactional
    public Regulation saveRegulation(Regulation regulation, String changedBy) {
        Regulation saved = persist(regulation);
        createSnapshot(saved, changedBy);
        publishEvent(saved, changedBy);
        logger.info("[REGULATION_WRITE_STAGED] version={}", saved.getVersion());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegulationSnapshot> getSnapshots() {
        return snapshotRepository.findTop50ByOrderByAppliedAtDesc();
    }

    @Override
    public void refreshCache() {
        // Compatibility endpoint: reads already use committed database state across all instances.
        int count=regulationRepository.findAll().size();
        logger.info("[REGULATION_REFRESH] entries={}", count);
    }

    private Regulation persist(Regulation regulation) {
        String key = regulation.getRegulationKey();
        Optional<Regulation> existing = regulationRepository.findByRegulationKey(key);

        if (existing.isPresent()) {
            Regulation saved = existing.get();
            if (saved.getVersion() != regulation.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Regulation version has changed");
            }
            saved.setName(regulation.getName());
            saved.setValue(regulation.getValue());
            saved.setDescription(regulation.getDescription());
            saved.setActive(regulation.isActive());
            return regulationRepository.saveAndFlush(saved);
        }

        regulation.setVersion(1);
        return regulationRepository.saveAndFlush(regulation);
    }

    private void createSnapshot(Regulation regulation, String changedBy) {
        RegulationSnapshot snapshot = new RegulationSnapshot(
                regulation.getRegulationKey(),
                regulation.getName(),
                regulation.getVersion(),
                buildSnapshotData(regulation),
                LocalDateTime.now(),
                changedBy
        );
        snapshotRepository.save(snapshot);
    }

    private String buildSnapshotData(Regulation regulation) {
        var data=new java.util.LinkedHashMap<String,Object>();
        data.put("key",regulation.getRegulationKey());data.put("name",regulation.getName());
        data.put("value",regulation.getValue());data.put("description",regulation.getDescription());
        data.put("active",regulation.isActive());data.put("version",regulation.getVersion());
        try { return new ObjectMapper().writeValueAsString(data); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Regulation snapshot encoding failed");
        }
    }

    private void publishEvent(Regulation regulation, String changedBy) {
        RegulationEvent event = new RegulationEvent(
                regulation.getRegulationKey(),
                regulation.getName(),
                regulation.getValue(),
                regulation.isActive(),
                regulation.getVersion(),
                changedBy,
                LocalDateTime.now()
        );
        eventPublisher.publish(event);
    }

    private void seedDefaultRegulations() {
        if (!regulationRepository.findAll().isEmpty()) {
            return;
        }

        logger.info("[REGULATION_CACHE_SEED] Seeding default regulations into provider cache");

        saveRegulation(new Regulation("HOLD_EXPIRY_MINUTES", "Hold Expiry Minutes", "15", "Default hold expiry time in minutes.", true, 1), "system");
        saveRegulation(new Regulation("COMMISSION_RATE", "Commission Rate", "0.10", "Default commission rate used by revenue calculation.", true, 1), "system");
        saveRegulation(new Regulation("POINTS_EARN_CONVERSION_RATE", "Points Earn Conversion Rate", "10000", "Default loyalty points earn conversion rate.", true, 1), "system");
        saveRegulation(new Regulation("POINTS_REDEMPTION_CONVERSION_RATE", "Points Redemption Conversion Rate", "500", "Default loyalty points redemption conversion rate.", true, 1), "system");
    }
}
