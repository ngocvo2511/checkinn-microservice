package com.example.revenueservice.client;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class HotelCapacityClient {
    private final InternalHotelReportClient catalog;
    public int getTotalRooms(String id) {
        if (id == null) return 0;
        Integer total = catalog.room(id).totalRooms();
        if (total == null || total < 0) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Historical room capacity unavailable");
        return total;
    }
}
