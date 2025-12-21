package com.example.hotelservice.City.service;

import com.example.hotelservice.City.dto.response.LocationResponse;
import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.City.entity.Province;
import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.City.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;

    /**
     * Lấy tất cả provinces và cities
     */
    public List<LocationResponse> getAllLocations() {
        List<LocationResponse> locations = new ArrayList<>();

        // Add all provinces
        var provinces = provinceRepository.findAll();
        for (Province province : provinces) {
            locations.add(new LocationResponse(
                    province.getId(),
                    province.getName(),
                    province.getLatitude(),
                    province.getLongitude(),
                    province.getHotelCount(),
                    province.getCreatedAt(),
                    null,  // provinces không có parent
                    "PROVINCE"
            ));
        }

        // Add all cities
        var cities = cityRepository.findAll();
        for (City city : cities) {
            var parent = city.getProvince();
            locations.add(new LocationResponse(
                    city.getId(),
                    city.getName(),
                    city.getLatitude(),
                    city.getLongitude(),
                    city.getHotelCount(),
                    city.getCreatedAt(),
                    parent != null ? parent.getName() : null,
                    "CITY"
            ));
        }

        return locations;
    }

    /**
     * Tìm kiếm locations theo tên hoặc code
     */
    public List<LocationResponse> searchLocations(String query) {
        String q = query.toLowerCase();
        List<LocationResponse> results = new ArrayList<>();

        // Search in provinces
        var provinces = provinceRepository.findAll();
        for (Province p : provinces) {
            if (p.getName().toLowerCase().contains(q)) {
                results.add(new LocationResponse(
                        p.getId(),
                        p.getName(),
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getHotelCount(),
                        p.getCreatedAt(),
                        null,
                        "PROVINCE"
                ));
            }
        }

        // Search in cities
        var cities = cityRepository.findAll();
        for (City c : cities) {
            if (c.getName().toLowerCase().contains(q)) {
                var parent = c.getProvince();
                results.add(new LocationResponse(
                        c.getId(),
                        c.getName(),
                        c.getLatitude(),
                        c.getLongitude(),
                        c.getHotelCount(),
                        c.getCreatedAt(),
                        parent != null ? parent.getName() : null,
                        "CITY"
                ));
            }
        }

        return results;
    }
}
