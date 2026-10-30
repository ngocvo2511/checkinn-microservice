package com.example.bookingservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(@Value("${clients.connect-timeout-ms:3000}") int connectTimeout,
                                     @Value("${clients.read-timeout-ms:5000}") int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) throw new IllegalArgumentException("Client timeouts must be positive");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
