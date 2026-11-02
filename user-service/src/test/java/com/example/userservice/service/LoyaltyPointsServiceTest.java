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
    final LoyaltyPoints account = LoyaltyPoints.builder().id(UUID.randomUUID()).userId(user)
            .totalPoints(1000L).usedPoints(0L).availablePoints(1000L).build();
    final LoyaltyPointsService service = new LoyaltyPointsService(accounts, ledger, rates, commands, mock(LoyaltyCommandLock.class));

    @BeforeEach void setup() {
        when(accounts.findByUserId(user)).thenReturn(Optional.of(account));
        when(accounts.findAllByUserId(user)).thenReturn(List.of(account));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(ledger.findByBookingId(any())).thenAnswer(call -> rows.stream()
                .filter(row -> Objects.equals(row.getBookingId(), call.getArgument(0))).toList());
        when(ledger.save(any())).thenAnswer(call -> { PointsTransaction row = call.getArgument(0); rows.add(row); return row; });
        when(commands.findById(any())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
        when(commands.save(any())).thenAnswer(call -> { LoyaltyCommand receipt = call.getArgument(0); receipts.put(receipt.getCommandKey(), receipt); return receipt; });
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

    ApplyPointsRequest use(String booking, long points) {
        return ApplyPointsRequest.builder().userId(user).bookingId(booking).pointsToUse(points).build();
    }
    void conflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }
}
