package com.example.revenueservice.security;

import com.example.revenueservice.controller.RevenueReportController;
import com.example.revenueservice.service.RevenueReportService;
import com.example.revenueservice.client.HotelGrpcClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=RevenueReportController.class,properties={"spring.cloud.config.enabled=false","spring.cloud.discovery.enabled=false"})
@org.springframework.context.annotation.Import(com.example.revenueservice.security.SecurityConfig.class)
class ReportAuthorizationTest {
    static final String ACTOR=UUID.randomUUID().toString(), OTHER=UUID.randomUUID().toString(), HOTEL=UUID.randomUUID().toString();
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;
    @MockBean RevenueReportService reports;
    @MockBean HotelGrpcClient hotels;
    @BeforeEach void setup() {
        when(decoder.decode(anyString())).thenAnswer(call -> Jwt.withTokenValue(call.getArgument(0)).header("alg","HS256")
            .subject(ACTOR).claim("role",call.getArgument(0).toString().toUpperCase()).build());
        when(hotels.getHotelInfo(HOTEL)).thenReturn(new HotelGrpcClient.HotelOwnerInfo("synthetic",ACTOR,null));
    }
    String dates(String path) { return path+"?from=2026-01-01&to=2026-01-02"; }
    @Test void anonymousCannotReadReports() throws Exception {
        mvc.perform(get(dates("/api/reports/admin/summary"))).andExpect(status().isUnauthorized()); verifyNoInteractions(reports);
    }
    @Test void customerCannotReadAdminReport() throws Exception {
        mvc.perform(get(dates("/api/reports/admin/summary")).header("Authorization","Bearer customer"))
            .andExpect(status().isForbidden()); verifyNoInteractions(reports);
    }
    @Test void ownerCannotChooseAnotherOwner() throws Exception {
        mvc.perform(get(dates("/api/reports/owner/summary")+"&ownerId="+OTHER).header("Authorization","Bearer owner"))
            .andExpect(status().isForbidden()); verifyNoInteractions(reports);
    }
    @Test void ownerCanReadOwnReport() throws Exception {
        mvc.perform(get(dates("/api/reports/owner/summary")+"&ownerId="+ACTOR).header("Authorization","Bearer owner"))
            .andExpect(status().isOk()); verify(reports).ownerSummary(eq(ACTOR),any(),any());
    }
    @Test void administratorCanReadAggregateReport() throws Exception {
        mvc.perform(get(dates("/api/reports/admin/summary")).header("Authorization","Bearer admin"))
            .andExpect(status().isOk()); verify(reports).adminSummary(any(),any());
    }
    @Test void ownerCannotOmitHotelForSystemWideRevenue() throws Exception {
        mvc.perform(get(dates("/api/reports/revenue")).header("Authorization","Bearer owner"))
            .andExpect(status().isForbidden()); verifyNoInteractions(reports);
    }
    @Test void ownerCanReadOwnHotelRevenue() throws Exception {
        mvc.perform(get(dates("/api/reports/revenue")+"&hotelId="+HOTEL).header("Authorization","Bearer owner"))
            .andExpect(status().isOk()); verify(reports).revenue(eq(HOTEL),any(),any(),any());
    }
    @Test void ownerCannotReadDifferentHotel() throws Exception {
        when(hotels.getHotelInfo(HOTEL)).thenReturn(new HotelGrpcClient.HotelOwnerInfo("synthetic",OTHER,null));
        mvc.perform(get(dates("/api/reports/occupancy")+"&hotelId="+HOTEL).header("Authorization","Bearer owner"))
            .andExpect(status().isForbidden()); verifyNoInteractions(reports);
    }
    @Test void unknownOwnershipFailsClosedWithServiceUnavailable() throws Exception {
        when(hotels.getHotelInfo(HOTEL)).thenReturn(new HotelGrpcClient.HotelOwnerInfo(null,null,null));
        mvc.perform(get(dates("/api/reports/revenue")+"&hotelId="+HOTEL).header("Authorization","Bearer owner"))
            .andExpect(status().isServiceUnavailable()); verifyNoInteractions(reports);
    }
    @Test void invalidOrRevokedBearerDoesNotReachReportQueries() throws Exception {
        when(decoder.decode("revoked")).thenThrow(new BadJwtException("revoked"));
        mvc.perform(get(dates("/api/reports/admin/summary")).header("Authorization","Bearer revoked"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(reports);
    }
}
