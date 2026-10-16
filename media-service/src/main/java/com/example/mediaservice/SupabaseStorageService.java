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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class SupabaseStorageService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String storageBaseUrl;
    private final String bucket;
    private final String privateBucket;
    private final String key;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.bucket}") String bucket,
            @Value("${supabase.private-bucket:hotel-documents}") String privateBucket,
            @Value("${supabase.service-role-key}") String key
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.storageBaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.bucket = bucket;
        this.privateBucket = privateBucket;
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

    public String uploadPrivateDocument(byte[] fileData, String objectName, String mimeType) throws IOException, InterruptedException {
        String encodedObjectName = encodePath(objectName);
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", storageBaseUrl, privateBucket, encodedObjectName);
        log.info("[SUPABASE_STORAGE_DOC_UPLOAD] Upload request prepared - bucket: {}, objectName: {}, mimeType: {}, bytes: {}, url: {}",
                privateBucket, objectName, mimeType, fileData != null ? fileData.length : 0, uploadUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .header("Content-Type", mimeType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[SUPABASE_STORAGE_DOC_UPLOAD] Upload failed - bucket: {}, objectName: {}, status: {}, body: {}",
                    privateBucket, objectName, response.statusCode(), response.body());
            throw new IOException("Supabase private document upload failed: " + response.statusCode() + " - " + response.body());
        }

        log.info("[SUPABASE_STORAGE_DOC_UPLOAD] Upload success - bucket: {}, objectName: {}", privateBucket, objectName);
        return objectName;
    }

    public String createSignedUrl(String objectName, int expiresInSeconds) throws IOException, InterruptedException {
        String encodedObjectName = encodePath(objectName);
        String signUrl = String.format("%s/storage/v1/object/sign/%s/%s", storageBaseUrl, privateBucket, encodedObjectName);
        String requestBody = String.format("{\"expiresIn\":%d}", expiresInSeconds > 0 ? expiresInSeconds : 300);

        log.info("[SUPABASE_STORAGE_SIGN] Sign request prepared - bucket: {}, objectName: {}, expiresIn: {}, url: {}",
                privateBucket, objectName, expiresInSeconds, signUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(signUrl))
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[SUPABASE_STORAGE_SIGN] Sign failed - bucket: {}, objectName: {}, status: {}, body: {}",
                    privateBucket, objectName, response.statusCode(), response.body());
            throw new IOException("Supabase sign url failed: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String signedUrlPart = root.hasNonNull("signedURL") ? root.get("signedURL").asText() :
                root.hasNonNull("signedUrl") ? root.get("signedUrl").asText() : null;

        if (signedUrlPart == null || signedUrlPart.isBlank()) {
            throw new IOException("Supabase returned empty signedURL: " + response.body());
        }

        return formatSignedUrl(signedUrlPart);
    }

    public Map<String, String> createSignedUrls(List<String> objectNames, int expiresInSeconds) throws IOException, InterruptedException {
        if (objectNames == null || objectNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> validNames = objectNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (validNames.isEmpty()) {
            return Collections.emptyMap();
        }

        String signBatchUrl = String.format("%s/storage/v1/object/sign/%s", storageBaseUrl, privateBucket);
        int expiry = expiresInSeconds > 0 ? expiresInSeconds : 300;

        Map<String, Object> reqBodyMap = new HashMap<>();
        reqBodyMap.put("expiresIn", expiry);
        reqBodyMap.put("paths", validNames);
        String requestBody = objectMapper.writeValueAsString(reqBodyMap);

        log.info("[SUPABASE_STORAGE_SIGN_BATCH] Sign batch request prepared - bucket: {}, fileCount: {}, expiresIn: {}, url: {}",
                privateBucket, validNames.size(), expiry, signBatchUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(signBatchUrl))
                .header("Authorization", "Bearer " + key)
                .header("apikey", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                Map<String, String> resultMap = new HashMap<>();
                for (JsonNode item : root) {
                    String path = item.hasNonNull("path") ? item.get("path").asText() : null;
                    String error = item.hasNonNull("error") ? item.get("error").asText() : null;
                    String signedUrlPart = item.hasNonNull("signedURL") ? item.get("signedURL").asText() :
                            item.hasNonNull("signedUrl") ? item.get("signedUrl").asText() : null;

                    if (path != null) {
                        if (error != null && !error.isBlank()) {
                            log.warn("[SUPABASE_STORAGE_SIGN_BATCH] Error for path {}: {}", path, error);
                        } else if (signedUrlPart != null && !signedUrlPart.isBlank()) {
                            resultMap.put(path, formatSignedUrl(signedUrlPart));
                        }
                    }
                }
                log.info("[SUPABASE_STORAGE_SIGN_BATCH] Sign batch success - requested: {}, signed: {}", validNames.size(), resultMap.size());
                return resultMap;
            }
        }

        log.warn("[SUPABASE_STORAGE_SIGN_BATCH] Batch sign returned status {}, falling back to individual calls: {}",
                response.statusCode(), response.body());

        Map<String, String> fallbackMap = new HashMap<>();
        for (String name : validNames) {
            try {
                String signed = createSignedUrl(name, expiry);
                if (signed != null) {
                    fallbackMap.put(name, signed);
                }
            } catch (Exception e) {
                log.warn("[SUPABASE_STORAGE_SIGN_BATCH] Fallback sign failed for {}: {}", name, e.getMessage());
            }
        }
        return fallbackMap;
    }

    private String formatSignedUrl(String signedUrlPart) {
        if (signedUrlPart == null || signedUrlPart.isBlank()) {
            return null;
        }

        if (signedUrlPart.startsWith("http://") || signedUrlPart.startsWith("https://")) {
            return signedUrlPart;
        }

        if (signedUrlPart.startsWith("/storage/v1")) {
            return storageBaseUrl + signedUrlPart;
        }

        return storageBaseUrl + "/storage/v1/" + signedUrlPart;
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                result.append("/");
            }
            result.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return result.toString();
    }
}

