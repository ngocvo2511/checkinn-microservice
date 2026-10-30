package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Room.availability.dto.HoldRequest;
import com.example.hotelservice.Room.availability.entity.RoomAvailability;
import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.enums.HoldStatus;
import com.example.hotelservice.Room.availability.controller.AvailabilityController;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AvailabilityServiceTest {
    private final RoomTypeRepository rooms = mock(RoomTypeRepository.class);
    private final RoomAvailabilityRepository availability = mock(RoomAvailabilityRepository.class);
    private final RoomHoldRepository holds = mock(RoomHoldRepository.class);
    private final AvailabilityService service = new AvailabilityService(rooms, availability, holds, new HoldExpiryProperties());
    private final UUID roomId = UUID.randomUUID();
    private final LocalDate checkIn = LocalDate.of(2027, 1, 10);
    private final LocalDate checkOut = checkIn.plusDays(2);
    private RoomType room;

    @BeforeEach void setUp() {
        room = new RoomType();
        room.setId(roomId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1})
    void invalidInventoryCannotBeAdvertisedAsTenRooms(Integer total) {
        room.setTotalRooms(total);
        assertThatThrownBy(() -> service.getAvailableRoomCount(roomId, checkIn, checkOut))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inventory");
        assertThatThrownBy(() -> service.isAvailable(roomId, checkIn, checkOut, 1))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(availability, holds);
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1})
    void invalidInventoryCannotCreateHoldOrTouchCounters(Integer total) {
        room.setTotalRooms(total);
        assertThatThrownBy(() -> service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inventory");
        verifyNoInteractions(availability, holds);
    }

    @Test void explicitlyConfiguredTenRoomsRemainValid() {
        room.setTotalRooms(10);
        assertThat(service.getAvailableRoomCount(roomId, checkIn, checkOut)).isEqualTo(10);
        assertThat(service.isAvailable(roomId, checkIn, checkOut, 10)).isTrue();
        assertThat(service.isAvailable(roomId, checkIn, checkOut, 11)).isFalse();
    }

    @Test void availabilityUsesMinimumAcrossStayAndPreservesExistingCounters() {
        room.setTotalRooms(10);
        RoomAvailability first = new RoomAvailability();
        first.setHeld(2); first.setBooked(3);
        RoomAvailability second = new RoomAvailability();
        second.setHeld(3); second.setBooked(5);
        when(availability.findByRoomTypeIdAndDateReadOnly(roomId, checkIn)).thenReturn(Optional.of(first));
        when(availability.findByRoomTypeIdAndDateReadOnly(roomId, checkIn.plusDays(1))).thenReturn(Optional.of(second));
        assertThat(service.getAvailableRoomCount(roomId, checkIn, checkOut)).isEqualTo(2);
        assertThat(service.isAvailable(roomId, checkIn, checkOut, 3)).isFalse();
        assertThat(second.getHeld()).isEqualTo(3);
        assertThat(second.getBooked()).isEqualTo(5);
        verify(availability, never()).save(any());
        verifyNoInteractions(holds);
    }

    @Test void missingInventoryReturnsConflictForAvailabilityApi() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(service))
                .setControllerAdvice(new com.example.hotelservice.exception.GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/availability/check").param("roomTypeId", roomId.toString())
                        .param("checkIn", checkIn.toString()).param("checkOut", checkOut.toString()).param("quantity", "1"))
                .andExpect(status().isConflict());
        verifyNoInteractions(availability, holds);
    }

    @Test void missingInventoryReturnsConflictForHoldApi() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(service))
                .setControllerAdvice(new com.example.hotelservice.exception.GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/availability/hold").contentType("application/json")
                        .content("{\"roomTypeId\":\"" + roomId + "\",\"checkInDate\":\"" + checkIn
                                + "\",\"checkOutDate\":\"" + checkOut + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(availability, holds);
    }

    @Test void invalidConfigurationDoesNotPreventReleaseOfExistingHold() {
        UUID holdId = UUID.randomUUID();
        RoomHold hold = new RoomHold();
        hold.setId(holdId); hold.setRoomTypeId(roomId); hold.setQuantity(1);
        hold.setCheckInDate(checkIn); hold.setCheckOutDate(checkIn.plusDays(1)); hold.setStatus(HoldStatus.HELD);
        RoomAvailability counters = new RoomAvailability();
        counters.setHeld(1); counters.setBooked(2);
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(availability.findByRoomTypeIdAndDate(eq(roomId), any())).thenReturn(Optional.of(counters));
        service.releaseHold(holdId);
        assertThat(counters.getHeld()).isZero();
        assertThat(counters.getBooked()).isEqualTo(2);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    }

    @Test void expiredHoldCannotBeConfirmedBeforeScheduledCleanup() {
        room.setTotalRooms(10);
        UUID holdId = UUID.randomUUID();
        RoomHold hold = heldRoom(holdId);
        hold.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        RoomAvailability counters = new RoomAvailability(); counters.setHeld(1);
        when(availability.findByRoomTypeIdAndDate(eq(roomId), any())).thenReturn(Optional.of(counters));
        assertThatThrownBy(() -> service.confirmHold(holdId)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        verifyNoInteractions(availability);
        verify(holds, never()).save(any());
    }

    @Test void successfulConfirmationCanBeRetriedWithoutBookingTwice() {
        room.setTotalRooms(10);
        UUID holdId = UUID.randomUUID();
        RoomHold hold = heldRoom(holdId);
        RoomAvailability counters = new RoomAvailability(); counters.setHeld(1);
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(availability.findByRoomTypeIdAndDate(eq(roomId), any())).thenReturn(Optional.of(counters));
        service.confirmHold(holdId);
        service.confirmHold(holdId);
        assertThat(counters.getBooked()).isEqualTo(1);
        assertThat(counters.getHeld()).isZero();
        verify(availability, times(1)).save(any());
    }

    private RoomHold heldRoom(UUID holdId) {
        RoomHold hold = new RoomHold();
        hold.setId(holdId); hold.setRoomTypeId(roomId); hold.setQuantity(1);
        hold.setCheckInDate(checkIn); hold.setCheckOutDate(checkIn.plusDays(1));
        hold.setStatus(HoldStatus.HELD); hold.setExpiresAt(java.time.Instant.now().plusSeconds(900));
        return hold;
    }
    @Test void confirmedReservationCancellationRestoresBookedRoomsOnce() {
        UUID id = UUID.randomUUID(); RoomHold hold = heldRoom(id); hold.setStatus(HoldStatus.CONFIRMED);
        RoomAvailability counters = new RoomAvailability(); counters.setBooked(2); counters.setHeld(0);
        when(holds.findByIdForUpdate(id)).thenReturn(Optional.of(hold));
        when(availability.findByRoomTypeIdAndDate(eq(roomId), any())).thenReturn(Optional.of(counters));
        service.cancelReservation(id); service.cancelReservation(id);
        assertThat(counters.getBooked()).isEqualTo(1); assertThat(counters.getHeld()).isZero();
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
        verify(availability, times(1)).save(any());
    }
    @Test void staleCleanupCandidateCannotReleaseAnAlreadyConfirmedHold() {
        UUID id = UUID.randomUUID(); RoomHold hold = heldRoom(id); hold.setStatus(HoldStatus.CONFIRMED);
        hold.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        when(holds.findByIdForUpdate(id)).thenReturn(Optional.of(hold));
        service.expireHold(id);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.CONFIRMED);
        verifyNoInteractions(availability);
    }
    @Test void releasingInconsistentCountersMustNotClampAwayCorruption() {
        UUID id = UUID.randomUUID(); RoomHold hold = heldRoom(id);
        when(holds.findByIdForUpdate(id)).thenReturn(Optional.of(hold));
        when(availability.findByRoomTypeIdAndDate(eq(roomId), any())).thenReturn(Optional.of(new RoomAvailability()));
        assertThatThrownBy(() -> service.releaseHold(id)).isInstanceOf(IllegalStateException.class);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        verify(holds, never()).save(any());
    }
    @Test void releaseOfConfirmedHoldReturnsConflictThroughRealExceptionAdvice() throws Exception {
        UUID id = UUID.randomUUID(); RoomHold hold = heldRoom(id); hold.setStatus(HoldStatus.CONFIRMED);
        when(holds.findByIdForUpdate(id)).thenReturn(Optional.of(hold));
        var mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(service))
                .setControllerAdvice(new com.example.hotelservice.exception.GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/availability/hold/" + id + "/release")).andExpect(status().isConflict());
        verifyNoInteractions(availability);
    }
}
