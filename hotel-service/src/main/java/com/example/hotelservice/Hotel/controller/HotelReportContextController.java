package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.example.hotelservice.City.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Service-only reporting context; never returns contact, documents, media or coordinates. */
@RestController
@RequestMapping("/internal/reports")
@RequiredArgsConstructor
public class HotelReportContextController {
    private final HotelService hotels;
    private final RoomTypeService rooms;
    private final CityRepository cities;
    public record ReportRoom(UUID id, Integer totalRooms) {}
    public record ReportHotel(UUID id, UUID ownerId, String name, String city, List<ReportRoom> roomTypes) {}
    @GetMapping("/hotels/{id}")
    public org.springframework.http.ResponseEntity<ReportHotel> hotel(@PathVariable UUID id) {
        var hotel = hotels.getById(id);
        String city = cities.findById(hotel.getCityId()).map(com.example.hotelservice.City.entity.City::getName).orElse(null);
        var result = new ReportHotel(id, hotel.getOwnerId(), hotel.getName(), city,
                rooms.getByHotel(id).stream().map(room -> new ReportRoom(room.getId(), room.getTotalRooms())).toList());
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(result);
    }
    @GetMapping("/rooms/{id}")
    public org.springframework.http.ResponseEntity<ReportRoom> room(@PathVariable UUID id) {
        var room = rooms.getById(id);
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new ReportRoom(room.getId(), room.getTotalRooms()));
    }
}
