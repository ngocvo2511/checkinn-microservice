package com.example.hotelservice.Hotel.mapper;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Hotel.dto.response.OwnerResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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
    @Mapping(target = "amenityCategories", ignore = true)
    public abstract HotelResponse toHotelResponse(Hotel entity);

    /**
     * Attach policies/amenities/amenityCategories to a base HotelResponse produced by MapStruct.
     */
    public HotelResponse toHotelResponseWithExtras(Hotel entity,
                                                   java.util.List<String> policies,
                                                   java.util.List<String> amenities,
                                                   java.util.List<AmenityResponse> amenityCategories) {
        HotelResponse base = toHotelResponse(entity);
        return new HotelResponse(
                base.id(),
                base.ownerId(),
                base.cityId(),
                base.name(),
                base.description(),
                base.starRating(),
                base.address(),
                base.contactEmail(),
                base.contactPhone(),
                policies,
                amenities,
                base.isActive(),
                base.approvedStatus(),
                base.city(),
                base.createdAt(),
                base.updatedAt(),
                base.lowestPrice(),
                base.roomTypes(),
                base.mediaAssets(),
                amenityCategories
        );
    }

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

    protected java.util.List<com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse> toMediaAssetResponses(
            java.util.List<com.example.hotelservice.MediaAsset.entity.MediaAsset> assets
    ) {
        if (assets == null) return java.util.List.of();
        return assets.stream()
                .map(mediaAssetMapper::toMediaAssetResponse)
                .toList();
    }

    protected java.util.List<com.example.hotelservice.Room.dto.response.RoomTypeResponse> toRoomTypeResponses(
            java.util.List<com.example.hotelservice.Room.entity.RoomType> roomTypes
    ) {
        if (roomTypes == null) return java.util.List.of();
        return roomTypes.stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();
    }

    protected java.math.BigDecimal calculateLowestPrice(
            java.util.List<com.example.hotelservice.Room.entity.RoomType> roomTypes
    ) {
        if (roomTypes == null || roomTypes.isEmpty()) return null;
        return roomTypes.stream()
                .filter(rt -> rt.getIsActive() != null && rt.getIsActive())
                .map(com.example.hotelservice.Room.entity.RoomType::getBasePrice)
                .filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(null);
    }
}
