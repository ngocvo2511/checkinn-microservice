package com.example.hotelservice.Hotel.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record HotelCreateRequest(
        @NotBlank String name,
        String description,
        Short starRating,
        @NotNull UUID cityId,
        @NotNull HotelAddressDto address,

        // ====== Tài liệu pháp lý ======
        @NotBlank String businessLicenseNumber,
        String taxId,
        String operationLicenseNumber,
        @NotBlank String ownerIdentityNumber
) {}

