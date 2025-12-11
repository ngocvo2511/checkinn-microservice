package com.example.hotelservice.City.mapper;

import com.example.hotelservice.City.dto.request.CityCreateRequest;
import com.example.hotelservice.City.dto.response.CityResponse;
import com.example.hotelservice.City.entity.City;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CityMapper {
    @Mapping(target = "hotelCount", expression = "java(request.hotelCount() == null ? 0 : request.hotelCount())")
    City toCityEntity(CityCreateRequest request);

    @Mapping(target = "parentName", expression = "java(null)")
    @Mapping(target = "parentCode", expression = "java(null)")
    CityResponse toCityResponse(City entity);
}
