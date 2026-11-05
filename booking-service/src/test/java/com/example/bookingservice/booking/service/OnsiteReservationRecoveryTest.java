package com.example.bookingservice.booking.service;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.messaging.HotelEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.concurrent.Executor;
import static org.mockito.Mockito.*;

class OnsiteReservationRecoveryTest {
    @Test void postCommitWakeupDoesNotBlockTheRequestOpeningAnotherTransaction() {
        var bookings=mock(BookingRepository.class); var service=mock(OnsiteReservationService.class);
        var events=mock(HotelEventPublisher.class); var executor=mock(Executor.class);
        var recovery=new OnsiteReservationRecovery(bookings,service,events,executor);
        recovery.accepted(new OnsiteReservationService.Requested("booking"));
        verifyNoInteractions(service);
        var task=ArgumentCaptor.forClass(Runnable.class); verify(executor).execute(task.capture());
        task.getValue().run(); verify(service).process("booking");
    }
    @Test void boundedQueueRejectionDoesNotLoseTheDurableDatabaseTask() {
        var bookings=mock(BookingRepository.class); var service=mock(OnsiteReservationService.class);
        var events=mock(HotelEventPublisher.class); var executor=mock(Executor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(any());
        var recovery=new OnsiteReservationRecovery(bookings,service,events,executor);
        recovery.accepted(new OnsiteReservationService.Requested("booking"));
        when(bookings.findDueOnsite(any(),any())).thenReturn(List.of("booking"));
        recovery.recover(); verify(service).process("booking");
    }
}
