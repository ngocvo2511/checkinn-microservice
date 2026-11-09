package com.example.regulationsservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public org.springframework.amqp.rabbit.core.RabbitTemplate regulationRabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory, MessageConverter converter) {
        if(connectionFactory instanceof org.springframework.amqp.rabbit.connection.CachingConnectionFactory cached) {
            cached.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
            cached.setPublisherReturns(true);
        }
        var template=new org.springframework.amqp.rabbit.core.RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);template.setMandatory(true);return template;
    }

    public static final String REGULATION_ROUTING_KEY = "regulation.updated";

    @Bean
    public TopicExchange regulationExchange() {
        return new TopicExchange("hotel.events", true, false);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
