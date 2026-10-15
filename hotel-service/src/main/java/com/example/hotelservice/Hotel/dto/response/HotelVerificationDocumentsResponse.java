package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record HotelVerificationDocumentsResponse(
        UUID hotelId,
        HotelApprovalStatus approvedStatus,
        String rejectionReason,
        String businessLicenseNumber,
        String taxId,
        String operationLicenseNumber,
        String ownerIdentityNumber,
        String businessLicenseUrl,
        String operationLicenseUrl,
        String ownerIdentityFrontUrl,
        String ownerIdentityBackUrl
) {}
