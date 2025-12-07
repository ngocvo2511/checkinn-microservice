package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class HotelMapper {

    protected ObjectMapper objectMapper;
    @Autowired
    public void setObjectMapper(ObjectMapper om) {
        this.objectMapper = om;
    }

    // ----------- CreateHotelRequest -> Hotel -----------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roomTypes", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    @Mapping(target = "address", expression = "java(writeJson(request.address()))")
    public abstract Hotel toHotel(HotelCreateRequest request);


    // ----------- Hotel -> HotelResponse (record) -----------

    @Mapping(target = "address", expression = "java(readAddress(entity.getAddress()))")
    @Mapping(target = "roomTypes", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    public abstract HotelResponse toHotelResponse(Hotel entity);


    // ---------- JSON Helpers ----------
    protected String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected HotelAddressDto readAddress(String json) {
        try { return objectMapper.readValue(json, HotelAddressDto.class); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
