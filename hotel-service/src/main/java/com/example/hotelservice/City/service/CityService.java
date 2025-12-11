package com.example.hotelservice.City.service;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.entity.City;

import java.util.List;
import java.util.UUID;

public interface CityService {
    City createCity(CityCreateRequest request);
    City getById(UUID cityId);
    City getByName(String name);
    City getByCode(String code);
    List<City> getAllCities();
    City updateCity(UUID cityId, CityCreateRequest request);
    void deleteCity(UUID cityId);
}
