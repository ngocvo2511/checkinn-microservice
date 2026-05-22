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
            String objectName = UUID.randomUUID() + "_" + request.getFileName();
            String url = storageService.uploadMedia(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            log.info("[MEDIA_GRPC_UPLOAD] Upload success - fileName: {}, objectName: {}", request.getFileName(), objectName);
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
}

