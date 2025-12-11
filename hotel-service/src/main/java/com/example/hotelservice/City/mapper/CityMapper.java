package com.example.hotelservice.City.mapper;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.dto.response.CityResponse;
import com.example.hotelservice.City.entity.City;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CityMapper {
    City toCityEntity(CityCreateRequest request);
    CityResponse toCityResponse(City entity);
}
