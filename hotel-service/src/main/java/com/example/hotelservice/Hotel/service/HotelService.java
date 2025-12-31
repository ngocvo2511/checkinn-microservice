package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.dto.response.MyHotelShortResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.entity.Hotel;

import java.util.List;
import java.util.UUID;

public interface HotelService {

    Hotel createHotel(HotelCreateRequest request, UUID ownerId);

    Hotel updateHotel(UUID hotelId, HotelUpdateRequest request, UUID ownerId);

    Hotel getById(UUID hotelId);

    com.example.hotelservice.Hotel.dto.response.HotelResponse getDetail(UUID hotelId);

    List<Hotel> getByOwner(UUID ownerId);

    List<Hotel> getByCity(UUID cityId);

    List<Hotel> getByOwnerAndCity(UUID ownerId, UUID cityId);

    List<Hotel> getPendingHotels();

    PendingHotelDetailResponse getPendingHotelDetail(UUID hotelId);

    void activateHotel(UUID hotelId);

    void deactivateHotel(UUID hotelId);

    void approveHotel(UUID hotelId);

    void rejectHotel(UUID hotelId);
}
