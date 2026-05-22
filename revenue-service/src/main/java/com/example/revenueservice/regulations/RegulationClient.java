package com.example.revenueservice.regulations;

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

    public BigDecimal getCommissionRate() {
        String cached = cache.get("COMMISSION_RATE");
        if (cached != null) {
            try {
                return new BigDecimal(cached);
            } catch (NumberFormatException ex) {
                logger.warn("[REVENUE_REGULATION_CLIENT_INVALID_CACHE] Invalid cached commission rate: {}", cached);
            }
        }

        // Fallback to regulations service typed endpoint
        try {
            ResponseEntity<CommissionResponse> resp = restTemplate.getForEntity(regulationsBaseUrl + "/api/regulations/typed/commission", CommissionResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().commissionRate != null) {
                String value = resp.getBody().commissionRate.toString();
                cache.put("COMMISSION_RATE", value);
                return resp.getBody().commissionRate;
            }
        } catch (Exception ex) {
            logger.warn("[REVENUE_REGULATION_CLIENT_FALLBACK] Failed to fetch commission rate from regulations-service", ex);
        }

        // final fallback: 0.10
        return new BigDecimal("0.10");
    }

    // simple DTO for typed endpoint response
    private static class CommissionResponse {
        public BigDecimal commissionRate;
    }
}
