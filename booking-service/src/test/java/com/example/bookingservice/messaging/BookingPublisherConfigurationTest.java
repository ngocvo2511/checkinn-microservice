package com.example.bookingservice.messaging;

import com.example.bookingservice.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import static org.assertj.core.api.Assertions.assertThat;

class BookingPublisherConfigurationTest {
    @Test void mandatoryReturnsAreEnabledSoAnUnroutableAckCannotCompleteTheOutbox() {
        var connection = new CachingConnectionFactory("127.0.0.1");
        try {
            new RabbitConfig().rabbitTemplate(connection, new Jackson2JsonMessageConverter());
            assertThat(connection.isPublisherReturns()).isTrue();
        } finally {
            connection.destroy();
        }
    }
}
