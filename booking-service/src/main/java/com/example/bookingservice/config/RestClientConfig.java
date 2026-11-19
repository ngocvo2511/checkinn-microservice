package com.example.bookingservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientConfig {

    /** Built from the Boot-managed builder so client observations propagate the current trace (traceparent). */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${clients.connect-timeout-ms:3000}") int connectTimeout,
                                     @Value("${clients.read-timeout-ms:5000}") int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) throw new IllegalArgumentException("Client timeouts must be positive");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder.requestFactory(() -> factory).build();
    }
}
