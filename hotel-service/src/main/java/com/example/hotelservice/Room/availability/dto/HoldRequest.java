package com.example.hotelservice.Room.availability.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HoldRequest(
        UUID roomTypeId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int quantity
) {}
