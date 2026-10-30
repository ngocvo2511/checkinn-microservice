package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Room.availability.dto.HoldRequest;
import com.example.hotelservice.Room.availability.dto.HoldResponse;
import com.example.hotelservice.Room.availability.entity.RoomAvailability;
import com.example.hotelservice.Room.availability.entity.RoomHold;
import com.example.hotelservice.Room.availability.enums.HoldStatus;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityRepository roomAvailabilityRepository;
    private final RoomHoldRepository roomHoldRepository;
    private final HoldExpiryProperties holdExpiryProperties;

    @Transactional(readOnly = true)
    public boolean isAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int quantity) {
        validateDates(checkIn, checkOut);
        validateQuantity(quantity);
        RoomType roomType = loadRoomType(roomTypeId);
        int totalRooms = requireTotalRooms(roomType);

        for (LocalDate date : expandDates(checkIn, checkOut)) {
            RoomAvailability availability = roomAvailabilityRepository.findByRoomTypeIdAndDateReadOnly(roomTypeId, date)
                    .orElse(null);
            int held = availability != null ? availability.getHeld() : 0;
            int booked = availability != null ? availability.getBooked() : 0;
            if (totalRooms - held - booked < quantity) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public int getAvailableRoomCount(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn, checkOut);
        RoomType roomType = loadRoomType(roomTypeId);
        int totalRooms = requireTotalRooms(roomType);

        int minAvailable = totalRooms;
        for (LocalDate date : expandDates(checkIn, checkOut)) {
            RoomAvailability availability = roomAvailabilityRepository.findByRoomTypeIdAndDateReadOnly(roomTypeId, date)
                    .orElse(null);
            int held = availability != null ? availability.getHeld() : 0;
            int booked = availability != null ? availability.getBooked() : 0;
            int available = totalRooms - held - booked;
            if (available < minAvailable) {
                minAvailable = available;
            }
        }
        return Math.max(0, minAvailable);
    }

    @Transactional
    public HoldResponse checkAndHold(HoldRequest request) {
        validateDates(request.checkInDate(), request.checkOutDate());
        validateQuantity(request.quantity());
        // Serialize first-row creation as well as existing availability rows for this room type.
        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.roomTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
        int totalRooms = requireTotalRooms(roomType);

        List<LocalDate> dates = expandDates(request.checkInDate(), request.checkOutDate());

        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            int remaining = totalRooms - availability.getHeld() - availability.getBooked();
            if (remaining < request.quantity()) {
                throw new IllegalArgumentException("Not enough rooms on date: " + date);
            }
        }

        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            availability.setHeld(availability.getHeld() + request.quantity());
            roomAvailabilityRepository.save(availability);
        }

        RoomHold hold = new RoomHold();
        hold.setRoomTypeId(roomType.getId());
        hold.setHotelId(roomType.getHotelId());
        hold.setCheckInDate(request.checkInDate());
        hold.setCheckOutDate(request.checkOutDate());
        hold.setQuantity(request.quantity());
        hold.setStatus(HoldStatus.HELD);
        hold.setExpiresAt(calculateExpiryTime());
        hold = roomHoldRepository.save(hold);

        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());
    }


    @Transactional
    public HoldResponse confirmHold(UUID holdId) {
        log.info("Confirming hold: {}", holdId);
        RoomHold hold = lockHold(holdId);
        if (hold.getStatus() == HoldStatus.CONFIRMED) return response(hold);
        if (hold.getStatus() != HoldStatus.HELD) throw new IllegalStateException("Hold is already finalized");
        if (hold.getExpiresAt() == null || !hold.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Hold expired before confirmation");
        }
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        int totalRooms = requireTotalRooms(roomType);

        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            if (availability.getHeld() < hold.getQuantity()) {
                throw new IllegalStateException("Hold has insufficient held rooms for date: " + date);
            }
            if (availability.getBooked() < 0 || (long) availability.getBooked() + hold.getQuantity() > totalRooms) {
                throw new IllegalStateException("Confirmed rooms would exceed configured inventory");
            }
            int oldHeld = availability.getHeld();
            int oldBooked = availability.getBooked();
            availability.setHeld(availability.getHeld() - hold.getQuantity());
            availability.setBooked(availability.getBooked() + hold.getQuantity());
            roomAvailabilityRepository.save(availability);
            log.info("Updated availability for date {}: held {} -> {}, booked {} -> {}", 
                    date, oldHeld, availability.getHeld(), oldBooked, availability.getBooked());
        }

        hold.setStatus(HoldStatus.CONFIRMED);
        roomHoldRepository.save(hold);
        log.info("Hold {} confirmed successfully", holdId);
        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());
    }

    @Transactional
    public HoldResponse releaseHold(UUID holdId) {
        RoomHold hold = lockHold(holdId);
        if (hold.getStatus() == HoldStatus.RELEASED) return response(hold);
        if (hold.getStatus() != HoldStatus.HELD) throw new IllegalStateException("Only an active hold can be released");
        releaseHoldInternal(hold);
        return response(hold);
    }

    @Transactional(readOnly = true)
    public HoldResponse getHold(UUID holdId) {
        RoomHold hold = roomHoldRepository.findById(holdId)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found"));
        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());
    }

    @Transactional
    public HoldResponse cancelReservation(UUID holdId) {
        RoomHold hold = lockHold(holdId);
        if (hold.getStatus() == HoldStatus.RELEASED) return response(hold);
        if (hold.getStatus() == HoldStatus.HELD) {
            releaseHoldInternal(hold);
            return response(hold);
        }
        if (hold.getStatus() != HoldStatus.CONFIRMED) throw new IllegalStateException("Unsupported reservation state");
        for (var date : expandDates(hold.getCheckInDate(), hold.getCheckOutDate())) {
            var counters = getAvailabilityForUpdate(hold.getRoomTypeId(), date);
            if (counters.getBooked() < hold.getQuantity()) throw new IllegalStateException("Booked counters are inconsistent");
            counters.setBooked(counters.getBooked() - hold.getQuantity());
            roomAvailabilityRepository.save(counters);
        }
        hold.setStatus(HoldStatus.RELEASED);
        roomHoldRepository.save(hold);
        return response(hold);
    }

    private RoomAvailability getAvailabilityForUpdate(UUID roomTypeId, LocalDate date) {
        return roomAvailabilityRepository.findByRoomTypeIdAndDate(roomTypeId, date)
                .orElseGet(() -> {
                    RoomAvailability fresh = new RoomAvailability();
                    fresh.setRoomTypeId(roomTypeId);
                    fresh.setDate(date);
                    fresh.setHeld(0);
                    fresh.setBooked(0);
                    return roomAvailabilityRepository.save(fresh);
                });
    }

    private RoomType loadRoomType(UUID roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
    }

    private int requireTotalRooms(RoomType roomType) {
        Integer totalRooms = roomType.getTotalRooms();
        if (totalRooms == null || totalRooms <= 0) {
            throw new InventoryNotConfiguredException();
        }
        return totalRooms;
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Ngày nhận/trả phòng không hợp lệ");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Ngày trả phải sau ngày nhận");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phòng phải lớn hơn 0");
        }
    }

    private List<LocalDate> expandDates(LocalDate checkIn, LocalDate checkOut) {
        return checkIn.datesUntil(checkOut).toList();
    }

    private Instant calculateExpiryTime() {
        return Instant.now().plusSeconds((long) holdExpiryProperties.getMinutes() * 60);
    }

    @Transactional
    public void expireHold(UUID holdId) {
        RoomHold hold = lockHold(holdId);
        if (hold.getStatus() == HoldStatus.HELD && hold.getExpiresAt() != null
                && !hold.getExpiresAt().isAfter(Instant.now())) releaseHoldInternal(hold);
    }

    private RoomHold lockHold(UUID id) {
        return roomHoldRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found"));
    }

    private HoldResponse response(RoomHold hold) {
        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());
    }

    private void releaseHoldInternal(RoomHold hold) {
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            int newHeld = availability.getHeld() - hold.getQuantity();
            if (newHeld < 0) throw new IllegalStateException("Hold inventory counters are inconsistent");
            availability.setHeld(newHeld);
            roomAvailabilityRepository.save(availability);
        }
        hold.setStatus(HoldStatus.RELEASED);
        roomHoldRepository.save(hold);
    }
}
