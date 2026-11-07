package tests.engine;

import main.domain.BuyOrder;
import main.domain.OrderBook;
import main.domain.OrderStatus;
import main.domain.SellOrder;
import main.domain.TradingPair;
import main.engine.MatchingEngine;
import main.engine.TradingCoordinator;
import main.ledger.AccountAssetKey;
import main.ledger.BalanceBucket;
import main.ledger.Ledger;
import main.ledger.LedgerAuditReport;
import main.ledger.LedgerAuditor;
import main.ledger.LedgerBalanceView;
import main.ledger.LedgerOperation;
import main.ledger.LedgerOperationType;
import main.ledger.LedgerPosting;
import main.ledger.LedgerRecord;
import main.ledger.LedgerTransaction;
import main.ledger.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerReconciliationTest {

    @Test
    void replaysMixedDepositsHoldsTradeRefundReleaseAndWithdrawal() {
        Ledger ledger = new Ledger();
        ledger.createAccount("seller");
        ledger.createAccount("buyer");
        TradingPair pair = new TradingPair("ALUA", "ARS");
        OrderBook book = new OrderBook("ALUA-ARS");
        TradingCoordinator coordinator =
                new TradingCoordinator(pair, ledger, book, new MatchingEngine(book));

        ledger.deposit("seller", "ALUA", 10);
        ledger.deposit("buyer", "ARS", 1_200);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 10), 1L);
        coordinator.submitOrder(new BuyOrder(2, "buyer", 110, 10), 2L);
        ledger.withdraw("buyer", "ARS", 100);

        LedgerAuditReport actual = ledger.reconcile();
        LedgerAuditReport reconstructed = LedgerAuditor.replay(ledger.getJournal());
        assertEquals(reconstructed, actual);
        assertEquals(6L, actual.recordCount());
        assertEquals(1L, actual.tradeCount());
        assertEquals(new LedgerBalanceView(0, 0),
                actual.balances().get(new AccountAssetKey("seller", "ALUA")));
        assertEquals(new LedgerBalanceView(1_000, 0),
                actual.balances().get(new AccountAssetKey("seller", "ARS")));
        assertEquals(new LedgerBalanceView(100, 0),
                actual.balances().get(new AccountAssetKey("buyer", "ARS")));
        assertEquals(new LedgerBalanceView(10, 0),
                actual.balances().get(new AccountAssetKey("buyer", "ALUA")));
        assertThrows(UnsupportedOperationException.class, () ->
                actual.balances().clear());
        assertEquals(6L, ledger.getJournal().get(ledger.getJournal().size() - 1).sequence());
    }

    @Test
    void replaysCancellationAndPriceTimePriorityCollateralState() {
        Ledger ledger = new Ledger();
        ledger.createAccount("buyer");
        ledger.deposit("buyer", "ARS", 1_000L);
        OrderBook book = new OrderBook("ALUA-ARS");
        TradingCoordinator coordinator =
                new TradingCoordinator(new TradingPair("ALUA", "ARS"),
                        ledger, book, new MatchingEngine(book));
        BuyOrder pending = new BuyOrder(1L, "buyer", 100L, 8L);

        coordinator.submitOrder(pending, 1L);
        LedgerAuditReport open = ledger.reconcile();
        assertEquals(new LedgerBalanceView(200L, 800L),
                open.balances().get(new AccountAssetKey("buyer", "ARS")));

        assertTrue(coordinator.cancelOrder(1L));
        assertEquals(OrderStatus.CANCELED, pending.getStatus());
        assertEquals(new LedgerBalanceView(1_000L, 0L),
                ledger.reconcile().balances().get(new AccountAssetKey("buyer", "ARS")));
        assertEquals(0, ledger.getTransactions().size());
    }

    @Test
    void rejectsMissingOrDuplicatedSequenceNumbers() {
        LedgerOperation first = new LedgerOperation(
                1L, LedgerOperationType.DEPOSIT, "p", "ARS", 10L,
                List.of(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, 10L)));
        LedgerOperation skipped = new LedgerOperation(
                3L, LedgerOperationType.WITHDRAWAL, "p", "ARS", 1L,
                List.of(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, -1L)));

        assertThrows(IllegalStateException.class, () ->
                LedgerAuditor.replay(List.of(first, skipped)));
        assertThrows(IllegalStateException.class, () ->
                LedgerAuditor.replay(List.of(first, first)));
    }

    @Test
    void rejectsImpossibleUnfundedWithdrawalAndLeavesInputUnchanged() {
        LedgerOperation withdrawal = new LedgerOperation(
                1L, LedgerOperationType.WITHDRAWAL, "p", "ARS", 1L,
                List.of(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, -1L)));
        List<LedgerRecord> events = new ArrayList<>(List.of(withdrawal));

        assertThrows(IllegalStateException.class, () -> LedgerAuditor.replay(events));
        assertEquals(List.of(withdrawal), events);
    }

    @Test
    void rollbackDoesNotLeavePhantomTradeInReconstructedBalances() {
        Ledger ledger = new Ledger();
        ledger.createAccount("seller");
        ledger.createAccount("buyer");
        ledger.deposit("seller", "ALUA", 10);
        ledger.deposit("seller", "ARS", Long.MAX_VALUE - 5);
        ledger.deposit("buyer", "ARS", 1000);
        OrderBook book = new OrderBook("ALUA-ARS");
        TradingCoordinator coordinator =
                new TradingCoordinator(new TradingPair("ALUA", "ARS"),
                        ledger, book, new MatchingEngine(book));
        coordinator.submitOrder(new SellOrder(1L, "seller", 10, 10), 1L);
        var snapshot = ledger.getJournal();
        BuyOrder bid = new BuyOrder(2L, "buyer", 10, 10);

        assertThrows(ArithmeticException.class, () -> coordinator.submitOrder(bid, 2L));
        assertEquals(snapshot, ledger.getJournal());
        assertEquals(0L, ledger.reconcile().tradeCount());
        assertEquals(OrderStatus.CREATED, bid.getStatus());

        ledger.withdraw("seller", "ARS", Long.MAX_VALUE - 5);
        coordinator.submitOrder(bid, 3L);
        assertEquals(1L, ledger.reconcile().tradeCount());
    }

    @Test
    void rejectsOverflowingHistoricalAmounts() {
        LedgerOperation deposit = new LedgerOperation(
                1L, LedgerOperationType.DEPOSIT, "p", "ARS", Long.MAX_VALUE,
                List.of(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, Long.MAX_VALUE)));
        LedgerOperation extra = new LedgerOperation(
                2L, LedgerOperationType.DEPOSIT, "p", "ARS", 1L,
                List.of(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, 1L)));
        assertThrows(IllegalStateException.class, () ->
                LedgerAuditor.replay(List.of(deposit, extra)));
    }
}
