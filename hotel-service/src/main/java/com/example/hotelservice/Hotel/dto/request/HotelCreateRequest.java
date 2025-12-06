package com.example.hotelservice.Hotel.dto.request;

import jakarta.validation.constraints.*;


public record HotelCreateRequest(
        @NotBlank String name,
        String description,
        Short starRating,
        @NotNull HotelAddressDto address,

        // ====== Tài liệu pháp lý ======
        @NotBlank String businessLicenseNumber,
        String taxId,
        String operationLicenseNumber,
        @NotBlank String ownerIdentityNumber
) {}

