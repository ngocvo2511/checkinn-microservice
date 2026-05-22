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

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RegulationCacheProvider implements RegulationProvider {

    private static final Logger logger = LoggerFactory.getLogger(RegulationCacheProvider.class);

    private final RegulationRepository regulationRepository;
    private final RegulationSnapshotRepository snapshotRepository;
    private final RegulationEventPublisher eventPublisher;
    private final Map<String, Regulation> cache = new ConcurrentHashMap<>();

    public RegulationCacheProvider(RegulationRepository regulationRepository,
                                   RegulationSnapshotRepository snapshotRepository,
                                   RegulationEventPublisher eventPublisher) {
        this.regulationRepository = regulationRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initializeCache() {
        refreshCache();
        seedDefaultRegulations();
    }

    @Override
    public List<Regulation> getAllRegulations() {
        return new ArrayList<>(cache.values());
    }

    @Override
    public Optional<Regulation> getRegulation(String regulationKey) {
        return Optional.ofNullable(cache.get(regulationKey));
    }

    @Override
    @Transactional
    public Regulation saveRegulation(Regulation regulation, String changedBy) {
        logger.info("[REGULATION_CACHE_SAVE] Saving regulation - key: {}, changedBy: {}, active: {}", regulation.getRegulationKey(), changedBy, regulation.isActive());
        Regulation saved = persist(regulation);
        cache.put(saved.getRegulationKey(), saved);
        createSnapshot(saved, changedBy);
        publishEvent(saved, changedBy);
        logger.info("[REGULATION_CACHE_SAVE] Saved regulation - key: {}, version: {}, cacheSize: {}", saved.getRegulationKey(), saved.getVersion(), cache.size());
        return saved;
    }

    @Override
    public List<RegulationSnapshot> getSnapshots() {
        return snapshotRepository.findTop50ByOrderByAppliedAtDesc();
    }

    @Override
    public void refreshCache() {
        cache.clear();
        regulationRepository.findAll().forEach(regulation -> cache.put(regulation.getRegulationKey(), regulation));
        logger.info("[REGULATION_CACHE_REFRESH] Loaded {} regulation entries into local provider cache", cache.size());
    }

    private Regulation persist(Regulation regulation) {
        String key = regulation.getRegulationKey();
        Optional<Regulation> existing = regulationRepository.findByRegulationKey(key);

        if (existing.isPresent()) {
            logger.info("[REGULATION_CACHE_PERSIST_UPDATE] Updating existing regulation - key: {}", key);
            Regulation saved = existing.get();
            saved.setName(regulation.getName());
            saved.setValue(regulation.getValue());
            saved.setDescription(regulation.getDescription());
            saved.setActive(regulation.isActive());
            saved.setVersion(saved.getVersion() + 1);
            return regulationRepository.save(saved);
        }

        logger.info("[REGULATION_CACHE_PERSIST_CREATE] Creating new regulation - key: {}", key);
        regulation.setVersion(1);
        return regulationRepository.save(regulation);
    }

    private void createSnapshot(Regulation regulation, String changedBy) {
        logger.info("[REGULATION_CACHE_SNAPSHOT] Creating regulation snapshot - key: {}, version: {}, changedBy: {}", regulation.getRegulationKey(), regulation.getVersion(), changedBy);
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
        return String.format(
                "{\"key\":\"%s\",\"name\":\"%s\",\"value\":\"%s\",\"description\":\"%s\",\"active\":%s,\"version\":%d}",
                regulation.getRegulationKey(),
                regulation.getName(),
                regulation.getValue(),
                regulation.getDescription() == null ? "" : regulation.getDescription().replace("\"", "\\\""),
                regulation.isActive(),
                regulation.getVersion()
        );
    }

    private void publishEvent(Regulation regulation, String changedBy) {
        logger.info("[REGULATION_CACHE_EVENT_PUBLISH] Publishing regulation event - key: {}, version: {}, changedBy: {}", regulation.getRegulationKey(), regulation.getVersion(), changedBy);
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
        if (!cache.isEmpty()) {
            return;
        }

        logger.info("[REGULATION_CACHE_SEED] Seeding default regulations into provider cache");

        saveRegulation(new Regulation("HOLD_EXPIRY_MINUTES", "Hold Expiry Minutes", "15", "Default hold expiry time in minutes.", true, 1), "system");
        saveRegulation(new Regulation("COMMISSION_RATE", "Commission Rate", "0.10", "Default commission rate used by revenue calculation.", true, 1), "system");
        saveRegulation(new Regulation("POINTS_EARN_CONVERSION_RATE", "Points Earn Conversion Rate", "10000", "Default loyalty points earn conversion rate.", true, 1), "system");
        saveRegulation(new Regulation("POINTS_REDEMPTION_CONVERSION_RATE", "Points Redemption Conversion Rate", "500", "Default loyalty points redemption conversion rate.", true, 1), "system");
    }
}
