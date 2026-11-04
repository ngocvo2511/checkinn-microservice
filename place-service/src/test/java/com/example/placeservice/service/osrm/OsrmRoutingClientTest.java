package com.example.placeservice.service.osrm;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.OsrmTableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OsrmRoutingClientTest {

    private RestTemplate restTemplate;
    private RestTemplateBuilder restTemplateBuilder;
    private OsrmRoutingClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.requestFactory(any(java.util.function.Supplier.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        client = new OsrmRoutingClient("http://localhost:5000", 1500, true, restTemplateBuilder);
    }

    @Test
    void computeTable_WhenSuccessful_ReturnsParsedResponse() {
        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest1 = new Coordinate(21.0300, 105.8500);

        OsrmTableResponse mockResponse = OsrmTableResponse.builder()
                .code("Ok")
                .distances(List.of(List.of(0.0, 1500.0)))
                .durations(List.of(List.of(0.0, 300.0)))
                .build();

        when(restTemplate.getForEntity(anyString(), eq(OsrmTableResponse.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        Optional<OsrmTableResponse> response = client.computeTable(origin, List.of(dest1));

        assertThat(response).isPresent();
        assertThat(response.get().getCode()).isEqualTo("Ok");
        assertThat(response.get().getDistances().getFirst()).containsExactly(0.0, 1500.0);
        assertThat(response.get().getDurations().getFirst()).containsExactly(0.0, 300.0);
    }

    @Test
    void computeTable_WhenTimeoutOrException_ReturnsEmptyOptional() {
        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest1 = new Coordinate(21.0300, 105.8500);

        when(restTemplate.getForEntity(anyString(), eq(OsrmTableResponse.class)))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException()));

        Optional<OsrmTableResponse> response = client.computeTable(origin, List.of(dest1));

        assertThat(response).isEmpty();
    }

    @Test
    void computeTable_WhenDisabled_ReturnsEmptyOptionalWithoutNetworkCall() {
        OsrmRoutingClient disabledClient = new OsrmRoutingClient("http://localhost:5000", 1500, false, restTemplateBuilder);

        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest1 = new Coordinate(21.0300, 105.8500);

        Optional<OsrmTableResponse> response = disabledClient.computeTable(origin, List.of(dest1));

        assertThat(response).isEmpty();
        verifyNoInteractions(restTemplate);
    }
}
