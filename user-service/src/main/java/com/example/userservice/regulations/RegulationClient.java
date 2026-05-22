package com.example.userservice.regulations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class RegulationClient {

    private static final Logger logger = LoggerFactory.getLogger(RegulationClient.class);

    private final RegulationLocalCache cache;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String regulationsBaseUrl;

    public RegulationClient(RegulationLocalCache cache, @Value("${regulations.service.url:http://localhost:8090}") String regulationsBaseUrl) {
        this.cache = cache;
        this.regulationsBaseUrl = regulationsBaseUrl;
    }

    public BigDecimal getEarnConversionRate() {
        String cached = cache.get("POINTS_EARN_CONVERSION_RATE");
        if (cached != null) {
            try {
                return new BigDecimal(cached);
            } catch (NumberFormatException ex) {
                logger.warn("[USER_REGULATION_CLIENT_INVALID_CACHE] Invalid cached earn conversion rate: {}", cached);
            }
        }
        try {
            ResponseEntity<PointsResponse> resp = restTemplate.getForEntity(regulationsBaseUrl + "/api/regulations/typed/points", PointsResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                BigDecimal earn = resp.getBody().earnConversionRate;
                if (earn != null) cache.put("POINTS_EARN_CONVERSION_RATE", earn.toString());
                return earn;
            }
        } catch (Exception ex) {
            logger.warn("[USER_REGULATION_CLIENT_FALLBACK] Failed to fetch points conversion rates", ex);
        }
        return new BigDecimal("10000");
    }

    public BigDecimal getRedemptionConversionRate() {
        String cached = cache.get("POINTS_REDEMPTION_CONVERSION_RATE");
        if (cached != null) {
            try {
                return new BigDecimal(cached);
            } catch (NumberFormatException ex) {
                logger.warn("[USER_REGULATION_CLIENT_INVALID_CACHE] Invalid cached redemption conversion rate: {}", cached);
            }
        }
        try {
            ResponseEntity<PointsResponse> resp = restTemplate.getForEntity(regulationsBaseUrl + "/api/regulations/typed/points", PointsResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                BigDecimal red = resp.getBody().redemptionConversionRate;
                if (red != null) cache.put("POINTS_REDEMPTION_CONVERSION_RATE", red.toString());
                return red;
            }
        } catch (Exception ex) {
            logger.warn("[USER_REGULATION_CLIENT_FALLBACK] Failed to fetch points conversion rates", ex);
        }
        return new BigDecimal("500");
    }

    private static class PointsResponse {
        public BigDecimal earnConversionRate;
        public BigDecimal redemptionConversionRate;
    }
}
