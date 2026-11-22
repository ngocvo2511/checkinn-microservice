package com.example.revenueservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    /** Built from the Boot-managed builder so Hotel/Regulations calls carry the current trace (STD-030). */
    @Bean
    public RestTemplate restTemplate(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        return builder.requestFactory(() -> factory).build();
    }
}
