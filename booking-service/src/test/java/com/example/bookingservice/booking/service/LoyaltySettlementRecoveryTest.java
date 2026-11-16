package com.example.bookingservice.booking.service;
import static com.example.bookingservice.contract.TestIds.*;

import com.example.bookingservice.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LoyaltySettlementRecoveryTest {
    @Test void failedCompletionCommitDoesNotStarveNextCandidate() {
        var bookings = mock(BookingRepository.class);
        var settlement = mock(LoyaltySettlementService.class);
        when(bookings.findDueLoyalty(any(), any())).thenReturn(List.of(id("commit-failed"),id("next")));
        doThrow(new IllegalStateException("commit failed")).when(settlement).process(id("commit-failed"));
        new LoyaltySettlementRecovery(bookings, settlement).recover();
        verify(settlement).process(id("commit-failed"));
        verify(settlement).deferAfterFailure(id("commit-failed"));
        verify(settlement).process(id("next"));
    }
}
