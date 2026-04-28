package com.example.mediaservice.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class FirebaseStorageConfig {
    @Value("${firebase.credentials}")
    private Resource credentials;

    @Value("hotel-booking-f6cb8.firebasestorage.app")
    private String storageBucket;

    @Bean
    public StorageClient storageClient() throws IOException {
        GoogleCredentials googleCredentials = GoogleCredentials.fromStream(credentials.getInputStream());
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(googleCredentials)
                .setStorageBucket(storageBucket)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        return StorageClient.getInstance();
    }
}
