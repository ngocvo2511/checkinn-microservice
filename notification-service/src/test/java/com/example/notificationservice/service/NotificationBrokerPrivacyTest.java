package com.example.notificationservice.service;

import com.example.notificationservice.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ErrorHandler;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class NotificationBrokerPrivacyTest {
    private static final String PRIVATE = "synthetic-private-body-and-header";
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitMQConfig.class)
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new);

    private Message message() {
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("private-header", PRIVATE);
        return new Message(PRIVATE.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
    }

    @Test void exhaustedRetryRejectsWithoutLoggingPayloadOrException(CapturedOutput output) {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(MessageRecoverer.class);
            assertThatThrownBy(() -> ctx.getBean(MessageRecoverer.class).recover(message(), new RuntimeException(PRIVATE)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        });
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }

    @Test void conversionFailureRejectsWithoutLoggingPayloadOrException(CapturedOutput output) {
        context.run(ctx -> {
            var factory = ctx.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class);
            var container = factory.createListenerContainer();
            var handler = (ErrorHandler) ReflectionTestUtils.getField(container, "errorHandler");
            var failure = new ListenerExecutionFailedException(PRIVATE, new MessageConversionException(PRIVATE), message());
            assertThatThrownBy(() -> handler.handleError(failure)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        });
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }

    @Test void fatalReplayWithXDeathRejectsRatherThanDiscardingOrPrintingMessage(CapturedOutput output) {
        context.run(ctx -> {
            var container = ctx.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class)
                    .createListenerContainer();
            var handler = (ErrorHandler) ReflectionTestUtils.getField(container, "errorHandler");
            var message = message();
            message.getMessageProperties().setHeader("x-death", java.util.List.of(java.util.Map.of("reason", "rejected")));
            assertThatThrownBy(() -> handler.handleError(new ListenerExecutionFailedException(PRIVATE,
                    new MessageConversionException(PRIVATE), message)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        });
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }

    @Test void nonfatalFailureKeepsContainerRequeuePolicyWithoutPrintingMessage(CapturedOutput output) {
        context.run(ctx -> {
            var container = ctx.getBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class)
                    .createListenerContainer();
            var handler = (ErrorHandler) ReflectionTestUtils.getField(container, "errorHandler");
            assertThatCode(() -> handler.handleError(new ListenerExecutionFailedException(PRIVATE,
                    new IllegalStateException(PRIVATE), message()))).doesNotThrowAnyException();
        });
        assertThat(output.getAll()).doesNotContain(PRIVATE);
    }
}
