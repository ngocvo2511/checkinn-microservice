package com.example.revenueservice.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import javax.crypto.spec.SecretKeySpec;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class InternalHotelReportClientTest {
    private static final String SECRET = "synthetic-test-only-key-32-characters";
    @Test void historicalContextUsesExactShortLivedMachineIdentity() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.bindTo(rest).build();
        var catalog = new InternalHotelReportClient(rest,"http://hotel",SECRET);
        server.expect(requestTo("http://hotel/internal/reports/hotels/hotel"))
                .andExpect(request -> {
                    var token = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")).build()
                            .decode(request.getHeaders().getFirst("Authorization").substring(7));
                    assertThat(token.getSubject()).isEqualTo("revenue-service");
                    assertThat(token.getClaimAsString("role")).isEqualTo("REVENUE_SERVICE");
                    assertThat(java.time.Duration.between(token.getIssuedAt(),token.getExpiresAt()).getSeconds()).isEqualTo(300);
                }).andRespond(withSuccess("{\"id\":\"hotel\",\"ownerId\":\"owner\",\"name\":\"Inactive hotel\",\"city\":\"City\",\"roomTypes\":[]}",MediaType.APPLICATION_JSON));
        assertThat(new HotelGrpcClient(catalog).getHotelInfo("hotel").ownerId()).isEqualTo("owner"); server.verify();
    }
    @Test void capacityOutageIsNotCachedAsZeroAndCanRecover() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.bindTo(rest).build();
        var capacity = new HotelCapacityClient(new InternalHotelReportClient(rest,"http://hotel",SECRET));
        server.expect(requestTo("http://hotel/internal/reports/rooms/room")).andRespond(withServerError());
        server.expect(requestTo("http://hotel/internal/reports/rooms/room")).andRespond(withSuccess("{\"id\":\"room\",\"totalRooms\":3}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> capacity.getTotalRooms("room")).isInstanceOf(ResponseStatusException.class);
        assertThat(capacity.getTotalRooms("room")).isEqualTo(3); server.verify();
    }
    @Test void historicalHotelCapacityIncludesInactiveRoomsWithoutPublicLookup() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://hotel/internal/reports/hotels/hotel")).andRespond(withSuccess("{\"id\":\"hotel\",\"ownerId\":\"owner\",\"name\":\"Hotel\",\"roomTypes\":[{\"id\":\"room\",\"totalRooms\":3}]}",MediaType.APPLICATION_JSON));
        assertThat(new HotelGrpcClient(new InternalHotelReportClient(rest,"http://hotel",SECRET)).getHotelById("hotel").getRoomTypes(0).getTotal()).isEqualTo(3);
        server.verify();
    }
}
