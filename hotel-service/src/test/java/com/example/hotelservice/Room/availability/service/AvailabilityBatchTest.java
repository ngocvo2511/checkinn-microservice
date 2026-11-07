package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailabilityBatchTest {
    final RoomTypeRepository rooms=mock(RoomTypeRepository.class);
    final HotelRepository hotels=mock(HotelRepository.class);
    final RoomAvailabilityRepository counters=mock(RoomAvailabilityRepository.class);
    final RoomHoldRepository holds=mock(RoomHoldRepository.class);
    final AvailabilityService service=new AvailabilityService(rooms,hotels,counters,holds,new HoldExpiryProperties());
    final LocalDate start=LocalDate.of(2028,2,1),end=start.plusDays(3);
    final Hotel parent=Hotel.builder().id(UUID.randomUUID()).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
    final Map<UUID,RoomType> known=new HashMap<>();

    @BeforeEach void setup() {
        when(rooms.findAllById(any())).thenAnswer(call->{
            List<RoomType> found=new ArrayList<>();
            for(UUID id:(Iterable<UUID>)call.getArgument(0))if(known.containsKey(id))found.add(known.get(id));
            return found;
        });
        when(hotels.findAllById(any())).thenReturn(List.of(parent));
    }
    RoomType room(Integer total) {
        var room=new RoomType();room.setId(UUID.randomUUID());room.setHotelId(parent.getId());room.setIsActive(true);room.setTotalRooms(total);
        known.put(room.getId(),room);return room;
    }
    RoomAvailabilityRepository.OccupiedRoom peak(UUID id,long value) {
        return new RoomAvailabilityRepository.OccupiedRoom() {
            public UUID getRoomTypeId() { return id; }
            public Long getPeakOccupied() { return value; }
            public Long getInvalidCounters() { return 0L; }
        };
    }
    @Test void roomPeaksStaySeparateAndMissingDatesUseConfiguredStockWithoutWrites() {
        var a=room(5);var b=room(3);
        when(counters.findPeakOccupiedForRooms(any(),eq(start),eq(end))).thenReturn(List.of(peak(a.getId(),2)));
        assertThat(service.getAvailableRoomCounts(List.of(a.getId(),b.getId(),a.getId()),start,end))
                .containsExactlyInAnyOrderEntriesOf(Map.of(a.getId(),3,b.getId(),3));
        verify(counters,never()).save(any());verifyNoInteractions(holds);
    }
    @Test void missingRoomDoesNotBecomeZeroAvailabilityOrPartialSuccess() {
        var a=room(5);
        assertThatThrownBy(()->service.getAvailableRoomCounts(List.of(a.getId(),UUID.randomUUID()),start,end)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(counters,holds);
    }
    @Test void inactiveRoomAndUnpublishedParentFailBeforeCounterReads() {
        var a=room(5);a.setIsActive(false);
        assertThatThrownBy(()->service.getAvailableRoomCounts(List.of(a.getId()),start,end)).isInstanceOf(EntityNotFoundException.class);
        a.setIsActive(true);parent.setApprovedStatus(HotelApprovalStatus.DRAFT);
        assertThatThrownBy(()->service.getAvailableRoomCounts(List.of(a.getId()),start,end)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(counters,holds);
    }
    @ParameterizedTest @NullSource @ValueSource(ints={0,-1})
    void unknownStockRemainsAConfigurationError(Integer total) {
        var a=room(total);
        assertThatThrownBy(()->service.getAvailableRoomCounts(List.of(a.getId()),start,end)).isInstanceOf(InventoryNotConfiguredException.class);
        verifyNoInteractions(counters,holds);
    }
    @Test void emptyAndInvalidInputsDoNotReadRepositories() {
        assertThat(service.getAvailableRoomCounts(List.of(),start,end)).isEmpty();
        assertThatThrownBy(()->service.getAvailableRoomCounts(null,start,end)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.getAvailableRoomCounts(java.util.Arrays.asList((UUID)null),start,end)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.getAvailableRoomCounts(List.of(),end,start)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rooms,hotels,counters,holds);
    }
    @Test void internalSqlChunkingDoesNotTruncateRoomResults() {
        List<UUID> ids=new ArrayList<>();for(int i=0;i<501;i++)ids.add(room(2).getId());
        assertThat(service.getAvailableRoomCounts(ids,start,end)).hasSize(501).allSatisfy((id,count)->assertThat(count).isEqualTo(2));
        verify(rooms,times(2)).findAllById(any());verify(counters,times(2)).findPeakOccupiedForRooms(any(),eq(start),eq(end));
    }
    @Test void largeCountersClampWithoutIntegerOverflow() {
        var a=room(10);
        when(counters.findPeakOccupiedForRooms(any(),eq(start),eq(end))).thenReturn(List.of(peak(a.getId(),4_294_967_294L)));
        assertThat(service.getAvailableRoomCounts(List.of(a.getId()),start,end)).containsEntry(a.getId(),0);
    }
    @Test void inconsistentReadHasConflictDomainCodeInsteadOfClientValidationOrEmptyResults() throws Exception {
        var api=mock(AvailabilityService.class);UUID id=UUID.randomUUID();
        when(api.getAvailableRoomCount(id,start,end)).thenThrow(new InventoryInconsistentException());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.example.hotelservice.Room.availability.controller.AvailabilityController(api))
                .setControllerAdvice(new com.example.hotelservice.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/availability/check")
                .param("roomTypeId",id.toString()).param("checkIn",start.toString()).param("checkOut",end.toString()).param("quantity","1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("INVENTORY_INCONSISTENT"));
    }
}
