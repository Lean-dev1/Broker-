package tests.engine;

import main.domain.*;
import main.engine.*;
import main.ledger.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Request deduplication is guaranteed within one live coordinator instance only. */
class IdempotentSubmissionTest {
    private Ledger ledger;
    private TradingCoordinator coordinator;
    private OrderBook book;

    @BeforeEach
    void setup() {
        ledger = new Ledger();
        ledger.createAccount("buyer");
        ledger.createAccount("seller");
        book = new OrderBook("ALUA-ARS");
        coordinator = new TradingCoordinator(
                new TradingPair("ALUA", "ARS"), ledger, book, new MatchingEngine(book));
    }

    @Test
    void identicalRetryReturnsOriginalReceiptWithoutSecondHold() {
        ledger.deposit("buyer", "ARS", 1000L);
        BuyOrder incoming = new BuyOrder(1, "buyer", 100, 5);
        SubmissionReceipt first = coordinator.submitOrderIdempotent(
                incoming, 10L, TimeInForce.GTC, "submit:1");
        List<LedgerRecord> committed = ledger.getJournal();

        SubmissionReceipt retry = coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10L, TimeInForce.GTC, "submit:1");
        assertSame(first, retry);
        assertSame(first, coordinator.submitOrderIdempotent(
                incoming, 10L, TimeInForce.GTC, "submit:1"));
        assertEquals(committed, ledger.getJournal());
        assertTrue(first.trades().isEmpty());
        assertEquals(OrderStatus.OPEN, first.statusAtAcceptance());
        assertEquals(5, first.remainingQuantity());
        assertEquals(0, first.executedQuantity());
        assertEquals(0, first.canceledQuantity());
        assertEquals(500, ledger.getAccount("buyer").getHeldBalance("ARS"));
        assertEquals(500, ledger.getAccount("buyer").getAvailableBalance("ARS"));
        assertEquals(2L, ledger.reconcile().recordCount());
        assertTrue(book.containsOrder(1));
        assertThrows(UnsupportedOperationException.class, () -> first.trades().clear());
    }

    @Test
    void cachedReceiptRemainsHistoricalAfterLaterOrderCancellation() {
        ledger.deposit("buyer", "ARS", 1000);
        SubmissionReceipt accepted = coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10L, TimeInForce.GTC, "submit:2");
        assertTrue(coordinator.cancelOrder(1L));
        int journalSize = ledger.getJournal().size();

        SubmissionReceipt retry = coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10L, TimeInForce.GTC, "submit:2");
        assertSame(accepted, retry);
        assertEquals(OrderStatus.OPEN, retry.statusAtAcceptance());
        assertEquals(journalSize, ledger.getJournal().size());
        assertFalse(book.containsOrder(1L));
        assertEquals(1000, ledger.getAccount("buyer").getAvailableBalance("ARS"));
    }

    @Test
    void iocPartialFillCannotBeSettledTwiceByRetry() {
        ledger.deposit("seller", "ALUA", 40);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 40), 1);
        ledger.deposit("buyer", "ARS", 10_000);

        SubmissionReceipt first = coordinator.submitOrderIdempotent(
                new BuyOrder(2, "buyer", 100, 100), 2, TimeInForce.IOC, "api-request-2");
        assertEquals(OrderStatus.CANCELED, first.statusAtAcceptance());
        assertEquals(40, first.executedQuantity());
        assertEquals(60, first.canceledQuantity());
        assertEquals(0, first.remainingQuantity());
        assertEquals(1, first.trades().size());
        int before = ledger.getJournal().size();

        SubmissionReceipt replay = coordinator.submitOrderIdempotent(
                new BuyOrder(2, "buyer", 100, 100), 2, TimeInForce.IOC, "api-request-2");
        assertSame(first, replay);
        assertEquals(before, ledger.getJournal().size());
        assertEquals(1, ledger.getTransactions().size());
        assertEquals(6000, ledger.getAccount("buyer").getAvailableBalance("ARS"));
        assertEquals(40, ledger.getAccount("buyer").getAvailableBalance("ALUA"));
        ledger.reconcile();
    }

    @Test
    void noFillFokIsCommittedAndIdenticalRetryDoesNotCancelTwice() {
        ledger.deposit("buyer", "ARS", 1000);
        SubmissionReceipt receipt = coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10, TimeInForce.FOK, "fok-1");
        assertEquals(OrderStatus.CANCELED, receipt.statusAtAcceptance());
        assertEquals(5, receipt.canceledQuantity());
        assertTrue(receipt.trades().isEmpty());
        List<LedgerRecord> journal = ledger.getJournal();

        assertSame(receipt, coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10, TimeInForce.FOK, "fok-1"));
        assertEquals(journal, ledger.getJournal());
        assertEquals(0, ledger.getAccount("buyer").getHeldBalance("ARS"));
        assertEquals(1000, ledger.getAccount("buyer").getAvailableBalance("ARS"));
        assertFalse(book.containsOrder(1));
    }

    @Test
    void conflictingReuseOfCommandIdIsRejectedBeforeAnySideEffect() {
        ledger.deposit("buyer", "ARS", 1000);
        ledger.deposit("seller", "ALUA", 10);
        coordinator.submitOrderIdempotent(
                new BuyOrder(1, "buyer", 100, 5), 10, TimeInForce.GTC, "command-conflict");
        List<LedgerRecord> journal = ledger.getJournal();

        assertThrows(IdempotencyConflictException.class, () ->
                coordinator.submitOrderIdempotent(
                        new BuyOrder(1, "buyer", 101, 5), 10, TimeInForce.GTC, "command-conflict"));
        assertThrows(IdempotencyConflictException.class, () ->
                coordinator.submitOrderIdempotent(
                        new BuyOrder(1, "buyer", 100, 5), 11, TimeInForce.GTC, "command-conflict"));
        assertThrows(IdempotencyConflictException.class, () ->
                coordinator.submitOrderIdempotent(
                        new BuyOrder(1, "buyer", 100, 5), 10, TimeInForce.IOC, "command-conflict"));
        assertThrows(IdempotencyConflictException.class, () ->
                coordinator.submitOrderIdempotent(
                        new SellOrder(1, "seller", 100, 5), 10, TimeInForce.GTC, "command-conflict"));
        assertThrows(IdempotencyConflictException.class, () ->
                coordinator.submitOrderIdempotent(
                        new BuyOrder(9, "buyer", 100, 5), 10, TimeInForce.GTC, "command-conflict"));

        assertEquals(journal, ledger.getJournal());
        assertEquals(500, ledger.getAccount("buyer").getHeldBalance("ARS"));
        assertTrue(book.containsOrder(1));
        ledger.reconcile();
    }

    @Test
    void failedSubmissionDoesNotConsumeCommandOrOrderId() {
        BuyOrder incoming = new BuyOrder(1, "buyer", 100, 5);
        assertThrows(InsufficientFundsException.class, () ->
                coordinator.submitOrderIdempotent(incoming, 7, TimeInForce.GTC, "retryable-1"));
        assertEquals(OrderStatus.CREATED, incoming.getStatus());
        assertTrue(ledger.getJournal().isEmpty());

        ledger.deposit("buyer", "ARS", 500);
        SubmissionReceipt accepted = coordinator.submitOrderIdempotent(
                incoming, 7, TimeInForce.GTC, "retryable-1");
        assertEquals(OrderStatus.OPEN, accepted.statusAtAcceptance());
        assertSame(accepted, coordinator.submitOrderIdempotent(
                incoming, 7, TimeInForce.GTC, "retryable-1"));
        assertEquals(2, ledger.getJournal().size());
    }

    @Test
    void failedSettlementDoesNotConsumeMemoizedCommand() {
        ledger.deposit("seller", "ALUA", 10);
        ledger.deposit("seller", "ARS", Long.MAX_VALUE - 5);
        ledger.deposit("buyer", "ARS", 1000);
        coordinator.submitOrder(new SellOrder(1, "seller", 10, 10), 1);
        List<LedgerRecord> before = ledger.getJournal();
        BuyOrder incoming = new BuyOrder(2, "buyer", 10, 10);

        assertThrows(ArithmeticException.class, () ->
                coordinator.submitOrderIdempotent(incoming, 2, TimeInForce.GTC, "overflow-1"));
        assertEquals(before, ledger.getJournal());
        assertEquals(OrderStatus.CREATED, incoming.getStatus());

        ledger.withdraw("seller", "ARS", Long.MAX_VALUE - 5);
        SubmissionReceipt receipt = coordinator.submitOrderIdempotent(
                incoming, 2, TimeInForce.GTC, "overflow-1");
        assertEquals(OrderStatus.FILLED, receipt.statusAtAcceptance());
        assertEquals(1, receipt.trades().size());
        assertEquals(1, ledger.getTransactions().size());
        ledger.reconcile();
    }

    @Test
    void rejectsInvalidCommandIdAndReusedOrderIdUnderDifferentCommand() {
        ledger.deposit("buyer", "ARS", 1000);
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.submitOrderIdempotent(new BuyOrder(1,"buyer",100,1),
                        1, TimeInForce.GTC, "  "));
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.submitOrderIdempotent(new BuyOrder(1,"buyer",100,1),
                        1, TimeInForce.GTC, "unsafe/id"));
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.submitOrderIdempotent(new BuyOrder(1,"buyer",100,1),
                        1, TimeInForce.GTC, "x".repeat(129)));

        coordinator.submitOrderIdempotent(
                new BuyOrder(1,"buyer",100,1), 1, TimeInForce.GTC,"valid:1");
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.submitOrderIdempotent(new BuyOrder(1,"buyer",100,1),
                        1, TimeInForce.GTC,"valid:2"));
        assertEquals(2, ledger.getJournal().size());
    }

    @Test
    void rejectsReceiptsWithInconsistentStatusOrExecutionHistory() {
        assertThrows(IllegalArgumentException.class, () ->
                new SubmissionReceipt("x", 7, TimeInForce.GTC, 1,
                        OrderStatus.FILLED, 10, 10, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new SubmissionReceipt("x", 7, TimeInForce.GTC, 1,
                        OrderStatus.OPEN, 10, 5, 5, 0,
                        List.of(new Trade(7, 3, 100, 5, 1))));
        assertThrows(IllegalArgumentException.class, () ->
                new SubmissionReceipt("x", 7, TimeInForce.GTC, 1,
                        OrderStatus.PARTIALLY_FILLED, 10, 5, 5, 0,
                        List.of(new Trade(8, 3, 100, 5, 1))));
        assertThrows(IllegalArgumentException.class, () ->
                new SubmissionReceipt("x", 7, TimeInForce.GTC, 1,
                        OrderStatus.PARTIALLY_FILLED, 10, 5, 5, 0,
                        List.of(new Trade(7, 3, 100, 5, 2))));
    }

    @Test
    void concurrentIdenticalCommandsApplyExactlyOneHoldWithinOneCoordinator() throws Exception {
        ledger.deposit("buyer", "ARS", 1000);
        final int attempts = 8;
        ExecutorService workers = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SubmissionReceipt>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(workers.submit(() -> {
                    start.await();
                    return coordinator.submitOrderIdempotent(
                            new BuyOrder(1, "buyer", 100, 5), 2, TimeInForce.GTC, "concurrent:1");
                }));
            }
            start.countDown();
            SubmissionReceipt first = results.get(0).get(20, TimeUnit.SECONDS);
            for (Future<SubmissionReceipt> result : results) {
                assertSame(first, result.get(20, TimeUnit.SECONDS));
            }
            assertEquals(2, ledger.getJournal().size());
            assertEquals(500, ledger.getAccount("buyer").getHeldBalance("ARS"));
            assertTrue(book.containsOrder(1));
        } finally {
            workers.shutdownNow();
        }
    }
}
