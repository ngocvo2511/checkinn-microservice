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

    @Override
    @Transactional
    public RoomType createRoomType(RoomTypeCreateRequest request, UUID ownerId) {
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền tạo loại phòng cho khách sạn này.");

        RoomType rt = new RoomType();
        rt.setHotelId(request.hotelId());
        rt.setName(request.name());
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

    @Override
    public List<RoomType> getByHotel(UUID hotelId) {
        return roomTypeRepository.findByHotelId(hotelId);
    }

    @Override
    @Transactional
    public RoomType updateRoomType(UUID roomTypeId, RoomTypeUpdateRequest request, UUID ownerId) {
        RoomType rt = getById(roomTypeId);

        Hotel hotel = hotelRepository.findById(rt.getHotelId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Không có quyền cập nhật loại phòng");

        if (request.name() != null) rt.setName(request.name());
        if (request.basePrice() != null) rt.setBasePrice(request.basePrice());
        if (request.capacity() != null) rt.setCapacity(writeJson(request.capacity()));
        if (request.amenities() != null) rt.setAmenities(writeJson(request.amenities()));
        if (request.isActive() != null) rt.setIsActive(request.isActive());
        if (request.roomAmount() != null){
            if(request.roomAmount() < rt.getTotalRooms()) {
                throw new IllegalArgumentException("Số lượng phòng phải lớn hơn hoặc bằng số lượng phòng trước đó.");
            }
            rt.setTotalRooms(request.roomAmount());
        }

        return roomTypeRepository.save(rt);
    }

    @Override @Transactional
    public void activateRoomType(UUID roomTypeId, UUID ownerId) {
        RoomType rt = getById(roomTypeId);
        authorizeOwner(rt.getHotelId(), ownerId);
        rt.setIsActive(true);
    }

    @Override @Transactional
    public void deactivateRoomType(UUID roomTypeId, UUID ownerId) {
        RoomType rt = getById(roomTypeId);
        authorizeOwner(rt.getHotelId(), ownerId);
        rt.setIsActive(false);
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