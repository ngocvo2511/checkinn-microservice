package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PendingHotelResponse {
    private UUID id;

    private String name;
    private HotelAddressDto address;

    private Short starRating;
    private HotelApprovalStatus approvedStatus;

    private Instant createdAt;
}
