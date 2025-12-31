package com.example.hotelservice.Hotel.mapper;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityItemResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Hotel.dto.response.OwnerResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.entity.RoomType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class HotelMapper {

    protected ObjectMapper objectMapper;
    @Autowired
    public void setObjectMapper(ObjectMapper om) {
        this.objectMapper = om;
    }
    
    @Autowired
    protected com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper mediaAssetMapper;

    @Autowired
    protected com.example.hotelservice.Room.mapper.RoomTypeMapper roomTypeMapper;

    @Autowired
    protected com.example.hotelservice.Policy.repository.HotelPolicyRepository hotelPolicyRepository;

    @Autowired
    protected com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository hotelAmenityCategoryRepository;

    @Autowired
    protected com.example.hotelservice.Amenity.repository.HotelAmenityRepository hotelAmenityRepository;

    // ----------- CreateHotelRequest -> Hotel -----------

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roomTypes", ignore = true)
    @Mapping(target = "mediaAssets", ignore = true)
    @Mapping(target = "address", expression = "java(writeJson(request.address()))")
    public abstract Hotel toHotel(HotelCreateRequest request);


    // ----------- Hotel -> HotelResponse (record) -----------

    @Mapping(target = "address", expression = "java(readAddress(entity.getAddress()))")
    @Mapping(target = "roomTypes", expression = "java(toRoomTypeResponses(entity.getRoomTypes()))")
    @Mapping(target = "mediaAssets", expression = "java(toMediaAssetResponses(entity.getMediaAssets()))")
    @Mapping(target = "lowestPrice", expression = "java(calculateLowestPrice(entity.getRoomTypes()))")
    @Mapping(target = "policies", expression = "java(getPolicies(entity.getId()))")
    @Mapping(target = "amenityCategories", expression = "java(getAmenities(entity.getId()))")
    public abstract HotelResponse toHotelResponse(Hotel entity);

    // ----------- Hotel -> PendingHotelResponse (record) -----------
    public PendingHotelResponse toPendingHotelResponse(Hotel hotel) {

        if (hotel == null) {
            return null;
        }

        return PendingHotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .address(readAddress(hotel.getAddress())
                )
                .starRating(hotel.getStarRating())
                .approvedStatus(hotel.getApprovedStatus())
                .createdAt(hotel.getCreatedAt())
                .build();
    }

    public PendingHotelDetailResponse toPendingHotelDetailResponse(Hotel hotel,
                                                                   List<PolicyResponse> policies,
                                                                   List<AmenityResponse> amenityCategories,
                                                                   UserResponse ownerInfo) {

        if (hotel == null) {
            return null;
        }

        return PendingHotelDetailResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .starRating(hotel.getStarRating())

                .address(readAddress(hotel.getAddress()))

                .contactEmail(hotel.getContactEmail())
                .contactPhone(hotel.getContactPhone())

                .createdAt(hotel.getCreatedAt())
                .approvedStatus(hotel.getApprovedStatus())

                .owner(mapOwner(ownerInfo))

                .businessLicenseNumber(hotel.getBusinessLicenseNumber())
                .taxId(hotel.getTaxId())
                .operationLicenseNumber(hotel.getOperationLicenseNumber())
                .ownerIdentityNumber(hotel.getOwnerIdentityNumber())

                .policies(policies)
                .amenityCategories(amenityCategories)

                .mediaAssets(toMediaAssetResponses(hotel.getMediaAssets()))
                .build();
    }

    private OwnerResponse mapOwner(UserResponse user) {
        return OwnerResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }


    // ---------- JSON Helpers ----------
    protected String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected HotelAddressDto readAddress(String json) {
        try { return objectMapper.readValue(json, HotelAddressDto.class); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    protected List<MediaAssetResponse> toMediaAssetResponses(
            List<MediaAsset> assets
    ) {
        if (assets == null) return List.of();
        return assets.stream()
                .map(mediaAssetMapper::toMediaAssetResponse)
                .toList();
    }

    protected List<RoomTypeResponse> toRoomTypeResponses(
            List<RoomType> roomTypes
    ) {
        if (roomTypes == null) return java.util.List.of();
        return roomTypes.stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();
    }

    protected java.math.BigDecimal calculateLowestPrice(
            List<RoomType> roomTypes
    ) {
        if (roomTypes == null || roomTypes.isEmpty()) return null;
        return roomTypes.stream()
                .filter(rt -> rt.getIsActive() != null && rt.getIsActive())
                .map(com.example.hotelservice.Room.entity.RoomType::getBasePrice)
                .filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(null);
    }

    protected List<PolicyResponse> getPolicies(UUID hotelId) {
        if (hotelId == null) return List.of();
        return hotelPolicyRepository.findAllByHotelId(hotelId)
                .stream()
                .map(p -> PolicyResponse.builder()
                        .title(p.getTitle())
                        .content(p.getContent())
                        .build())
                .toList();
    }

    protected List<AmenityResponse> getAmenities(UUID hotelId) {
        if (hotelId == null) return List.of();

        List<HotelAmenityCategory> categories =
                hotelAmenityCategoryRepository.findAllByHotelId(hotelId);

        List<HotelAmenity> amenities =
                hotelAmenityRepository.findAllByHotelId(hotelId);

        Map<UUID, List<HotelAmenity>> amenityMap =
                amenities.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                a -> a.getCategory().getId()
                        ));

        return categories.stream()
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
                        .build())
                .toList();
    }
}
