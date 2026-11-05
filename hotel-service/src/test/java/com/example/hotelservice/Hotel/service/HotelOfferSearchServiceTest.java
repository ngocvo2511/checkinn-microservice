package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelOfferCriteria;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.dto.request.CapacityDto;
import com.example.hotelservice.Room.availability.service.AvailabilityService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelOfferSearchServiceTest {
    final AvailabilityService availability = mock(AvailabilityService.class);
    final HotelOfferSearchService service = new HotelOfferSearchService(availability);
    final LocalDate in = LocalDate.of(2026,12,1), out = in.plusDays(2);
    HotelOfferCriteria criteria(int rooms, int adults, int children, Integer min, Integer max, boolean dated) {
        return new HotelOfferCriteria(null,null,null,"Fixture",dated?in:null,dated?out:null,rooms,adults,children,
            min==null?null:BigDecimal.valueOf(min),max==null?null:BigDecimal.valueOf(max),null);
    }
    RoomTypeResponse room(int price, int adults, int children, int total, boolean active) {
        return new RoomTypeResponse(UUID.randomUUID(),UUID.randomUUID(),"Room",BigDecimal.valueOf(price),
            new CapacityDto(adults,children,null,null,null,null),List.of(),active,total,null,null,List.of());
    }
    HotelResponse hotel(RoomTypeResponse... rooms) {
        HotelResponse h = mock(HotelResponse.class);
        when(h.roomTypes()).thenReturn(List.of(rooms));
        return h;
    }
    @Test void requestedRoomCountRatherThanEntireInventoryDeterminesCapacity() {
        var r=room(100,2,0,10,true);
        assertTrue(service.match(hotel(r),criteria(1,4,0,null,null,false)).isEmpty());
        assertEquals(2,service.match(hotel(r),criteria(2,4,0,null,null,false)).orElseThrow().quantity());
    }
    @Test void childrenAndAdultsMustFitSameType() {
        var a=room(100,4,0,10,true); var b=room(200,1,2,10,true);
        assertTrue(service.match(hotel(a,b),criteria(1,4,2,null,null,false)).isEmpty());
    }
    @Test void soldOutCheapestDoesNotSetDisplayedPrice() {
        var cheap=room(100,2,0,3,true); var expensive=room(300,2,0,3,true);
        when(availability.getAvailableRoomCount(cheap.id(),in,out)).thenReturn(0);
        when(availability.getAvailableRoomCount(expensive.id(),in,out)).thenReturn(2);
        var offer=service.match(hotel(cheap,expensive),criteria(2,4,0,null,null,true)).orElseThrow();
        assertEquals(expensive.id(),offer.roomTypeId()); assertEquals(BigDecimal.valueOf(300),offer.unitPrice());
        assertEquals(BigDecimal.valueOf(600),offer.totalPerNight()); assertEquals(2,offer.availableRooms());
        assertEquals(in,offer.checkIn()); assertEquals(out,offer.checkOut()); assertNotNull(offer.checkedAt());
    }
    @Test void priceBoundsAreInclusiveAndEvaluateEachRoom() {
        var cheap=room(100,2,0,3,true); var valid=room(300,2,0,3,true);
        assertEquals(valid.id(),service.match(hotel(cheap,valid),criteria(1,1,0,300,300,false)).orElseThrow().roomTypeId());
        assertTrue(service.match(hotel(cheap,valid),criteria(1,1,0,101,299,false)).isEmpty());
    }
    @Test void capacityAndPriceCannotComeFromDifferentTypes() {
        assertTrue(service.match(hotel(room(100,1,0,3,true),room(300,4,0,3,true)),criteria(1,4,0,0,200,false)).isEmpty());
    }
    @Test void inactiveRoomsAreNeverOffers() {
        assertTrue(service.match(hotel(room(100,2,0,3,false)),criteria(1,1,0,null,null,false)).isEmpty());
        verifyNoInteractions(availability);
    }
    @Test void nonPositivePricesCannotAdvertiseRoomsRejectedByBooking() {
        assertTrue(service.match(hotel(room(0,2,0,3,true),room(-100,2,0,3,true)),
                criteria(1,1,0,null,null,false)).isEmpty());
        verifyNoInteractions(availability);
    }
    @Test void undatedOfferDoesNotPretendConfiguredInventoryIsAvailability() {
        var offer=service.match(hotel(room(100,2,0,3,true)),criteria(1,1,0,null,null,false)).orElseThrow();
        assertNull(offer.availableRooms()); assertNull(offer.checkedAt()); verifyNoInteractions(availability);
    }
    @Test void dependencyFailurePropagatesInsteadOfSoldOut() {
        var r=room(100,2,0,3,true);
        when(availability.getAvailableRoomCount(r.id(),in,out)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,()->service.match(hotel(r),criteria(1,1,0,null,null,true)));
    }
    @Test void invalidCriteriaAreRejected() {
        assertThrows(IllegalArgumentException.class,()->criteria(0,1,0,null,null,false).validate());
        assertThrows(IllegalArgumentException.class,()->criteria(1,0,0,null,null,false).validate());
        assertThrows(IllegalArgumentException.class,()->criteria(1,1,-1,null,null,false).validate());
        assertThrows(IllegalArgumentException.class,()->criteria(1,1,0,300,100,false).validate());
        assertThrows(IllegalArgumentException.class,()->criteria(1,1,0,-1,null,false).validate());
    }
    @Test void missingInventoryIsUnknownInsteadOfAnOffer() {
        var r=room(100,2,0,0,true);
        assertThrows(com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException.class,
            ()->service.match(hotel(r),criteria(1,1,0,null,null,false)));
    }
    @Test void oneRoomShortOnAnyStayNightExcludesOffer() {
        var r=room(100,2,0,3,true);
        when(availability.getAvailableRoomCount(r.id(),in,out)).thenReturn(1);
        assertTrue(service.match(hotel(r),criteria(2,4,0,null,null,true)).isEmpty());
    }
}
