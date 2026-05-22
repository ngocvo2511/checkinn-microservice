package com.example.mediaservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageService {

    private final HttpClient httpClient;
    private final String storageBaseUrl;
    private final String bucket;
    private final String key;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.bucket}") String bucket,
            @Value("${supabase.service-role-key}") String key
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.storageBaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.bucket = bucket;
        this.key = key;
    }

    public String uploadMedia(byte[] fileData, String objectName, String mimeType) throws IOException, InterruptedException {
        String encodedObjectName = encodePath(objectName);
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", storageBaseUrl, bucket, encodedObjectName);
        log.info("[SUPABASE_STORAGE_UPLOAD] Upload request prepared - bucket: {}, objectName: {}, mimeType: {}, bytes: {}, url: {}",
                bucket, objectName, mimeType, fileData != null ? fileData.length : 0, uploadUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .header("Content-Type", mimeType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[SUPABASE_STORAGE_UPLOAD] Upload failed - bucket: {}, objectName: {}, status: {}, body: {}",
                    bucket, objectName, response.statusCode(), response.body());
            throw new IOException("Supabase upload failed: " + response.statusCode() + " - " + response.body());
        }

        log.info("[SUPABASE_STORAGE_UPLOAD] Upload success - bucket: {}, objectName: {}", bucket, objectName);

        return String.format("%s/storage/v1/object/public/%s/%s", storageBaseUrl, bucket, encodedObjectName);
    }

    public boolean deleteMedia(String objectName) throws IOException, InterruptedException {
        String encodedObjectName = encodePath(objectName);
        String deleteUrl = String.format("%s/storage/v1/object/%s/%s", storageBaseUrl, bucket, encodedObjectName);

        log.info("[SUPABASE_STORAGE_DELETE] Delete request prepared - bucket: {}, objectName: {}, url: {}", bucket, objectName, deleteUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deleteUrl))
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean deleted = response.statusCode() == 200 || response.statusCode() == 204;
        if (deleted) {
            log.info("[SUPABASE_STORAGE_DELETE] Delete success - bucket: {}, objectName: {}, status: {}", bucket, objectName, response.statusCode());
        } else {
            log.warn("[SUPABASE_STORAGE_DELETE] Delete returned non-success status - bucket: {}, objectName: {}, status: {}, body: {}",
                    bucket, objectName, response.statusCode(), response.body());
        }
        return deleted;
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                result.append("/");
            }
            result.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
        }
        return result.toString();
    }
}

