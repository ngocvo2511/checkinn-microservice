package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.model.*;
import com.example.userservice.repository.*;
import com.example.userservice.regulations.RegulationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoyaltyPointsService {
    private final LoyaltyPointsRepository loyaltyPointsRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final RegulationClient regulationClient;
    private final LoyaltyCommandRepository commands;
    private final LoyaltyCommandLock commandLock;
    private final PointsReservationRepository reservations;

    @org.springframework.beans.factory.annotation.Value("${points.recovery.retry-seconds:60}")
    private long recoveryRetrySeconds = 60;

    public Long calculatePoints(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return 0L;
        return amount.divide(validRate(regulationClient.getEarnConversionRate()), 0, RoundingMode.DOWN).longValueExact();
    }

    public BigDecimal convertPointsToAmount(Long points) {
        if (points == null || points <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(points).multiply(validRate(regulationClient.getRedemptionConversionRate()));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PointsQuoteResponse quote(PointsQuoteRequest request) {
        if (request == null || request.userId() == null || request.pointsToUse() == null || request.pointsToUse() <= 0
                || request.totalAmount() == null || request.totalAmount().signum() <= 0) {
            throw new IllegalArgumentException("Positive points and total amount required");
        }
        requireUnambiguousAccount(request.userId());
        BigDecimal rate = validRate(regulationClient.getRedemptionConversionRate());
        BigDecimal cap = request.totalAmount().multiply(new BigDecimal("0.5"));
        long actual = cap.divide(rate, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(request.pointsToUse())).longValueExact();
        var account = account(request.userId());
        if (actual > spendable(account)) {
            throw new IllegalArgumentException("Insufficient points");
        }
        return new PointsQuoteResponse(actual, rate.multiply(BigDecimal.valueOf(actual)), rate);
    }

    @Transactional
    public PointsReservationResponse reserve(ReservePointsRequest request) {
        if (request == null) throw new IllegalArgumentException("Reservation required");
        validateCommand(request.userId(), request.bookingId());
        if (request.pointsToUse() == null || request.pointsToUse() <= 0 || request.totalAmount() == null
                || request.totalAmount().signum() <= 0 || request.holdExpiresAt() == null) {
            throw new IllegalArgumentException("Positive points, amount and inventory deadline required");
        }
        commandLock.acquire(request.bookingId(), request.userId());
        requireNotCancelled(request.userId(), request.bookingId());
        Instant deadline = request.holdExpiresAt().truncatedTo(ChronoUnit.MILLIS);
        var previous = reservations.findById(request.bookingId());
        if (previous.isPresent()) {
            var held = previous.get();
            if (!request.userId().equals(held.getUserId()) || !request.pointsToUse().equals(held.getRequestedPoints())
                    || request.totalAmount().compareTo(held.getTotalAmount()) != 0 || !deadline.equals(held.getHoldExpiresAt())
                    || held.getState() == PointsReservationState.CANCELLED) {
                throw conflict("Reservation key reused with a different input or cancelled booking");
            }
            return reservationResponse(held);
        }
        if (!deadline.isAfter(Instant.now())) throw conflict("Inventory hold deadline has passed");
        if (!history(request.userId(), request.bookingId()).isEmpty()
                || commands.existsById(key(request.bookingId(), "USE")) || commands.existsById(key(request.bookingId(), "EARN"))) {
            throw conflict("Existing booking ledger cannot be reserved retroactively");
        }
        var quote = quote(new PointsQuoteRequest(request.userId(), request.pointsToUse(), request.totalAmount()));
        var held = PointsReservation.builder().bookingId(request.bookingId()).userId(request.userId())
                .requestedPoints(request.pointsToUse()).totalAmount(request.totalAmount()).points(quote.pointsToUse())
                .discountAmount(quote.discountAmount()).redemptionRate(quote.redemptionRate()).state(PointsReservationState.HELD)
                .holdExpiresAt(deadline).nextCheckAt(deadline).build();
        reservations.save(held);
        return reservationResponse(held);
    }

    @Transactional
    public LoyaltyPointsResponse earnPoints(UUID userId, UUID bookingId, BigDecimal totalAmount) {
        validateCommand(userId, bookingId);
        if (totalAmount == null || totalAmount.signum() < 0) throw new IllegalArgumentException("Amount must be nonnegative");
        commandLock.acquire(bookingId, userId);
        requireNotCancelled(userId, bookingId);
        reservations.findById(bookingId).ifPresent(held -> {
            if (!userId.equals(held.getUserId()) || (held.getState() == PointsReservationState.HELD && held.getPoints() > 0)) {
                throw conflict("Reserved points must be committed before earning");
            }
            if (held.getState() == PointsReservationState.HELD) {
                held.setState(PointsReservationState.COMMITTED); // A zero-point hold needs no USE entry.
                reservations.save(held);
            }
        });
        var previous = commands.findById(key(bookingId, "EARN"));
        if (previous.isPresent()) {
            requireSameInput(previous.get(), userId, totalAmount, null);
            return earnedResponse(account(userId), previous.get().getResultPoints());
        }
        var history = history(userId, bookingId);
        if (history.stream().anyMatch(row -> "EARN".equals(row.getTransactionType()))) {
            throw conflict("Legacy EARN has no command receipt; reconciliation required");
        }
        requireUnambiguousAccount(userId);
        long earned = calculatePoints(totalAmount);
        var account = account(userId);
        account.setTotalPoints(Math.addExact(account.getTotalPoints(), earned));
        saveAccount(account);
        append(userId, bookingId, "EARN", earned, "Earned points for booking " + bookingId);
        receipt(userId, bookingId, "EARN", totalAmount, null, earned, null);
        return earnedResponse(account, earned);
    }

    @Transactional
    public BigDecimal usePoints(ApplyPointsRequest request) {
        if (request == null) throw new IllegalArgumentException("Points request required");
        UUID userId = request.getUserId();
        UUID bookingId = request.getBookingId();
        validateCommand(userId, bookingId);
        Long points = request.getPointsToUse();
        if (points == null || points <= 0) throw new IllegalArgumentException("Points to use must be greater than 0");
        BigDecimal expectedDiscount = request.getExpectedDiscountAmount();
        if (expectedDiscount != null && expectedDiscount.signum() <= 0) {
            throw new IllegalArgumentException("Expected discount must be positive");
        }
        commandLock.acquire(bookingId, userId);
        requireNotCancelled(userId, bookingId);
        var previous = commands.findById(key(bookingId, "USE"));
        if (previous.isPresent()) {
            requireSameInput(previous.get(), userId, expectedDiscount, points);
            return previous.get().getResultAmount();
        }
        var history = history(userId, bookingId);
        if (history.stream().anyMatch(row -> "USE".equals(row.getTransactionType()))) {
            throw conflict("Legacy USE has no monetary receipt; reconciliation required");
        }
        var account = account(userId);
        var own = reservations.findById(bookingId).orElse(null);
        BigDecimal discount;
        if (own != null) {
            if (!userId.equals(own.getUserId()) || own.getState() != PointsReservationState.HELD
                    || !points.equals(own.getPoints()) || expectedDiscount == null
                    || expectedDiscount.compareTo(own.getDiscountAmount()) != 0) {
                throw conflict("Redemption does not match the held reservation");
            }
            // The hold remains backed until explicit release, including payment at the inventory deadline.
            // Honour an existing hold even if another earning reversal has since created account debt.
            discount = own.getDiscountAmount();
            own.setState(PointsReservationState.COMMITTED);
            reservations.save(own);
        } else {
            requireUnambiguousAccount(userId);
            if (spendable(account) < points) throw new IllegalArgumentException("Insufficient points");
            discount = expectedDiscount == null ? convertPointsToAmount(points) : expectedDiscount;
        }
        account.setUsedPoints(Math.addExact(account.getUsedPoints(), points));
        saveAccount(account);
        append(userId, bookingId, "USE", points, "Used points for booking " + bookingId);
        receipt(userId, bookingId, "USE", expectedDiscount, points, points, discount);
        return discount;
    }

    /** Reads never insert an account or race a first EARN. */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public LoyaltyPointsResponse getLoyaltyPoints(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("User required");
        return mapToResponse(account(userId));
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getTransactionHistory(UUID userId) {
        return pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapTransactionToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getBookingTransactionHistory(UUID bookingId) {
        return pointsTransactionRepository.findByBookingId(bookingId).stream().map(this::mapTransactionToResponse).toList();
    }

    @Transactional
    public void refundPoints(UUID userId, UUID bookingId) {
        validateCommand(userId, bookingId);
        commandLock.acquire(bookingId, userId);
        var previous = commands.findById(key(bookingId, "CANCEL"));
        if (previous.isPresent()) {
            requireSameInput(previous.get(), userId, null, null);
            cancelReservation(userId, bookingId);
            return;
        }
        var history = history(userId, bookingId);
        long earned = history.stream().filter(row -> "EARN".equals(row.getTransactionType()))
                .mapToLong(PointsTransaction::getPoints).sum();
        long used = history.stream().filter(row -> "USE".equals(row.getTransactionType()))
                .mapToLong(PointsTransaction::getPoints).sum();
        var account = account(userId);
        if (account.getTotalPoints() < earned || account.getUsedPoints() < used) {
            throw conflict("Ledger and account disagree; reconciliation required");
        }
        account.setTotalPoints(Math.subtractExact(account.getTotalPoints(), earned));
        account.setUsedPoints(Math.subtractExact(account.getUsedPoints(), used));
        // Preserve debt if points earned here were already spent on another booking.
        saveAccount(account);
        if (earned != 0) append(userId, bookingId, "REVERSE_EARN", earned, "Reversed earning for booking " + bookingId);
        if (used != 0) append(userId, bookingId, "REVERSE_USE", -used, "Reversed redemption for booking " + bookingId);
        // Even an empty ledger needs a tombstone against delayed EARN/USE.
        receipt(userId, bookingId, "CANCEL", null, null, used - earned, null);
        cancelReservation(userId, bookingId);
    }

    /** The booking lookup runs outside this transaction; never wait on booking while holding a points lock. */
    @Transactional
    public void reconcileReservation(UUID bookingId, UUID userId, String observedBookingState) {
        validateCommand(userId, bookingId);
        commandLock.acquire(bookingId, userId);
        var held = reservations.findById(bookingId).orElse(null);
        Instant now = Instant.now();
        if (held == null || held.getState() != PointsReservationState.HELD || !userId.equals(held.getUserId())
                || held.getHoldExpiresAt().isAfter(now)) return;
        if ("MISSING".equals(observedBookingState) || "CANCELLED".equals(observedBookingState)) {
            refundPoints(userId, bookingId);
        } else {
            // ACTIVE and unknown/outage outcomes must not release funds promised to a payment.
            held.setNextCheckAt(now.plusSeconds(Math.max(1, recoveryRetrySeconds)));
            reservations.save(held);
        }
    }

    private void cancelReservation(UUID userId, UUID bookingId) {
        reservations.findById(bookingId).ifPresent(held -> {
            if (!userId.equals(held.getUserId())) throw conflict("Reservation belongs to another user");
            held.setState(PointsReservationState.CANCELLED);
            reservations.save(held);
        });
    }

    private PointsReservationResponse reservationResponse(PointsReservation held) {
        return new PointsReservationResponse(held.getBookingId(), held.getPoints(), held.getDiscountAmount(),
                held.getRedemptionRate(), held.getHoldExpiresAt(), held.getState().name());
    }

    private long spendable(LoyaltyPoints account) {
        return Math.subtractExact(Math.subtractExact(account.getTotalPoints(), account.getUsedPoints()),
                reservations.sumHeldByUserId(account.getUserId()));
    }

    private void requireUnambiguousAccount(UUID userId) {
        if (pointsTransactionRepository.hasAmbiguousEarningOrUse(userId)) {
            throw conflict("Account has ambiguous historical points; reconciliation required before new commands");
        }
    }

    private void validateCommand(UUID userId, UUID bookingId) {
        if (userId == null || bookingId == null) {
            throw new IllegalArgumentException("User and booking UUID required");
        }
    }

    private void requireNotCancelled(UUID userId, UUID bookingId) {
        commands.findById(key(bookingId, "CANCEL")).ifPresent(previous -> {
            requireSameInput(previous, userId, null, null);
            throw conflict("Booking points have been cancelled");
        });
    }

    private List<PointsTransaction> history(UUID userId, UUID bookingId) {
        var rows = pointsTransactionRepository.findByBookingId(bookingId);
        if (rows.stream().anyMatch(row -> !userId.equals(row.getUserId()) || row.getPoints() == null
                || row.getPoints() < 0 || !Set.of("EARN", "USE").contains(row.getTransactionType()))
                || rows.stream().filter(row -> "EARN".equals(row.getTransactionType())).count() > 1
                || rows.stream().filter(row -> "USE".equals(row.getTransactionType())).count() > 1) {
            throw conflict("Ambiguous legacy booking ledger; reconciliation required");
        }
        return rows;
    }

    private LoyaltyPoints account(UUID userId) {
        var accounts = loyaltyPointsRepository.findAllByUserId(userId);
        if (accounts.size() > 1) throw conflict("Multiple loyalty accounts; reconciliation required");
        return accounts.isEmpty() ? LoyaltyPoints.builder().userId(userId).totalPoints(0L).usedPoints(0L).availablePoints(0L).build()
                : accounts.getFirst();
    }

    private void saveAccount(LoyaltyPoints account) {
        account.setAvailablePoints(Math.subtractExact(account.getTotalPoints(), account.getUsedPoints()));
        loyaltyPointsRepository.save(account);
    }

    private BigDecimal validRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Points conversion rate unavailable");
        }
        return rate;
    }

    private void requireSameInput(LoyaltyCommand previous, UUID userId, BigDecimal amount, Long points) {
        if (!userId.equals(previous.getUserId()) || !Objects.equals(points, previous.getInputPoints())
                || (amount == null ? previous.getInputAmount() != null
                : previous.getInputAmount() == null || amount.compareTo(previous.getInputAmount()) != 0)) {
            throw conflict("Command key reused with a different user or payload");
        }
    }

    private String key(UUID bookingId, String type) { return bookingId + "|" + type; }

    private void receipt(UUID userId, UUID bookingId, String type, BigDecimal amount, Long points, long result, BigDecimal discount) {
        commands.save(LoyaltyCommand.builder().commandKey(key(bookingId, type)).bookingId(bookingId).commandType(type)
                .userId(userId).inputAmount(amount).inputPoints(points).resultPoints(result).resultAmount(discount).build());
    }

    private void append(UUID userId, UUID bookingId, String type, long points, String description) {
        pointsTransactionRepository.save(PointsTransaction.builder().userId(userId).bookingId(bookingId)
                .transactionType(type).points(points).description(description).build());
    }

    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }

    private LoyaltyPointsResponse mapToResponse(LoyaltyPoints account) {
        long reserved = reservations.sumHeldByUserId(account.getUserId());
        return LoyaltyPointsResponse.builder().id(account.getId()).userId(account.getUserId())
                .totalPoints(account.getTotalPoints()).usedPoints(account.getUsedPoints())
                .availablePoints(Math.subtractExact(Math.subtractExact(account.getTotalPoints(), account.getUsedPoints()), reserved))
                .reservedPoints(reserved)
                .createdAt(account.getCreatedAt()).updatedAt(account.getUpdatedAt()).build();
    }

    private LoyaltyPointsResponse earnedResponse(LoyaltyPoints account, long earned) {
        var response = mapToResponse(account);
        response.setAwardedPoints(earned);
        return response;
    }

    private PointsTransactionResponse mapTransactionToResponse(PointsTransaction row) {
        return PointsTransactionResponse.builder().id(row.getId()).userId(row.getUserId())
                .transactionType(row.getTransactionType()).points(row.getPoints()).bookingId(row.getBookingId())
                .description(row.getDescription()).createdAt(row.getCreatedAt()).build();
    }
}
