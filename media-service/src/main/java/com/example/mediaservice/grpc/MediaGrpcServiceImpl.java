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
            log.info("[MEDIA_GRPC_UPLOAD] Upload request received - fileName: {}, mimeType: {}, bytes: {}",
                request.getFileName(), request.getMimeType(), request.getFileData().size());
            String sanitizedFileName = sanitizeFileName(request.getFileName());
            String objectName = UUID.randomUUID() + "_" + sanitizedFileName;
            String url = storageService.uploadMedia(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            log.info("[MEDIA_GRPC_UPLOAD] Upload success - originalFileName: {}, objectName: {}", request.getFileName(), objectName);
            responseObserver.onNext(
                    com.example.mediaservice.grpc.UploadMediaResponse.newBuilder()
                            .setUrl(url)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[MEDIA_GRPC_UPLOAD] Upload failed - fileName: {}, error: {}", request.getFileName(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Upload media failed")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void deleteMedia(
            com.example.mediaservice.grpc.DeleteMediaRequest request,
            StreamObserver<com.example.mediaservice.grpc.DeleteMediaResponse> responseObserver
    ) {
        try {
            log.info("[MEDIA_GRPC_DELETE] Delete request received - fileName: {}", request.getFileName());
            boolean deleted = storageService.deleteMedia(request.getFileName());

            log.info("[MEDIA_GRPC_DELETE] Delete completed - fileName: {}, success: {}", request.getFileName(), deleted);
            responseObserver.onNext(
                    com.example.mediaservice.grpc.DeleteMediaResponse.newBuilder()
                            .setSuccess(deleted)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[MEDIA_GRPC_DELETE] Delete failed - fileName: {}, error: {}", request.getFileName(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Delete media failed")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void uploadPrivateDocument(
            com.example.mediaservice.grpc.UploadDocumentRequest request,
            StreamObserver<com.example.mediaservice.grpc.UploadDocumentResponse> responseObserver
    ) {
        try {
            log.info("[MEDIA_GRPC_DOC_UPLOAD] Private document upload received - fileName: {}, mimeType: {}, bytes: {}",
                    request.getFileName(), request.getMimeType(), request.getFileData().size());
            String sanitizedFileName = sanitizeFileName(request.getFileName());
            String objectName = UUID.randomUUID() + "_" + sanitizedFileName;
            String filePath = storageService.uploadPrivateDocument(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            log.info("[MEDIA_GRPC_DOC_UPLOAD] Private document uploaded - originalFileName: {}, filePath: {}", request.getFileName(), filePath);
            responseObserver.onNext(
                    com.example.mediaservice.grpc.UploadDocumentResponse.newBuilder()
                            .setFilePath(filePath)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[MEDIA_GRPC_DOC_UPLOAD] Document upload failed - fileName: {}, error: {}", request.getFileName(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Upload private document failed: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void getSignedUrl(
            com.example.mediaservice.grpc.GetSignedUrlRequest request,
            StreamObserver<com.example.mediaservice.grpc.GetSignedUrlResponse> responseObserver
    ) {
        try {
            log.info("[MEDIA_GRPC_SIGN] Get signed url request received - filePath: {}, expiresIn: {}",
                    request.getFilePath(), request.getExpiresInSeconds());
            String signedUrl = storageService.createSignedUrl(
                    request.getFilePath(),
                    request.getExpiresInSeconds()
            );

            log.info("[MEDIA_GRPC_SIGN] Signed url generated for filePath: {}", request.getFilePath());
            responseObserver.onNext(
                    com.example.mediaservice.grpc.GetSignedUrlResponse.newBuilder()
                            .setSignedUrl(signedUrl)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[MEDIA_GRPC_SIGN] Get signed url failed - filePath: {}, error: {}", request.getFilePath(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Get signed url failed: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException()
            );
        }
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

