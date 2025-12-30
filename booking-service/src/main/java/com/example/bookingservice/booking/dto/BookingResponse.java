package com.example.bookingservice.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.bookingservice.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {
    private String id;
    private String userId;
    private String hotelId;
    private String hotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer adults;
    private Integer children;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String voucherCode;
    private BigDecimal voucherDiscount;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String specialRequests;
    private String holdId;
    private LocalDateTime holdExpiresAt;
    private List<BookingItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
