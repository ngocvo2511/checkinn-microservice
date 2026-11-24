package com.example.travelsocialservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarations must match docker/rabbitmq/definitions.json exactly: a queue redeclared with different
 * arguments is refused by the broker.
 */
@Configuration
public class RabbitConfig {

    public static final String HOTEL_EVENTS_EXCHANGE = "hotel.events";
    public static final String BOOKING_QUEUE = "social.booking.queue";
    public static final String DEAD_LETTER_EXCHANGE = "social.dlx";
    public static final String DEAD_LETTER_QUEUE = "social.events.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "social.booking.dead";

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public TopicExchange hotelEventsExchange() {
        return new TopicExchange(HOTEL_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange socialDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue socialBookingQueue() {
        return QueueBuilder.durable(BOOKING_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue socialDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding socialBookingBinding(Queue socialBookingQueue, TopicExchange hotelEventsExchange) {
        return BindingBuilder.bind(socialBookingQueue).to(hotelEventsExchange).with("booking.status.*");
    }

    @Bean
    public Binding socialDeadLetterBinding(Queue socialDeadLetterQueue, TopicExchange socialDeadLetterExchange) {
        return BindingBuilder.bind(socialDeadLetterQueue).to(socialDeadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }
}
