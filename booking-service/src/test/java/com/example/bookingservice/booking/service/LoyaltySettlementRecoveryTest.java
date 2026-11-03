package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LoyaltySettlementRecoveryTest {
    @Test void failedCompletionCommitDoesNotStarveNextCandidate() {
        var bookings = mock(BookingRepository.class);
        var settlement = mock(LoyaltySettlementService.class);
        when(bookings.findDueLoyalty(any(), any())).thenReturn(List.of("commit-failed", "next"));
        doThrow(new IllegalStateException("commit failed")).when(settlement).process("commit-failed");
        new LoyaltySettlementRecovery(bookings, settlement).recover();
        verify(settlement).process("commit-failed");
        verify(settlement).deferAfterFailure("commit-failed");
        verify(settlement).process("next");
    }
}
