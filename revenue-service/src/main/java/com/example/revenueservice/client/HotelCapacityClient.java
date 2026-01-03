package com.example.revenueservice.client;

import com.example.revenueservice.dto.RoomTypeCapacityResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HotelCapacityClient {

    private final RestTemplate restTemplate;
    private final String hotelServiceUrl;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public HotelCapacityClient(RestTemplate restTemplate,
                               @Value("${hotel.service.url:http://hotel-service}") String hotelServiceUrl) {
        this.restTemplate = restTemplate;
        this.hotelServiceUrl = hotelServiceUrl;
    }

    public int getTotalRooms(String roomTypeId) {
        if (roomTypeId == null) {
            return 0;
        }
        return cache.computeIfAbsent(roomTypeId, this::fetchTotalRooms);
    }

    private int fetchTotalRooms(String roomTypeId) {
        try {
            String url = hotelServiceUrl + "/api/v1/" + roomTypeId;
            RoomTypeCapacityResponse response = restTemplate.getForObject(url, RoomTypeCapacityResponse.class);
            if (response != null && response.totalRooms() != null) {
                return response.totalRooms();
            }
        } catch (Exception ex) {
            // ignore; fallback to zero
        }
        return 0;
    }
}
