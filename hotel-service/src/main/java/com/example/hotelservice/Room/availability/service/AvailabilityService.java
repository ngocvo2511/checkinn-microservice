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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository.OccupiedRoom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private static final int READ_BATCH_SIZE = 500;

    private final RoomTypeRepository roomTypeRepository;
    private final com.example.hotelservice.Hotel.repository.HotelRepository hotelRepository;
    private final RoomAvailabilityRepository roomAvailabilityRepository;
    private final RoomHoldRepository roomHoldRepository;
    private final HoldExpiryProperties holdExpiryProperties;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public boolean isAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int quantity) {
        validateQuantity(quantity);
        return getAvailableRoomCount(roomTypeId, checkIn, checkOut) >= quantity;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public int getAvailableRoomCount(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn, checkOut);
        RoomType roomType = loadRoomType(roomTypeId);
        requirePublished(roomType);
        int totalRooms = requireTotalRooms(roomType);

        long peakOccupied = requirePeak(roomAvailabilityRepository.findLoadForStay(roomTypeId, checkIn, checkOut));
        return (int) Math.max(0L, Math.min(totalRooms, (long) totalRooms - peakOccupied));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<UUID,Integer> getAvailableRoomCounts(List<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut) {
        validateDates(checkIn,checkOut);
        if (roomTypeIds == null || roomTypeIds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Room IDs are required");
        var result=new LinkedHashMap<UUID,Integer>();
        var ids=new ArrayList<>(new LinkedHashSet<>(roomTypeIds));
        // Bound SQL parameter sets, not the total number of eligible rooms/results.
        for (int offset=0;offset<ids.size();offset+=READ_BATCH_SIZE) {
            var chunk=ids.subList(offset,Math.min(offset+READ_BATCH_SIZE,ids.size()));
            var rooms=roomTypeRepository.findAllById(chunk);
            var byId=rooms.stream().collect(Collectors.toMap(RoomType::getId,r->r));
            if (!byId.keySet().equals(new HashSet<>(chunk)))
                throw new EntityNotFoundException("Không tìm thấy loại phòng");
            var parentIds=rooms.stream().map(RoomType::getHotelId).collect(Collectors.toSet());
            var parents=hotelRepository.findAllById(parentIds).stream().collect(Collectors.toMap(
                    Hotel::getId,h->h));
            for (var room:rooms) {
                requirePublished(room,parents.get(room.getHotelId()));
                requireTotalRooms(room);
            }
            var peaks=roomAvailabilityRepository.findPeakOccupiedForRooms(chunk,checkIn,checkOut).stream()
                    .collect(Collectors.toMap(
                            OccupiedRoom::getRoomTypeId,
                            this::requirePeak));
            for (var id:chunk) {
                int total=requireTotalRooms(byId.get(id));
                long peak=peaks.getOrDefault(id,0L);
                result.put(id,(int)Math.max(0L,Math.min(total,(long)total-peak)));
            }
        }
        return Map.copyOf(result);
    }

    @Transactional
    public HoldResponse checkAndHold(HoldRequest request) {
        validateDates(request.checkInDate(), request.checkOutDate());
        validateQuantity(request.quantity());
        if (request.commandId() != null) {
            // Lock the command before room/hold rows, including first-row and cross-room races.
            roomHoldRepository.lockCreationCommand(request.commandId());
            RoomHold existing = roomHoldRepository.findByCommandId(request.commandId()).orElse(null);
            if (existing != null) {
                if (!request.roomTypeId().equals(existing.getRoomTypeId())
                        || !request.checkInDate().equals(existing.getCheckInDate())
                        || !request.checkOutDate().equals(existing.getCheckOutDate())
                        || !Integer.valueOf(request.quantity()).equals(existing.getQuantity())) {
                    throw new IllegalStateException("Inventory command already used for different input");
                }
                // Return the original state/deadline, including terminal states. Never renew/rebook it.
                return response(existing);
            }
        }
        // Serialize first-row creation as well as existing availability rows for this room type.
        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.roomTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
        requirePublished(roomType);
        int totalRooms = requireTotalRooms(roomType);

        List<LocalDate> dates = expandDates(request.checkInDate(), request.checkOutDate());

        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            if (availability.getHeld() < 0 || availability.getBooked() < 0) throw new InventoryInconsistentException();
            long remaining = (long) totalRooms - availability.getHeld() - availability.getBooked();
            if (remaining < request.quantity()) {
                throw new InsufficientInventoryException(date);
            }
        }

        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            availability.setHeld(availability.getHeld() + request.quantity());
            roomAvailabilityRepository.save(availability);
        }

        RoomHold hold = new RoomHold();
        hold.setCommandId(request.commandId());
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
        requireHoldQuantity(hold);
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        int totalRooms = requireTotalRooms(roomType);

        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            if (availability.getHeld() < 0 || availability.getBooked() < 0
                    || (long) availability.getHeld() + availability.getBooked() > totalRooms)
                throw new InventoryInconsistentException();
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
        requireHoldQuantity(hold);
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

    private void requirePublished(RoomType room) {
        var hotel = hotelRepository.findById(room.getHotelId()).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
        requirePublished(room,hotel);
    }

    private void requirePublished(RoomType room, com.example.hotelservice.Hotel.entity.Hotel hotel) {
        if (hotel == null || !Boolean.TRUE.equals(room.getIsActive()) || !Boolean.TRUE.equals(hotel.getIsActive())
                || hotel.getApprovedStatus() != com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED)
            throw new EntityNotFoundException("Không tìm thấy loại phòng");
    }

    private int requireTotalRooms(RoomType roomType) {
        Integer totalRooms = roomType.getTotalRooms();
        if (totalRooms == null || totalRooms <= 0) {
            throw new InventoryNotConfiguredException();
        }
        return totalRooms;
    }

    private long requirePeak(RoomAvailabilityRepository.StayLoad load) {
        if (load == null || load.getPeakOccupied() == null || load.getInvalidCounters() == null
                || load.getInvalidCounters() != 0 || load.getPeakOccupied() < 0)
            throw new InventoryInconsistentException();
        return load.getPeakOccupied();
    }

    private void requireHoldQuantity(RoomHold hold) {
        if (hold.getQuantity() == null || hold.getQuantity() <= 0) throw new InventoryInconsistentException();
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
        // PostgreSQL timestamps have less precision than Instant. Persist and return the same deadline.
        return Instant.now().plusSeconds((long) holdExpiryProperties.getMinutes() * 60)
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
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
        requireHoldQuantity(hold);
        RoomType roomType = loadRoomType(hold.getRoomTypeId());
        List<LocalDate> dates = expandDates(hold.getCheckInDate(), hold.getCheckOutDate());
        for (LocalDate date : dates) {
            RoomAvailability availability = getAvailabilityForUpdate(roomType.getId(), date);
            if (availability.getHeld() < hold.getQuantity()) throw new InventoryInconsistentException();
            int newHeld = availability.getHeld() - hold.getQuantity();
            availability.setHeld(newHeld);
            roomAvailabilityRepository.save(availability);
        }
        hold.setStatus(HoldStatus.RELEASED);
        roomHoldRepository.save(hold);
    }
}
