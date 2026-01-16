package com.example.userservice.controller;

import com.example.userservice.dto.ApplyPointsRequest;
import com.example.userservice.dto.LoyaltyPointsResponse;
import com.example.userservice.dto.PointsTransactionResponse;
import com.example.userservice.service.LoyaltyPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loyalty-points")
@RequiredArgsConstructor
@Slf4j
public class LoyaltyPointsController {

    private final LoyaltyPointsService loyaltyPointsService;

    /**
     * Lấy thông tin điểm của người dùng
     * GET /api/loyalty-points/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<LoyaltyPointsResponse> getPoints(@PathVariable UUID userId) {
        log.info("Getting loyalty points for user: {}", userId);
        LoyaltyPointsResponse response = loyaltyPointsService.getLoyaltyPoints(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy lịch sử giao dịch điểm của người dùng
     * GET /api/loyalty-points/{userId}/transactions
     */
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<PointsTransactionResponse>> getTransactionHistory(@PathVariable UUID userId) {
        log.info("Getting transaction history for user: {}", userId);
        List<PointsTransactionResponse> transactions = loyaltyPointsService.getTransactionHistory(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Lấy lịch sử giao dịch của một booking
     * GET /api/loyalty-points/booking/{bookingId}/transactions
     */
    @GetMapping("/booking/{bookingId}/transactions")
    public ResponseEntity<List<PointsTransactionResponse>> getBookingTransactions(@PathVariable String bookingId) {
        log.info("Getting transaction history for booking: {}", bookingId);
        List<PointsTransactionResponse> transactions = loyaltyPointsService.getBookingTransactionHistory(bookingId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Tích điểm từ booking thành công (được gọi bởi booking-service)
     * POST /api/loyalty-points/earn
     */
    @PostMapping("/earn")
    public ResponseEntity<LoyaltyPointsResponse> earnPoints(
            @RequestParam String userId,
            @RequestParam String bookingId,
            @RequestParam BigDecimal totalAmount) {
        log.info("Earning points for user: {}, booking: {}, amount: {}", userId, bookingId, totalAmount);
        LoyaltyPointsResponse response = loyaltyPointsService.earnPoints(UUID.fromString(userId), bookingId, totalAmount);
        return ResponseEntity.ok(response);
    }

    /**
     * Sử dụng điểm để giảm giá (được gọi bởi booking-service)
     * POST /api/loyalty-points/use
     */
    @PostMapping("/use")
    public ResponseEntity<BigDecimal> usePoints(@RequestBody ApplyPointsRequest request) {
        log.info("Using points for user: {}, points: {}", request.getUserId(), request.getPointsToUse());
        BigDecimal discountAmount = loyaltyPointsService.usePoints(request);
        return ResponseEntity.ok(discountAmount);
    }

    /**
     * Hoàn lại điểm khi hủy booking (được gọi bởi booking-service)
     * POST /api/loyalty-points/refund
     */
    @PostMapping("/refund")
    public ResponseEntity<Void> refundPoints(
            @RequestParam String userId,
            @RequestParam String bookingId) {
        log.info("Refunding points for user: {}, booking: {}", userId, bookingId);
        loyaltyPointsService.refundPoints(UUID.fromString(userId), bookingId);
        return ResponseEntity.ok().build();
    }
}
