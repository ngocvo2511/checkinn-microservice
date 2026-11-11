package com.example.hotelservice.MediaAsset.service;

import com.example.mediaservice.grpc.*;
import com.google.protobuf.ByteString;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


@Component
public class MediaClient {
    private final com.example.hotelservice.security.HotelServiceTokenProvider tokens;
    public MediaClient(com.example.hotelservice.security.HotelServiceTokenProvider tokens) { this.tokens = tokens; }
    private MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub authorizedStub() {
        io.grpc.Metadata headers = new io.grpc.Metadata();
        headers.put(io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER), tokens.authorization());
        return mediaStub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS);
    }


    @GrpcClient("media-service")
    private MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub mediaStub;

    public String upload(String fileName, String mimeType, byte[] data) {

        try{
            UploadMediaRequest request = UploadMediaRequest.newBuilder()
                    .setFileName(fileName)
                    .setMimeType(mimeType)
                    .setFileData(ByteString.copyFrom(data))
                    .build();

            UploadMediaResponse response = authorizedStub().uploadMedia(request);
            return response.getUrl();
        }
        catch (Exception e) {
            throw dependencyError(e,true);
        }

    }

    public boolean deleteByFileName(String fileName) {
        try {
            DeleteMediaRequest request = DeleteMediaRequest.newBuilder()
                    .setFileName(fileName)
                    .build();

            DeleteMediaResponse response = authorizedStub().deleteMedia(request);
            return response.getSuccess();
        } catch (Exception e) {
            throw dependencyError(e);
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

            UploadDocumentResponse response = authorizedStub().uploadPrivateDocument(request);
            return response.getFilePath();
        } catch (Exception e) {
            throw dependencyError(e,true);
        }
    }

    public String getSignedUrl(String filePath, int expiresInSeconds) {
        try {
            GetSignedUrlRequest request = GetSignedUrlRequest.newBuilder()
                    .setFilePath(filePath)
                    .setExpiresInSeconds(expiresInSeconds)
                    .build();

            GetSignedUrlResponse response = authorizedStub().getSignedUrl(request);
            return response.getSignedUrl();
        } catch (Exception e) {
            throw dependencyError(e);
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

            GetSignedUrlsResponse response = authorizedStub().getSignedUrls(request);
            return response.getSignedUrlsMap() != null
                    ? new java.util.HashMap<>(response.getSignedUrlsMap())
                    : java.util.Collections.emptyMap();
        } catch (Exception e) {
            throw dependencyError(e);
        }
    }
    private org.springframework.web.server.ResponseStatusException dependencyError(Exception error) {
        return dependencyError(error,false);
    }
    private org.springframework.web.server.ResponseStatusException dependencyError(Exception error,boolean userInput) {
        var code=io.grpc.Status.fromThrowable(error).getCode();
        var status=switch(code) {
            case INVALID_ARGUMENT -> userInput?org.springframework.http.HttpStatus.BAD_REQUEST:org.springframework.http.HttpStatus.BAD_GATEWAY;
            case DEADLINE_EXCEEDED -> org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE,UNAUTHENTICATED,PERMISSION_DENIED,CANCELLED -> org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
            default -> org.springframework.http.HttpStatus.BAD_GATEWAY;
        };
        return new org.springframework.web.server.ResponseStatusException(status,"Storage operation unavailable");
    }

}

