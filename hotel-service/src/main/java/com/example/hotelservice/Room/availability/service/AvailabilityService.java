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
import org.springframework.scheduling.annotation.Scheduled;
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
        RoomType roomType = loadRoomType(request.roomTypeId());
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
        RoomHold hold = roomHoldRepository.findByIdAndStatus(holdId, HoldStatus.HELD)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found or already finalized"));
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        int totalRooms = requireTotalRooms(roomType);

        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            if (availability.getHeld() < hold.getQuantity()) {
                throw new IllegalStateException("Hold has insufficient held rooms for date: " + date);
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
        RoomHold hold = roomHoldRepository.findByIdAndStatus(holdId, HoldStatus.HELD)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found or already finalized"));
        RoomType roomType = loadRoomType(hold.getRoomTypeId());

        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            int newHeld = availability.getHeld() - hold.getQuantity();
            if (newHeld < 0) newHeld = 0;
            availability.setHeld(newHeld);
            roomAvailabilityRepository.save(availability);
        }

        hold.setStatus(HoldStatus.RELEASED);
        roomHoldRepository.save(hold);
        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());

    }

    @Transactional(readOnly = true)
    public HoldResponse getHold(UUID holdId) {
        RoomHold hold = roomHoldRepository.findById(holdId)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found"));
        return new HoldResponse(hold.getId(), hold.getStatus(), hold.getExpiresAt());
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
        // If totalRooms is not configured, use a sensible default of 10
        if (totalRooms == null || totalRooms <= 0) {
            // Log warning but don't fail - use default of 10
            System.err.println("Warning: Room type " + roomType.getId() + " has no totalRooms configured, using default of 10");
            return 10;
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

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void cleanupExpiredHolds() {
        List<RoomHold> expiredHolds = roomHoldRepository.findExpiredHolds(Instant.now());
        for (RoomHold hold : expiredHolds) {
            releaseHoldInternal(hold);
        }
    }

    private void releaseHoldInternal(RoomHold hold) {
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            int newHeld = availability.getHeld() - hold.getQuantity();
            if (newHeld < 0) newHeld = 0;
            availability.setHeld(newHeld);
            roomAvailabilityRepository.save(availability);
        }
        hold.setStatus(HoldStatus.RELEASED);
        roomHoldRepository.save(hold);
    }
}
