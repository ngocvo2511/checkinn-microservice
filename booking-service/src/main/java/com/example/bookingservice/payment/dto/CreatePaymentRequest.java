package com.example.bookingservice.payment.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.bookingservice.payment.enums.PaymentMethod;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {
    @jakarta.validation.constraints.NotNull
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.example.bookingservice.contract.UuidContract.Deserializer.class)
    private UUID bookingId;
    private BigDecimal amount;
    private PaymentMethod method;
}
