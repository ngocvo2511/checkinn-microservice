package com.example.regulationsservice.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@EnableScheduling
public class RegulationEventRecovery {
    private final JdbcTemplate jdbc;
    private final RegulationEventDelivery delivery;
    public RegulationEventRecovery(JdbcTemplate jdbc,RegulationEventDelivery delivery) { this.jdbc=jdbc;this.delivery=delivery; }
    @Scheduled(fixedDelayString="${events.outbox.delay-ms:1000}",initialDelayString="${events.outbox.initial-delay-ms:10000}")
    public void recover() {
        try {
            var ids=jdbc.queryForList("""
                SELECT e.id FROM regulation_event_outbox e WHERE e.state='PENDING' AND e.next_attempt_at<=now()
                AND NOT EXISTS(SELECT 1 FROM regulation_event_outbox p WHERE p.regulation_key=e.regulation_key
                    AND p.state='PENDING' AND p.source_version<e.source_version)
                ORDER BY e.created_at,e.id LIMIT 50
                """,UUID.class);
            for(UUID id:ids)try { delivery.deliver(id); }
            catch(Exception failure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("[REGULATION_DELIVERY_RETRY_PENDING]"); }
        } catch(Exception failure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("[REGULATION_OUTBOX_SCAN_UNAVAILABLE]"); }
    }
}
