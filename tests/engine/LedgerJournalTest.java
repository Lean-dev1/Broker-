package tests.engine;

import main.domain.*;
import main.engine.MatchingEngine;
import main.engine.TradingCoordinator;
import main.ledger.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** All balance mutations must leave an inspectable in-memory journal entry. */
class LedgerJournalTest {
    @Test
    void recordsExternalFlowsAndBalancedTransfersInSequence() {
        Ledger ledger = new Ledger();
        Account account = ledger.createAccount("trader");

        LedgerOperation deposit = ledger.deposit("trader", "ARS", 1_000);
        LedgerOperation hold = ledger.hold("trader", "ARS", 700);
        LedgerOperation release = ledger.release("trader", "ARS", 200);
        LedgerOperation withdrawal = ledger.withdraw("trader", "ARS", 100);

        assertEquals(List.of(1L, 2L, 3L, 4L),
                ledger.getJournal().stream().map(LedgerRecord::sequence).toList());
        assertEquals(List.of(LedgerOperationType.DEPOSIT, LedgerOperationType.HOLD,
                LedgerOperationType.RELEASE, LedgerOperationType.WITHDRAWAL),
                ledger.getJournal().stream().map(e -> ((LedgerOperation)e).type()).toList());
        assertEquals(400, account.getAvailableBalance("ARS"));
        assertEquals(500, account.getHeldBalance("ARS"));
        assertEquals(900, account.getTotalBalance("ARS"));
        assertEquals(1, deposit.postings().size());
        assertEquals(1, withdrawal.postings().size());
        assertEquals(2, hold.postings().size());
        assertEquals(2, release.postings().size());
        for (LedgerOperation operation : List.of(hold, release)) {
            long sum = operation.postings().stream().mapToLong(LedgerPosting::delta).sum();
            assertEquals(0L, sum, "Internal reserve/release must be zero-sum");
        }
        assertThrows(UnsupportedOperationException.class, () -> ledger.getJournal().clear());
        assertThrows(UnsupportedOperationException.class, () -> deposit.postings().clear());
    }

    @Test
    void rejectedBalanceMutationsDoNotConsumeJournalSequence() {
        Ledger ledger = new Ledger();
        Account account = ledger.createAccount("trader");
        ledger.deposit("trader", "ARS", 50);

        assertThrows(InsufficientFundsException.class, () -> ledger.withdraw("trader", "ARS", 51));
        assertThrows(InsufficientFundsException.class, () -> ledger.hold("trader", "ARS", 51));
        assertThrows(IllegalArgumentException.class, () -> ledger.deposit("trader", "ARS", -1));
        assertThrows(IllegalArgumentException.class, () -> ledger.release("trader", "ARS", 0));
        assertEquals(1, ledger.getJournal().size());
        assertEquals(50, account.getAvailableBalance("ARS"));
        assertEquals(0, account.getHeldBalance("ARS"));

        LedgerOperation next = ledger.hold("trader", "ARS", 20);
        assertEquals(2L, next.sequence());
        assertEquals(2, ledger.getJournal().size());
    }

    @Test
    void submissionRejectionRollsBackTemporaryHoldEntry() {
        Ledger ledger = new Ledger();
        Account buyer = ledger.createAccount("buyer");
        Account seller = ledger.createAccount("seller");
        ledger.deposit("buyer", "ARS", 1_000);
        ledger.deposit("seller", "ALUA", 10);
        // Approaching overflow at seller's cash balance forces settlement to fail.
        ledger.deposit("seller", "ARS", Long.MAX_VALUE - 5);
        OrderBook book = new OrderBook("ALUA-ARS");
        TradingCoordinator coordinator =
                new TradingCoordinator(new TradingPair("ALUA", "ARS"),
                        ledger, book, new MatchingEngine(book));

        coordinator.submitOrder(new SellOrder(1, "seller", 10, 10), 1L);
        List<LedgerRecord> before = ledger.getJournal();
        BuyOrder incoming = new BuyOrder(2, "buyer", 10, 10);
        assertThrows(ArithmeticException.class, () -> coordinator.submitOrder(incoming, 2L));
        assertEquals(before, ledger.getJournal());
        assertEquals(OrderStatus.CREATED, incoming.getStatus());
        assertEquals(1_000, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(10, seller.getHeldBalance("ALUA"));

        // After moving the seller's external balance out, a retry may use the ID.
        LedgerOperation withdrawal =
                ledger.withdraw("seller", "ARS", Long.MAX_VALUE - 5);
        assertEquals(before.size() + 1L, withdrawal.sequence());
        assertEquals(1, coordinator.submitOrder(incoming, 3L).size());
        assertEquals(1, ledger.getTransactions().size());
        assertEquals(withdrawal.sequence() + 1, ledger.getJournal().get(
                ledger.getJournal().size() - 2).sequence());
    }

    @Test
    void mixedJournalKeepsAccountMovementsAndTradeInOneSequence() {
        Ledger ledger = new Ledger();
        Account buyer = ledger.createAccount("buyer");
        Account seller = ledger.createAccount("seller");
        OrderBook book = new OrderBook("ALUA-ARS");
        TradingCoordinator coordinator =
                new TradingCoordinator(new TradingPair("ALUA", "ARS"),
                        ledger, book, new MatchingEngine(book));
        ledger.deposit("buyer", "ARS", 1100);
        ledger.deposit("seller", "ALUA", 10);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 10), 1L);
        coordinator.submitOrder(new BuyOrder(2, "buyer", 110, 10), 2L);

        List<LedgerRecord> history = ledger.getJournal();
        assertEquals(5, history.size()); // 2 external deposits + 2 holds + 1 trade
        assertEquals(1, ledger.getTransactions().size());
        for (int i = 0; i < history.size(); i++) {
            assertEquals(i + 1L, history.get(i).sequence());
        }
        assertInstanceOf(LedgerTransaction.class, history.get(4));
        assertEquals(100, buyer.getAvailableBalance("ARS"));
        assertEquals(10, buyer.getAvailableBalance("ALUA"));
        assertEquals(1000, seller.getAvailableBalance("ARS"));
    }

    @Test
    void rejectsCombinedAvailableAndHeldTotalOverflow() {
        Ledger ledger = new Ledger();
        Account account = ledger.createAccount("trader");
        ledger.deposit("trader", "ARS", Long.MAX_VALUE);
        ledger.hold("trader", "ARS", Long.MAX_VALUE);

        assertThrows(ArithmeticException.class,
                () -> ledger.deposit("trader", "ARS", 1));
        assertEquals(2, ledger.getJournal().size());
        assertEquals(0, account.getAvailableBalance("ARS"));
        assertEquals(Long.MAX_VALUE, account.getHeldBalance("ARS"));
        assertEquals(Long.MAX_VALUE, account.getTotalBalance("ARS"));
    }

    @Test
    void invalidOperationPostingsCannotBeForged() {
        LedgerPosting correct = new LedgerPosting("participant", "ARS", BalanceBucket.AVAILABLE, 10);
        assertThrows(IllegalArgumentException.class, () ->
                new LedgerOperation(1, LedgerOperationType.HOLD, "participant", "ARS", 10,
                        List.of(correct)));
        assertThrows(IllegalArgumentException.class, () ->
                new LedgerOperation(1, LedgerOperationType.DEPOSIT, "participant", "ARS", 10,
                        List.of(new LedgerPosting("different", "ARS", BalanceBucket.AVAILABLE, 10))));
    }
}
