package com.example.userservice.controller;

import static com.example.userservice.contract.UuidContract.*;
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
    private final com.example.userservice.security.JwtService jwtService;

    private io.jsonwebtoken.Claims actor(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Bearer token required");
        }
        try { return jwtService.parseToken(authorization.substring(7)).getBody(); }
        catch (RuntimeException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }
    }
    private boolean bookingService(io.jsonwebtoken.Claims actor) {
        return "booking-service".equals(actor.getSubject()) && "BOOKING_SERVICE".equals(actor.get("role",String.class));
    }
    private void requireService(String authorization) {
        if (!bookingService(actor(authorization))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Booking service identity required");
        }
    }
    private void requireRead(String authorization, UUID userId) {
        var actor = actor(authorization);
        if (!bookingService(actor) && !"ADMIN".equals(actor.get("role",String.class)) && !userId.toString().equals(actor.getSubject())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Cannot access another user's points");
        }
    }

    /**
     * Lấy thông tin điểm của người dùng
     * GET /api/loyalty-points/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<LoyaltyPointsResponse> getPoints(@PathVariable UUID userId, @RequestHeader(value="Authorization",required=false) String authorization) {
        requireRead(authorization,userId);
        log.info("[LOYALTY_POINTS_GET] Getting loyalty points for user: {}", userId);
        LoyaltyPointsResponse response = loyaltyPointsService.getLoyaltyPoints(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy lịch sử giao dịch điểm của người dùng
     * GET /api/loyalty-points/{userId}/transactions
     */
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<PointsTransactionResponse>> getTransactionHistory(@PathVariable UUID userId, @RequestHeader(value="Authorization",required=false) String authorization) {
        requireRead(authorization,userId);
        log.info("[LOYALTY_POINTS_HISTORY_USER] Getting transaction history for user: {}", userId);
        List<PointsTransactionResponse> transactions = loyaltyPointsService.getTransactionHistory(userId);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Lấy lịch sử giao dịch của một booking
     * GET /api/loyalty-points/booking/{bookingId}/transactions
     */
    @GetMapping("/booking/{bookingId}/transactions")
    public ResponseEntity<List<PointsTransactionResponse>> getBookingTransactions(@PathVariable String bookingId, @RequestHeader(value="Authorization",required=false) String authorization) {
        var actor = actor(authorization);
        if (!bookingService(actor) && !"ADMIN".equals(actor.get("role",String.class))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Booking ledger is service/admin only");
        }
        log.info("[LOYALTY_POINTS_HISTORY_BOOKING] Getting transaction history for booking: {}", bookingId);
        List<PointsTransactionResponse> transactions = loyaltyPointsService.getBookingTransactionHistory(required(bookingId));
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
            @RequestParam BigDecimal totalAmount, @RequestHeader(value="Authorization",required=false) String authorization) {
        requireService(authorization);
        log.info("[LOYALTY_POINTS_EARN] Earning points for user: {}, booking: {}, amount: {}", userId, bookingId, totalAmount);
        LoyaltyPointsResponse response = loyaltyPointsService.earnPoints(required(userId), required(bookingId), totalAmount);
        return ResponseEntity.ok(response);
    }

    /**
     * Sử dụng điểm để giảm giá (được gọi bởi booking-service)
     * POST /api/loyalty-points/use
     */
    @PostMapping("/use")
    public ResponseEntity<BigDecimal> usePoints(@RequestBody ApplyPointsRequest request, @RequestHeader(value="Authorization",required=false) String authorization) {
        requireService(authorization);
        log.info("[LOYALTY_POINTS_USE] Using points for user: {}, points: {}", request.getUserId(), request.getPointsToUse());
        BigDecimal discountAmount = loyaltyPointsService.usePoints(request);
        return ResponseEntity.ok(discountAmount);
    }

    @PostMapping("/quote")
    public com.example.userservice.dto.PointsQuoteResponse quote(
            @RequestBody com.example.userservice.dto.PointsQuoteRequest request,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        requireService(authorization);
        return loyaltyPointsService.quote(request);
    }

    @PostMapping("/reserve")
    public com.example.userservice.dto.PointsReservationResponse reserve(
            @RequestBody com.example.userservice.dto.ReservePointsRequest request,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        requireService(authorization);
        return loyaltyPointsService.reserve(request);
    }

    public record QuotePreviewRequest(Long pointsToUse, BigDecimal totalAmount) {}

    /** Read-only preview; the caller never receives a service mutation credential. */
    @PostMapping("/{userId}/quote")
    public com.example.userservice.dto.PointsQuoteResponse previewQuote(@PathVariable UUID userId,
            @RequestBody QuotePreviewRequest request,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        requireRead(authorization, userId);
        return loyaltyPointsService.quote(new com.example.userservice.dto.PointsQuoteRequest(
                userId, request.pointsToUse(), request.totalAmount()));
    }

    /**
     * Hoàn lại điểm khi hủy booking (được gọi bởi booking-service)
     * POST /api/loyalty-points/refund
     */
    @PostMapping("/refund")
    public ResponseEntity<Void> refundPoints(
            @RequestParam String userId,
            @RequestParam String bookingId, @RequestHeader(value="Authorization",required=false) String authorization) {
        requireService(authorization);
        log.info("[LOYALTY_POINTS_REFUND] Refunding points for user: {}, booking: {}", userId, bookingId);
        loyaltyPointsService.refundPoints(required(userId), required(bookingId));
        return ResponseEntity.ok().build();
    }
}
