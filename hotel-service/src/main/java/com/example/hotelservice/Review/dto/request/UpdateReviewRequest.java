package com.example.hotelservice.Review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReviewRequest {

    @DecimalMin(value = "1.0", message = "Đánh giá phải từ 1-10 điểm")
    @DecimalMax(value = "10.0", message = "Đánh giá phải từ 1-10 điểm")
    private BigDecimal rating;

    // Detailed ratings (1-10 scale)
    @DecimalMin(value = "1.0", message = "Đánh giá nhân viên phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá nhân viên phải từ 1-10")
    private Double staffRating;

    @DecimalMin(value = "1.0", message = "Đánh giá tiện nghi phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá tiện nghi phải từ 1-10")
    private Double amenitiesRating;

    @DecimalMin(value = "1.0", message = "Đánh giá sạch sẽ phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá sạch sẽ phải từ 1-10")
    private Double cleanlinessRating;

    @DecimalMin(value = "1.0", message = "Đánh giá thoải mái phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá thoải mái phải từ 1-10")
    private Double comfortRating;

    @DecimalMin(value = "1.0", message = "Đánh giá giá trị phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá giá trị phải từ 1-10")
    private Double valueForMoneyRating;

    @DecimalMin(value = "1.0", message = "Đánh giá địa điểm phải từ 1-10")
    @DecimalMax(value = "10.0", message = "Đánh giá địa điểm phải từ 1-10")
    private Double locationRating;

    @Size(min = 5, max = 255, message = "Tiêu đề phải từ 5-255 ký tự")
    private String title;

    @Size(min = 10, max = 5000, message = "Nội dung phải từ 10-5000 ký tự")
    private String content;
}
