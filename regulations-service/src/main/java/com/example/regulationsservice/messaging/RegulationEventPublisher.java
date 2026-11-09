package com.example.regulationsservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegulationEventPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String exchangeName;

    public RegulationEventPublisher(JdbcTemplate jdbc, ObjectMapper json,
                                    @Value("${app.rabbit.exchange:hotel.events}") String exchangeName) {
        this.jdbc = jdbc;
        this.json = json;
        this.exchangeName = exchangeName;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void publish(RegulationEvent event) {
        try {
            jdbc.update("INSERT INTO regulation_event_outbox(id,regulation_key,source_version,exchange_name,payload) VALUES (?,?,?,?,CAST(? AS jsonb))",
                    UUID.randomUUID(),event.getRegulationKey(),event.getVersion(),exchangeName,json.writeValueAsString(event));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Regulation event encoding failed");
        }
    }
}
