package com.example.revenueservice.service;

import com.example.revenueservice.regulations.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CommissionBoundaryTest {
    @Test void productionHttpClientEnforcesReadDeadlineAgainstRealDelayedSocket() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();server.setExecutor(executor);
        var contacted=new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/api/regulations/typed/commission",exchange->{
            contacted.set(true);
            try {Thread.sleep(8000);}catch(InterruptedException interrupted) {Thread.currentThread().interrupt();}
            finally {exchange.close();}
        });server.start();
        try {
            var client=new RegulationClient(new com.example.revenueservice.config.RestClientConfig().restTemplate(),
                    "http://127.0.0.1:"+server.getAddress().getPort());
            long started=System.nanoTime();
            assertThatThrownBy(client::getCommissionRate).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(contacted.get()).isTrue();
            assertThat(java.time.Duration.ofNanos(System.nanoTime()-started).toMillis()).isBetween(4000L,10000L);
        } finally {server.stop(0);executor.shutdownNow();}
    }
    @Test void unavailableAuthorityCannotBecomeHardcodedTenPercent() {
        var client=new RegulationClient(new RestTemplate(),"http://commission.invalid");
        var http=(RestTemplate)ReflectionTestUtils.getField(client,"restTemplate");
        var server=MockRestServiceServer.bindTo(http).build();server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(client::getCommissionRate).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        server.verify();
    }
    @Test void unversionedCacheCannotOverrideAuthoritativeCurrentRate() {
        var cache=new RegulationLocalCache();cache.put("COMMISSION_RATE","0.10");
        var client=new RegulationClient(new RestTemplate(),"http://commission.invalid");
        var http=(RestTemplate)ReflectionTestUtils.getField(client,"restTemplate");
        var server=MockRestServiceServer.bindTo(http).build();
        server.expect(anything()).andRespond(withSuccess("{\"commissionRate\":0.23}",org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(client.getCommissionRate()).isEqualByComparingTo("0.23");server.verify();
    }
}
