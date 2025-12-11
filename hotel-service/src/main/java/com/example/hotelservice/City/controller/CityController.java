package com.example.hotelservice.City.controller;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.dto.response.CityResponse;
import com.example.hotelservice.City.mapper.CityMapper;
import com.example.hotelservice.City.service.CityService;
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

    /**
     * Tạo thành phố mới (Admin only)
     */
    @PostMapping
    public ResponseEntity<CityResponse> createCity(@Valid @RequestBody CityCreateRequest request) {
        var city = cityService.createCity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cityMapper.toCityResponse(city));
    }

    /**
     * Lấy danh sách tất cả thành phố
     */
    @GetMapping
    public ResponseEntity<List<CityResponse>> getAllCities() {
        var cities = cityService.getAllCities()
                .stream()
                .map(cityMapper::toCityResponse)
                .toList();
        return ResponseEntity.ok(cities);
    }

    /**
     * Lấy chi tiết thành phố theo ID
     */
    @GetMapping("/{cityId}")
    public ResponseEntity<CityResponse> getCityById(@PathVariable UUID cityId) {
        var city = cityService.getById(cityId);
        return ResponseEntity.ok(cityMapper.toCityResponse(city));
    }

    /**
     * Lấy thành phố theo tên
     */
    @GetMapping("/search/by-name")
    public ResponseEntity<CityResponse> getCityByName(@RequestParam String name) {
        var city = cityService.getByName(name);
        return ResponseEntity.ok(cityMapper.toCityResponse(city));
    }

    /**
     * Lấy thành phố theo code
     */
    @GetMapping("/search/by-code")
    public ResponseEntity<CityResponse> getCityByCode(@RequestParam String code) {
        var city = cityService.getByCode(code);
        return ResponseEntity.ok(cityMapper.toCityResponse(city));
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
        return ResponseEntity.ok(cityMapper.toCityResponse(city));
    }

    /**
     * Xóa thành phố (Admin only)
     */
    @DeleteMapping("/{cityId}")
    public ResponseEntity<Void> deleteCity(@PathVariable UUID cityId) {
        cityService.deleteCity(cityId);
        return ResponseEntity.noContent().build();
    }
}
