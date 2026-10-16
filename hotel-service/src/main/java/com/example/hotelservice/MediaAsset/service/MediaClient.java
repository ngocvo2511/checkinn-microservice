package com.example.hotelservice.MediaAsset.service;

import com.example.mediaservice.grpc.*;
import com.google.protobuf.ByteString;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


@Component
public class MediaClient {

    @GrpcClient("media-service")
    private MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub mediaStub;

    public String upload(String fileName, String mimeType, byte[] data) {

        try{
            UploadMediaRequest request = UploadMediaRequest.newBuilder()
                    .setFileName(fileName)
                    .setMimeType(mimeType)
                    .setFileData(ByteString.copyFrom(data))
                    .build();

            UploadMediaResponse response = mediaStub.uploadMedia(request);
            return response.getUrl();
        }
        catch (Exception e) {
            throw new RuntimeException("Upload media to media-service failed", e);
        }

    }

    public boolean deleteByFileName(String fileName) {
        try {
            DeleteMediaRequest request = DeleteMediaRequest.newBuilder()
                    .setFileName(fileName)
                    .build();

            DeleteMediaResponse response = mediaStub.deleteMedia(request);
            return response.getSuccess();
        } catch (Exception e) {
            throw new RuntimeException("Delete media on media-service failed", e);
        }
    }

    public boolean deleteByUrl(String url) {
        // vì media-service delete theo file_name, mà hotel-service lưu URL
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        return deleteByFileName(fileName);
    }

    public String uploadPrivateDocument(String fileName, String mimeType, byte[] data) {
        try {
            UploadDocumentRequest request = UploadDocumentRequest.newBuilder()
                    .setFileName(fileName)
                    .setMimeType(mimeType)
                    .setFileData(ByteString.copyFrom(data))
                    .build();

            UploadDocumentResponse response = mediaStub.uploadPrivateDocument(request);
            return response.getFilePath();
        } catch (Exception e) {
            throw new RuntimeException("Upload private document to media-service failed", e);
        }
    }

    public String getSignedUrl(String filePath, int expiresInSeconds) {
        try {
            GetSignedUrlRequest request = GetSignedUrlRequest.newBuilder()
                    .setFilePath(filePath)
                    .setExpiresInSeconds(expiresInSeconds)
                    .build();

            GetSignedUrlResponse response = mediaStub.getSignedUrl(request);
            return response.getSignedUrl();
        } catch (Exception e) {
            throw new RuntimeException("Get signed url from media-service failed", e);
        }
    }

    public java.util.Map<String, String> getSignedUrls(java.util.List<String> filePaths, int expiresInSeconds) {
        if (filePaths == null || filePaths.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        java.util.List<String> validPaths = filePaths.stream()
                .filter(p -> p != null && !p.isBlank())
                .toList();

        if (validPaths.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        try {
            GetSignedUrlsRequest request = GetSignedUrlsRequest.newBuilder()
                    .addAllFilePaths(validPaths)
                    .setExpiresInSeconds(expiresInSeconds)
                    .build();

            GetSignedUrlsResponse response = mediaStub.getSignedUrls(request);
            return response.getSignedUrlsMap() != null
                    ? new java.util.HashMap<>(response.getSignedUrlsMap())
                    : java.util.Collections.emptyMap();
        } catch (Exception e) {
            throw new RuntimeException("Get signed urls batch from media-service failed", e);
        }
    }
}

