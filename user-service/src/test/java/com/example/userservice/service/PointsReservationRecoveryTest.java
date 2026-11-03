package com.example.userservice.service;

import com.example.userservice.model.*;
import com.example.userservice.repository.PointsReservationRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PointsReservationRecoveryTest {
    @Test void aFailedLookupDoesNotReleaseFundsOrStarveTheNextCandidate() {
        var repo = mock(PointsReservationRepository.class);
        var client = mock(PointsBookingStateClient.class);
        var service = mock(LoyaltyPointsService.class);
        UUID user = UUID.randomUUID();
        when(repo.findTop20ByStateAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(any(), any())).thenReturn(List.of(
                PointsReservation.builder().bookingId("first").userId(user).build(),
                PointsReservation.builder().bookingId("second").userId(user).build()));
        when(client.getState("first", user)).thenThrow(new IllegalStateException("Unavailable"));
        when(client.getState("second", user)).thenReturn("CANCELLED");
        new PointsReservationRecovery(repo, client, service).recover();
        verify(service, never()).reconcileReservation(eq("first"), any(), any());
        verify(service).reconcileReservation("second", user, "CANCELLED");
    }
}
