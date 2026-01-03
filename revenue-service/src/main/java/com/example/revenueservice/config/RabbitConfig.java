package com.example.revenueservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.rabbit.exchange:hotel.events}")
    private String exchangeName;

    @Value("${app.rabbit.payment-queue:revenue.payment.queue}")
    private String paymentQueueName;

    @Value("${app.rabbit.booking-queue:revenue.booking.queue}")
    private String bookingQueueName;

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public TopicExchange hotelEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue paymentQueue() {
        return new Queue(paymentQueueName, true);
    }

    @Bean
    public Queue bookingQueue() {
        return new Queue(bookingQueueName, true);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange hotelEventsExchange) {
        return BindingBuilder.bind(paymentQueue).to(hotelEventsExchange).with("payment.*");
    }

    @Bean
    public Binding bookingBinding(Queue bookingQueue, TopicExchange hotelEventsExchange) {
        return BindingBuilder.bind(bookingQueue).to(hotelEventsExchange).with("booking.status.*");
    }
}
