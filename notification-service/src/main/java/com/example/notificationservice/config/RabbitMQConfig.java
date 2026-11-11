package com.example.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageRecoverer notificationMessageRecoverer() {
        // The default recoverer logs Message.toString(), including body and headers.
        // Reject to the existing broker DLQ without echoing the payload or SMTP cause.
        return (message, cause) -> {
            org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                    .warn("[NOTIFICATION_RETRY_EXHAUSTED] Rejecting to dead-letter queue");
            throw new AmqpRejectAndDontRequeueException("Notification retry exhausted");
        };
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        // Retain Boot's configured AUTO ack, bounded retries, converter and prefetch.
        configurer.configure(factory, connectionFactory);
        var strategy = new ConditionalRejectingErrorHandler.DefaultExceptionStrategy() {
            @Override
            protected void logFatalException(ListenerExecutionFailedException exception, Throwable cause) {
                // The handler below records a static failure code; no raw cause/message.
            }
        };
        var handler = new ConditionalRejectingErrorHandler(strategy) {
            @Override
            protected void log(Throwable failure) {
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .warn("[NOTIFICATION_LISTENER_FAILED] Message processing failed");
            }
        };
        // Fatal replays remain quarantined instead of the default x-death discard path,
        // which also prints the original message. DLQs have no automatic replay binding.
        handler.setDiscardFatalsWithXDeath(false);
        factory.setErrorHandler(handler);
        return factory;
    }

    public static final String EXCHANGE_NAME = "hotel.events";
    public static final String USER_EXCHANGE_NAME = "user.events";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "notification.dlx";

    // PAYMENT
    public static final String PAYMENT_QUEUE = "notification.payment.queue";
    public static final String PAYMENT_DLQ = "notification.payment.dlq";
    public static final String PAYMENT_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_DEAD_LETTER_ROUTING_KEY = "notification.payment.dead";

    // OTP
    public static final String OTP_QUEUE = "notification.otp.queue";
    public static final String OTP_DLQ = "notification.otp.dlq";
    public static final String OTP_ROUTING_KEY = "otp.verification";
    public static final String OTP_DEAD_LETTER_ROUTING_KEY = "notification.otp.dead";

    // ---------- QUEUES ----------
    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(PAYMENT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue otpQueue() {
        return QueueBuilder.durable(OTP_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(OTP_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    public Queue otpDeadLetterQueue() {
        return QueueBuilder.durable(OTP_DLQ).build();
    }

    // ---------- EXCHANGE ----------
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE_NAME);
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
    public Binding otpBinding(Queue otpQueue, TopicExchange userExchange) {
        return BindingBuilder
                .bind(otpQueue)
                .to(userExchange)
                .with(OTP_ROUTING_KEY);
    }

    @Bean
    public Binding paymentDeadLetterBinding(Queue paymentDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder
                .bind(paymentDeadLetterQueue)
                .to(deadLetterExchange)
                .with(PAYMENT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding otpDeadLetterBinding(Queue otpDeadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder
                .bind(otpDeadLetterQueue)
                .to(deadLetterExchange)
                .with(OTP_DEAD_LETTER_ROUTING_KEY);
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

