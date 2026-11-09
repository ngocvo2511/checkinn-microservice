package com.example.hotelservice.security;

import com.example.hotelservice.MediaAsset.service.MediaClient;
import com.example.mediaservice.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaClientContractTest {
    @ParameterizedTest @ValueSource(strings={"UNAVAILABLE","DEADLINE_EXCEEDED","UNAUTHENTICATED","PERMISSION_DENIED","INVALID_ARGUMENT","INTERNAL","NOT_FOUND"})
    void everyMediaOperationKeepsSafeUpstreamClassificationOverActualGrpc(String code) throws Exception {
        String marker="synthetic-private-upstream-error";Status error=Status.fromCode(Status.Code.valueOf(code)).withDescription(marker);
        var service=new MediaGrpcServiceGrpc.MediaGrpcServiceImplBase(){
            @Override public void uploadMedia(UploadMediaRequest r,StreamObserver<UploadMediaResponse> o){o.onError(error.asRuntimeException());}
            @Override public void deleteMedia(DeleteMediaRequest r,StreamObserver<DeleteMediaResponse> o){o.onError(error.asRuntimeException());}
            @Override public void uploadPrivateDocument(UploadDocumentRequest r,StreamObserver<UploadDocumentResponse> o){o.onError(error.asRuntimeException());}
            @Override public void getSignedUrl(GetSignedUrlRequest r,StreamObserver<GetSignedUrlResponse> o){o.onError(error.asRuntimeException());}
            @Override public void getSignedUrls(GetSignedUrlsRequest r,StreamObserver<GetSignedUrlsResponse> o){o.onError(error.asRuntimeException());}
        };
        var server=io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1",0)).addService(service).build().start();
        var channel=ManagedChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().build();
        try {
            var tokens=mock(HotelServiceTokenProvider.class);when(tokens.authorization()).thenReturn("Bearer synthetic-local-token");
            var client=new MediaClient(tokens);org.springframework.test.util.ReflectionTestUtils.setField(client,"mediaStub",MediaGrpcServiceGrpc.newBlockingStub(channel));
            int dependencyStatus=switch(code){case "DEADLINE_EXCEEDED"->504;case "INVALID_ARGUMENT","INTERNAL","NOT_FOUND"->502;default->503;};
            var operations=List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                ()->client.upload("image.png","image/png",new byte[]{1}),()->client.deleteByFileName("image.png"),
                ()->client.uploadPrivateDocument("identity.pdf","application/pdf",new byte[]{1}),
                ()->client.getSignedUrl("identity.pdf",300),()->client.getSignedUrls(List.of("identity.pdf"),300));
            for(int index=0;index<operations.size();index++) {
                int expected=code.equals("INVALID_ARGUMENT")&&(index==0||index==2)?400:dependencyStatus;
                assertThatThrownBy(operations.get(index)).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                    failure->{assertThat(failure.getStatusCode().value()).isEqualTo(expected);assertThat(failure.getMessage()).doesNotContain(marker);assertThat(failure.getCause()).isNull();});
            }
        }finally{channel.shutdownNow().awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);server.shutdownNow().awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
    }
}
