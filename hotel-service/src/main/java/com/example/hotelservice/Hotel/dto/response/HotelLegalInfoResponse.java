package com.example.hotelservice.Hotel.dto.response;

public record HotelLegalInfoResponse(
        String businessLicenseNumber,
        String taxId,
        String operationLicenseNumber,
        String ownerIdentityNumber
) {}

