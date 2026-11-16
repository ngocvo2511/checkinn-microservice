package com.example.userservice.security;
import static com.example.userservice.contract.TestIds.*;

import com.example.userservice.controller.LoyaltyPointsController;
import com.example.userservice.service.LoyaltyPointsService;
import com.example.userservice.config.SecurityConfig;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=LoyaltyPointsController.class, properties={"spring.cloud.config.enabled=false","spring.cloud.discovery.enabled=false"})
@Import({SecurityConfig.class,JwtMdcFilter.class})
class LoyaltyAuthorizationTest {
    @Test void malformedBookingUuidIs400WithoutLedgerEffects() throws Exception {
        mvc.perform(post("/api/loyalty-points/use").contentType("application/json")
            .content("{\"userId\":\""+ACTOR+"\",\"bookingId\":\"1-1-1-1-1\",\"pointsToUse\":1}")
            .header("Authorization","Bearer service")).andExpect(status().isBadRequest());
        verifyNoInteractions(points);
    }
    static final UUID ACTOR=UUID.randomUUID(),OTHER=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean LoyaltyPointsService points;
    @BeforeEach void tokens() {
        when(jwt.parseToken(anyString())).thenAnswer(call -> {
            String token=call.getArgument(0);
            if (token.equals("invalid")) throw new JwtException("invalid");
            Claims claims=Jwts.claims().setSubject(token.equals("service")?"booking-service":ACTOR.toString());
            claims.put("role",token.equals("service")||token.equals("forged-service")?"BOOKING_SERVICE":token.equals("admin")?"ADMIN":"CUSTOMER");
            Jws<Claims> signed=mock(Jws.class); when(signed.getBody()).thenReturn(claims); return signed;
        });
    }
    @Test void removedDebugTokenIssuerIsNotFound() throws Exception {
        mvc.perform(get("/api/test/generate-token/"+OTHER)).andExpect(status().isNotFound());
        verifyNoInteractions(jwt,points);
    }
    @Test void anonymousCannotReadSomeonePoints() throws Exception {
        mvc.perform(get("/api/loyalty-points/"+OTHER)).andExpect(status().isUnauthorized()); verifyNoInteractions(points);
    }
    @Test void customerCannotReadOtherUser() throws Exception {
        mvc.perform(get("/api/loyalty-points/"+OTHER).header("Authorization","Bearer customer"))
            .andExpect(status().isForbidden()); verifyNoInteractions(points);
    }
    @Test void customerCanReadSelf() throws Exception {
        mvc.perform(get("/api/loyalty-points/"+ACTOR).header("Authorization","Bearer customer")).andExpect(status().isOk());
        verify(points).getLoyaltyPoints(ACTOR);
    }
    @Test void anonymousCannotEarn() throws Exception {
        mvc.perform(post("/api/loyalty-points/earn").param("userId",ACTOR.toString()).param("bookingId",wire("synthetic")).param("totalAmount","1000"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(points);
    }

    @Test void quoteRemainsServiceOnly() throws Exception {
        String body = "{\"userId\":\"" + ACTOR + "\",\"pointsToUse\":10,\"totalAmount\":1000000}";
        for (String token : new String[]{"customer", "admin", "forged-service"}) {
            mvc.perform(post("/api/loyalty-points/quote").contentType("application/json").content(body)
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(points);
        mvc.perform(post("/api/loyalty-points/quote").contentType("application/json").content(body)
                .header("Authorization", "Bearer service")).andExpect(status().isOk());
        verify(points).quote(any());
    }

    @Test void reservationIsNeverGrantedByCustomerAdminOrForgedServiceRole() throws Exception {
        String body = "{\"userId\":\"" + ACTOR + "\",\"bookingId\":\""+wire("synthetic")+"\",\"pointsToUse\":10,\"totalAmount\":1000000,\"holdExpiresAt\":\"2098-01-01T00:00:00Z\"}";
        for (String token : new String[]{"customer", "admin", "forged-service"}) {
            mvc.perform(post("/api/loyalty-points/reserve").contentType("application/json").content(body)
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(points);
        mvc.perform(post("/api/loyalty-points/reserve").contentType("application/json").content(body)
                .header("Authorization", "Bearer service")).andExpect(status().isOk());
        verify(points).reserve(any());
    }

    @Test void ownQuotePreviewUsesVerifiedPathScopeNotBodyIdentity() throws Exception {
        String body = "{\"userId\":\"" + OTHER + "\",\"pointsToUse\":900,\"totalAmount\":1000000}";
        mvc.perform(post("/api/loyalty-points/" + ACTOR + "/quote").contentType("application/json").content(body)
                .header("Authorization", "Bearer customer")).andExpect(status().isOk());
        verify(points).quote(argThat(request -> ACTOR.equals(request.userId()) && request.pointsToUse() == 900));
    }

    @Test void anonymousAndCrossUserCannotPreviewAnotherUsersPoints() throws Exception {
        String body = "{\"pointsToUse\":1,\"totalAmount\":1000000}";
        mvc.perform(post("/api/loyalty-points/" + OTHER + "/quote").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/loyalty-points/" + OTHER + "/quote").contentType("application/json").content(body)
                .header("Authorization", "Bearer customer")).andExpect(status().isForbidden());
        verifyNoInteractions(points);
    }
    @Test void adminAndForgedServiceCannotEarn() throws Exception {
        for(String token:new String[]{"admin","customer","forged-service"})
            mvc.perform(post("/api/loyalty-points/earn").param("userId",ACTOR.toString()).param("bookingId",wire("synthetic")).param("totalAmount","1000")
                .header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        verifyNoInteractions(points);
    }
    @Test void verifiedBookingServiceCanEarn() throws Exception {
        mvc.perform(post("/api/loyalty-points/earn").param("userId",ACTOR.toString()).param("bookingId",wire("synthetic")).param("totalAmount","1000")
            .header("Authorization","Bearer service")).andExpect(status().isOk());
        verify(points).earnPoints(eq(ACTOR),eq(id("synthetic")),any());
    }
    @Test void customerCannotUseOrRefundPointsDirectly() throws Exception {
        mvc.perform(post("/api/loyalty-points/use").contentType("application/json")
            .content("{\"userId\":\""+ACTOR+"\",\"bookingId\":\""+wire("synthetic")+"\",\"pointsToUse\":1}")
            .header("Authorization","Bearer customer")).andExpect(status().isForbidden());
        mvc.perform(post("/api/loyalty-points/refund").param("userId",ACTOR.toString()).param("bookingId",wire("synthetic"))
            .header("Authorization","Bearer customer")).andExpect(status().isForbidden()); verifyNoInteractions(points);
    }
    @Test void customerCannotEnumerateBookingLedger() throws Exception {
        mvc.perform(get("/api/loyalty-points/booking/synthetic/transactions").header("Authorization","Bearer customer"))
            .andExpect(status().isForbidden()); verifyNoInteractions(points);
    }
    @Test void invalidTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/loyalty-points/"+ACTOR).header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()); verifyNoInteractions(points);
    }
    @Test void verifiedBookingServiceCanUseAndRefund() throws Exception {
        mvc.perform(post("/api/loyalty-points/use").contentType("application/json")
            .content("{\"userId\":\""+ACTOR+"\",\"bookingId\":\""+wire("synthetic")+"\",\"pointsToUse\":1}")
            .header("Authorization","Bearer service")).andExpect(status().isOk());
        verify(points).usePoints(any());
        mvc.perform(post("/api/loyalty-points/refund").param("userId",ACTOR.toString()).param("bookingId",wire("synthetic"))
            .header("Authorization","Bearer service")).andExpect(status().isOk());
        verify(points).refundPoints(ACTOR,id("synthetic"));
    }
}
