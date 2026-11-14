package com.example.bookingservice.contract;

import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UuidWebConfiguration implements WebMvcConfigurer {
    @Override public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, UUID.class, UuidContract::required);
    }
}
