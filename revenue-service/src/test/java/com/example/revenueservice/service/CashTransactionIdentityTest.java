package com.example.revenueservice.service;

import com.example.revenueservice.messaging.event.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashTransactionIdentityTest {
    @Test void aRefundCannotReuseAnotherBookingsPaymentIdentityEvenWithTheSameAmount() {
        var jdbc = mock(JdbcTemplate.class);
        var from = LocalDate.of(2027, 1, 10);
        var opposite = new HashMap<String,Object>();
        opposite.put("amount", BigDecimal.TEN);
        opposite.put("booking_id", "original-booking");
        opposite.put("customer_id", "guest");
        opposite.put("hotel_id", "hotel");
        opposite.put("room_type_id", null);
        opposite.put("check_in_date", java.sql.Date.valueOf(from));
        opposite.put("check_out_date", java.sql.Date.valueOf(from.plusDays(1)));
        opposite.put("nights", 1);
        opposite.put("rooms", 1);
        opposite.put("payment_method", "VNPAY");
        when(jdbc.queryForList(contains("kind<>?"), anyString(), anyString())).thenReturn(List.of(opposite));
        var event = new PaymentEvent("foreign-booking", "guest", "hotel", null, null, null, null, null, null,
                from, from.plusDays(1), 1, 1, 1, BigDecimal.TEN, "REFUNDED", "VNPAY",
                LocalDateTime.of(2027, 1, 1, 12, 0), LocalDateTime.of(2027, 1, 2, 12, 0),
                2L, UUID.randomUUID(), "existing-payment", "refund", Instant.parse("2027-01-02T05:00:00Z"));
        assertThat(new CashTransactionWriter(jdbc).record(event))
                .isEqualTo(CashTransactionWriter.Outcome.TRANSACTION_CONFLICT);
        assertThat(mockingDetails(jdbc).getInvocations()).noneMatch(call ->
                call.getMethod().getName().equals("update") && call.getArgument(0, String.class)
                        .contains("INSERT INTO revenue_cash_transactions"));
    }
}
