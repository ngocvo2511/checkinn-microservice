package com.example.mediaservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class SupabaseStorageConfig {

    @Bean
    public HttpClient httpClient(@org.springframework.beans.factory.annotation.Value("${supabase.connect-timeout-ms:2000}") long connectMillis) {
        if(connectMillis<=0)throw new IllegalArgumentException("Positive storage connection timeout required");
        return HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(connectMillis))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
