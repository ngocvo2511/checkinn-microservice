package com.example.bookingservice.integration.hotel;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/** Trusted catalog snapshots; never accepts price/ownership metadata from the booking client. */
@Service
@RequiredArgsConstructor
public class HotelCatalogClient {
    private final RestTemplate restTemplate;
    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public record Capacity(Integer adults, Integer children) {}
    public record Room(String id, String hotelId, String name, BigDecimal basePrice,
                       Capacity capacity, Boolean isActive, Integer totalRooms) {}
    public record Hotel(String id, String name, Boolean isActive, String approvedStatus) {}

    public Room getRoom(String id) { return get("/api/v1/{id}", id, Room.class); }
    public Hotel getHotel(String id) { return get("/hotels/{id}", id, Hotel.class); }

    private <T> T get(String path, String id, Class<T> type) {
        try {
            T response = restTemplate.getForObject(hotelServiceUrl + path, type, id);
            if (response == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog response is empty");
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new IllegalArgumentException("Catalog resource not found");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel catalog unavailable");
        }
    }
}
