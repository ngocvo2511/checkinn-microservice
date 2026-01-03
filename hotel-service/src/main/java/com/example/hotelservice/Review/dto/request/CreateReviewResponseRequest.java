package com.example.hotelservice.Review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewResponseRequest {

    @NotBlank(message = "Nội dung trả lời không được để trống")
    @Size(min = 5, max = 5000, message = "Nội dung phải từ 5-5000 ký tự")
    private String content;
}
