package com.example.regulationsservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegulationEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RegulationEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public RegulationEventPublisher(RabbitTemplate rabbitTemplate,
                                    @Value("${app.rabbit.exchange:hotel.events}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    public void publish(RegulationEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, "regulation.updated", event);
            logger.info("Published regulation.updated event for key={} version={}", event.getRegulationKey(), event.getVersion());
        } catch (Exception error) {
            logger.warn("Unable to publish regulation event: {}", error.getMessage(), error);
        }
    }
}
