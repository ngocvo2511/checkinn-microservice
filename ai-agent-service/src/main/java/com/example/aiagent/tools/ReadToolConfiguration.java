package com.example.aiagent.tools;

import com.example.aiagent.config.AgentProperties;
import java.net.URI;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
class ReadToolConfiguration {
    @Bean(destroyMethod = "close")
    ToolRegistry readToolRegistry(AgentProperties properties,
            @Value("${checkinn.read-tools.gateway-origin:http://127.0.0.1:8080}") URI gatewayOrigin,
            @Value("${checkinn.read-tools.connect-timeout:PT3S}") Duration connectTimeout) {
        return new ToolRegistry(properties.enabled(), gatewayOrigin, connectTimeout, properties.toolTimeout(), Clock.systemUTC());
    }
}
