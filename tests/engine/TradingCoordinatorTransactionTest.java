package tests.engine;

import main.domain.*;
import main.engine.MatchingEngine;
import main.engine.TradingCoordinator;
import main.ledger.Account;
import main.ledger.Ledger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradingCoordinatorTransactionTest {
    private final TradingPair pair = new TradingPair("ALUA", "ARS");
    private Ledger ledger;
    private OrderBook book;
    private TradingCoordinator coordinator;
    private Account buyer;
    private Account seller;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        book = new OrderBook("ALUA-ARS");
        coordinator = new TradingCoordinator(pair, ledger, book, new MatchingEngine(book));
        buyer = ledger.createAccount("buyer");
        seller = ledger.createAccount("seller");
    }

    @Test
    void rollbackRestoresBookAccountsOrderAndIdAfterSettlementFailure() {
        seller.deposit("ALUA", 10);
        seller.deposit("ARS", Long.MAX_VALUE - 10);
        SellOrder ask = new SellOrder(1, "seller", 10, 10);
        coordinator.submitOrder(ask, 1);
        buyer.deposit("ARS", 1000);
        BuyOrder bid = new BuyOrder(2, "buyer", 10, 10);

        assertThrows(ArithmeticException.class, () -> coordinator.submitOrder(bid, 2));
        assertEquals(OrderStatus.CREATED, bid.getStatus());
        assertEquals(10, bid.getRemainingQuantity());
        assertEquals(0, bid.getExecutedQuantity());
        assertEquals(1000, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(0, buyer.getAvailableBalance("ALUA"));
        assertEquals(Long.MAX_VALUE - 10, seller.getAvailableBalance("ARS"));
        assertEquals(10, seller.getHeldBalance("ALUA"));
        assertEquals(10L, book.getBestAsk());
        assertSame(ask, book.getBestAskLevel().getHead().getOrder());
        assertEquals(OrderStatus.OPEN, ask.getStatus());
        assertEquals(10, ask.getRemainingQuantity());

        // A rejected/rolled-back ID is reusable because it never committed.
        seller.withdraw("ARS", Long.MAX_VALUE - 10);
        List<Trade> accepted = coordinator.submitOrder(bid, 3);
        assertEquals(1, accepted.size());
        assertTrue(bid.isFilled());
        assertEquals(100, seller.getAvailableBalance("ARS"));
    }

    @Test
    void rollbackAfterFirstSuccessfulTradeRestoresBothRestingOrdersAndFifo() {
        Account secondSeller = ledger.createAccount("seller2");
        seller.deposit("ALUA", 10);
        secondSeller.deposit("ALUA", 5);
        secondSeller.deposit("ARS", Long.MAX_VALUE - 5);
        SellOrder first = new SellOrder(1, "seller", 10, 10);
        SellOrder second = new SellOrder(2, "seller2", 10, 5);
        coordinator.submitOrder(first, 1);
        coordinator.submitOrder(second, 2);

        buyer.deposit("ARS", 150);
        BuyOrder incoming = new BuyOrder(3, "buyer", 10, 15);
        assertThrows(ArithmeticException.class, () -> coordinator.submitOrder(incoming, 3));
        assertEquals(OrderStatus.CREATED, incoming.getStatus());
        assertEquals(15, incoming.getRemainingQuantity());
        assertEquals(150, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(0, seller.getAvailableBalance("ARS"));
        assertEquals(10, seller.getHeldBalance("ALUA"));
        assertEquals(5, secondSeller.getHeldBalance("ALUA"));
        assertSame(first, book.getBestAskLevel().getHead().getOrder());
        assertSame(second, book.getBestAskLevel().getHead().getNext().getOrder());

        secondSeller.withdraw("ARS", Long.MAX_VALUE - 5);
        List<Trade> trades = coordinator.submitOrder(incoming, 4);
        assertEquals(2, trades.size());
        assertEquals(1L, trades.get(0).restingOrderId());
        assertEquals(2L, trades.get(1).restingOrderId());
        assertEquals(15, buyer.getAvailableBalance("ALUA"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
    }

    @Test
    void iocExecutesAvailableAndCancelsRemainingWithFundsReleased() {
        seller.deposit("ALUA", 40);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 40), 1);
        buyer.deposit("ARS", 10_000);
        BuyOrder order = new BuyOrder(2, "buyer", 100, 100);

        List<Trade> trades = coordinator.submitOrder(order, 2, TimeInForce.IOC);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).executedQuantity());
        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(40, order.getExecutedQuantity());
        assertEquals(60, order.getCanceledQuantity());
        assertEquals(0, order.getRemainingQuantity());
        assertEquals(6_000, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(40, buyer.getAvailableBalance("ALUA"));
        assertNull(book.getBestBid());
        assertNull(book.getBestAsk());
    }

    @Test
    void fokWithoutEnoughLiquidityCancelsWithoutTouchingBook() {
        seller.deposit("ALUA", 40);
        SellOrder existing = new SellOrder(1, "seller", 100, 40);
        coordinator.submitOrder(existing, 1);
        buyer.deposit("ARS", 10_000);
        BuyOrder order = new BuyOrder(2, "buyer", 100, 100);

        List<Trade> trades = coordinator.submitOrder(order, 2, TimeInForce.FOK);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(0, order.getExecutedQuantity());
        assertEquals(100, order.getCanceledQuantity());
        assertEquals(10_000, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(40, seller.getHeldBalance("ALUA"));
        assertSame(existing, book.getBestAskLevel().getHead().getOrder());
    }

    @Test
    void fokWithMultipleLevelsFillsEntireOrderAtRestingPrices() {
        Account seller2 = ledger.createAccount("seller2");
        seller.deposit("ALUA", 50);
        seller2.deposit("ALUA", 50);
        coordinator.submitOrder(new SellOrder(1, "seller", 100, 50), 1);
        coordinator.submitOrder(new SellOrder(2, "seller2", 105, 50), 2);
        buyer.deposit("ARS", 11_000);
        BuyOrder order = new BuyOrder(3, "buyer", 110, 100);

        List<Trade> trades = coordinator.submitOrder(order, 3, TimeInForce.FOK);

        assertEquals(2, trades.size());
        assertEquals(100, trades.get(0).matchPrice());
        assertEquals(105, trades.get(1).matchPrice());
        assertEquals(50, trades.get(0).executedQuantity());
        assertEquals(50, trades.get(1).executedQuantity());
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(0, order.getCanceledQuantity());
        assertEquals(750, buyer.getAvailableBalance("ARS"));
        assertEquals(0, buyer.getHeldBalance("ARS"));
        assertEquals(100, buyer.getAvailableBalance("ALUA"));
        assertNull(book.getBestAsk());
    }

    @Test
    void canceledOrderCannotBeSubmittedAgainWithSameId() {
        seller.deposit("ALUA", 10);
        SellOrder order = new SellOrder(1, "seller", 100, 10);
        coordinator.submitOrder(order, 1);
        assertEquals(OrderStatus.OPEN, order.getStatus());
        assertTrue(coordinator.cancelOrder(1));
        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(10, order.getCanceledQuantity());
        assertEquals(0, order.getRemainingQuantity());
        assertEquals(10, seller.getAvailableBalance("ALUA"));
        assertEquals(0, seller.getHeldBalance("ALUA"));
        assertFalse(coordinator.cancelOrder(1));

        assertThrows(IllegalArgumentException.class, () ->
            coordinator.submitOrder(new SellOrder(1, "seller", 100, 10), 2));
        assertThrows(IllegalArgumentException.class, () ->
            coordinator.submitOrder(order, 3));
    }

    @Test
    void orderConservesQuantitiesAndRejectsInvalidFills() {
        BuyOrder order = new BuyOrder(10, "buyer", 100, 20);
        assertThrows(IllegalArgumentException.class, () -> order.fill(0));
        assertThrows(IllegalArgumentException.class, () -> order.fill(-1));
        assertThrows(IllegalArgumentException.class, () -> order.fill(21));
        order.fill(7);
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        assertEquals(7, order.getExecutedQuantity());
        assertEquals(13, order.getRemainingQuantity());
        order.cancelRemaining();
        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(13, order.getCanceledQuantity());
        assertEquals(20, order.getExecutedQuantity() + order.getCanceledQuantity() + order.getRemainingQuantity());
        assertThrows(IllegalArgumentException.class, () -> order.fill(1));
    }
}
