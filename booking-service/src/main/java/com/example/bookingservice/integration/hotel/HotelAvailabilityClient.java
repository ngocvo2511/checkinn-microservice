package com.example.bookingservice.integration.hotel;

import com.example.bookingservice.integration.hotel.dto.HoldRequest;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelAvailabilityClient {

    private final RestTemplate restTemplate;

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public HoldResponse holdRooms(HoldRequest request) {
        String url = hotelServiceUrl + "/api/v1/availability/hold";
        log.info("Calling hotel service to hold rooms: url={}, request={}", url, request);
        HoldResponse response = restTemplate.postForObject(url, request, HoldResponse.class);
        log.info("Hold rooms response: {}", response);
        return response;
    }

    public void confirmHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/confirm";
        log.info("Calling hotel service to confirm hold: url={}, holdId={}", url, holdId);
        try {
            restTemplate.postForEntity(url, null, Void.class);
            log.info("Successfully confirmed hold: {}", holdId);
        } catch (Exception e) {
            log.error("Failed to confirm hold {}: {}", holdId, e.getMessage(), e);
            throw e;
        }
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
