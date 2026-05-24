package com.example.userservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange
    public static final String OTP_EXCHANGE = "user.events";
    // Queue
    public static final String OTP_QUEUE = "notification.otp.queue";
    public static final String OTP_DLQ = "notification.otp.dlq";
    public static final String DEAD_LETTER_EXCHANGE = "notification.dlx";
    // Routing Key
    public static final String OTP_ROUTING_KEY = "otp.verification";
    public static final String OTP_DEAD_LETTER_ROUTING_KEY = "notification.otp.dead";

    @Bean
    public TopicExchange otpExchange() {
        return new TopicExchange(OTP_EXCHANGE, true, false);
    }

    @Bean
    public Queue otpQueue() {
        return QueueBuilder.durable(OTP_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(OTP_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, TopicExchange otpExchange) {
        return BindingBuilder.bind(otpQueue)
                .to(otpExchange)
                .with(OTP_ROUTING_KEY);
    }

    @Bean
    public Queue otpDeadLetterQueue() {
        return QueueBuilder.durable(OTP_DLQ).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding otpDeadLetterBinding(Queue otpDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(otpDeadLetterQueue)
                .to(deadLetterExchange)
                .with(OTP_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
