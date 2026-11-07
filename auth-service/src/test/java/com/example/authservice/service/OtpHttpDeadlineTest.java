package com.example.authservice.service;

import com.example.authservice.AuthServiceApplication;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;

class OtpHttpDeadlineTest {
    @Test void actualOtpHttpTransportHasFiniteReadTimeout() throws Exception {
        var gate=new CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/stall",exchange->{try{gate.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}});server.start();
        try {
            var client=new AuthServiceApplication().restTemplate(100,100);
            var failure=assertThrows(org.springframework.web.client.ResourceAccessException.class,()->client.getForObject("http://127.0.0.1:"+server.getAddress().getPort()+"/stall",String.class));
            assertInstanceOf(java.net.SocketTimeoutException.class,failure.getMostSpecificCause());
            assertThrows(IllegalArgumentException.class,()->new AuthServiceApplication().restTemplate(0,100));
        } finally {gate.countDown();server.stop(0);}
    }
}
