package com.example.hotelservice.Hotel.dto.request;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.dto.QuestionRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record HotelUpdateRequest(
        @jakarta.validation.constraints.Pattern(regexp="(?s).*\\S.*", message="must not be blank") @jakarta.validation.constraints.Size(max=255) String name,
        String description,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) Short starRating,
        UUID cityId,
        @jakarta.validation.Valid HotelAddressDto address,
        @Email @jakarta.validation.constraints.Size(max=100) String contactEmail,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number") @jakarta.validation.constraints.Size(max=20) String contactPhone,
        Boolean isActive,
        @jakarta.validation.Valid List<@jakarta.validation.constraints.NotNull PolicyRequest> policies,
        @jakarta.validation.Valid List<@jakarta.validation.constraints.NotNull QuestionRequest> questions,
        @jakarta.validation.Valid List<@jakarta.validation.constraints.NotNull AmenityRequest> amenityCategories,
        @jakarta.validation.constraints.Size(max=50) String businessLicenseNumber,
        @jakarta.validation.constraints.Size(max=50) String taxId,
        @jakarta.validation.constraints.Size(max=50) String operationLicenseNumber,
        @jakarta.validation.constraints.Size(max=50) String ownerIdentityNumber,
        com.example.hotelservice.Hotel.enums.AccommodationType accommodationType
) {}

