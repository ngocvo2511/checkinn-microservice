package com.example.hotelservice.Hotel.service;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityItemResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Policy.entity.HotelPolicy;
import com.example.hotelservice.Policy.repository.HotelPolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final UserGrpcClient userGrpcClient;
    private final HotelRepository hotelRepository;
    private final HotelPolicyRepository hotelPolicyRepository;
    private final HotelAmenityCategoryRepository hotelAmenityCategoryRepository;
    private final HotelAmenityRepository hotelAmenityRepository;
    private final ObjectMapper objectMapper;
    private final HotelMapper hotelMapper;

    // ========== CREATE HOTEL ==========
    @Override
    @Transactional
    public Hotel createHotel(HotelCreateRequest request, UUID ownerId) {
        Hotel hotel = new Hotel();
        hotel.setOwnerId(ownerId);
        hotel.setCityId(request.cityId());
        hotel.setName(request.name());
        hotel.setDescription(request.description());
        hotel.setStarRating(request.starRating());
        hotel.setAddress(writeJson(request.address()));
        hotel.setContactEmail(request.contactEmail());
        hotel.setContactPhone(request.contactPhone());
        hotel.setBusinessLicenseNumber(request.businessLicenseNumber());
        hotel.setTaxId(request.taxId());
        hotel.setOperationLicenseNumber(request.operationLicenseNumber());
        hotel.setOwnerIdentityNumber(request.ownerIdentityNumber());

        hotel.setIsActive(false);
        hotel.setApprovedStatus(HotelApprovalStatus.PENDING);

        hotel = hotelRepository.save(hotel);
        Hotel finalHotel = hotel;

        if (request.policies() != null) {
            request.policies().forEach(p -> {
                HotelPolicy policy = HotelPolicy.builder()
                        .hotel(finalHotel)
                        .title(p.getTitle())
                        .content(p.getContent())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                hotelPolicyRepository.save(policy);
            });
        }

        if (request.amenityCategories() != null) {
            request.amenityCategories().forEach(catReq -> {
                HotelAmenityCategory cat = HotelAmenityCategory.builder()
                        .hotelId(finalHotel.getId())
                        .title(catReq.getTitle())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                cat = hotelAmenityCategoryRepository.save(cat);

                if (catReq.getAmenities() != null) {
                    HotelAmenityCategory finalCat = cat;
                    catReq.getAmenities().forEach(itemReq -> {
                        HotelAmenity amenity = HotelAmenity.builder()
                                .hotelId(finalHotel.getId())
                                .category(finalCat)
                                .title(itemReq.getTitle())
                                .createdAt(Instant.now())
                                .build();
                        hotelAmenityRepository.save(amenity);
                    });
                }
            });
        }
        return hotel;
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
        if (request.cityId() != null) hotel.setCityId(request.cityId());
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

    // ========== SEARCH: HOTELS BY CITY ==========
    @Override
    public List<Hotel> getByCity(UUID cityId) {
        return hotelRepository.findByCityId(cityId);
    }

    // ========== SEARCH: HOTELS BY OWNER AND CITY ==========
    @Override
    public List<Hotel> getByOwnerAndCity(UUID ownerId, UUID cityId) {
        return hotelRepository.findByOwnerIdAndCityId(ownerId, cityId);
    }

    // ========== ADMIN: PENDING HOTELS ==========
    @Override
    public List<Hotel> getPendingHotels() {
        return hotelRepository.findByApprovedStatus(HotelApprovalStatus.PENDING);
    }

    @Override
    public PendingHotelDetailResponse getPendingHotelDetail(UUID hotelId) {
        Hotel hotel = getById(hotelId);
        if (hotel.getApprovedStatus() != HotelApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Khách sạn không ở trạng thái chờ duyệt.");
        }
        UserResponse owner = userGrpcClient.GetUserById(hotel.getOwnerId());
        List<PolicyResponse> policies =
                hotelPolicyRepository.findAllByHotelId(hotelId)
                        .stream()
                        .map(p -> PolicyResponse.builder()
                                .title(p.getTitle())
                                .content(p.getContent())
                                .build())
                        .toList();
        List<HotelAmenityCategory> categories =
                hotelAmenityCategoryRepository.findAllByHotelId(hotelId);

        List<HotelAmenity> amenities =
                hotelAmenityRepository.findAllByHotelId(hotelId);

        Map<UUID, List<HotelAmenity>> amenityMap =
                amenities.stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getCategory().getId()
                        ));

        List<AmenityResponse> amenityResponses =
                categories.stream()
                        .map(category -> AmenityResponse.builder()
                                .id(category.getId().toString())
                                .title(category.getTitle())
                                .items(
                                        amenityMap
                                                .getOrDefault(category.getId(), List.of())
                                                .stream()
                                                .map(item -> AmenityItemResponse.builder()
                                                        .id(item.getId().toString())
                                                        .title(item.getTitle())
                                                        .build())
                                                .toList()
                                )
                                .build()
                        )
                        .toList();


        return hotelMapper.toPendingHotelDetailResponse(hotel, policies, amenityResponses, owner);
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
}
