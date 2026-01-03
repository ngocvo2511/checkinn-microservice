package com.example.apigateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Configuration
public class GatewayCorsConfig {

    @Bean
    public GlobalFilter corsPreflightFilter() {
        return (exchange, chain) -> {
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }
}
