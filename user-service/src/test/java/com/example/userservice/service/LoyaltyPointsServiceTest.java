package com.example.userservice.service;

import com.example.userservice.dto.ApplyPointsRequest;
import com.example.userservice.model.*;
import com.example.userservice.repository.*;
import com.example.userservice.regulations.RegulationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoyaltyPointsServiceTest {
    final UUID user = UUID.randomUUID();
    final LoyaltyPointsRepository accounts = mock(LoyaltyPointsRepository.class);
    final PointsTransactionRepository ledger = mock(PointsTransactionRepository.class);
    final LoyaltyCommandRepository commands = mock(LoyaltyCommandRepository.class);
    final RegulationClient rates = mock(RegulationClient.class);
    final List<PointsTransaction> rows = new ArrayList<>();
    final Map<String, LoyaltyCommand> receipts = new HashMap<>();
    final Map<String, PointsReservation> holds = new HashMap<>();
    final PointsReservationRepository reservations = mock(PointsReservationRepository.class);
    final LoyaltyPoints account = LoyaltyPoints.builder().id(UUID.randomUUID()).userId(user)
            .totalPoints(1000L).usedPoints(0L).availablePoints(1000L).build();
    final LoyaltyPointsService service = new LoyaltyPointsService(accounts, ledger, rates, commands, mock(LoyaltyCommandLock.class), reservations);

    @BeforeEach void setup() {
        when(accounts.findByUserId(user)).thenReturn(Optional.of(account));
        when(accounts.findAllByUserId(user)).thenReturn(List.of(account));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(ledger.findByBookingId(any())).thenAnswer(call -> rows.stream()
                .filter(row -> Objects.equals(row.getBookingId(), call.getArgument(0))).toList());
        when(ledger.save(any())).thenAnswer(call -> { PointsTransaction row = call.getArgument(0); rows.add(row); return row; });
        when(commands.findById(any())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
        when(commands.save(any())).thenAnswer(call -> { LoyaltyCommand receipt = call.getArgument(0); receipts.put(receipt.getCommandKey(), receipt); return receipt; });
        when(reservations.findById(any())).thenAnswer(call -> Optional.ofNullable(holds.get(call.getArgument(0))));
        when(reservations.save(any())).thenAnswer(call -> { PointsReservation hold = call.getArgument(0); holds.put(hold.getBookingId(), hold); return hold; });
        when(reservations.sumHeldByUserId(any())).thenAnswer(call -> holds.values().stream()
                .filter(hold -> hold.getUserId().equals(call.getArgument(0)) && hold.getState() == PointsReservationState.HELD)
                .mapToLong(PointsReservation::getPoints).sum());
        when(rates.getEarnConversionRate()).thenReturn(new BigDecimal("100"));
        when(rates.getRedemptionConversionRate()).thenReturn(new BigDecimal("500"));
    }

    @Test void earnReplayDoesNotAwardAgainEvenWhenRateChanges() {
        service.earnPoints(user, "booking", new BigDecimal("10000"));
        when(rates.getEarnConversionRate()).thenReturn(new BigDecimal("1000"));
        var replay = service.earnPoints(user, "booking", new BigDecimal("10000.00"));
        assertThat(replay.getTotalPoints()).isEqualTo(1100L);
        assertThat(replay.getAwardedPoints()).isEqualTo(100L);
        assertThat(rows).hasSize(1);
    }

    @Test void readSeparatesHeldPointsFromUsedPoints() {
        held("pending", 600);
        var balance = service.getLoyaltyPoints(user);
        assertThat(balance.getAvailablePoints()).isEqualTo(400L);
        assertThat(balance.getReservedPoints()).isEqualTo(600L);
        assertThat(balance.getUsedPoints()).isZero();
    }

    @Test void pendingBookingHoldCannotFundAnotherQuote() {
        held("pending", 600);
        assertThatThrownBy(() -> service.quote(new com.example.userservice.dto.PointsQuoteRequest(user, 500L, new BigDecimal("1000000"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void directRedemptionCannotConsumeAnotherBookingsHold() {
        held("pending", 600);
        assertThatThrownBy(() -> service.usePoints(use("other", 500))).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getUsedPoints()).isZero();
    }

    PointsReservation held(String booking, long points) {
        var hold = PointsReservation.builder().bookingId(booking).userId(user).requestedPoints(points)
                .totalAmount(new BigDecimal("1000000")).points(points).discountAmount(BigDecimal.valueOf(points * 500))
                .redemptionRate(new BigDecimal("500")).state(PointsReservationState.HELD)
                .holdExpiresAt(java.time.Instant.now().plusSeconds(300)).nextCheckAt(java.time.Instant.now().plusSeconds(300)).build();
        holds.put(booking, hold);
        return hold;
    }

    @Test void reserveBindsQuoteAndCannotBeDuplicatedOrOverbooked() {
        var deadline = java.time.Instant.now().plusSeconds(300);
        var request = new com.example.userservice.dto.ReservePointsRequest(user, "first", 600L, new BigDecimal("1000000"), deadline);
        var reserved = service.reserve(request);
        assertThat(reserved.pointsToUse()).isEqualTo(600L);
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(400L);
        when(rates.getRedemptionConversionRate()).thenThrow(new IllegalStateException("Rate unavailable"));
        assertThat(service.reserve(request)).isEqualTo(reserved);
        assertThat(holds).hasSize(1);
        assertThat(rows).isEmpty();
        doReturn(new BigDecimal("500")).when(rates).getRedemptionConversionRate();
        assertThatThrownBy(() -> service.reserve(new com.example.userservice.dto.ReservePointsRequest(user, "second", 600L,
                new BigDecimal("1000000"), deadline))).isInstanceOf(IllegalArgumentException.class);
        conflict(() -> service.reserve(new com.example.userservice.dto.ReservePointsRequest(user, "first", 601L,
                new BigDecimal("1000000"), deadline)));
    }

    @Test void reservedRateSnapshotSurvivesRateChangeAndOutageThroughPaymentAndReplay() {
        var request = new com.example.userservice.dto.ReservePointsRequest(user, "versioned", 900L,
                new BigDecimal("1000000"), java.time.Instant.now().plusSeconds(300));
        when(rates.getRedemptionConversionRate()).thenReturn(new BigDecimal("700"));
        var quote = service.reserve(request);
        assertThat(quote.pointsToUse()).isEqualTo(714L);
        when(rates.getRedemptionConversionRate()).thenReturn(new BigDecimal("900"));
        assertThat(service.reserve(request)).isEqualTo(quote);
        var apply = use("versioned", 714); apply.setExpectedDiscountAmount(new BigDecimal("499800"));
        assertThat(service.usePoints(apply)).isEqualByComparingTo("499800");
        when(rates.getRedemptionConversionRate()).thenThrow(new IllegalStateException("Regulations unavailable"));
        assertThat(service.usePoints(apply)).isEqualByComparingTo("499800");
        assertThat(account.getUsedPoints()).isEqualTo(714L);
        assertThat(rows).hasSize(1);
    }

    @Test void commitConvertsHoldToUseOnceEvenAfterDeadlineWithoutChangingSpendableBalance() {
        var held = held("booking", 600);
        held.setHoldExpiresAt(java.time.Instant.now().minusSeconds(1));
        var request = use("booking", 600); request.setExpectedDiscountAmount(new BigDecimal("300000"));
        assertThat(service.usePoints(request)).isEqualByComparingTo("300000");
        assertThat(service.usePoints(request)).isEqualByComparingTo("300000");
        assertThat(held.getState()).isEqualTo(PointsReservationState.COMMITTED);
        assertThat(account.getUsedPoints()).isEqualTo(600L);
        assertThat(service.getLoyaltyPoints(user).getReservedPoints()).isZero();
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(400L);
        assertThat(rows).hasSize(1);
        service.reconcileReservation("booking", user, "MISSING");
        assertThat(account.getUsedPoints()).isEqualTo(600L);
        assertThat(receipts).doesNotContainKey("booking|CANCEL");
    }

    @Test void heldSnapshotCannotBeChangedOrEarnedBeforeCommit() {
        held("booking", 600);
        var request = use("booking", 600); request.setExpectedDiscountAmount(new BigDecimal("300001"));
        conflict(() -> service.usePoints(request));
        conflict(() -> service.earnPoints(user, "booking", BigDecimal.TEN));
        assertThat(account.getUsedPoints()).isZero();
        assertThat(service.getLoyaltyPoints(user).getReservedPoints()).isEqualTo(600L);
    }

    @Test void cancelHeldPointsReleasesWithoutCreatingAUseEntryAndBlocksLateReserve() {
        var held = held("booking", 600);
        service.refundPoints(user, "booking");
        service.refundPoints(user, "booking");
        assertThat(held.getState()).isEqualTo(PointsReservationState.CANCELLED);
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(1000L);
        assertThat(rows).isEmpty();
        conflict(() -> service.reserve(new com.example.userservice.dto.ReservePointsRequest(user, "booking", 600L,
                new BigDecimal("1000000"), held.getHoldExpiresAt())));
    }

    @Test void expiredHoldRequiresAuthoritativeMissingOrCancelledBookingBeforeRelease() {
        var held = held("booking", 600);
        held.setHoldExpiresAt(java.time.Instant.now().minusSeconds(10));
        service.reconcileReservation("booking", user, "ACTIVE");
        service.reconcileReservation("booking", user, "UNKNOWN");
        assertThat(held.getState()).isEqualTo(PointsReservationState.HELD);
        assertThat(held.getNextCheckAt()).isAfter(java.time.Instant.now());
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(400L);
        service.reconcileReservation("booking", user, "MISSING");
        assertThat(held.getState()).isEqualTo(PointsReservationState.CANCELLED);
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(1000L);
    }

    @Test void existingReservationRemainsHonouredIfAnotherEarningReversalCreatesDebt() {
        account.setTotalPoints(0L);
        service.earnPoints(user, "earned-here", new BigDecimal("2000"));
        service.reserve(new com.example.userservice.dto.ReservePointsRequest(user, "held-elsewhere", 15L,
                new BigDecimal("1000000"), java.time.Instant.now().plusSeconds(300)));
        service.refundPoints(user, "earned-here");
        var request = use("held-elsewhere", 15); request.setExpectedDiscountAmount(new BigDecimal("7500"));
        assertThat(service.usePoints(request)).isEqualByComparingTo("7500");
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(-15L);
    }

    @Test void changedEarnPayloadCannotReuseCommand() {
        service.earnPoints(user, "booking", new BigDecimal("10000"));
        conflict(() -> service.earnPoints(user, "booking", new BigDecimal("10001")));
        assertThat(account.getTotalPoints()).isEqualTo(1100L);
    }

    @Test void useReplayReturnsOriginalDiscountAndDoesNotDebitAgain() {
        assertThat(service.usePoints(use("booking", 2))).isEqualByComparingTo("1000");
        when(rates.getRedemptionConversionRate()).thenReturn(new BigDecimal("700"));
        assertThat(service.usePoints(use("booking", 2))).isEqualByComparingTo("1000");
        assertThat(account.getUsedPoints()).isEqualTo(2L);
        assertThat(rows).hasSize(1);
        conflict(() -> service.usePoints(use("booking", 3)));
    }

    @Test void cancellationReversesEarnAndUseOnlyOnceAndBlocksLateCommands() {
        service.earnPoints(user, "booking", new BigDecimal("2000"));
        service.usePoints(use("booking", 10));
        service.refundPoints(user, "booking");
        service.refundPoints(user, "booking");
        assertThat(account.getTotalPoints()).isEqualTo(1000L);
        assertThat(account.getUsedPoints()).isZero();
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(1000L);
        conflict(() -> service.earnPoints(user, "booking", new BigDecimal("2000")));
        conflict(() -> service.usePoints(use("booking", 10)));
    }

    @Test void cancellationBeforeAnyAwardLeavesATombstone() {
        service.refundPoints(user, "booking");
        conflict(() -> service.earnPoints(user, "booking", new BigDecimal("2000")));
        assertThat(account.getTotalPoints()).isEqualTo(1000L);
    }

    @Test void crossUserCommandCannotReverseAnotherUsersLedger() {
        rows.add(PointsTransaction.builder().bookingId("booking").userId(UUID.randomUUID())
                .transactionType("EARN").points(20L).build());
        conflict(() -> service.refundPoints(user, "booking"));
        assertThat(account.getTotalPoints()).isEqualTo(1000L);
    }

    @Test void ambiguousLegacyLedgerRequiresReconciliation() {
        for (int i = 0; i < 2; i++) rows.add(PointsTransaction.builder().bookingId("booking").userId(user)
                .transactionType("EARN").points(20L).build());
        conflict(() -> service.refundPoints(user, "booking"));
        assertThat(account.getTotalPoints()).isEqualTo(1000L);
    }

    @Test void readOfMissingAccountDoesNotCreateOne() {
        service.getLoyaltyPoints(UUID.randomUUID());
        verify(accounts, never()).save(any());
    }

    @Test void invalidCommandInputDoesNotWrite() {
        assertThatThrownBy(() -> service.earnPoints(user, "", BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.earnPoints(user, "booking", new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        verify(accounts, never()).save(any());
        verify(ledger, never()).save(any());
    }

    @Test void quoteCapsWholePointsUsingRegulationsWithoutDebiting() {
        when(rates.getRedemptionConversionRate()).thenReturn(new BigDecimal("700"));
        var quote = service.quote(new com.example.userservice.dto.PointsQuoteRequest(user, 900L, new BigDecimal("1000000")));
        assertThat(quote.pointsToUse()).isEqualTo(714L);
        assertThat(quote.discountAmount()).isEqualByComparingTo("499800");
        assertThat(quote.redemptionRate()).isEqualByComparingTo("700");
        assertThat(account.getUsedPoints()).isZero();
        assertThat(service.quote(new com.example.userservice.dto.PointsQuoteRequest(user, Long.MAX_VALUE, new BigDecimal("1")))
                .pointsToUse()).isZero();
        verify(accounts, never()).save(any());
    }

    @Test void bookingSnapshotIsUsedEvenAfterRateChanges() {
        var request = use("booking", 10);
        request.setExpectedDiscountAmount(new BigDecimal("5000"));
        when(rates.getRedemptionConversionRate()).thenThrow(new IllegalStateException("Regulations unavailable"));
        assertThat(service.usePoints(request)).isEqualByComparingTo("5000");
        assertThat(service.usePoints(request)).isEqualByComparingTo("5000");
        request.setExpectedDiscountAmount(new BigDecimal("6000"));
        conflict(() -> service.usePoints(request));
        assertThat(account.getUsedPoints()).isEqualTo(10L);
    }

    @Test void zeroEarningStillHasAnImmutableCommandReceipt() {
        service.earnPoints(user, "small", BigDecimal.ONE);
        when(rates.getEarnConversionRate()).thenReturn(new BigDecimal("0.1"));
        assertThat(service.earnPoints(user, "small", BigDecimal.ONE).getAwardedPoints()).isZero();
        assertThat(account.getTotalPoints()).isEqualTo(1000L);
        assertThat(rows).hasSize(1);
    }

    @Test void reversalPreservesDebtForPointsAlreadySpentElsewhere() {
        account.setTotalPoints(0L);
        service.earnPoints(user, "earned-here", new BigDecimal("2000"));
        service.usePoints(use("spent-elsewhere", 15));
        service.refundPoints(user, "earned-here");
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(-15L);
        assertThatThrownBy(() -> service.usePoints(use("another-booking", 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateLegacyAccountsAreNotMergedAutomatically() {
        when(accounts.findAllByUserId(user)).thenReturn(List.of(account, account));
        conflict(() -> service.getLoyaltyPoints(user));
        verify(accounts, never()).save(any());
    }

    @Test void ambiguousLegacyLedgerCannotFundNewBookingsOrAccumulateNewEarnings() {
        when(ledger.hasAmbiguousEarningOrUse(user)).thenReturn(true);
        conflict(() -> service.quote(new com.example.userservice.dto.PointsQuoteRequest(user, 1L, new BigDecimal("10000"))));
        conflict(() -> service.usePoints(use("new", 1)));
        conflict(() -> service.earnPoints(user, "new", new BigDecimal("10000")));
        assertThat(service.getLoyaltyPoints(user).getAvailablePoints()).isEqualTo(1000L);
        assertThat(rows).isEmpty();
        verify(accounts, never()).save(any());
    }

    @Test void existingReceiptsAndBackedHoldsRemainUsableWhenLegacyAccountNeedsReview() {
        service.earnPoints(user, "receipted", new BigDecimal("100"));
        held("backed", 10);
        when(ledger.hasAmbiguousEarningOrUse(user)).thenReturn(true);
        assertThat(service.earnPoints(user, "receipted", new BigDecimal("100")).getAwardedPoints()).isEqualTo(1L);
        var request = use("backed", 10); request.setExpectedDiscountAmount(new BigDecimal("5000"));
        assertThat(service.usePoints(request)).isEqualByComparingTo("5000");
        service.refundPoints(user, "backed");
        assertThat(account.getUsedPoints()).isZero();
        assertThat(account.getTotalPoints()).isEqualTo(1001L);
    }

    ApplyPointsRequest use(String booking, long points) {
        return ApplyPointsRequest.builder().userId(user).bookingId(booking).pointsToUse(points).build();
    }
    void conflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }
}
