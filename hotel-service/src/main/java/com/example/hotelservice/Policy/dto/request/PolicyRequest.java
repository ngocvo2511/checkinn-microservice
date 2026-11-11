package com.example.hotelservice.Policy.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;


@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolicyRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    String category;
    @jakarta.validation.constraints.NotBlank
    String content;
}
