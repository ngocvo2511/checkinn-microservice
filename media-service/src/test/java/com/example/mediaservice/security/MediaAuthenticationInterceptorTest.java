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
    @Test void signedHotelServiceCanProceed() {
        bearer("hotel-service","HOTEL_SERVICE"); when(next.startCall(any(),any())).thenReturn(new ServerCall.Listener<>() {});
        new MediaAuthenticationInterceptor(decoder).interceptCall(call,headers,next);
        verify(next).startCall(call,headers); verify(call,never()).close(any(),any());
    }
}
