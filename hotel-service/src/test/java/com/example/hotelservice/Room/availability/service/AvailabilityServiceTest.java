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
    private final com.example.hotelservice.Hotel.repository.HotelRepository hotels = mock(com.example.hotelservice.Hotel.repository.HotelRepository.class);
    private final AvailabilityService service = new AvailabilityService(rooms, hotels, availability, holds, new HoldExpiryProperties());
    private final UUID roomId = UUID.randomUUID();
    private final LocalDate checkIn = LocalDate.of(2027, 1, 10);
    private final LocalDate checkOut = checkIn.plusDays(2);
    private RoomType room;

    @BeforeEach void setUp() {
        when(availability.findLoadForStay(any(),any(),any())).thenReturn(load(0,0));
        room = new RoomType();
        room.setId(roomId);
        room.setHotelId(UUID.randomUUID());
        room.setIsActive(true);
        when(hotels.findById(room.getHotelId())).thenReturn(Optional.of(com.example.hotelservice.Hotel.entity.Hotel.builder()
                .isActive(true).approvedStatus(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED).build()));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
    }

    @Test void correlatedHoldRetryReturnsOriginalWithoutIncrementingInventoryOrRenewingExpiry() {
        var command = UUID.randomUUID();
        var original = new RoomHold();
        original.setId(UUID.randomUUID()); original.setCommandId(command); original.setRoomTypeId(roomId);
        original.setCheckInDate(checkIn); original.setCheckOutDate(checkOut); original.setQuantity(1);
        original.setStatus(HoldStatus.HELD); original.setExpiresAt(java.time.Instant.now().plusSeconds(600));
        when(holds.findByCommandId(command)).thenReturn(Optional.of(original));
        var response = service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1, command));
        assertThat(response.holdId()).isEqualTo(original.getId());
        assertThat(response.expiresAt()).isEqualTo(original.getExpiresAt());
        verifyNoInteractions(availability, rooms);
        verify(holds, never()).save(any());
    }

    @Test void correlatedHoldRejectsDifferentRoomOrQuantity() {
        var command = UUID.randomUUID();
        var original = new RoomHold(); original.setRoomTypeId(UUID.randomUUID());
        when(holds.findByCommandId(command)).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1, command)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(availability, rooms);
        verify(holds, never()).save(any());
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

    @Test void newHoldDeadlineUsesDatabaseSafePrecisionBeforeItsFirstResponse() {
        room.setTotalRooms(10);
        when(availability.save(any())).thenAnswer(call -> call.getArgument(0));
        when(holds.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1, UUID.randomUUID()));
        assertThat(response.expiresAt().getNano() % 1_000_000).isZero();
        verify(holds).save(org.mockito.ArgumentMatchers.argThat(hold -> hold.getExpiresAt().equals(response.expiresAt())));
    }

    @Test void availabilityUsesMinimumAcrossStayAndPreservesExistingCounters() {
        room.setTotalRooms(10);
        RoomAvailability first = new RoomAvailability();
        first.setHeld(2); first.setBooked(3);
        RoomAvailability second = new RoomAvailability();
        second.setHeld(3); second.setBooked(5);
        when(availability.findLoadForStay(roomId, checkIn, checkOut)).thenReturn(load(8,0));
        assertThat(service.getAvailableRoomCount(roomId, checkIn, checkOut)).isEqualTo(2);
        assertThat(service.isAvailable(roomId, checkIn, checkOut, 3)).isFalse();
        assertThat(second.getHeld()).isEqualTo(3);
        assertThat(second.getBooked()).isEqualTo(5);
        verify(availability, never()).save(any());
        verifyNoInteractions(holds);
    }

    @Test void rangeReadClampsOvercommitButRejectsInconsistentNegativeCounters() {
        room.setTotalRooms(10);
        when(availability.findLoadForStay(roomId, checkIn, checkOut)).thenReturn(load(4_294_967_294L,0));
        assertThat(service.getAvailableRoomCount(roomId, checkIn, checkOut)).isZero();
        when(availability.findLoadForStay(roomId, checkIn, checkOut)).thenReturn(load(4,1));
        assertThatThrownBy(()->service.getAvailableRoomCount(roomId, checkIn, checkOut)).isInstanceOf(InventoryInconsistentException.class);
        verify(availability, never()).save(any());
        verifyNoInteractions(holds);
    }

    private RoomAvailabilityRepository.StayLoad load(long peak,long invalid) {
        return new RoomAvailabilityRepository.StayLoad() {
            public Long getPeakOccupied() { return peak; }
            public Long getInvalidCounters() { return invalid; }
        };
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.example.hotelservice.Hotel.enums.HotelApprovalStatus.class, names = {"DRAFT", "PENDING", "REJECTED"})
    void unpublishedParentCannotAdvertiseOrCreateNewHold(com.example.hotelservice.Hotel.enums.HotelApprovalStatus state) {
        room.setTotalRooms(4);
        when(hotels.findById(room.getHotelId())).thenReturn(Optional.of(com.example.hotelservice.Hotel.entity.Hotel.builder()
                .isActive(true).approvedStatus(state).build()));
        assertThatThrownBy(() -> service.getAvailableRoomCount(roomId, checkIn, checkOut)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> service.isAvailable(roomId, checkIn, checkOut, 1)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1))).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verifyNoInteractions(availability, holds);
    }
    @Test void inactiveRoomOrMissingParentCannotAdvertiseInventory() {
        room.setTotalRooms(4); room.setIsActive(false);
        assertThatThrownBy(() -> service.getAvailableRoomCount(roomId, checkIn, checkOut)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        room.setIsActive(true);
        when(hotels.findById(room.getHotelId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkAndHold(new HoldRequest(roomId, checkIn, checkOut, 1))).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verifyNoInteractions(availability, holds);
    }

}
