package com.example.regulationsservice.security;

import com.example.regulationsservice.config.SecurityConfig;
import com.example.regulationsservice.controller.RegulationController;
import com.example.regulationsservice.service.RegulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegulationController.class)
@Import({SecurityConfig.class,JwtFilter.class})
class RegulationBoundaryTest {
    @Test void missingRouteRemains404WithSafeEnvelope() throws Exception {
        mvc.perform(get("/api/regulations/synthetic/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void unsupportedMethodRemains405AndAdvertisesAllowedMethods() throws Exception {
        mvc.perform(post("/api/regulations/COMMISSION_RATE").header("Authorization","Bearer ADMIN"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
    }
    @Test void unavailableSessionStoreIs503WithoutInvokingAdministrativeBusinessLogic() throws Exception {
        when(jwt.validateToken("STORE_DOWN")).thenThrow(new SessionStoreUnavailableException());
        mvc.perform(get("/api/regulations/snapshots").header("Authorization","Bearer STORE_DOWN"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SESSION_STORE_UNAVAILABLE"));
        verifyNoInteractions(service);
    }
    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean RegulationService service;
    @BeforeEach void tokens() {
        for(String role:List.of("ADMIN","OWNER","CUSTOMER")) {
            when(jwt.validateToken(role)).thenReturn(true);
            when(jwt.extractRole(role)).thenReturn(role);
            when(jwt.extractUserId(role)).thenReturn(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        }
    }
    @Test void anonymousCannotReadAdministrativeAuditSnapshots() throws Exception {
        mvc.perform(get("/api/regulations/snapshots")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void ownerAndCustomerCannotReadAdministrativeAuditSnapshots() throws Exception {
        for(String role:List.of("OWNER","CUSTOMER"))
            mvc.perform(get("/api/regulations/snapshots").header("Authorization","Bearer "+role)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void administratorCanReadSnapshotsAndAnonymousCanStillReadPublicRates() throws Exception {
        when(service.getPointsConversionRatesDto()).thenReturn(new com.example.regulationsservice.dto.PointsConversionDto(
                java.math.BigDecimal.TEN,java.math.BigDecimal.ONE));
        mvc.perform(get("/api/regulations/snapshots").header("Authorization","Bearer ADMIN")).andExpect(status().isOk());
        mvc.perform(get("/api/regulations/typed/points")).andExpect(status().isOk());
        verify(service).listSnapshots();verify(service).getPointsConversionRatesDto();
    }
    @Test void pathAndBodyKeyMustAgreeBeforeMutation() throws Exception {
        mvc.perform(put("/api/regulations/COMMISSION_RATE").header("Authorization","Bearer ADMIN")
                .contentType("application/json").content("{\"regulationKey\":\"POINTS_EARN_CONVERSION_RATE\",\"name\":\"Synthetic\",\"value\":\"2\",\"active\":true,\"version\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void dependencyFailureUsesSafeUnavailableEnvelope() throws Exception {
        when(service.listSnapshots()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-private-db-detail"));
        mvc.perform(get("/api/regulations/snapshots").header("Authorization","Bearer ADMIN"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-db-detail"))));
    }
}
