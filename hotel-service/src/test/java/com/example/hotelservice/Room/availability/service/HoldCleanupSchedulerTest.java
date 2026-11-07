package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class HoldCleanupSchedulerTest {
    @Test void oneInconsistentHoldDoesNotPreventCleanupOfOtherExpiredHolds() {
        var holds=mock(RoomHoldRepository.class);
        var availability=mock(AvailabilityService.class);
        var invalid=new RoomHold();invalid.setId(UUID.randomUUID());
        var valid=new RoomHold();valid.setId(UUID.randomUUID());
        when(holds.findExpiredHolds(any())).thenReturn(List.of(invalid,valid));
        doThrow(new InventoryInconsistentException()).when(availability).expireHold(invalid.getId());
        new HoldCleanupScheduler(holds,availability).cleanupExpiredHolds();
        verify(availability).expireHold(invalid.getId());
        verify(availability).expireHold(valid.getId());
    }
}
