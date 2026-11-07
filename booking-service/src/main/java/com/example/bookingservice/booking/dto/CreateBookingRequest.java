package com.example.bookingservice.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {
    private String idempotencyKey;
    private String userId;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", message="must be a UUID")
    private String hotelId;
    private String hotelName;
    @jakarta.validation.constraints.NotNull
    private LocalDate checkInDate;
    @jakarta.validation.constraints.NotNull
    private LocalDate checkOutDate;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(1)
    private Integer adults;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(0)
    private Integer children;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    private String contactName;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max=255)
    private String contactEmail;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="^\\+?[0-9]{7,15}$", message="Invalid phone number")
    private String contactPhone;
    @jakarta.validation.constraints.Size(max=255)
    private String specialRequests;
    private String voucherCode;
    @jakarta.validation.constraints.Min(0)
    private Long pointsToUse;  // Số điểm muốn sử dụng để giảm giá (optional)
    @jakarta.validation.constraints.NotEmpty(message="Booking must contain at least one item")
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateBookingItemRequest> items;
    private String acceptedCancellationPolicyVersion;
}
