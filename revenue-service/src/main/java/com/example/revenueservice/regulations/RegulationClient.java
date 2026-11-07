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

    private final RestTemplate restTemplate;
    private final String regulationsBaseUrl;

    public RegulationClient(RestTemplate restTemplate, @Value("${regulations.service.url:http://localhost:8090}") String regulationsBaseUrl) {
        this.restTemplate = restTemplate;
        this.regulationsBaseUrl = regulationsBaseUrl;
    }

    public BigDecimal getCommissionRate() {
        // Unversioned broker cache values cannot identify a current financial rule.
        try {
            ResponseEntity<CommissionResponse> resp = restTemplate.getForEntity(regulationsBaseUrl + "/api/regulations/typed/commission", CommissionResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().commissionRate != null) {
                return resp.getBody().commissionRate;
            }
        } catch (Exception ex) {
            logger.warn("[REVENUE_COMMISSION_UNAVAILABLE]");
        }

        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Commission authority unavailable");
    }

    // simple DTO for typed endpoint response
    private static class CommissionResponse {
        public BigDecimal commissionRate;
    }
}
