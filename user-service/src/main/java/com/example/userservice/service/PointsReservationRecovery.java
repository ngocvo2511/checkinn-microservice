package com.example.userservice.service;

import com.example.userservice.model.PointsReservationState;
import com.example.userservice.repository.PointsReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component @RequiredArgsConstructor @Slf4j
public class PointsReservationRecovery {
    private final PointsReservationRepository reservations;
    private final PointsBookingStateClient bookingState;
    private final LoyaltyPointsService points;

    @Scheduled(fixedDelayString = "${points.recovery.delay-ms:30000}", initialDelayString = "${points.recovery.initial-delay-ms:30000}")
    public void recover() {
        var candidates = reservations.findTop20ByStateAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(PointsReservationState.HELD, Instant.now());
        for (var candidate : candidates) {
            try {
                // No points transaction/lock is open during the remote booking lookup.
                String observed = bookingState.getState(candidate.getBookingId(), candidate.getUserId());
                points.reconcileReservation(candidate.getBookingId(), candidate.getUserId(), observed);
            } catch (RuntimeException ex) {
                log.warn("Points reservation recovery deferred (type={})", ex.getClass().getSimpleName());
            }
        }
    }
}
