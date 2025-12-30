package com.example.bookingservice.integration.hotel;

import com.example.bookingservice.integration.hotel.dto.HoldRequest;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class HotelAvailabilityClient {

    private final RestTemplate restTemplate;

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public HoldResponse holdRooms(HoldRequest request) {
        String url = hotelServiceUrl + "/api/v1/availability/hold";
        return restTemplate.postForObject(url, request, HoldResponse.class);
    }

    public void confirmHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/confirm";
        restTemplate.postForEntity(url, null, Void.class);
    }

    public void releaseHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/release";
        restTemplate.postForEntity(url, null, Void.class);
    }

    public HoldResponse getHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId;
        return restTemplate.getForObject(url, HoldResponse.class);
    }
}
