package com.example.bookingservice.messaging;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class UuidOutboxBindingTest {
    @Test void nativeBookingReferenceIsUuidWhileEventPayloadAndRoutingStayText() {
        UUID booking=UUID.fromString("d3616870-e674-310a-b1ee-6a282806ec94");
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class))).thenReturn(7L);
        var outbox=new BookingEventOutbox(jdbc,new ObjectMapper());
        outbox.enqueue(booking,"booking.status.changed",Map.of("bookingId",booking.toString()));
        verify(jdbc).update(startsWith("INSERT INTO booking_event_outbox"),any(UUID.class),eq(booking),eq(7L),isNull(),eq("booking.status.changed"),contains(booking.toString()));
    }
}
