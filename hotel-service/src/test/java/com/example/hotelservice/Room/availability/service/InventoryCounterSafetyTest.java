package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.availability.dto.HoldRequest;
import com.example.hotelservice.Room.availability.entity.RoomAvailability;
import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.enums.HoldStatus;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryCounterSafetyTest {
    final RoomTypeRepository rooms=mock(RoomTypeRepository.class);
    final HotelRepository hotels=mock(HotelRepository.class);
    final RoomAvailabilityRepository counters=mock(RoomAvailabilityRepository.class);
    final RoomHoldRepository holds=mock(RoomHoldRepository.class);
    final AvailabilityService service=new AvailabilityService(rooms,hotels,counters,holds,new HoldExpiryProperties());
    final UUID roomId=UUID.randomUUID(),hotelId=UUID.randomUUID(),holdId=UUID.randomUUID();
    final LocalDate start=LocalDate.of(2028,2,1),end=start.plusDays(1);
    final RoomAvailability row=new RoomAvailability();
    final RoomHold hold=new RoomHold();
    @BeforeEach void setup() {
        var room=new RoomType();room.setId(roomId);room.setHotelId(hotelId);room.setIsActive(true);room.setTotalRooms(10);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));when(rooms.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(hotels.findById(hotelId)).thenReturn(Optional.of(Hotel.builder().id(hotelId).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build()));
        row.setRoomTypeId(roomId);row.setDate(start);row.setHeld(1);row.setBooked(1);
        when(counters.findByRoomTypeIdAndDate(roomId,start)).thenReturn(Optional.of(row));
        when(counters.save(any())).thenAnswer(call->call.getArgument(0));
        when(holds.save(any())).thenAnswer(call->{RoomHold value=call.getArgument(0);if(value.getId()==null)value.setId(UUID.randomUUID());return value;});
        hold.setId(holdId);hold.setRoomTypeId(roomId);hold.setHotelId(hotelId);hold.setQuantity(1);hold.setStatus(HoldStatus.HELD);
        hold.setCheckInDate(start);hold.setCheckOutDate(end);hold.setExpiresAt(Instant.now().plusSeconds(300));
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
    }
    @Test void overcommittedIntegerCountersCannotWrapIntoAvailableSupply() {
        row.setHeld(Integer.MAX_VALUE);row.setBooked(Integer.MAX_VALUE);
        assertThatThrownBy(()->service.checkAndHold(new HoldRequest(roomId,start,end,1))).isInstanceOf(InsufficientInventoryException.class);
        assertThat(row.getHeld()).isEqualTo(Integer.MAX_VALUE);assertThat(row.getBooked()).isEqualTo(Integer.MAX_VALUE);
        verify(counters,never()).save(any());verify(holds,never()).save(any());
    }
    @Test void negativeHeldCounterCannotWrapDuringRelease() {
        row.setHeld(Integer.MIN_VALUE);
        assertThatThrownBy(()->service.releaseHold(holdId)).isInstanceOf(IllegalStateException.class);
        assertThat(row.getHeld()).isEqualTo(Integer.MIN_VALUE);assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        verify(counters,never()).save(any());verify(holds,never()).save(any());
    }
    @Test void confirmationCannotFinalizeAnAlreadyOvercommittedLoad() {
        row.setHeld(11);row.setBooked(0);
        assertThatThrownBy(()->service.confirmHold(holdId)).isInstanceOf(InventoryInconsistentException.class);
        assertThat(row.getHeld()).isEqualTo(11);assertThat(row.getBooked()).isZero();assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        verify(counters,never()).save(any());verify(holds,never()).save(any());
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> invalidPersistedQuantities() {
        return Stream.of("confirm","release","cancel","expire").flatMap(operation->Stream.of(-1,0)
                .map(quantity->org.junit.jupiter.params.provider.Arguments.of(operation,quantity)));
    }
    @ParameterizedTest @MethodSource("invalidPersistedQuantities")
    void invalidPersistedQuantityRequiresReviewInsteadOfChangingCounters(String operation,int quantity) {
        hold.setQuantity(quantity);
        if(operation.equals("cancel"))hold.setStatus(HoldStatus.CONFIRMED);
        if(operation.equals("expire"))hold.setExpiresAt(Instant.now().minusSeconds(10));
        HoldStatus before=hold.getStatus();
        assertThatThrownBy(()->{
            switch(operation) {
                case "confirm" -> service.confirmHold(holdId);
                case "release" -> service.releaseHold(holdId);
                case "cancel" -> service.cancelReservation(holdId);
                case "expire" -> service.expireHold(holdId);
                default -> throw new AssertionError("Unknown operation");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(row.getHeld()).isEqualTo(1);assertThat(row.getBooked()).isEqualTo(1);assertThat(hold.getStatus()).isEqualTo(before);
        verify(counters,never()).save(any());verify(holds,never()).save(any());
    }
}
