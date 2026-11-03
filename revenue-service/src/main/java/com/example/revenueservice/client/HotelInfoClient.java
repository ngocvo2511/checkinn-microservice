package com.example.revenueservice.client;
import com.example.revenueservice.dto.HotelInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class HotelInfoClient {
    private final InternalHotelReportClient catalog;
    public HotelInfo getHotelInfo(String id) {
        if (id == null) return new HotelInfo(null, null, null);
        var hotel = catalog.hotel(id);
        return new HotelInfo(id, hotel.name(), hotel.ownerId());
    }
}
