package com.example.revenueservice.client;

import com.example.revenueservice.dto.HotelInfo;
import com.example.revenueservice.dto.HotelResponseLite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HotelInfoClient {

    private final RestTemplate restTemplate;
    private final String hotelServiceUrl;
    private final Map<String, HotelInfo> cache = new ConcurrentHashMap<>();

    public HotelInfoClient(RestTemplate restTemplate,
                           @Value("${hotel.service.url:http://hotel-service}") String hotelServiceUrl) {
        this.restTemplate = restTemplate;
        this.hotelServiceUrl = hotelServiceUrl;
    }

    public HotelInfo getHotelInfo(String hotelId) {
        if (hotelId == null) {
            return new HotelInfo(null, null, null);
        }
        return cache.computeIfAbsent(hotelId, this::fetchHotelInfo);
    }

    private HotelInfo fetchHotelInfo(String hotelId) {
        try {
            String url = hotelServiceUrl + "/hotels/" + hotelId;
            HotelResponseLite response = restTemplate.getForObject(url, HotelResponseLite.class);
            if (response != null) {
                String owner = response.ownerId() != null ? response.ownerId().toString() : null;
                return new HotelInfo(hotelId, response.name(), owner);
            }
        } catch (Exception ignored) {
        }
        return new HotelInfo(hotelId, null, null);
    }
}
