package com.example.revenueservice.client;

import com.example.revenueservice.grpc.GetHotelByIdResponse;
import com.example.revenueservice.grpc.HotelAddress;
import com.example.revenueservice.grpc.RoomTypeBasic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Existing report facade now reads minimal authenticated historical context. */
@Component
@RequiredArgsConstructor
public class HotelGrpcClient {
    private final InternalHotelReportClient catalog;
    public HotelOwnerInfo getHotelInfo(String id) {
        if (id == null) return new HotelOwnerInfo(null, null, null);
        var hotel = catalog.hotel(id);
        return new HotelOwnerInfo(hotel.name(), hotel.ownerId(), hotel.city());
    }
    public GetHotelByIdResponse getHotelById(String id) {
        var hotel = catalog.hotel(id);
        var result = GetHotelByIdResponse.newBuilder().setId(hotel.id()).setOwnerId(hotel.ownerId())
                .setName(hotel.name() == null ? "" : hotel.name())
                .setAddress(HotelAddress.newBuilder().setCity(hotel.city() == null ? "" : hotel.city()));
        if (hotel.roomTypes() == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Historical room capacity unavailable");
        for (var room : hotel.roomTypes()) {
            if (room.totalRooms() == null) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Historical room capacity unavailable");
            result.addRoomTypes(RoomTypeBasic.newBuilder().setId(room.id()).setTotal(room.totalRooms()));
        }
        return result.build();
    }
    public record HotelOwnerInfo(String hotelName, String ownerId, String city) {}
}
