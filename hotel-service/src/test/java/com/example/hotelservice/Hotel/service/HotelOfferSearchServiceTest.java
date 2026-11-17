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
    final java.util.Map<UUID,Integer> counts = new java.util.HashMap<>();
    @org.junit.jupiter.api.BeforeEach void approvedFixtures() {
        when(availability.approvedRoomIds(anyList())).thenAnswer(c -> new java.util.HashSet<>((List<UUID>)c.getArgument(0)));
        when(availability.getAvailableRoomCounts(anyList(),any(),any())).thenAnswer(c -> {
            var result = new java.util.HashMap<UUID,Integer>();
            for (UUID id : (List<UUID>)c.getArgument(0)) result.put(id, counts.getOrDefault(id,0));
            return result;
        });
    }
    final LocalDate in = LocalDate.of(2026,12,1), out = in.plusDays(2);
    @Test void unapprovedPositiveStockNeverProducesDatedOrUndatedOffer() {
        var r=room(100,2,0,10,true);
        when(availability.approvedRoomIds(List.of(r.id()))).thenReturn(java.util.Set.of());
        assertTrue(service.match(hotel(r),criteria(1,1,0,null,null,false)).isEmpty());
        assertTrue(service.match(hotel(r),criteria(1,1,0,null,null,true)).isEmpty());
        verify(availability,never()).getAvailableRoomCount(any(),any(),any());
    }
    @Test void batchedApprovalIsOneReadForAllHotelsAndExcludesUnapprovedRooms() {
        var approved=room(100,2,0,2,true);var pending=room(50,2,0,2,true);var other=room(200,2,0,2,true);
        var h1=hotel(approved,pending);var h2=hotel(other);
        when(availability.approvedRoomIds(anyList())).thenReturn(java.util.Set.of(approved.id(),other.id()));
        var undated=criteria(1,1,0,null,null,false);
        var snapshot=service.approvalFor(List.of(h1,h2),undated);
        verify(availability,times(1)).approvedRoomIds(List.of(approved.id(),pending.id(),other.id()));
        assertEquals(approved.id(),service.match(h1,undated,java.util.Map.of(),snapshot).orElseThrow().roomTypeId());
        assertEquals(other.id(),service.match(h2,undated,java.util.Map.of(),snapshot).orElseThrow().roomTypeId());
        // The request snapshot replaces per-hotel re-reads; the pending cheaper room is never offered.
        verify(availability,times(1)).approvedRoomIds(anyList());
        assertTrue(service.match(hotel(pending),undated,java.util.Map.of(),snapshot).isEmpty());
    }
    @Test void batchedApprovalSkipsDatedSearchesAndEmptyEligibility() {
        var r=room(100,2,0,2,true);
        assertEquals(java.util.Set.of(),service.approvalFor(List.of(hotel(r)),criteria(1,1,0,null,null,true)));
        assertEquals(java.util.Set.of(),service.approvalFor(List.of(hotel(r)),criteria(1,1,0,500,null,false)));
        verify(availability,never()).approvedRoomIds(anyList());
        var unconfigured=room(100,2,0,0,true);
        assertThrows(com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException.class,
                ()->service.approvalFor(List.of(hotel(unconfigured)),criteria(1,1,0,null,null,false)));
    }
    HotelOfferCriteria criteria(int rooms, int adults, int children, Integer min, Integer max, boolean dated) {
        return new HotelOfferCriteria(null,null,null,"Fixture",dated?in:null,dated?out:null,rooms,adults,children,
            min==null?null:BigDecimal.valueOf(min),max==null?null:BigDecimal.valueOf(max),null,null);
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
    @Test void partialLegacyCapacityCannotAdvertiseOrCrashBeforeTheInventoryRead() {
        for(var capacity:List.of(new CapacityDto(null,0,null,null,null,null),new CapacityDto(2,null,null,null,null,null))){
            var r=new RoomTypeResponse(UUID.randomUUID(),UUID.randomUUID(),"Legacy unknown",BigDecimal.valueOf(100),capacity,List.of(),true,2,null,null,List.of());
            assertTrue(service.match(hotel(r),criteria(1,1,0,null,null,true)).isEmpty());
        }
        verifyNoInteractions(availability);
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
        counts.put(cheap.id(),0);
        counts.put(expensive.id(),2);
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
        assertNull(offer.availableRooms()); assertNull(offer.checkedAt()); verify(availability).approvedRoomIds(anyList());
        verify(availability,never()).getAvailableRoomCount(any(),any(),any());
    }
    @Test void dependencyFailurePropagatesInsteadOfSoldOut() {
        var r=room(100,2,0,3,true);
        when(availability.getAvailableRoomCounts(anyList(),eq(in),eq(out))).thenThrow(new IllegalStateException("unavailable"));
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
        counts.put(r.id(),1);
        assertTrue(service.match(hotel(r),criteria(2,4,0,null,null,true)).isEmpty());
    }

    @Test void pagedSnapshotReadsOnlyEligibleRoomIdsOnceAndKeepsSoldOutPriceSeparate() {
        var wrongCapacity=room(50,1,0,0,true);
        var cheap=room(100,2,0,3,true);var expensive=room(300,2,0,3,true);
        var h=hotel(wrongCapacity,cheap,expensive);var criteria=criteria(2,4,0,null,null,true);
        var snapshot=java.util.Map.of(cheap.id(),0,expensive.id(),2);
        when(availability.getAvailableRoomCounts(List.of(cheap.id(),expensive.id()),in,out)).thenReturn(snapshot);
        assertEquals(snapshot,service.availabilityFor(List.of(h,h),criteria));
        var offer=service.match(h,criteria,snapshot).orElseThrow();
        assertEquals(expensive.id(),offer.roomTypeId());assertEquals(BigDecimal.valueOf(300),offer.unitPrice());assertEquals(2,offer.availableRooms());
        verify(availability,times(1)).getAvailableRoomCounts(List.of(cheap.id(),expensive.id()),in,out);
        verify(availability,never()).getAvailableRoomCount(any(),any(),any());
    }
    @Test void incompleteOrFailedBatchReadCannotBecomeAnEmptyOrUndatedOffer() {
        var r=room(100,2,0,3,true);var h=hotel(r);var criteria=criteria(1,1,0,null,null,true);
        assertThrows(IllegalStateException.class,()->service.match(h,criteria,java.util.Map.of()));
        when(availability.getAvailableRoomCounts(anyList(),eq(in),eq(out))).thenThrow(new IllegalStateException("upstream unavailable"));
        assertThrows(IllegalStateException.class,()->service.availabilityFor(List.of(h),criteria));
    }
    @Test void undatedCandidateCollectionNeverQueriesDatedAvailability() {
        var h=hotel(room(100,2,0,3,true));
        var snapshot=service.availabilityFor(List.of(h),criteria(1,1,0,null,null,false));
        assertTrue(snapshot.isEmpty());assertNull(service.match(h,criteria(1,1,0,null,null,false),snapshot).orElseThrow().availableRooms());
        verify(availability).approvedRoomIds(anyList());
        verify(availability,never()).getAvailableRoomCount(any(),any(),any());
    }
}
