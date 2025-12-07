package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MyHotelShortResponse {
    private UUID id;
    private String name;
    private HotelApprovalStatus approvedStatus;
    private Boolean isActive;
}
