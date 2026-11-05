package com.example.bookingservice.booking.service;
import com.example.bookingservice.booking.entity.Booking;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;

class OnsiteEventsTest {
    @Test void eventsOnlyRunAfterActualCommitAndAreDiscardedOnRollback() {
        var booking=Booking.builder().onsiteReservationStatus("V1_PENDING").build();
        Runnable publish=mock(Runnable.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            OnsiteEvents.afterCommit(booking,publish); verifyNoInteractions(publish);
            // A rollback never invokes afterCommit.
            TransactionSynchronizationManager.clearSynchronization();
            verifyNoInteractions(publish);
            TransactionSynchronizationManager.initSynchronization();
            OnsiteEvents.afterCommit(booking,publish);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(publish,times(1)).run();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
