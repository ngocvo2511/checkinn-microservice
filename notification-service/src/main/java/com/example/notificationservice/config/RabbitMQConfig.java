package com.example.notificationservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "hotel.events";

    // PAYMENT
    public static final String PAYMENT_QUEUE = "notification.payment.queue";
    public static final String PAYMENT_ROUTING_KEY = "payment.completed";

    // OTP
    public static final String OTP_QUEUE = "notification.otp.queue";
    public static final String OTP_ROUTING_KEY = "otp.send";

    // ---------- QUEUES ----------
    @Bean
    public Queue paymentQueue() {
        return new Queue(PAYMENT_QUEUE, true);
    }

    @Bean
    public Queue otpQueue() {
        return new Queue(OTP_QUEUE, true);
    }

    // ---------- EXCHANGE ----------
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // ---------- BINDINGS ----------
    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange exchange) {
        return BindingBuilder
                .bind(paymentQueue)
                .to(exchange)
                .with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, TopicExchange exchange) {
        return BindingBuilder
                .bind(otpQueue)
                .to(exchange)
                .with(OTP_ROUTING_KEY);
    }

    // ---------- MESSAGE CONVERTER ----------
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ---------- RABBIT TEMPLATE ----------
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}

