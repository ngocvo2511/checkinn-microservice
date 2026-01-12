package com.example.mediaservice.grpc;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.minio.*;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;

@GrpcService
public class MediaGrpcServiceImpl extends MediaGrpcServiceGrpc.MediaGrpcServiceImplBase {

    private static final String BUCKET = "checkinn_media_storage";
    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Override
    public void uploadMedia(
            UploadMediaRequest request,
            StreamObserver<UploadMediaResponse> responseObserver
    ) {
        try {
            String objectName = request.getFileName();

            BlobId blobId = BlobId.of(BUCKET, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(request.getMimeType())
                    .build();

            storage.create(blobInfo, request.getFileData().toByteArray());

            String url = "https://storage.googleapis.com/" + BUCKET + "/" + objectName;

            responseObserver.onNext(
                    UploadMediaResponse.newBuilder()
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
            DeleteMediaRequest request,
            StreamObserver<DeleteMediaResponse> responseObserver
    ) {
        try {
            boolean deleted = storage.delete(BUCKET, request.getFileName());

            responseObserver.onNext(
                    DeleteMediaResponse.newBuilder()
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

