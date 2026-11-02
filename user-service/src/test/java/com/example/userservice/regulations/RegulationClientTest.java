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
