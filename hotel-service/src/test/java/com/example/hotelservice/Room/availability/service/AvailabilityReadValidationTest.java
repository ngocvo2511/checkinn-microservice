package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Batch 9B C2: the public availability read rejects impossible stays before touching any repository. */
class AvailabilityReadValidationTest {
    final RoomTypeRepository rooms = mock(RoomTypeRepository.class);
    final HotelRepository hotels = mock(HotelRepository.class);
    final RoomAvailabilityRepository counters = mock(RoomAvailabilityRepository.class);
    final RoomHoldRepository holds = mock(RoomHoldRepository.class);
    final AvailabilityService service = new AvailabilityService(rooms, hotels, counters, holds, new HoldExpiryProperties());
    final UUID room = UUID.randomUUID();
    final LocalDate day = LocalDate.of(2030, 1, 10);

    @Test void impossibleStaysAreRejectedWithoutRepositoryReads() {
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableRoomCount(room, day, day));
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableRoomCount(room, day.plusDays(2), day));
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableRoomCount(room, null, day));
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableRoomCount(room, day, null));
        assertThrows(IllegalArgumentException.class, () -> service.isAvailable(room, day, day.plusDays(1), 0));
        assertThrows(IllegalArgumentException.class, () -> service.getAvailableRoomCounts(List.of(room), day, day));
        verifyNoInteractions(rooms, hotels, counters, holds);
    }
}
