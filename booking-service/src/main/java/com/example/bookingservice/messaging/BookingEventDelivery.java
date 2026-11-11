package com.example.bookingservice.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class BookingEventDelivery {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final long confirmMillis;
    private final int retrySeconds;
    public BookingEventDelivery(JdbcTemplate jdbc,RabbitTemplate rabbit,
            @Value("${events.outbox.confirm-timeout-ms:3000}") long confirmMillis,
            @Value("${events.outbox.retry-seconds:5}") int retrySeconds) {
        if(confirmMillis<=0 || retrySeconds<=0)throw new IllegalArgumentException("Positive outbox time budgets required");
        this.jdbc=jdbc;this.rabbit=rabbit;this.confirmMillis=confirmMillis;this.retrySeconds=retrySeconds;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void deliver(UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM booking_event_outbox WHERE id=? AND state='PENDING' AND next_attempt_at<=now() FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty())return;
        var row=rows.getFirst();String failure="BROKER_UNAVAILABLE";
        boolean delivered=false;
        try {
            var properties=new MessageProperties();properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);properties.setMessageId(id.toString());
            var message=new Message(row.get("payload").toString().getBytes(StandardCharsets.UTF_8),properties);
            var correlation=new CorrelationData(id.toString());
            rabbit.send((String)row.get("exchange_name"),(String)row.get("routing_key"),message,correlation);
            var confirm=correlation.getFuture().get(confirmMillis,TimeUnit.MILLISECONDS);
            if(!confirm.isAck())failure="BROKER_NACK";
            else if(correlation.getReturned()!=null)failure="UNROUTABLE_EVENT";
            else delivered=true;
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();failure="DELIVERY_INTERRUPTED";
        } catch(java.util.concurrent.TimeoutException timeout) { failure="CONFIRM_TIMEOUT";
        } catch(Exception unavailable) { /* Never persist/log raw transport errors or payloads. */ }
        int current=((Number)row.get("attempts")).intValue();int attempts=current==Integer.MAX_VALUE?current:current+1;
        if(delivered)jdbc.update("UPDATE booking_event_outbox SET state='DELIVERED',delivered_at=now(),attempts=?,last_error=NULL WHERE id=?",attempts,id);
        else {
            jdbc.update("UPDATE booking_event_outbox SET attempts=?,next_attempt_at=now()+(? * interval '1 second'),last_error=? WHERE id=?",attempts,retrySeconds,failure,id);
            org.slf4j.LoggerFactory.getLogger(BookingEventDelivery.class).warn("[BOOKING_EVENT_DELIVERY_PENDING] {}",failure);
        }
    }
}
