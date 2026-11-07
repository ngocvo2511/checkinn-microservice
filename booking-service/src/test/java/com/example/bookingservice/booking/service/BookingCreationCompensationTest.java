package com.example.bookingservice.booking.service;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import static org.mockito.Mockito.*;

class BookingCreationCompensationTest {
    @Test void rollbackReleasesKnownResourcesOnceButCommitAndUnknownNeverDo() {
        var inventory=mock(HotelAvailabilityClient.class);var points=mock(LoyaltyPointsClient.class);
        var cleanup=new BookingCreationCompensation(inventory,points,new HoldResponse("hold", "HELD", "2027-01-01T00:00:00Z"),"guest");
        cleanup.reservedPointsFor("booking");
        cleanup.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        cleanup.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);verifyNoInteractions(inventory,points);
        cleanup.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);cleanup.run();
        verify(inventory,times(1)).releaseHold("hold");verify(points,times(1)).refundPoints("guest","booking");
    }
    @Test void rollbackRetriesFailedCleanupWithoutBlockingIndependentPointRelease() {
        var inventory=mock(HotelAvailabilityClient.class);var points=mock(LoyaltyPointsClient.class);
        doThrow(new IllegalStateException("synthetic outage")).doNothing().when(inventory).releaseHold("hold");
        var cleanup=new BookingCreationCompensation(inventory,points,new HoldResponse("hold", "HELD", "2027-01-01T00:00:00Z"),"guest");
        cleanup.reservedPointsFor("booking");cleanup.run();cleanup.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(inventory,times(2)).releaseHold("hold");verify(points,times(1)).refundPoints("guest","booking");
    }
}
