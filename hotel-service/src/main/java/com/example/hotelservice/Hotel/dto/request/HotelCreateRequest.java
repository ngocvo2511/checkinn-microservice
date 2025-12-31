package com.example.hotelservice.Hotel.dto.request;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.dto.QuestionRequest;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record HotelCreateRequest(
        @NotBlank String name,
        String description,
        Short starRating,
        @NotNull UUID cityId,
        @NotNull HotelAddressDto address,
        @Email String contactEmail,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number") String contactPhone,

        // ====== Tài liệu pháp lý ======
        @NotBlank String businessLicenseNumber,
        @NotBlank String taxId,
        String operationLicenseNumber,
        @NotBlank String ownerIdentityNumber,

        // ====== Chính sách và tiện ích ======
        List<PolicyRequest> policies,
        List<QuestionRequest> questions,
        List<AmenityRequest> amenityCategories
) {}

