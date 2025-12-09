package com.example.hotelservice.MediaAsset.service;

import com.example.mediaservice.grpc.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


@Component
@RequiredArgsConstructor
public class MediaClient {

    private final MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub mediaStub;

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
}

