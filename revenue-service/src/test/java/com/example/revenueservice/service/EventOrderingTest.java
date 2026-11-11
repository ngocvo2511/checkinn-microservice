package com.example.revenueservice.service;

import com.example.revenueservice.entity.*;
import com.example.revenueservice.messaging.event.*;
import com.example.revenueservice.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventOrderingTest {
    final PaymentRecordRepository payments=mock(PaymentRecordRepository.class);
    final BookingStatusRecordRepository bookings=mock(BookingStatusRecordRepository.class);
    final EventIngestionService service=new EventIngestionService(payments,bookings,mock(org.springframework.jdbc.core.JdbcTemplate.class),new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),new CashTransactionWriter(mock(org.springframework.jdbc.core.JdbcTemplate.class)));
    final LocalDateTime time=LocalDateTime.of(2027,1,1,12,0);
    final String booking=UUID.randomUUID().toString(), hotel=UUID.randomUUID().toString(), customer=UUID.randomUUID().toString();
    BookingStatusEvent status(String state,LocalDateTime at) {
        return new BookingStatusEvent(booking,hotel,null,LocalDate.of(2027,1,10),LocalDate.of(2027,1,11),1,1,state,at,null,null);
    }
    PaymentEvent payment(String state,LocalDateTime at) {
        return new PaymentEvent(booking,customer,hotel,null,null,null,null,null,null,
                LocalDate.of(2027,1,10),LocalDate.of(2027,1,11),1,1,1,BigDecimal.TEN,state,"VNPAY",time,at,null,null,null,null,null);
    }
    AtomicReference<BookingStatusRecord> bookingState() {
        var state=new AtomicReference<BookingStatusRecord>();
        when(bookings.findByBookingId(booking)).thenAnswer(i->Optional.ofNullable(state.get()));
        when(bookings.save(any())).thenAnswer(i->{ state.set(i.getArgument(0));return state.get(); });
        return state;
    }
    @Test void delayedBookingEventCannotResurrectCancelledReservation() {
        var state=bookingState();
        service.saveBookingStatusEvent(status("CANCELLED",time.plusMinutes(1)));
        service.saveBookingStatusEvent(status("CONFIRMED",time));
        assertThat(state.get().getBookingStatus()).isEqualTo("CANCELLED");
        verify(bookings,times(1)).save(any());
    }
    @Test void exactDuplicateDoesNotRewriteProjection() {
        bookingState();var event=status("CONFIRMED",time);
        service.saveBookingStatusEvent(event);service.saveBookingStatusEvent(event);
        verify(bookings,times(1)).save(any());
    }
    @Test void timestampMissingDoesNotInventSourceOrder() {
        bookingState();service.saveBookingStatusEvent(status("CONFIRMED",null));
        verify(bookings,never()).save(any());
    }
    @Test void equalTimestampWithDifferentStateDoesNotChooseArrivalOrder() {
        var state=bookingState();service.saveBookingStatusEvent(status("CANCELLED",time));
        service.saveBookingStatusEvent(status("CONFIRMED",time));
        assertThat(state.get().getBookingStatus()).isEqualTo("CANCELLED");
        verify(bookings,times(1)).save(any());
    }
    @Test void delayedPaymentCannotReplaceNewerRefundProjection() {
        var state=new AtomicReference<PaymentRecord>();
        when(payments.findByBookingId(booking)).thenAnswer(i->Optional.ofNullable(state.get()));
        when(payments.save(any())).thenAnswer(i->{state.set(i.getArgument(0));return state.get();});
        service.savePaymentEvent(payment("REFUNDED",time.plusMinutes(1)));
        service.savePaymentEvent(payment("COMPLETED",time));
        assertThat(state.get().getPaymentStatus()).isEqualTo("REFUNDED");
        verify(payments,times(1)).save(any());
    }
    @Test void newerValidBookingEventStillApplies() {
        var state=bookingState();service.saveBookingStatusEvent(status("CONFIRMED",time));
        service.saveBookingStatusEvent(status("CHECKED_IN",time.plusMinutes(1)));
        assertThat(state.get().getBookingStatus()).isEqualTo("CHECKED_IN");
        verify(bookings,times(2)).save(any());
    }
    @Test void sourceVersionWinsEvenWhenProducerClockMovesBackwards() {
        var state=bookingState();
        var a=status("CONFIRMED",time.plusMinutes(1));var b=status("CANCELLED",time);
        service.saveBookingStatusEvent(new BookingStatusEvent(a.bookingId(),a.hotelId(),a.roomTypeId(),a.checkInDate(),a.checkOutDate(),a.nights(),a.rooms(),a.bookingStatus(),a.eventAt(),1L,UUID.randomUUID()));
        service.saveBookingStatusEvent(new BookingStatusEvent(b.bookingId(),b.hotelId(),b.roomTypeId(),b.checkInDate(),b.checkOutDate(),b.nights(),b.rooms(),b.bookingStatus(),b.eventAt(),2L,UUID.randomUUID()));
        assertThat(state.get().getBookingStatus()).isEqualTo("CANCELLED");
        assertThat(state.get().getEventVersion()).isEqualTo(2L);
    }
    @Test void legacyEventCannotOverwriteVersionedProjection() {
        var state=bookingState();var a=status("CANCELLED",time);
        service.saveBookingStatusEvent(new BookingStatusEvent(a.bookingId(),a.hotelId(),a.roomTypeId(),a.checkInDate(),a.checkOutDate(),a.nights(),a.rooms(),a.bookingStatus(),a.eventAt(),1L,UUID.randomUUID()));
        service.saveBookingStatusEvent(status("CONFIRMED",time.plusDays(1)));
        assertThat(state.get().getBookingStatus()).isEqualTo("CANCELLED");
        verify(bookings,times(1)).save(any());
    }
}
