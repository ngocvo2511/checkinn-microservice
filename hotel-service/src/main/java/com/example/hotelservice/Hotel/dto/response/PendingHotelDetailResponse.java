package com.example.hotelservice.Hotel.dto.response;

import com.checkinn.user.grpc.UserResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Question.dto.QuestionResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PendingHotelDetailResponse {
    UUID id;
    String name;
    String description;
    Short starRating;

    HotelAddressDto address;

    String contactEmail;
    String contactPhone;

    Instant createdAt;
    HotelApprovalStatus approvedStatus;

    // ==== Owner info ====
    OwnerResponse owner;

    // ==== Legal ====
    String businessLicenseNumber;
    String taxId;
    String operationLicenseNumber;
    String ownerIdentityNumber;

    // ==== Policies & Amenities ====
    List<PolicyResponse> policies;
    List<AmenityResponse> amenityCategories;
    List<QuestionResponse> faqs;

    // ==== Media ====
    List<MediaAssetResponse> mediaAssets;
}
