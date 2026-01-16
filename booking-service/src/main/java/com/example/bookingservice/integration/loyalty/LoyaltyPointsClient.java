package com.example.bookingservice.integration.loyalty;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(name = "user-service", url = "${user-service.url:http://localhost:8081}")
public interface LoyaltyPointsClient {

    /**
     * Lấy thông tin điểm của người dùng
     */
    @GetMapping("/api/loyalty-points/{userId}")
    LoyaltyPointsDTO getLoyaltyPoints(@PathVariable String userId);

    /**
     * Tích điểm từ booking thành công
     */
    @PostMapping("/api/loyalty-points/earn")
    LoyaltyPointsDTO earnPoints(
            @RequestParam String userId,
            @RequestParam String bookingId,
            @RequestParam BigDecimal totalAmount
    );

    /**
     * Sử dụng điểm để giảm giá
     */
    @PostMapping("/api/loyalty-points/use")
    BigDecimal usePoints(@RequestBody ApplyPointsRequestDTO request);

    /**
     * Hoàn lại điểm khi hủy booking
     */
    @PostMapping("/api/loyalty-points/refund")
    void refundPoints(
            @RequestParam String userId,
            @RequestParam String bookingId
    );
}
