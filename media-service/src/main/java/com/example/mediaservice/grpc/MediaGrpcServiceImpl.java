package com.example.mediaservice.grpc;

import com.example.mediaservice.SupabaseStorageService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@GrpcService
@Slf4j
public class MediaGrpcServiceImpl extends com.example.mediaservice.grpc.MediaGrpcServiceGrpc.MediaGrpcServiceImplBase {

    private final SupabaseStorageService storageService;

    public MediaGrpcServiceImpl(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void uploadMedia(
            com.example.mediaservice.grpc.UploadMediaRequest request,
            StreamObserver<com.example.mediaservice.grpc.UploadMediaResponse> responseObserver
    ) {
        try {
            log.debug("[MEDIA_GRPC_UPLOAD]");
            String sanitizedFileName = sanitizeFileName(request.getFileName());
            String objectName = UUID.randomUUID() + "_" + sanitizedFileName;
            String url = storageService.uploadMedia(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            log.debug("[MEDIA_GRPC_UPLOAD]");
            responseObserver.onNext(
                    com.example.mediaservice.grpc.UploadMediaResponse.newBuilder()
                            .setUrl(url)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.debug("[MEDIA_GRPC_UPLOAD]");
            fail(e, responseObserver);
        }
    }

    @Override
    public void deleteMedia(
            com.example.mediaservice.grpc.DeleteMediaRequest request,
            StreamObserver<com.example.mediaservice.grpc.DeleteMediaResponse> responseObserver
    ) {
        try {
            log.debug("[MEDIA_GRPC_DELETE]");
            boolean deleted = storageService.deleteMedia(request.getFileName());

            log.debug("[MEDIA_GRPC_DELETE]");
            responseObserver.onNext(
                    com.example.mediaservice.grpc.DeleteMediaResponse.newBuilder()
                            .setSuccess(deleted)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.debug("[MEDIA_GRPC_DELETE]");
            fail(e, responseObserver);
        }
    }

    @Override
    public void uploadPrivateDocument(
            com.example.mediaservice.grpc.UploadDocumentRequest request,
            StreamObserver<com.example.mediaservice.grpc.UploadDocumentResponse> responseObserver
    ) {
        try {
            log.debug("[MEDIA_GRPC_DOC_UPLOAD]");
            String sanitizedFileName = sanitizeFileName(request.getFileName());
            String objectName = UUID.randomUUID() + "_" + sanitizedFileName;
            String filePath = storageService.uploadPrivateDocument(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            log.debug("[MEDIA_GRPC_DOC_UPLOAD]");
            responseObserver.onNext(
                    com.example.mediaservice.grpc.UploadDocumentResponse.newBuilder()
                            .setFilePath(filePath)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.debug("[MEDIA_GRPC_DOC_UPLOAD]");
            fail(e, responseObserver);
        }
    }

    @Override
    public void getSignedUrl(
            com.example.mediaservice.grpc.GetSignedUrlRequest request,
            StreamObserver<com.example.mediaservice.grpc.GetSignedUrlResponse> responseObserver
    ) {
        try {
            log.debug("[MEDIA_GRPC_SIGN]");
            String signedUrl = storageService.createSignedUrl(
                    request.getFilePath(),
                    request.getExpiresInSeconds()
            );

            log.debug("[MEDIA_GRPC_SIGN]");
            responseObserver.onNext(
                    com.example.mediaservice.grpc.GetSignedUrlResponse.newBuilder()
                            .setSignedUrl(signedUrl)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.debug("[MEDIA_GRPC_SIGN]");
            fail(e, responseObserver);
        }
    }

    @Override
    public void getSignedUrls(
            com.example.mediaservice.grpc.GetSignedUrlsRequest request,
            StreamObserver<com.example.mediaservice.grpc.GetSignedUrlsResponse> responseObserver
    ) {
        try {
            log.debug("[MEDIA_GRPC_SIGN_BATCH]");

            java.util.List<String> paths = request.getFilePathsList();

            java.util.Map<String, String> signedUrlsMap = storageService.createSignedUrls(
                    paths,
                    request.getExpiresInSeconds()
            );

            com.example.mediaservice.grpc.GetSignedUrlsResponse.Builder responseBuilder =
                    com.example.mediaservice.grpc.GetSignedUrlsResponse.newBuilder();

            responseBuilder.putAllSignedUrls(signedUrlsMap);

            for (java.util.Map.Entry<String, String> entry : signedUrlsMap.entrySet()) {
                responseBuilder.addItems(
                        com.example.mediaservice.grpc.SignedUrlItem.newBuilder()
                                .setFilePath(entry.getKey())
                                .setSignedUrl(entry.getValue())
                                .build()
                );
            }

            log.debug("[MEDIA_GRPC_SIGN_BATCH]");
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.debug("[MEDIA_GRPC_SIGN_BATCH]");
            fail(e, responseObserver);
        }
    }

    private void fail(Exception error,StreamObserver<?> observer) {
        Status status;
        if(error instanceof InterruptedException) {
            Thread.currentThread().interrupt();status=Status.CANCELLED.withDescription("Storage operation interrupted");
        } else if(error instanceof java.net.http.HttpTimeoutException) {
            status=Status.DEADLINE_EXCEEDED.withDescription("Storage operation timed out");
        } else if(error instanceof IllegalArgumentException) {
            status=Status.INVALID_ARGUMENT.withDescription("Invalid storage request");
        } else if(error instanceof java.io.IOException) {
            status=Status.UNAVAILABLE.withDescription("Storage service unavailable");
        } else status=Status.INTERNAL.withDescription("Unable to complete storage operation");
        log.warn("[MEDIA_OPERATION_FAILED] {}",status.getCode());
        observer.onError(status.asRuntimeException());
    }

    /**
     * Chuẩn hoá tên file:
     * - Bỏ dấu tiếng Việt
     * - Thay khoảng trắng và các ký tự đặc biệt bằng dấu gạch dưới '_'
     * - Giữ nguyên extension an toàn
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file";
        }

        // Bỏ dấu tiếng Việt (NFD + regex)
        String normalized = java.text.Normalizer.normalize(fileName, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'D');

        int lastDotIndex = normalized.lastIndexOf('.');
        String baseName;
        String extension = "";
        if (lastDotIndex > 0 && lastDotIndex < normalized.length() - 1) {
            baseName = normalized.substring(0, lastDotIndex);
            extension = normalized.substring(lastDotIndex).toLowerCase().replaceAll("[^a-z0-9.]", "");
        } else {
            baseName = normalized;
        }

        // Chỉ giữ lại chữ cái, số, gạch dưới, gạch ngang
        baseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        baseName = baseName.replaceAll("_+", "_");
        baseName = baseName.replaceAll("^_|_$", "");

        if (baseName.isBlank()) {
            baseName = "file";
        }

        return baseName + extension;
    }
}

