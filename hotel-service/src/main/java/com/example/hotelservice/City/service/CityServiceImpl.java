package com.example.hotelservice.City.service;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.City.mapper.CityMapper;
import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.City.repository.ProvinceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;
    private final ProvinceRepository provinceRepository;
    private final CityMapper cityMapper;

    @Override
    @Transactional
    public City createCity(CityCreateRequest request) {
        if (cityRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Thành phố '" + request.name() + "' đã tồn tại");
        }

        City city = cityMapper.toCityEntity(request);
        
        // Load and set province if provinceId provided
        if (request.provinceId() != null) {
            var province = provinceRepository.findById(request.provinceId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tỉnh/thành phố với ID: " + request.provinceId()));
            city.setProvince(province);
        }
        
        return cityRepository.save(city);
    }

    @Override
    public City getById(UUID cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thành phố"));
    }

    @Override
    public City getByName(String name) {
        return cityRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thành phố: " + name));
    }

    @Override
    public List<City> getAllCities() {
        return cityRepository.findAll();
    }

    @Override
    @Transactional
    public City updateCity(UUID cityId, CityCreateRequest request) {
        City city = getById(cityId);

        if (request.name() != null && !request.name().equals(city.getName())) {
            if (cityRepository.existsByName(request.name())) {
                throw new IllegalArgumentException("Thành phố '" + request.name() + "' đã tồn tại");
            }
            city.setName(request.name());
        }

        if (request.latitude() != null) city.setLatitude(request.latitude());
        if (request.longitude() != null) city.setLongitude(request.longitude());
        
        if (request.provinceId() != null) {
            var province = provinceRepository.findById(request.provinceId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tỉnh/thành phố với ID: " + request.provinceId()));
            city.setProvince(province);
        } else {
            city.setProvince(null);
        }
        
        if (request.hotelCount() != null) city.setHotelCount(request.hotelCount());

        return cityRepository.save(city);
    }

    @Override
    @Transactional
    public void deleteCity(UUID cityId) {
        City city = getById(cityId);
        cityRepository.delete(city);
    }
}
