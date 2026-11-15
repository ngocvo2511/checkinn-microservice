package com.example.mediaservice.security;

import io.grpc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.jwt.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaAuthenticationInterceptorTest {
    final JwtDecoder decoder=mock(JwtDecoder.class);
    final ServerCall<String,String> call=mock(ServerCall.class);
    final ServerCallHandler<String,String> next=mock(ServerCallHandler.class);
    final Metadata headers=new Metadata();
    @BeforeEach void setup() {
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<String,String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("media.MediaGrpcService/DeleteMedia")
            .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class)).setResponseMarshaller(mock(MethodDescriptor.Marshaller.class)).build());
    }
    void bearer(String subject,String role) {
        headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer test");
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","HS256").subject(subject).claim("role",role).build());
    }
    @Test void anonymousCannotDeleteOrSignPrivateFiles() {
        new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(call).close(argThat(s->s.getCode()==Status.Code.UNAUTHENTICATED),any()); verifyNoInteractions(next);
    }
    @Test void adminIsNotStorageService() {
        bearer("user","ADMIN"); new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(call).close(argThat(s->s.getCode()==Status.Code.PERMISSION_DENIED),any()); verifyNoInteractions(next);
    }
    @Test void forgedServiceRoleWithoutSubjectIsDenied() {
        bearer("user","HOTEL_SERVICE"); new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(call).close(argThat(s->s.getCode()==Status.Code.PERMISSION_DENIED),any()); verifyNoInteractions(next);
    }
    @Test void invalidOrExpiredSignatureIsDenied() {
        bearer("hotel-service","HOTEL_SERVICE"); when(decoder.decode("test")).thenThrow(new JwtException("invalid"));
        new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(call).close(argThat(s->s.getCode()==Status.Code.UNAUTHENTICATED),any()); verifyNoInteractions(next);
    }
    void method(String bareName) {
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<String,String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("media.MediaGrpcService/"+bareName)
            .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class)).setResponseMarshaller(mock(MethodDescriptor.Marshaller.class)).build());
    }
    MediaCaller proceedAndCaptureCaller() {
        MediaCaller[] seen = new MediaCaller[1];
        when(next.startCall(any(),any())).thenAnswer(inv -> { seen[0] = MediaCaller.current(); return new ServerCall.Listener<String>() {}; });
        new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(call,never()).close(any(),any());
        return seen[0];
    }
    @Test void travelSocialServiceCanUploadAndDeleteWithItsIdentityInContext() {
        bearer("travel-social-service","TRAVEL_SOCIAL_SERVICE");
        for (String rpc : new String[]{"UploadMedia","DeleteMedia"}) {
            method(rpc); reset(next);
            org.assertj.core.api.Assertions.assertThat(proceedAndCaptureCaller()).isEqualTo(MediaCaller.TRAVEL_SOCIAL_SERVICE);
        }
    }
    @Test void travelSocialServiceCannotUsePrivateDocumentRpcs() {
        bearer("travel-social-service","TRAVEL_SOCIAL_SERVICE");
        for (String rpc : new String[]{"UploadPrivateDocument","GetSignedUrl","GetSignedUrls"}) {
            reset(call); method(rpc);
            new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
            verify(call).close(argThat(s->s.getCode()==Status.Code.PERMISSION_DENIED),any());
        }
        verifyNoInteractions(next);
    }
    @Test void forgedTravelSocialIdentitiesAreDenied() {
        for (String[] identity : new String[][]{{"travel-social-service","HOTEL_SERVICE"},{"user","TRAVEL_SOCIAL_SERVICE"}}) {
            bearer(identity[0],identity[1]); reset(call); method("UploadMedia");
            new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
            verify(call).close(argThat(s->s.getCode()==Status.Code.PERMISSION_DENIED),any());
        }
        verifyNoInteractions(next);
    }
    @Test void hotelServiceKeepsPrivateDocumentAccess() {
        bearer("hotel-service","HOTEL_SERVICE"); method("GetSignedUrl");
        org.assertj.core.api.Assertions.assertThat(proceedAndCaptureCaller()).isEqualTo(MediaCaller.HOTEL_SERVICE);
    }
    @Test void signedHotelServiceCanProceed() {
        bearer("hotel-service","HOTEL_SERVICE"); when(next.startCall(any(),any())).thenReturn(new ServerCall.Listener<>() {});
        new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(next).startCall(call,headers); verify(call,never()).close(any(),any());
    }
}
