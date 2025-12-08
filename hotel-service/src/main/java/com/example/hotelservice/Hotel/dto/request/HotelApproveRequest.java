package com.example.hotelservice.Hotel.dto.request;

public record HotelApproveRequest(
        boolean approve,
        String note
) {}

