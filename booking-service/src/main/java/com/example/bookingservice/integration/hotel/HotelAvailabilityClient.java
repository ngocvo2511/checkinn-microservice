package com.example.bookingservice.integration.hotel;

import com.example.bookingservice.integration.hotel.dto.HoldRequest;
import com.example.bookingservice.integration.hotel.dto.HoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.bookingservice.security.JwtService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelAvailabilityClient {

    private final RestTemplate restTemplate;
    private final JwtService jwtService;

    private HttpEntity<?> authenticated(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateInventoryServiceToken());
        return new HttpEntity<>(body, headers);
    }

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public HoldResponse holdRooms(HoldRequest request) {
        String url = hotelServiceUrl + "/api/v1/availability/hold";
        log.info("Calling hotel service to hold rooms: url={}, request={}", url, request);
        HoldResponse response = restTemplate.exchange(url, HttpMethod.POST, authenticated(request), HoldResponse.class).getBody();
        log.info("Hold rooms response: {}", response);
        return response;
    }

    public void confirmHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/confirm";
        log.info("Calling hotel service to confirm hold: url={}, holdId={}", url, holdId);
        try {
            restTemplate.exchange(url, HttpMethod.POST, authenticated(null), Void.class);
            log.info("Successfully confirmed hold: {}", holdId);
        } catch (Exception e) {
            log.error("Failed to confirm hold {}: {}", holdId, e.getMessage(), e);
            throw e;
        }
    }

    public void releaseHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/release";
        restTemplate.exchange(url, HttpMethod.POST, authenticated(null), Void.class);
    }

    public HoldResponse getHold(String holdId) {
        String url = hotelServiceUrl + "/api/v1/availability/hold/" + holdId;
        return restTemplate.exchange(url, HttpMethod.GET, authenticated(null), HoldResponse.class).getBody();
    }

    public void cancelReservation(String holdId) {
        restTemplate.exchange(hotelServiceUrl + "/api/v1/availability/hold/" + holdId + "/cancel",
                HttpMethod.POST, authenticated(null), Void.class);
    }
}
