package com.example.userservice.service;

import com.example.userservice.dto.ApplyPointsRequest;
import com.example.userservice.dto.LoyaltyPointsResponse;
import com.example.userservice.dto.PointsTransactionResponse;
import com.example.userservice.model.LoyaltyPoints;
import com.example.userservice.model.PointsTransaction;
import com.example.userservice.repository.LoyaltyPointsRepository;
import com.example.userservice.repository.PointsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyPointsService {

    private final LoyaltyPointsRepository loyaltyPointsRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    // Tỷ lệ tích điểm: 1000 VND = 1 điểm (cần đặt phòng nhiều để tích)
    private static final BigDecimal EARN_CONVERSION_RATE = new BigDecimal("10000");
    
    // Tỷ lệ sử dụng điểm: 500 VND = 1 điểm (1 điểm chỉ giảm 500 VND)
    private static final BigDecimal REDEMPTION_CONVERSION_RATE = new BigDecimal("500");

    /**
     * Tính điểm từ số tiền (tích điểm)
     * @param amount Số tiền (VND)
     * @return Số điểm tương ứng (1000 VND = 1 điểm)
     */
    public Long calculatePoints(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return amount.divide(EARN_CONVERSION_RATE, 0, java.math.RoundingMode.DOWN).longValue();
    }

    /**
     * Chuyển đổi điểm thành tiền (sử dụng điểm để giảm giá)
     * @param points Số điểm
     * @return Số tiền tương ứng (VND) - 1 điểm = 500 VND
     */
    public BigDecimal convertPointsToAmount(Long points) {
        if (points == null || points <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(points).multiply(REDEMPTION_CONVERSION_RATE);
    }

    /**
     * Tích điểm khi đặt phòng thành công
     * @param userId ID của người dùng
     * @param bookingId ID của booking
     * @param totalAmount Tổng tiền của booking
     */
    @Transactional
    public LoyaltyPointsResponse earnPoints(UUID userId, String bookingId, BigDecimal totalAmount) {
        log.info("Earning points for user: {}, booking: {}, amount: {}", userId, bookingId, totalAmount);

        Long pointsToEarn = calculatePoints(totalAmount);
        if (pointsToEarn == 0) {
            log.warn("No points to earn for amount: {}", totalAmount);
            return getLoyaltyPoints(userId);
        }

        LoyaltyPoints loyaltyPoints = loyaltyPointsRepository.findByUserId(userId)
                .orElseGet(() -> LoyaltyPoints.builder()
                        .userId(userId)
                        .totalPoints(0L)
                        .usedPoints(0L)
                        .availablePoints(0L)
                        .build());

        loyaltyPoints.setTotalPoints(loyaltyPoints.getTotalPoints() + pointsToEarn);
        loyaltyPoints.setAvailablePoints(loyaltyPoints.getTotalPoints() - loyaltyPoints.getUsedPoints());
        LoyaltyPoints savedPoints = loyaltyPointsRepository.save(loyaltyPoints);

        // Ghi nhận transaction
        PointsTransaction transaction = PointsTransaction.builder()
                .userId(userId)
                .transactionType("EARN")
                .points(pointsToEarn)
                .bookingId(bookingId)
                .description("Tích điểm từ đặt phòng #" + bookingId + ", số tiền: " + totalAmount + " VND")
                .build();
        pointsTransactionRepository.save(transaction);

        log.info("Successfully earned {} points for user: {}", pointsToEarn, userId);
        return mapToResponse(savedPoints);
    }

    /**
     * Sử dụng điểm để giảm giá
     * @param request Thông tin sử dụng điểm
     * @return Tổng tiền được giảm
     */
    @Transactional
    public BigDecimal usePoints(ApplyPointsRequest request) {
        log.info("Using points for user: {}, points: {}, booking: {}", 
                request.getUserId(), request.getPointsToUse(), request.getBookingId());

        UUID userId = request.getUserId();
        Long pointsToUse = request.getPointsToUse();

        if (pointsToUse == null || pointsToUse <= 0) {
            throw new IllegalArgumentException("Points to use must be greater than 0");
        }

        LoyaltyPoints loyaltyPoints = loyaltyPointsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User has no loyalty points account"));

        Long availablePoints = loyaltyPoints.getTotalPoints() - loyaltyPoints.getUsedPoints();
        if (availablePoints < pointsToUse) {
            throw new IllegalArgumentException(
                    String.format("Insufficient points. Available: %d, Requested: %d", availablePoints, pointsToUse));
        }

        // Cập nhật điểm đã sử dụng
        loyaltyPoints.setUsedPoints(loyaltyPoints.getUsedPoints() + pointsToUse);
        loyaltyPointsRepository.save(loyaltyPoints);

        // Ghi nhận transaction
        PointsTransaction transaction = PointsTransaction.builder()
                .userId(userId)
                .transactionType("USE")
                .points(pointsToUse)
                .bookingId(request.getBookingId())
                .description(request.getDescription() != null ? request.getDescription() 
                        : "Sử dụng " + pointsToUse + " điểm cho đơn hàng #" + request.getBookingId())
                .build();
        pointsTransactionRepository.save(transaction);

        BigDecimal discountAmount = convertPointsToAmount(pointsToUse);
        log.info("Successfully used {} points for user: {}, discount amount: {}", pointsToUse, userId, discountAmount);
        return discountAmount;
    }

    /**
     * Lấy thông tin điểm của người dùng
     * @param userId ID của người dùng
     * @return Thông tin điểm
     */
    public LoyaltyPointsResponse getLoyaltyPoints(UUID userId) {
        LoyaltyPoints loyaltyPoints = loyaltyPointsRepository.findByUserId(userId)
                .orElseGet(() -> LoyaltyPoints.builder()
                        .userId(userId)
                        .totalPoints(0L)
                        .usedPoints(0L)
                        .availablePoints(0L)
                        .build());
        
        // Nếu là entity mới (chưa lưu), lưu nó vào DB
        if (loyaltyPoints.getId() == null) {
            loyaltyPoints = loyaltyPointsRepository.save(loyaltyPoints);
        }
        
        return mapToResponse(loyaltyPoints);
    }

    /**
     * Lấy lịch sử giao dịch điểm
     * @param userId ID của người dùng
     * @return Danh sách giao dịch
     */
    public List<PointsTransactionResponse> getTransactionHistory(UUID userId) {
        List<PointsTransaction> transactions = pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return transactions.stream()
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy lịch sử giao dịch của một booking
     * @param bookingId ID của booking
     * @return Danh sách giao dịch
     */
    public List<PointsTransactionResponse> getBookingTransactionHistory(String bookingId) {
        List<PointsTransaction> transactions = pointsTransactionRepository.findByBookingId(bookingId);
        return transactions.stream()
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Hoàn lại điểm khi hủy booking
     * @param userId ID của người dùng
     * @param bookingId ID của booking
     */
    @Transactional
    public void refundPoints(UUID userId, String bookingId) {
        log.info("Refunding points for user: {}, booking: {}", userId, bookingId);

        // Tìm tất cả transaction liên quan đến booking này
        List<PointsTransaction> transactions = pointsTransactionRepository.findByBookingId(bookingId);

        LoyaltyPoints loyaltyPoints = loyaltyPointsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User has no loyalty points account"));

        for (PointsTransaction transaction : transactions) {
            if ("EARN".equals(transaction.getTransactionType())) {
                // Hoàn lại điểm tích lũy
                loyaltyPoints.setTotalPoints(Math.max(0, loyaltyPoints.getTotalPoints() - transaction.getPoints()));
                
                // Ghi nhận transaction hoàn lại
                PointsTransaction refundTransaction = PointsTransaction.builder()
                        .userId(userId)
                        .transactionType("REFUND")
                        .points(transaction.getPoints())
                        .bookingId(bookingId)
                        .description("Hoàn lại " + transaction.getPoints() + " điểm từ hủy đặt phòng #" + bookingId)
                        .build();
                pointsTransactionRepository.save(refundTransaction);
            } else if ("USE".equals(transaction.getTransactionType())) {
                // Hoàn lại điểm đã sử dụng
                loyaltyPoints.setUsedPoints(Math.max(0, loyaltyPoints.getUsedPoints() - transaction.getPoints()));
                
                // Ghi nhận transaction hoàn lại
                PointsTransaction refundTransaction = PointsTransaction.builder()
                        .userId(userId)
                        .transactionType("REFUND")
                        .points(-transaction.getPoints())
                        .bookingId(bookingId)
                        .description("Hoàn lại " + transaction.getPoints() + " điểm đã sử dụng từ hủy đặt phòng #" + bookingId)
                        .build();
                pointsTransactionRepository.save(refundTransaction);
            }
        }

        loyaltyPoints.setAvailablePoints(loyaltyPoints.getTotalPoints() - loyaltyPoints.getUsedPoints());
        loyaltyPointsRepository.save(loyaltyPoints);
        log.info("Successfully refunded points for user: {}", userId);
    }

    private LoyaltyPointsResponse mapToResponse(LoyaltyPoints loyaltyPoints) {
        return LoyaltyPointsResponse.builder()
                .id(loyaltyPoints.getId())
                .userId(loyaltyPoints.getUserId())
                .totalPoints(loyaltyPoints.getTotalPoints())
                .usedPoints(loyaltyPoints.getUsedPoints())
                .availablePoints(loyaltyPoints.getAvailablePoints())
                .createdAt(loyaltyPoints.getCreatedAt())
                .updatedAt(loyaltyPoints.getUpdatedAt())
                .build();
    }

    private PointsTransactionResponse mapTransactionToResponse(PointsTransaction transaction) {
        return PointsTransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .transactionType(transaction.getTransactionType())
                .points(transaction.getPoints())
                .bookingId(transaction.getBookingId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
