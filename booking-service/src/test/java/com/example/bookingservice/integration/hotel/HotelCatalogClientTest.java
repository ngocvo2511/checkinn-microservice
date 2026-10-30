package com.example.bookingservice.integration.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HotelCatalogClientTest {
    @Test void readsAuthoritativeRoomOfferAndIgnoresUnrelatedMetadata() {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var client = new HotelCatalogClient(rest);
        ReflectionTestUtils.setField(client, "hotelServiceUrl", "http://hotel.test");
        server.expect(requestTo("http://hotel.test/api/v1/room-1")).andRespond(withSuccess(
                "{\"id\":\"room-1\",\"hotelId\":\"hotel-1\",\"basePrice\":1000000,\"isActive\":true,\"totalRooms\":2,\"capacity\":{\"adults\":2,\"children\":1},\"mediaAssets\":[]}", MediaType.APPLICATION_JSON));
        var room = client.getRoom("room-1");
        assertThat(room.basePrice()).isEqualByComparingTo("1000000");
        assertThat(room.capacity().adults()).isEqualTo(2);
        server.verify();
    }
    @Test void dependencyOutageIsNotAnEmptyOfferOrSoldOutResult() {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var client = new HotelCatalogClient(rest);
        ReflectionTestUtils.setField(client, "hotelServiceUrl", "http://hotel.test");
        server.expect(requestTo("http://hotel.test/hotels/hotel-1")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.getHotel("hotel-1")).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
