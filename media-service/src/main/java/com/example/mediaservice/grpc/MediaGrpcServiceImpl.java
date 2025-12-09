package com.example.mediaservice.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.minio.*;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;

@GrpcService
public class MediaGrpcServiceImpl extends MediaGrpcServiceGrpc.MediaGrpcServiceImplBase {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String defaultBucketName;

    @Value("${minio.url}")
    private String minioUrl;

    @Override
    public void uploadMedia(UploadMediaRequest request, StreamObserver<UploadMediaResponse> responseObserver) {
        try {
            // 1. Lấy dữ liệu từ request
            byte[] data = request.getFileData().toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            String filename = request.getFileName();
            String finalFilename = java.util.UUID.randomUUID() + "_" + filename;

            String contentType = request.getMimeType();

            // 2. Kiểm tra bucket tồn tại chưa, nếu chưa thì tạo (Optional)
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucketName).build());
            }

            // 3. Upload lên MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucketName)
                            .object(finalFilename)
                            .stream(inputStream, data.length, -1)
                            .contentType(contentType)
                            .build()
            );

            // 4. Tạo URL trả về
            String imageUrl = minioUrl + "/" + defaultBucketName + "/" + finalFilename;

            // 5. Trả về response cho Hotel Service
            UploadMediaResponse response = UploadMediaResponse.newBuilder()
                    .setUrl(imageUrl)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            // Xử lý lỗi và trả về gRPC Error
            e.printStackTrace();
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Upload failed: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void deleteMedia(DeleteMediaRequest request, StreamObserver<DeleteMediaResponse> responseObserver) {
        try {
            String objectName = request.getFileName();

            // Thực hiện xóa trên MinIO
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(defaultBucketName)
                            .object(objectName)
                            .build()
            );

            // Trả về kết quả thành công
            DeleteMediaResponse response = DeleteMediaResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            e.printStackTrace();
            // Trả về lỗi nếu có sự cố kết nối hoặc permission
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Delete failed: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }
}
