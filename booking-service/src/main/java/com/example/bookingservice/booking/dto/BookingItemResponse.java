package com.example.bookingservice.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingItemResponse {
    private String id;
    private String roomTypeId;
    private String roomTypeName;
    private String ratePlanId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer nights;
    private BigDecimal subtotal;
    private BigDecimal taxFee;
    private String cancellationPolicy;
    private String guestName;
}
