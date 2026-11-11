package com.example.userservice.regulations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Duration;

@Component
public class RegulationClient {
    private final RegulationLocalCache cache;
    private final RestTemplate restTemplate = new RestTemplateBuilder().setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(2)).build();
    private final String regulationsBaseUrl;

    public RegulationClient(RegulationLocalCache cache, @Value("${regulations.service.url:http://localhost:8091}") String regulationsBaseUrl) {
        this.cache = cache;
        this.regulationsBaseUrl = regulationsBaseUrl;
    }

    public BigDecimal getEarnConversionRate() { return rate("POINTS_EARN_CONVERSION_RATE", true); }
    public BigDecimal getRedemptionConversionRate() { return rate("POINTS_REDEMPTION_CONVERSION_RATE", false); }

    private BigDecimal rate(String key, boolean earn) {
        // Event caches are advisory: an old positive rate must not authorize a financial command.
        try {
            PointsResponse response = restTemplate.getForObject(regulationsBaseUrl + "/api/regulations/typed/points", PointsResponse.class);
            BigDecimal rate = response == null ? null : earn ? response.earnConversionRate : response.redemptionConversionRate;
            if (rate != null && rate.signum() > 0) {
                cache.put(key, rate.toString());
                return rate;
            }
        } catch (org.springframework.web.client.RestClientException ignored) { /* No invented fallback rate. */ }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Points conversion rate unavailable");
    }

    private static class PointsResponse {
        public BigDecimal earnConversionRate;
        public BigDecimal redemptionConversionRate;
    }
}
