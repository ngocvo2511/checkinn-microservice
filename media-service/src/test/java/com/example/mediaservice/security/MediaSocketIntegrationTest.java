package com.example.mediaservice.security;

import com.example.mediaservice.SupabaseStorageService;

import com.example.mediaservice.grpc.*;
import com.example.mediaservice.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real loopback HTTP + HTTP/2 gRPC + JWT; no provider credentials/network or mocks. */
class MediaSocketIntegrationTest {
    static final String KEY="synthetic-media-socket-test-key-012345678901234567890123456789";
    HttpServer provider;Server server;ManagedChannel channel;String base;int status=200;
    final ObjectMapper json=new ObjectMapper();final AtomicInteger calls=new AtomicInteger();
    @BeforeEach void start() throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/",exchange->{
            calls.incrementAndGet();byte[] input=exchange.getRequestBody().readAllBytes();String path=exchange.getRequestURI().getPath();
            String body="{}";
            if(status!=200)body="synthetic-private-provider-detail";
            else if(path.equals("/storage/v1/object/sign/private")) {
                var entries=new ArrayList<Map<String,String>>();
                for(var name:json.readTree(input).get("paths"))entries.add(Map.of("path",name.asText(),"signedURL","/object/sign/private/"+name.asText()+"?token=synthetic"));
                body=json.writeValueAsString(entries);
            }else if(path.startsWith("/storage/v1/object/sign/"))body=json.writeValueAsString(Map.of("signedURL","/object/sign/private/identity.pdf?token=synthetic"));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        provider.start();base="http://127.0.0.1:"+provider.getAddress().getPort();
        var storage=new SupabaseStorageService(base,"public","private","synthetic-provider-key");
        var guard=new MediaAuthenticationInterceptor(new MediaSecurityConfig().mediaJwtDecoder(KEY));
        server=io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1",0))
                .addService(ServerInterceptors.intercept(new MediaGrpcServiceImpl(storage),guard)).build().start();
        channel=ManagedChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().build();
    }
    @AfterEach void stop() throws Exception {
        channel.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);server.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);provider.stop(0);
    }
    String token(String subject,String role,long seconds) throws Exception {
        var encoder=Base64.getUrlEncoder().withoutPadding();
        String data=encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))+"."
                +encoder.encodeToString(json.writeValueAsBytes(Map.of("sub",subject,"role",role,"exp",System.currentTimeMillis()/1000+seconds)));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return data+"."+encoder.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
    MediaGrpcServiceGrpc.MediaGrpcServiceBlockingStub stub(String token) {
        var stub=MediaGrpcServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5,TimeUnit.SECONDS);
        if(token==null)return stub;
        var headers=new Metadata();headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
    @ParameterizedTest @ValueSource(strings={"anonymous","admin","forged-subject","expired","bad-signature"})
    void unauthorizedCallsNeverReachPrivilegedProviderHttp(String scenario) throws Exception {
        String token=switch(scenario){case "anonymous"->null;case "admin"->token(UUID.randomUUID().toString(),"ADMIN",60);
            case "forged-subject"->token(UUID.randomUUID().toString(),"HOTEL_SERVICE",60);case "expired"->token("hotel-service","HOTEL_SERVICE",-300);
            default->token("hotel-service","HOTEL_SERVICE",60)+"x";};
        assertThatThrownBy(()->stub(token).getSignedUrl(GetSignedUrlRequest.newBuilder().setFilePath("identity.pdf").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,error->assertThat(error.getStatus().getCode()).isEqualTo(
                        scenario.equals("admin")||scenario.equals("forged-subject")?Status.Code.PERMISSION_DENIED:Status.Code.UNAUTHENTICATED));
        assertThat(calls.get()).isZero();
    }
    @Test void validServiceSupportsAllFiveOperationsWithRealSerializationAndHttp() throws Exception {
        var stub=stub(token("hotel-service","HOTEL_SERVICE",60));
        var bytes=com.google.protobuf.ByteString.copyFrom(new byte[]{1,2,3});
        assertThat(stub.uploadMedia(UploadMediaRequest.newBuilder().setFileName("synthetic.png").setMimeType("image/png").setFileData(bytes).build()).getUrl()).startsWith(base+"/storage/v1/object/public/public/");
        assertThat(stub.uploadPrivateDocument(UploadDocumentRequest.newBuilder().setFileName("identity.pdf").setMimeType("application/pdf").setFileData(bytes).build()).getFilePath()).endsWith("_identity.pdf");
        assertThat(stub.deleteMedia(DeleteMediaRequest.newBuilder().setFileName("synthetic.png").build()).getSuccess()).isTrue();
        assertThat(stub.getSignedUrl(GetSignedUrlRequest.newBuilder().setFilePath("identity.pdf").build()).getSignedUrl()).isEqualTo(base+"/storage/v1/object/sign/private/identity.pdf?token=synthetic");
        assertThat(stub.getSignedUrls(GetSignedUrlsRequest.newBuilder().addFilePaths("identity.pdf").addFilePaths("license.pdf").build()).getSignedUrlsMap()).hasSize(2);
        assertThat(calls.get()).isEqualTo(5);
    }
    @Test void providerFailureAndInvalidPathsAreExplicitAcrossBothNetworkBoundaries() throws Exception {
        var stub=stub(token("hotel-service","HOTEL_SERVICE",60));status=503;
        assertThatThrownBy(()->stub.getSignedUrls(GetSignedUrlsRequest.newBuilder().addFilePaths("identity.pdf").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,error->{assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);assertThat(error.getMessage()).doesNotContain("synthetic-private");});
        assertThat(calls.get()).isEqualTo(1);
        assertThatThrownBy(()->stub.getSignedUrl(GetSignedUrlRequest.newBuilder().setFilePath("../identity.pdf").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,error->assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThat(calls.get()).isEqualTo(1);
    }
}
