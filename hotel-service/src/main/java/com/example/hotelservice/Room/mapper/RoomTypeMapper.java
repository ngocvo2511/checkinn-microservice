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


    // ---------------- CreateRoomTypeRequest -> RoomType ----------------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "capacity", expression = "java(writeJson(request.capacity()))")
    @Mapping(target = "amenities", expression = "java(writeJson(request.amenities()))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract RoomType toRoomType(RoomTypeCreateRequest request);


    // ---------------- RoomType -> RoomTypeResponse (record) ----------------

    @Mapping(target = "capacity", expression = "java(readCapacity(entity.getCapacity()))")
    @Mapping(target = "amenities", expression = "java(readAmenities(entity.getAmenities()))")
    @Mapping(target = "mediaAssets", ignore = true)
    public abstract RoomTypeResponse toRoomTypeResponse(RoomType entity);


    // -------- JSON helper methods --------

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
}
