package com.example.hotelservice.Room.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomTypeServiceImpl implements RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;
    private final RoomInventoryReviewService inventoryReview;

    @Override
    @Transactional
    public RoomType createRoomType(RoomTypeCreateRequest request, UUID ownerId) {
        com.example.hotelservice.validation.InputValidation.require(request);
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền tạo loại phòng cho khách sạn này.");

        RoomType rt = new RoomType();
        rt.setHotelId(request.hotelId());
        rt.setName(request.name());
        rt.setDescription(request.description());
        rt.setBasePrice(request.basePrice());
        rt.setCapacity(writeJson(request.capacity()));
        rt.setAmenities(writeJson(request.amenities()));
        if(request.roomAmount() < 1) {
            throw new IllegalArgumentException("Số lượng phòng phải lớn hơn hoặc bằng 1.");
        }
        rt.setTotalRooms(request.roomAmount());
        rt.setIsActive(true);

        return roomTypeRepository.save(rt);
    }


    @Override
    public RoomType getById(UUID roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
    }

    // Mutations share the inventory row lock so Hibernate cannot flush a stale full row.
    private RoomType getByIdForUpdate(UUID roomTypeId) {
        return roomTypeRepository.findByIdForUpdate(roomTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy loại phòng"));
    }

    @Override
    public List<RoomType> getByHotel(UUID hotelId) {
        return roomTypeRepository.findByHotelId(hotelId);
    }

    @Override
    @Transactional
    public RoomType updateRoomType(UUID roomTypeId, RoomTypeUpdateRequest request, UUID ownerId) {
        com.example.hotelservice.validation.InputValidation.require(request);
        RoomType rt = getByIdForUpdate(roomTypeId);

        Hotel hotel = hotelRepository.findById(rt.getHotelId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Không có quyền cập nhật loại phòng");

        if (request.name() != null) rt.setName(request.name());
        // Partial-update compatibility: omitted/null leaves existing text; empty string explicitly clears it.
        if (request.description() != null) rt.setDescription(request.description());
        if (request.basePrice() != null) rt.setBasePrice(request.basePrice());
        if (request.capacity() != null) rt.setCapacity(writeJson(request.capacity()));
        if (request.amenities() != null) rt.setAmenities(writeJson(request.amenities()));
        if (request.isActive() != null) rt.setIsActive(request.isActive());
        if (request.roomAmount() != null){
            inventoryReview.requireLegacyUpdateAllowed(rt, request.roomAmount());
            if (rt.getTotalRooms() == null || rt.getTotalRooms() <= 0)
                throw new com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException();
            if(request.roomAmount() < rt.getTotalRooms()) {
                throw new IllegalArgumentException("Số lượng phòng phải lớn hơn hoặc bằng số lượng phòng trước đó.");
            }
            rt.setTotalRooms(request.roomAmount());
        }

        return roomTypeRepository.save(rt);
    }

    @Override @Transactional
    public void activateRoomType(UUID roomTypeId, UUID ownerId) {
        RoomType rt = getByIdForUpdate(roomTypeId);
        authorizeOwner(rt.getHotelId(), ownerId);
        rt.setIsActive(true);
    }

    @Override @Transactional
    public void deactivateRoomType(UUID roomTypeId, UUID ownerId) {
        RoomType rt = getByIdForUpdate(roomTypeId);
        authorizeOwner(rt.getHotelId(), ownerId);
        rt.setIsActive(false);
    }

    @Override public RoomType getPublicById(UUID roomTypeId) {
        RoomType room = getById(roomTypeId);
        requirePublishedHotel(room.getHotelId());
        if (!Boolean.TRUE.equals(room.getIsActive())) throw new EntityNotFoundException("Không tìm thấy loại phòng");
        return room;
    }

    @Override public List<RoomType> getPublicByHotel(UUID hotelId) {
        requirePublishedHotel(hotelId);
        return getByHotel(hotelId).stream().filter(room -> Boolean.TRUE.equals(room.getIsActive())).toList();
    }

    @Override public RoomType getPreviewById(UUID roomTypeId, UUID actorId, boolean admin) {
        RoomType room = getById(roomTypeId);
        authorizePreview(room.getHotelId(), actorId, admin);
        return room;
    }

    @Override public List<RoomType> getPreviewByHotel(UUID hotelId, UUID actorId, boolean admin) {
        authorizePreview(hotelId, actorId, admin);
        return getByHotel(hotelId);
    }

    private void requirePublishedHotel(UUID hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));
        if (!Boolean.TRUE.equals(hotel.getIsActive()) || hotel.getApprovedStatus() != com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED)
            throw new EntityNotFoundException("Không tìm thấy khách sạn");
    }

    private void authorizePreview(UUID hotelId, UUID actorId, boolean admin) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));
        if (actorId == null || (!admin && !actorId.equals(hotel.getOwnerId())))
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem loại phòng");
    }

    private void authorizeOwner(UUID hotelId, UUID ownerId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));
        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Không có quyền thực hiện thao tác này.");
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
