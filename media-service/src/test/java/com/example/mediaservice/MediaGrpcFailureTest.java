package com.example.mediaservice;

import com.example.mediaservice.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class MediaGrpcFailureTest {
    @Test void unavailableProviderDoesNotExposeItsBodyOrPrivatePath(CapturedOutput output) throws Exception {
        var storage=mock(SupabaseStorageService.class);
        String marker="synthetic-private-document-and-provider-marker";
        when(storage.createSignedUrl(anyString(),anyInt())).thenThrow(new java.io.IOException(marker));
        StreamObserver<GetSignedUrlResponse> observer=mock(StreamObserver.class);
        new MediaGrpcServiceImpl(storage).getSignedUrl(GetSignedUrlRequest.newBuilder().setFilePath(marker).build(),observer);
        var error=org.mockito.ArgumentCaptor.forClass(Throwable.class);verify(observer).onError(error.capture());
        assertThat(Status.fromThrowable(error.getValue()).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(error.getValue().getMessage()).doesNotContain(marker);assertThat(error.getValue().getCause()).isNull();
        assertThat(output.getAll()).doesNotContain(marker);
    }
    @Test void interruptionIsPreservedAndDoesNotBecomeAnInternalServerFailure() throws Exception {
        var storage=mock(SupabaseStorageService.class);when(storage.createSignedUrl(anyString(),anyInt())).thenThrow(new InterruptedException());
        StreamObserver<GetSignedUrlResponse> observer=mock(StreamObserver.class);
        try {
            new MediaGrpcServiceImpl(storage).getSignedUrl(GetSignedUrlRequest.newBuilder().setFilePath("identity.pdf").build(),observer);
            var error=org.mockito.ArgumentCaptor.forClass(Throwable.class);verify(observer).onError(error.capture());
            assertThat(Status.fromThrowable(error.getValue()).getCode()).isEqualTo(Status.Code.CANCELLED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }finally{Thread.interrupted();}
    }
}
