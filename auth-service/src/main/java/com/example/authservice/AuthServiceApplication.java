package com.example.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@EnableDiscoveryClient
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    /** Built from the Boot-managed builder so OTP calls carry the current trace (STD-030). */
    @Bean
    public RestTemplate restTemplate(org.springframework.boot.web.client.RestTemplateBuilder builder,
                                     @org.springframework.beans.factory.annotation.Value("${clients.connect-timeout-ms:3000}") int connectTimeout,
                                     @org.springframework.beans.factory.annotation.Value("${clients.read-timeout-ms:5000}") int readTimeout) {
        if(connectTimeout<=0 || readTimeout<=0)throw new IllegalArgumentException("Client timeouts must be positive");
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);factory.setReadTimeout(readTimeout);
        return builder.requestFactory(() -> factory).build();
    }

}
