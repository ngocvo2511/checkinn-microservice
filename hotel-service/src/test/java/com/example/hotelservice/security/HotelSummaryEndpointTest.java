package com.example.hotelservice.security;

import com.example.hotelservice.Hotel.controller.HotelSummaryController;
import com.example.hotelservice.Hotel.dto.response.HotelSummaryResponse;
import com.example.hotelservice.Hotel.service.HotelSummaryService;
import com.example.hotelservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HotelSummaryController.class, properties = {
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import(SecurityConfig.class)
class HotelSummaryEndpointTest {
    private static final UUID HOTEL = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;
    @MockBean HotelSummaryService summaries;

    @Test
    void anonymousCallerGetsOnlyPublicDisplayFields() throws Exception {
        when(summaries.summarize(List.of(HOTEL))).thenReturn(List.of(
                new HotelSummaryResponse(HOTEL, "Pine Hill", "Đà Lạt", "Lâm Đồng", (short) 4, "https://cdn.test/a.jpg")));

        mvc.perform(get("/hotels/batch").param("ids", HOTEL.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(HOTEL.toString()))
                .andExpect(jsonPath("$[0].name").value("Pine Hill"))
                .andExpect(jsonPath("$[0].provinceName").value("Lâm Đồng"))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$[0].contactEmail").doesNotExist())
                .andExpect(jsonPath("$[0].contactPhone").doesNotExist())
                .andExpect(jsonPath("$[0].taxId").doesNotExist())
                .andExpect(jsonPath("$[0].businessLicenseNumber").doesNotExist())
                .andExpect(jsonPath("$[0].ownerIdentityNumber").doesNotExist());
    }

    @Test
    void malformedOrMissingIdsAreBadRequests() throws Exception {
        mvc.perform(get("/hotels/batch").param("ids", "not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/batch")).andExpect(status().isBadRequest());
        verify(summaries, never()).summarize(anyList());
    }

    @Test
    void serviceLimitViolationIsBadRequest() throws Exception {
        when(summaries.summarize(anyList())).thenThrow(new IllegalArgumentException("Provide between 1 and 50 hotel identifiers"));

        mvc.perform(get("/hotels/batch").param("ids", HOTEL.toString())).andExpect(status().isBadRequest());
    }
}
