package com.example.mediaservice.grpc;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@GrpcService
public class MediaGrpcServiceImpl extends com.example.mediaservice.grpc.MediaGrpcServiceGrpc.MediaGrpcServiceImplBase {

    private final StorageClient storageClient;

    public MediaGrpcServiceImpl(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    @Override
    public void uploadMedia(
            com.example.mediaservice.grpc.UploadMediaRequest request,
            StreamObserver<com.example.mediaservice.grpc.UploadMediaResponse> responseObserver
    ) {
        try {
            String objectName = UUID.randomUUID() + "_" + request.getFileName();
            Bucket bucket = storageClient.bucket();

            Blob blob = bucket.create(
                    objectName,
                    request.getFileData().toByteArray(),
                    request.getMimeType()
            );

            String encodedObjectName = URLEncoder.encode(blob.getName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String url = "https://firebasestorage.googleapis.com/v0/b/"
                    + bucket.getName()
                    + "/o/"
                    + encodedObjectName
                    + "?alt=media";

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
            Bucket bucket = storageClient.bucket();
            Blob blob = bucket.get(request.getFileName());
            boolean deleted = blob != null && blob.delete();

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

