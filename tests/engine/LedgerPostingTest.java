package tests.engine;

import main.domain.*;
import main.engine.MatchingEngine;
import main.engine.TradingCoordinator;
import main.ledger.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class LedgerPostingTest {
    private final TradingPair pair = new TradingPair("ALUA", "ARS");
    private Ledger ledger;
    private TradingCoordinator coordinator;
    private Account buyer;
    private Account seller;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        OrderBook book = new OrderBook("ALUA-ARS");
        coordinator = new TradingCoordinator(pair, ledger, book, new MatchingEngine(book));
        buyer = ledger.createAccount("buyer");
        seller = ledger.createAccount("seller");
    }

    @Test
    void postsBalancedTradeAndRefundsBuyerAtBetterPrice() {
        ledger.deposit(seller.getParticipantId(), "ALUA", 10L);
        ledger.deposit(buyer.getParticipantId(), "ARS", 1100L);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 10), 10);
        assertTrue(ledger.getTransactions().isEmpty());

        List<Trade> trades = coordinator.submitOrder(new BuyOrder(2, "buyer", 110, 10), 11);

        assertEquals(1, trades.size());
        LedgerTransaction tx = ledger.getTransactions().get(0);
        assertEquals(ledger.getJournal().size(), tx.sequence());
        assertEquals(trades.get(0), tx.trade());
        assertEquals(5, tx.postings().size());

        Map<String, Long> totals = new HashMap<>();
        for (LedgerPosting posting : tx.postings()) {
            totals.merge(posting.assetSymbol(), posting.delta(), Math::addExact);
        }
        assertEquals(Map.of("ARS", 0L, "ALUA", 0L), totals);

        assertEquals(100L, buyer.getAvailableBalance("ARS"));
        assertEquals(0L, buyer.getHeldBalance("ARS"));
        assertEquals(10L, buyer.getAvailableBalance("ALUA"));
        assertEquals(1000L, seller.getAvailableBalance("ARS"));
        assertEquals(0L, seller.getHeldBalance("ALUA"));
        assertTrue(tx.postings().contains(
                new LedgerPosting("buyer", "ARS", BalanceBucket.AVAILABLE, 100L)));
        assertTrue(tx.postings().contains(
                new LedgerPosting("buyer", "ARS", BalanceBucket.HELD, -1100L)));
        assertThrows(UnsupportedOperationException.class,
                () -> tx.postings().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> ledger.getTransactions().clear());
    }

    @Test
    void recordsTradeWhenSellerIsAggressiveAndBuyerIsResting() {
        ledger.deposit(buyer.getParticipantId(), "ARS", 1100);
        ledger.deposit(seller.getParticipantId(), "ALUA", 10);
        coordinator.submitOrder(new BuyOrder(1, "buyer", 110, 10), 10);
        List<Trade> trades = coordinator.submitOrder(new SellOrder(2, "seller", 100, 10), 11);

        assertEquals(1, trades.size());
        assertEquals(110, trades.get(0).matchPrice());
        assertEquals(1, ledger.getTransactions().size());
        assertEquals(4, ledger.getTransactions().get(0).postings().size());
        assertEquals(1100L, seller.getAvailableBalance("ARS"));
        assertEquals(10L, buyer.getAvailableBalance("ALUA"));
        assertEquals(0L, buyer.getHeldBalance("ARS"));
    }

    @Test
    void rollbackDiscardsPostingForSuccessfulFirstLegWhenSecondLegFails() {
        Account secondSeller = ledger.createAccount("seller2");
        ledger.deposit(seller.getParticipantId(), "ALUA", 10);
        ledger.deposit(secondSeller.getParticipantId(), "ALUA", 5);
        ledger.deposit(secondSeller.getParticipantId(), "ARS", Long.MAX_VALUE - 5);
        coordinator.submitOrder(new SellOrder(1, "seller", 10, 10), 1);
        coordinator.submitOrder(new SellOrder(2, "seller2", 10, 5), 2);
        ledger.deposit(buyer.getParticipantId(), "ARS", 150);
        BuyOrder incoming = new BuyOrder(3, "buyer", 10, 15);

        assertThrows(ArithmeticException.class, () -> coordinator.submitOrder(incoming, 3));
        assertTrue(ledger.getTransactions().isEmpty());
        assertEquals(OrderStatus.CREATED, incoming.getStatus());
        assertEquals(150, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(10, seller.getHeldBalance("ALUA"));
        assertEquals(5, secondSeller.getHeldBalance("ALUA"));

        ledger.withdraw(secondSeller.getParticipantId(), "ARS", Long.MAX_VALUE - 5);
        List<Trade> retry = coordinator.submitOrder(incoming, 4);
        assertEquals(2, retry.size());
        assertEquals(2, ledger.getTransactions().size());
        long firstSequence = ledger.getTransactions().get(0).sequence();
        long secondSequence = ledger.getTransactions().get(1).sequence();
        assertEquals(firstSequence + 1L, secondSequence);
        assertEquals(ledger.getJournal().size(), secondSequence);
    }

    @Test
    void cancellationWithoutExecutionDoesNotCreateTradePosting() {
        ledger.deposit(seller.getParticipantId(), "ALUA", 10);
        SellOrder order = new SellOrder(1, "seller", 100, 10);
        coordinator.submitOrder(order, 1);
        assertTrue(coordinator.cancelOrder(1));
        assertEquals(10, seller.getAvailableBalance("ALUA"));
        assertEquals(0, seller.getHeldBalance("ALUA"));
        assertTrue(ledger.getTransactions().isEmpty());
    }

    @Test
    void rejectsUnbalancedTransactionAndZeroPosting() {
        Trade trade = new Trade(1, 2, 100, 1, 3);
        assertThrows(IllegalArgumentException.class, () ->
                new LedgerPosting("x", "ARS", BalanceBucket.AVAILABLE, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new LedgerTransaction(1, trade, List.of(
                        new LedgerPosting("x", "ARS", BalanceBucket.AVAILABLE, 100))));
    }
}
