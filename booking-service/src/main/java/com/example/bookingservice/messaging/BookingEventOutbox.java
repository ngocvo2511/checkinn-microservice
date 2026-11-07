package com.example.bookingservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Event snapshots are committed by the SAME database transaction as booking/payment changes. */
@Service
@RequiredArgsConstructor
public class BookingEventOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @Value("${app.rabbit.exchange:hotel.events}") private String exchange;

    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(String bookingId,String route,Object event) {
        if(bookingId==null || bookingId.isBlank() || event==null
                || !Set.of("booking.status.changed","payment.completed","payment.refunded").contains(route))
            throw new IllegalArgumentException("Invalid booking event snapshot");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"booking-events:"+bookingId);
        Long version=jdbc.queryForObject("SELECT nextval('booking_event_outbox_version_seq')",Long.class);
        UUID id=UUID.randomUUID();
        ObjectNode payload=json.valueToTree(event);payload.put("eventVersion",version);payload.put("eventId",id.toString());
        jdbc.update("INSERT INTO booking_event_outbox(id,booking_id,event_version,exchange_name,routing_key,payload) VALUES (?,?,?,?,?,CAST(? AS jsonb))",
                id,bookingId,version,exchange,route,payload.toString());
    }
}
