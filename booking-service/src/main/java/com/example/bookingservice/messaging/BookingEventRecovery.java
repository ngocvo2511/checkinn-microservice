package com.example.bookingservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingEventRecovery {
    private final JdbcTemplate jdbc;
    private final BookingEventDelivery delivery;
    @Scheduled(fixedDelayString="${events.outbox.delay-ms:1000}",initialDelayString="${events.outbox.initial-delay-ms:10000}")
    public void recover() {
        // A pending predecessor blocks later snapshots of the same booking, even during retries.
        var ids=jdbc.queryForList("""
            SELECT o.id FROM booking_event_outbox o WHERE o.state='PENDING' AND o.next_attempt_at<=now()
            AND NOT EXISTS(SELECT 1 FROM booking_event_outbox p WHERE p.booking_id=o.booking_id
                AND p.state='PENDING' AND p.event_version<o.event_version)
            ORDER BY o.event_version LIMIT 50
            """,UUID.class);
        for(UUID id:ids)try { delivery.deliver(id); }
        catch(RuntimeException unavailable) {
            org.slf4j.LoggerFactory.getLogger(BookingEventRecovery.class).warn("[BOOKING_EVENT_RECOVERY_DEFERRED]");
        }
    }
}
