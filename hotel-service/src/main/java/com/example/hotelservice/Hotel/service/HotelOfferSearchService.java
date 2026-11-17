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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HotelOfferSearchService {
    private final AvailabilityService availability;

    public Optional<RoomOffer> match(HotelResponse hotel, HotelOfferCriteria criteria) {
        return match(hotel,criteria,null);
    }

    public Map<UUID,Integer> availabilityFor(List<HotelResponse> hotels, HotelOfferCriteria criteria) {
        criteria.validate();
        if (criteria.checkIn() == null) return Map.of();
        var ids=new java.util.LinkedHashSet<UUID>();
        for(var hotel:hotels)for(var room:eligibleRooms(hotel,criteria)) {
            requireInventory(room);
            ids.add(room.id());
        }
        return availability.getAvailableRoomCounts(List.copyOf(ids),criteria.checkIn(),criteria.checkOut());
    }

    /**
     * Undated searches: one effective-approval read for the eligible rooms of all hotels in the
     * response, instead of one per hotel. Dated searches use the availability snapshot instead.
     */
    public java.util.Set<UUID> approvalFor(List<HotelResponse> hotels, HotelOfferCriteria criteria) {
        criteria.validate();
        if (criteria.checkIn() != null) return java.util.Set.of();
        var ids=new java.util.LinkedHashSet<UUID>();
        for(var hotel:hotels)for(var room:eligibleRooms(hotel,criteria)) {
            requireInventory(room);
            ids.add(room.id());
        }
        return ids.isEmpty() ? java.util.Set.of() : availability.approvedRoomIds(List.copyOf(ids));
    }

    private List<RoomTypeResponse> eligibleRooms(HotelResponse hotel, HotelOfferCriteria criteria) {
        criteria.validate();
        if (hotel.roomTypes() == null) return List.of();
        return hotel.roomTypes().stream().filter(r -> Boolean.TRUE.equals(r.isActive()))
            .filter(r -> r.capacity() != null && r.capacity().adults() != null && r.capacity().children() != null
                      && r.capacity().adults() > 0 && r.capacity().children() >= 0)
            .filter(r -> (long) criteria.roomCount() * r.capacity().adults() >= criteria.adultCount()
                      && (long) criteria.roomCount() * r.capacity().children() >= criteria.childCount())
            .filter(r -> r.basePrice() != null && r.basePrice().signum() > 0)
            .filter(r -> (criteria.minPrice() == null || r.basePrice().compareTo(criteria.minPrice()) >= 0)
                      && (criteria.maxPrice() == null || r.basePrice().compareTo(criteria.maxPrice()) <= 0)).toList();
    }

    private void requireInventory(RoomTypeResponse room) {
        if (room.totalRooms() == null || room.totalRooms() <= 0) throw new InventoryNotConfiguredException();
    }

    public Optional<RoomOffer> match(HotelResponse hotel, HotelOfferCriteria criteria, Map<UUID,Integer> snapshot) {
        return match(hotel,criteria,snapshot,null);
    }

    /** {@code approvedSnapshot} must come from {@link #approvalFor} in the same request; null reads it now. */
    public Optional<RoomOffer> match(HotelResponse hotel, HotelOfferCriteria criteria, Map<UUID,Integer> snapshot,
                                     java.util.Set<UUID> approvedSnapshot) {
        var eligible = eligibleRooms(hotel,criteria);
        if (eligible.isEmpty()) return Optional.empty();
        eligible.forEach(this::requireInventory);
        var ids = eligible.stream().map(RoomTypeResponse::id).toList();
        var approved = criteria.checkIn() != null ? java.util.Set.<UUID>of()
                : approvedSnapshot != null ? approvedSnapshot : availability.approvedRoomIds(ids);
        var counts = criteria.checkIn() == null ? null : snapshot == null
                ? availability.getAvailableRoomCounts(ids, criteria.checkIn(), criteria.checkOut()) : snapshot;
        return eligible.stream()
            .map(r -> {
                requireInventory(r);
                if (criteria.checkIn() == null && !approved.contains(r.id())) return null;
                Integer count = null;
                if (criteria.checkIn() != null) {
                    count = counts.get(r.id());
                }
                if (criteria.checkIn() != null && (count == null || count < 0))
                    throw new IllegalStateException("Availability snapshot is incomplete");
                int supply = count == null ? r.totalRooms() : count;
                if (supply < criteria.roomCount()) return null;
                return new RoomOffer(r.id(),criteria.roomCount(),r.basePrice(),r.basePrice().multiply(BigDecimal.valueOf(criteria.roomCount())),
                    count,criteria.checkIn(),criteria.checkOut(),count == null ? null : Instant.now());
            }).filter(java.util.Objects::nonNull)
            .min(Comparator.comparing(RoomOffer::unitPrice).thenComparing(o -> o.roomTypeId().toString()));
    }
}
