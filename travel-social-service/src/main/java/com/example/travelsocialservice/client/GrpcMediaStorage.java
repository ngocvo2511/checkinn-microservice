package com.example.travelsocialservice.client;

import com.example.mediaservice.grpc.DeleteMediaRequest;
import com.example.mediaservice.grpc.MediaGrpcServiceGrpc;
import com.example.mediaservice.grpc.UploadMediaRequest;
import com.example.mediaservice.grpc.UploadMediaResponse;
import com.example.travelsocialservice.security.ServiceTokenProvider;
import com.google.protobuf.ByteString;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GrpcMediaStorage implements MediaStorage {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ServiceTokenProvider tokens;

    @GrpcClient("media-service")
    private MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub mediaStub;

    public GrpcMediaStorage(ServiceTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public StoredObject upload(String fileName, String mimeType, byte[] data) {
        UploadMediaResponse response;
        try {
            response = authorizedStub().uploadMedia(UploadMediaRequest.newBuilder()
                    .setFileName(fileName).setMimeType(mimeType).setFileData(ByteString.copyFrom(data)).build());
        } catch (RuntimeException ex) {
            throw new MediaStorageException("Upload to media-service failed", ex);
        }
        if (response.getObjectName().isBlank()) {
            // An older media-service does not return the object name, so the upload could never be deleted.
            throw new MediaStorageException("media-service did not return an object name", null);
        }
        return new StoredObject(response.getUrl(), response.getObjectName());
    }

    @Override
    public void delete(String objectName) {
        try {
            authorizedStub().deleteMedia(DeleteMediaRequest.newBuilder().setFileName(objectName).build());
        } catch (RuntimeException ex) {
            throw new MediaStorageException("Delete on media-service failed", ex);
        }
    }

    private MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub authorizedStub() {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, tokens.authorization());
        return mediaStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(15, TimeUnit.SECONDS);
    }
}
