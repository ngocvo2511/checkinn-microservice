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
    
    @Autowired
    protected com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper mediaAssetMapper;

    @Autowired
    protected com.example.hotelservice.Room.mapper.RoomTypeMapper roomTypeMapper;

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
    @Mapping(target = "roomTypes", expression = "java(toRoomTypeResponses(entity.getRoomTypes()))")
    @Mapping(target = "mediaAssets", expression = "java(toMediaAssetResponses(entity.getMediaAssets()))")
    @Mapping(target = "lowestPrice", expression = "java(calculateLowestPrice(entity.getRoomTypes()))")
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

    protected java.util.List<com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse> toMediaAssetResponses(
            java.util.List<com.example.hotelservice.MediaAsset.entity.MediaAsset> assets
    ) {
        if (assets == null) return java.util.List.of();
        return assets.stream()
                .map(mediaAssetMapper::toMediaAssetResponse)
                .toList();
    }

    protected java.util.List<com.example.hotelservice.Room.dto.response.RoomTypeResponse> toRoomTypeResponses(
            java.util.List<com.example.hotelservice.Room.entity.RoomType> roomTypes
    ) {
        if (roomTypes == null) return java.util.List.of();
        return roomTypes.stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();
    }

    protected java.math.BigDecimal calculateLowestPrice(
            java.util.List<com.example.hotelservice.Room.entity.RoomType> roomTypes
    ) {
        if (roomTypes == null || roomTypes.isEmpty()) return null;
        return roomTypes.stream()
                .filter(rt -> rt.getIsActive() != null && rt.getIsActive())
                .map(com.example.hotelservice.Room.entity.RoomType::getBasePrice)
                .filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(null);
    }
}
