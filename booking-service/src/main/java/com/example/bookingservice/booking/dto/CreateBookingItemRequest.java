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
public class CreateBookingItemRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", message="must be a UUID")
    private String roomTypeId;
    private String roomTypeName;
    private String ratePlanId;
    @jakarta.validation.constraints.NotNull
    private LocalDate checkInDate;
    @jakarta.validation.constraints.NotNull
    private LocalDate checkOutDate;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(1)
    private Integer quantity;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private BigDecimal unitPrice;
    private Integer nights;
    @jakarta.validation.constraints.Size(max=255)
    private String guestName;
    @jakarta.validation.constraints.Size(max=255)
    private String cancellationPolicy;
}
