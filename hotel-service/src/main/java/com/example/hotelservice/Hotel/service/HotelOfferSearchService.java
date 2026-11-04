package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelOfferCriteria;
import com.example.hotelservice.Hotel.dto.response.HotelOfferResponse.RoomOffer;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Room.availability.service.AvailabilityService;
import com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelOfferSearchService {
    private final AvailabilityService availability;

    public Optional<RoomOffer> match(HotelResponse hotel, HotelOfferCriteria criteria) {
        criteria.validate();
        if (hotel.roomTypes() == null) return Optional.empty();
        return hotel.roomTypes().stream().filter(r -> Boolean.TRUE.equals(r.isActive()))
            .filter(r -> r.capacity() != null && r.capacity().adults() > 0 && r.capacity().children() >= 0)
            .filter(r -> (long) criteria.roomCount() * r.capacity().adults() >= criteria.adultCount()
                      && (long) criteria.roomCount() * r.capacity().children() >= criteria.childCount())
            .filter(r -> r.basePrice() != null && r.basePrice().signum() > 0)
            .filter(r -> (criteria.minPrice() == null || r.basePrice().compareTo(criteria.minPrice()) >= 0)
                      && (criteria.maxPrice() == null || r.basePrice().compareTo(criteria.maxPrice()) <= 0))
            .map(r -> {
                if (r.totalRooms() == null || r.totalRooms() <= 0) throw new InventoryNotConfiguredException();
                Integer count = criteria.checkIn() == null ? null : availability.getAvailableRoomCount(r.id(),criteria.checkIn(),criteria.checkOut());
                int supply = count == null ? r.totalRooms() : count;
                if (supply < criteria.roomCount()) return null;
                return new RoomOffer(r.id(),criteria.roomCount(),r.basePrice(),r.basePrice().multiply(BigDecimal.valueOf(criteria.roomCount())),
                    count,criteria.checkIn(),criteria.checkOut(),count == null ? null : Instant.now());
            }).filter(java.util.Objects::nonNull)
            .min(Comparator.comparing(RoomOffer::unitPrice).thenComparing(o -> o.roomTypeId().toString()));
    }
}
