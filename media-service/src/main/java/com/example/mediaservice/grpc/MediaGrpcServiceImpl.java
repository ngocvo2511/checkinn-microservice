package com.example.mediaservice.grpc;

import com.example.mediaservice.SupabaseStorageService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
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
            String objectName = UUID.randomUUID() + "_" + request.getFileName();
            String url = storageService.uploadMedia(
                    request.getFileData().toByteArray(),
                    objectName,
                    request.getMimeType()
            );

            responseObserver.onNext(
                    com.example.mediaservice.grpc.UploadMediaResponse.newBuilder()
                            .setUrl(url)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            e.printStackTrace();
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
            boolean deleted = storageService.deleteMedia(request.getFileName());

            responseObserver.onNext(
                    com.example.mediaservice.grpc.DeleteMediaResponse.newBuilder()
                            .setSuccess(deleted)
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Delete media failed")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }
}

