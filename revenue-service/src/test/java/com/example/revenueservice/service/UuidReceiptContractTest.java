package com.example.revenueservice.service;

import java.util.UUID;
import com.example.revenueservice.repository.*;
import com.example.revenueservice.messaging.event.BookingStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class UuidReceiptContractTest {
    @Test void legacyGapBindsUuidReferencesWithoutInventingFinancialMovement() {
        var jdbc=mock(JdbcTemplate.class);
        UUID booking=UUID.randomUUID(),customer=UUID.randomUUID(),hotel=UUID.randomUUID();
        var from=java.time.LocalDate.of(2027,1,10);
        var event=new com.example.revenueservice.messaging.event.PaymentEvent(booking.toString(),customer.toString(),hotel.toString(),null,null,null,null,null,null,
            from,from.plusDays(1),1,1,1,java.math.BigDecimal.TEN,"COMPLETED","VNPAY",null,java.time.LocalDateTime.of(2027,1,1,12,0),null,null,null,null,null);
        assertThat(new CashTransactionWriter(jdbc).record(event)).isEqualTo(CashTransactionWriter.Outcome.LEGACY_PROJECTION_ONLY);
        verify(jdbc).update(startsWith("INSERT INTO revenue_cashflow_gaps"),eq(booking),eq(hotel));
        assertThat(mockingDetails(jdbc).getInvocations()).noneMatch(call -> call.getMethod().getName().equals("update") && call.getArgument(0,String.class).contains("INSERT INTO revenue_cash_transactions"));
    }
    @Test void malformedIdStillHasDurableRawReceiptAndNeverTouchesProjectionOrCash() {
        var jdbc=mock(JdbcTemplate.class);
        var payments=mock(PaymentRecordRepository.class);
        var bookings=mock(BookingStatusRecordRepository.class);
        var cash=mock(CashTransactionWriter.class);
        var service=new EventIngestionService(payments,bookings,jdbc,new ObjectMapper(),cash);
        var event=new BookingStatusEvent("malformed-booking",UUID.randomUUID().toString(),null,null,null,0,0,"PENDING",null,1L,UUID.randomUUID());
        service.saveBookingStatusEvent(event);
        verify(jdbc).update(startsWith("INSERT INTO revenue_event_receipts"),any(UUID.class),eq("BOOKING"),eq("malformed-booking"),anyString(),isNull(),eq("REVIEW_REQUIRED"),eq("INVALID_SOURCE_EVENT"),contains("malformed-booking"));
        verifyNoInteractions(payments,bookings,cash);
    }
    @Test void referencesAreNativeUuidButRawReceiptProviderAndBusinessKeysStayText() throws Exception {
        assertThat(com.example.revenueservice.entity.PaymentRecord.class.getDeclaredField("bookingId").getType()).isEqualTo(UUID.class);
        assertThat(com.example.revenueservice.entity.PaymentRecord.class.getDeclaredField("hotelId").getType()).isEqualTo(UUID.class);
        assertThat(com.example.revenueservice.contract.UuidContract.valid("provider:ABC/123")).isFalse();
    }
}
