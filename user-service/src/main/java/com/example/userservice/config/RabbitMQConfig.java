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
    // Routing Key
    public static final String OTP_ROUTING_KEY = "otp.verification";

    @Bean
    public TopicExchange otpExchange() {
        return new TopicExchange(OTP_EXCHANGE, true, false);
    }

    @Bean
    public Queue otpQueue() {
        return new Queue(OTP_QUEUE, true);
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, TopicExchange otpExchange) {
        return BindingBuilder.bind(otpQueue)
                .to(otpExchange)
                .with(OTP_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
