package com.example.bookingservice.booking.service;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.messaging.HotelEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.concurrent.Executor;
import static org.mockito.Mockito.*;

class PaidReservationRecoveryTest {
    @Test void postCommitWakeupDoesNotBlockTheRequestOpeningAnotherTransaction() {
        var bookings=mock(BookingRepository.class); var service=mock(PaidReservationService.class);
        var events=mock(HotelEventPublisher.class); var executor=mock(Executor.class);
        var recovery=new PaidReservationRecovery(bookings,service,events,executor);
        recovery.accepted(new PaidReservationService.Requested("booking"));
        verifyNoInteractions(service);
        var task=ArgumentCaptor.forClass(Runnable.class); verify(executor).execute(task.capture());
        task.getValue().run(); verify(service).process("booking");
    }
    @Test void boundedQueueRejectionDoesNotLoseTheDurableDatabaseTask() {
        var bookings=mock(BookingRepository.class); var service=mock(PaidReservationService.class);
        var events=mock(HotelEventPublisher.class); var executor=mock(Executor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException()).when(executor).execute(any());
        var recovery=new PaidReservationRecovery(bookings,service,events,executor);
        recovery.accepted(new PaidReservationService.Requested("booking"));
        when(bookings.findDuePaidReservations(any(),any())).thenReturn(List.of("booking"));
        recovery.recover(); verify(service).process("booking");
    }
    @Test void failedCompletionTransactionDefersBeforeAnyEventIsPublished() {
        var bookings=mock(BookingRepository.class);var service=mock(PaidReservationService.class);
        var events=mock(HotelEventPublisher.class);var executor=mock(Executor.class);
        when(bookings.findDuePaidReservations(any(),any())).thenReturn(List.of("booking"));
        when(service.process("booking")).thenThrow(new IllegalStateException("synthetic commit failure"));
        new PaidReservationRecovery(bookings,service,events,executor).recover();
        verify(service).defer("booking");verifyNoInteractions(events);
    }

}
