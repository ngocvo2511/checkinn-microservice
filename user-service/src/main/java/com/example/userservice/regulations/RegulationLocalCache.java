package com.example.userservice.regulations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegulationLocalCache {

    private static final Logger logger = LoggerFactory.getLogger(RegulationLocalCache.class);

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        cache.put(key, value);
        logger.info("[USER_REGULATION_CACHE_UPDATED] Regulation cache updated - key: {}, value: {}", key, value);
    }

    public String get(String key) {
        return cache.get(key);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "", durable = "true", autoDelete = "true"),
            exchange = @Exchange(value = "hotel.events", type = "topic", durable = "true"),
            key = "regulation.updated"
    ))
    public void handleRegulationEvent(java.util.Map<String, Object> payload) {
        try {
            Object key = payload.get("regulationKey");
            Object value = payload.get("value");
            if (key != null && value != null) {
                put(key.toString(), value.toString());
            }
            logger.info("[USER_REGULATION_EVENT_PROCESSED] Processed regulation.updated event - key: {}, value: {}", key, value);
        } catch (Exception ex) {
            logger.warn("[USER_REGULATION_EVENT_ERROR] Failed to process regulation.updated event", ex);
        }
    }
}
