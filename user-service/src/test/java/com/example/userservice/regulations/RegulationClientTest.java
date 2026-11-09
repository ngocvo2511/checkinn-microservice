package com.example.userservice.regulations;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;

class RegulationClientTest {
    @Test void actualHttpReadsObserveChangedRateAndNeverUseCachedSuccessDuringOutage() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/regulations/typed/points",exchange->{
            int call=calls.incrementAndGet();
            byte[] body=("{\"earnConversionRate\":"+(call==1?10000:8000)+",\"redemptionConversionRate\":700}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            // A gateway outage also verifies failure without the HTTP library's optional 503 retry.
            exchange.sendResponseHeaders(call>2?502:200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var cache=new RegulationLocalCache();cache.put("POINTS_EARN_CONVERSION_RATE","123");
            var client=new RegulationClient(cache,"http://127.0.0.1:"+server.getAddress().getPort());
            assertThat(client.getEarnConversionRate()).isEqualByComparingTo("10000");
            assertThat(client.getEarnConversionRate()).isEqualByComparingTo("8000");
            assertThatThrownBy(client::getEarnConversionRate).isInstanceOfSatisfying(ResponseStatusException.class,
                    error->assertThat(error.getStatusCode().value()).isEqualTo(503));
            assertThat(calls.get()).isEqualTo(3);
        } finally {server.stop(0);}
    }
    @Test void stalePositiveCacheCannotOverrideTheAuthoritativeRateOrHideItsOutage() {
        var cache=mock(RegulationLocalCache.class);
        when(cache.get("POINTS_EARN_CONVERSION_RATE")).thenReturn("123");
        var client=new RegulationClient(cache,"http://regulations");
        var server=MockRestServiceServer.bindTo((RestTemplate)ReflectionTestUtils.getField(client,"restTemplate")).build();
        server.expect(requestTo("http://regulations/api/regulations/typed/points"))
                .andRespond(withSuccess("{\"earnConversionRate\":10000,\"redemptionConversionRate\":700}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://regulations/api/regulations/typed/points")).andRespond(withServerError());
        assertThat(client.getEarnConversionRate()).isEqualByComparingTo("10000");
        assertThatThrownBy(client::getEarnConversionRate).isInstanceOfSatisfying(ResponseStatusException.class,
                error->assertThat(error.getStatusCode().value()).isEqualTo(503));
        server.verify();
    }
    @Test void missingRateNeverBecomesAHardcodedFallback() {
        var client = new RegulationClient(mock(RegulationLocalCache.class), "http://regulations");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        server.expect(requestTo("http://regulations/api/regulations/typed/points")).andRespond(withServerError());
        assertThatThrownBy(client::getRedemptionConversionRate).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        server.verify();
    }

    @Test void invalidCachedAndRemoteRatesAreRejected() {
        var cache = mock(RegulationLocalCache.class);
        when(cache.get("POINTS_EARN_CONVERSION_RATE")).thenReturn("0");
        var client = new RegulationClient(cache, "http://regulations");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        server.expect(requestTo("http://regulations/api/regulations/typed/points"))
                .andRespond(withSuccess("{\"earnConversionRate\":-1,\"redemptionConversionRate\":500}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::getEarnConversionRate).isInstanceOf(ResponseStatusException.class);
        verify(cache, never()).put(any(), any());
    }

    @Test void validRateComesFromRegulations() {
        var cache = mock(RegulationLocalCache.class);
        var client = new RegulationClient(cache, "http://regulations");
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        server.expect(requestTo("http://regulations/api/regulations/typed/points"))
                .andRespond(withSuccess("{\"earnConversionRate\":10000,\"redemptionConversionRate\":700}", MediaType.APPLICATION_JSON));
        assertThat(client.getRedemptionConversionRate()).isEqualByComparingTo("700");
        verify(cache).put("POINTS_REDEMPTION_CONVERSION_RATE", "700");
        server.verify();
    }
}
