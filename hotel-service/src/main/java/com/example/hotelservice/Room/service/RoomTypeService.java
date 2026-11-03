package com.example.hotelservice.Room.service;

import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.entity.RoomType;

import java.util.List;
import java.util.UUID;

public interface RoomTypeService {
    RoomType createRoomType(RoomTypeCreateRequest request, UUID ownerId);

    RoomType updateRoomType(UUID roomTypeId, RoomTypeUpdateRequest request, UUID ownerId);

    RoomType getById(UUID roomTypeId);

    List<RoomType> getByHotel(UUID hotelId);

    RoomType getPublicById(UUID roomTypeId);
    List<RoomType> getPublicByHotel(UUID hotelId);
    RoomType getPreviewById(UUID roomTypeId, UUID actorId, boolean admin);
    List<RoomType> getPreviewByHotel(UUID hotelId, UUID actorId, boolean admin);

    void activateRoomType(UUID roomTypeId, UUID ownerId);

    void deactivateRoomType(UUID roomTypeId, UUID ownerId);
}
