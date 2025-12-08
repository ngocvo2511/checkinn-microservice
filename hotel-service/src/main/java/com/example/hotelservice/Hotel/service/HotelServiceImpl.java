package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;

    // ========== CREATE HOTEL ==========
    @Override
    @Transactional
    public Hotel createHotel(HotelCreateRequest request, UUID ownerId) {
        Hotel hotel = new Hotel();
        hotel.setOwnerId(ownerId);
        hotel.setName(request.name());
        hotel.setDescription(request.description());
        hotel.setStarRating(request.starRating());
        hotel.setAddress(writeJson(request.address()));
        hotel.setIsActive(false);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);

        return hotelRepository.save(hotel);
    }

    // ========== UPDATE HOTEL ==========
    @Override
    @Transactional
    public Hotel updateHotel(UUID hotelId, HotelUpdateRequest request, UUID ownerId) {
        Hotel hotel = getById(hotelId);

        if (!hotel.getOwnerId().equals(ownerId))
            throw new SecurityException("Bạn không có quyền cập nhật khách sạn này.");

        if (request.name() != null) hotel.setName(request.name());
        if (request.description() != null) hotel.setDescription(request.description());
        if (request.starRating() != null) hotel.setStarRating(request.starRating());
        if (request.address() != null) hotel.setAddress(writeJson(request.address()));

        return hotelRepository.save(hotel);
    }

    // ========== GET HOTEL ==========
    @Override
    public Hotel getById(UUID hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khách sạn"));
    }

    // ========== OWNER: MY HOTELS ==========
    @Override
    public List<Hotel> getByOwner(UUID ownerId) {
        return hotelRepository.findByOwnerId(ownerId);
    }

    // ========== ADMIN: APPROVE ==========
    @Override @Transactional
    public void approveHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.APPROVED);
        hotel.setIsActive(true);
    }

    // ========== ADMIN: REJECT ==========
    @Override @Transactional
    public void rejectHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setApprovedStatus(HotelApprovalStatus.REJECTED);
        hotel.setIsActive(false);
    }

    // ========== ACTIVATE ==========
    @Override @Transactional
    public void activateHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setIsActive(true);
    }

    // ========== ADMIN DISABLE ==========
    @Override @Transactional
    public void deactivateHotel(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        hotel.setIsActive(false);
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private UUID getCurrentUserId() {
        // TODO: extract from JWT token (Auth-service)
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
