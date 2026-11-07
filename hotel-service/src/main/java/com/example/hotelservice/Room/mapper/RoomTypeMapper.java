package com.example.hotelservice.Room.mapper;

import com.example.hotelservice.Room.dto.request.CapacityDto;
import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.entity.RoomType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RoomTypeMapper {

    @Autowired
    protected ObjectMapper objectMapper;
    
    @Autowired
    protected com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper mediaAssetMapper;


    // ---------------- CreateRoomTypeRequest -> RoomType ----------------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "description", expression = "java(request.description())")
    @Mapping(target = "capacity", expression = "java(writeJson(request.capacity()))")
    @Mapping(target = "amenities", expression = "java(writeJson(request.amenities()))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract RoomType toRoomType(RoomTypeCreateRequest request);


    // ---------------- RoomType -> RoomTypeResponse (record) ----------------

    @Mapping(target = "capacity", expression = "java(readCapacity(entity.getCapacity()))")
    @Mapping(target = "description", expression = "java(entity.getDescription())")
    @Mapping(target = "amenities", expression = "java(readAmenities(entity.getAmenities()))")
    @Mapping(target = "mediaAssets", expression = "java(toMediaAssetResponses(entity.getMediaAssets()))")
    public abstract RoomTypeResponse toRoomTypeResponse(RoomType entity);

    /** Internal eligibility facts only; never returned as a public room representation. */
    @Named("searchCandidate")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "basePrice", source = "basePrice")
    @Mapping(target = "capacity", expression = "java(readCapacity(entity.getCapacity()))")
    @Mapping(target = "isActive", source = "isActive")
    @Mapping(target = "totalRooms", source = "totalRooms")
    @Mapping(target = "hotelId", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "amenities", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    @Mapping(target = "description", ignore = true)
    public abstract RoomTypeResponse toSearchCandidateResponse(RoomType entity);


    // -------- JSON helper methods --------

    @Named("jsonColumn")
    protected String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected CapacityDto readCapacity(String json) {
        try { return objectMapper.readValue(json, CapacityDto.class); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected List<String> readAmenities(String json) {
        try { return objectMapper.readValue(json, List.class); }
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
}
