package com.example.mediaservice.grpc;

import com.example.mediaservice.SupabaseStorageService;
import com.example.mediaservice.security.MediaCaller;
import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Storage folder scoping per verified caller. */
class MediaGrpcServiceImplScopeTest {

    private SupabaseStorageService storage;
    private MediaGrpcServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(SupabaseStorageService.class);
        when(storage.uploadMedia(any(), anyString(), anyString())).thenAnswer(inv -> "https://storage.invalid/public/" + inv.getArgument(1));
        when(storage.deleteMedia(anyString())).thenReturn(true);
        service = new MediaGrpcServiceImpl(storage);
    }

    private static void as(MediaCaller caller, Runnable action) {
        Context.current().withValue(MediaCaller.CONTEXT_KEY, caller).run(action);
    }

    private UploadMediaResponse upload(MediaCaller caller, String fileName) {
        @SuppressWarnings("unchecked")
        StreamObserver<UploadMediaResponse> observer = mock(StreamObserver.class);
        as(caller, () -> service.uploadMedia(UploadMediaRequest.newBuilder().setFileName(fileName)
                .setMimeType("image/jpeg").setFileData(ByteString.copyFromUtf8("x")).build(), observer));
        ArgumentCaptor<UploadMediaResponse> response = ArgumentCaptor.forClass(UploadMediaResponse.class);
        verify(observer).onNext(response.capture());
        return response.getValue();
    }

    @Test
    void socialUploadsAreStoredUnderSocialFolderEvenIfNameTriesToEscape() throws Exception {
        UploadMediaResponse response = upload(MediaCaller.TRAVEL_SOCIAL_SERVICE, "../hotels/evil photo.JPG");

        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(storage).uploadMedia(any(), objectName.capture(), eq("image/jpeg"));
        assertThat(objectName.getValue()).startsWith("social/").doesNotContain("..").endsWith(".jpg");
        assertThat(objectName.getValue().substring("social/".length())).doesNotContain("/");
        assertThat(response.getObjectName()).isEqualTo(objectName.getValue());
        assertThat(MediaGrpcServiceImpl.isSocialObjectName(response.getObjectName())).isTrue();
    }

    @Test
    void hotelUploadsKeepTheirExistingRootLevelNames() throws Exception {
        UploadMediaResponse response = upload(MediaCaller.HOTEL_SERVICE, "room.png");

        assertThat(response.getObjectName()).doesNotContain("/").endsWith("_room.png");
        assertThat(response.getUrl()).endsWith(response.getObjectName());
    }

    @Test
    void socialDeletesOutsideItsFolderAreDenied() throws Exception {
        for (String name : new String[]{"abc_room.png", "social/", "social/../abc.png", "social/a/b.png", "social/%2e%2e", "/social/x.png"}) {
            @SuppressWarnings("unchecked")
            StreamObserver<DeleteMediaResponse> observer = mock(StreamObserver.class);
            as(MediaCaller.TRAVEL_SOCIAL_SERVICE, () -> service.deleteMedia(DeleteMediaRequest.newBuilder().setFileName(name).build(), observer));

            ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
            verify(observer).onError(error.capture());
            assertThat(((StatusRuntimeException) error.getValue()).getStatus().getCode()).as(name).isEqualTo(Status.Code.PERMISSION_DENIED);
        }
        verify(storage, never()).deleteMedia(anyString());
    }

    @Test
    void socialDeleteInsideItsFolderProceeds() throws Exception {
        @SuppressWarnings("unchecked")
        StreamObserver<DeleteMediaResponse> observer = mock(StreamObserver.class);
        as(MediaCaller.TRAVEL_SOCIAL_SERVICE, () -> service.deleteMedia(DeleteMediaRequest.newBuilder().setFileName("social/abc_photo.jpg").build(), observer));

        verify(storage).deleteMedia("social/abc_photo.jpg");
        verify(observer).onCompleted();
    }

    @Test
    void hotelDeletesAreUnchanged() throws Exception {
        @SuppressWarnings("unchecked")
        StreamObserver<DeleteMediaResponse> observer = mock(StreamObserver.class);
        as(MediaCaller.HOTEL_SERVICE, () -> service.deleteMedia(DeleteMediaRequest.newBuilder().setFileName("abc_room.png").build(), observer));

        verify(storage).deleteMedia("abc_room.png");
        verify(observer).onCompleted();
    }
}
