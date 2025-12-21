package com.example.hotelservice.City.controller;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.dto.response.CityResponse;
import com.example.hotelservice.City.dto.response.LocationResponse;
import com.example.hotelservice.City.mapper.CityMapper;
import com.example.hotelservice.City.service.CityService;
import com.example.hotelservice.City.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;
    private final CityMapper cityMapper;
    private final LocationService locationService;

    /**
     * Tạo thành phố mới (Admin only)
     */
    @PostMapping
    public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityCreateRequest request) {
        var city = cityService.createCity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(city));
    }

    /**
     * Lấy danh sách tất cả locations (provinces + cities) cho search/dropdown
     */
    @GetMapping("/all-locations")
    public ResponseEntity<List<LocationResponse>> getAllLocations() {
        var locations = locationService.getAllLocations();
        return ResponseEntity.ok(locations);
    }

    /**
     * Tìm kiếm locations (provinces + cities) theo tên/code
     */
    @GetMapping("/search/locations")
    public ResponseEntity<List<LocationResponse>> searchLocations(@RequestParam String query) {
        var results = locationService.searchLocations(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Lấy danh sách tất cả thành phố
     */
    @GetMapping
    public ResponseEntity<List<CityResponse>> getAllCities() {
        var cities = cityService.getAllCities();
        var responses = cities.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Lấy chi tiết thành phố theo ID
     */
    @GetMapping("/{cityId}")
    public ResponseEntity<CityResponse> getCityById(@PathVariable UUID cityId) {
        var city = cityService.getById(cityId);
        return ResponseEntity.ok(toResponse(city));
    }

    /**
     * Lấy thành phố theo tên
     */
    @GetMapping("/search/by-name")
    public ResponseEntity<CityResponse> getCityByName(@RequestParam String name) {
        var city = cityService.getByName(name);
        return ResponseEntity.ok(toResponse(city));
    }

    /**
     * Cập nhật thành phố (Admin only)
     */
    @PutMapping("/{cityId}")
    public ResponseEntity<CityResponse> updateCity(
            @PathVariable UUID cityId,
            @Valid @RequestBody CityCreateRequest request
    ) {
        var city = cityService.updateCity(cityId, request);
        return ResponseEntity.ok(toResponse(city));
    }

    /**
     * Xóa thành phố (Admin only)
     */
    @DeleteMapping("/{cityId}")
    public ResponseEntity<Void> deleteCity(@PathVariable UUID cityId) {
        cityService.deleteCity(cityId);
        return ResponseEntity.noContent().build();
    }

    private CityResponse toResponse(com.example.hotelservice.City.entity.City city) {
        var parent = city.getProvince();

        return new CityResponse(
                city.getId(),
                city.getName(),
                city.getLatitude(),
                city.getLongitude(),
                city.getHotelCount(),
                city.getCreatedAt(),
                parent != null ? parent.getName() : null
        );
    }
}
