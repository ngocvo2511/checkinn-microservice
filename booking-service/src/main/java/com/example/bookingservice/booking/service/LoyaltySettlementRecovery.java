package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltySettlementRecovery {
    private final BookingRepository bookings;
    private final LoyaltySettlementService settlement;

    @Scheduled(fixedDelayString = "${loyalty.settlement.delay-ms:5000}", initialDelayString = "${loyalty.settlement.initial-delay-ms:30000}")
    public void recover() {
        for (String id : bookings.findDueLoyalty(LocalDateTime.now(), PageRequest.of(0, 20))) {
            try { settlement.process(id); }
            catch (RuntimeException ex) {
                try { settlement.deferAfterFailure(id); }
                catch (RuntimeException unavailable) { log.warn("Loyalty retry scheduling unavailable (type={})", unavailable.getClass().getSimpleName()); }
                log.warn("Loyalty settlement transaction deferred (type={})", ex.getClass().getSimpleName());
            }
        }
    }
}
