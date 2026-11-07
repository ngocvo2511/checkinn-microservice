package com.example.hotelservice.Hotel.dto.request;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.dto.QuestionRequest;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record HotelCreateRequest(
        @NotBlank @jakarta.validation.constraints.Size(max=255) String name,
        String description,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Short starRating,
        @NotNull UUID cityId,
        @NotNull @jakarta.validation.Valid HotelAddressDto address,
        @Email @jakarta.validation.constraints.Size(max=100) String contactEmail,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number") @jakarta.validation.constraints.Size(max=20) String contactPhone,

        // ====== Tài liệu pháp lý ======
        @NotBlank @jakarta.validation.constraints.Size(max=50) String businessLicenseNumber,
        @NotBlank @jakarta.validation.constraints.Size(max=50) String taxId,
        @jakarta.validation.constraints.Size(max=50) String operationLicenseNumber,
        @NotBlank @jakarta.validation.constraints.Size(max=50) String ownerIdentityNumber,

        // ====== Chính sách và tiện ích ======
        List<PolicyRequest> policies,
        List<QuestionRequest> questions,
        List<AmenityRequest> amenityCategories,
        com.example.hotelservice.Hotel.enums.AccommodationType accommodationType
) {}

