package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Each hold is finalized in its own proxied transaction, with the same lock as confirm/release. */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldCleanupScheduler {
    private final RoomHoldRepository holds;
    private final AvailabilityService availability;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanupExpiredHolds() {
        for (var candidate : holds.findExpiredHolds(Instant.now())) {
            try {
                availability.expireHold(candidate.getId());
            } catch (RuntimeException ex) {
                log.warn("Expired hold cleanup failed; will retry on the next scan (type={})", ex.getClass().getSimpleName());
            }
        }
    }
}
