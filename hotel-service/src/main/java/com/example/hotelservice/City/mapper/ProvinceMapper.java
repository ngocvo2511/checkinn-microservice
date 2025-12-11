package com.example.hotelservice.City.mapper;

import com.example.hotelservice.City.dto.request.ProvinceCreateRequest;
import com.example.hotelservice.City.dto.response.ProvinceResponse;
import com.example.hotelservice.City.entity.Province;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvinceMapper {
    Province toProvinceEntity(ProvinceCreateRequest request);
    ProvinceResponse toProvinceResponse(Province entity);
}
