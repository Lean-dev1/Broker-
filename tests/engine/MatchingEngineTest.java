package engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import main.domain.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private OrderBook book;
    private MatchingEngine engine;
    private long ticker; // Deterministic logical clock

    @BeforeEach
    void setUp() {
        book = new OrderBook("ALUA-AR"); // Example ticker
        engine = new MatchingEngine(book);
        ticker = 1000L;
    }

    @Test
    void shouldNotMatchWhenSpreadIsPositive() {
        // Arrange
        engine.process(new SellOrder(1, "SellerA", 15000, 100), ticker++);
        
        // Act: Buyer wants to pay less than the best ask
        List<Trade> trades = engine.process(new BuyOrder(2, "BuyerA", 14000, 100), ticker++);

        // Assert: Invariant - No trades should happen
        assertTrue(trades.isEmpty(), "Trades list should be empty");
        assertNotNull(book.getBestAsk(), "Seller should remain in the book");
        assertNotNull(book.getBestBid(), "Buyer should be enqueued in the book");
    }

    @Test
    void shouldExecuteExactMatch() {
        // Arrange
        engine.process(new SellOrder(1, "SellerA", 15000, 100), ticker++);

        // Act
        List<Trade> trades = engine.process(new BuyOrder(2, "BuyerA", 15000, 100), ticker++);

        // Assert: Invariant - Conservation of quantity
        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(15000, trade.matchPrice());
        assertEquals(100, trade.executedQuantity());
        assertEquals(1, trade.restingOrderId());
        assertEquals(2, trade.aggressiveOrderId());
        
        assertNull(book.getBestAsk(), "Book should be empty after exact match");
    }

    @Test
    void shouldRespectPriceAndTimePriority() {
        // Arrange: Build the resting book
        // Seller 1: Asks for $100 (Best Price, arrived first)
        engine.process(new SellOrder(1, "Seller1", 10000, 50), ticker++);
        // Seller 2: Asks for $100 (Best Price, arrived second)
        engine.process(new SellOrder(2, "Seller2", 10000, 50), ticker++);
        // Seller 3: Asks for $105 (Worse Price)
        engine.process(new SellOrder(3, "Seller3", 10500, 50), ticker++);

        // Act: Aggressive buyer wants 75 units, willing to pay up to $105
        List<Trade> trades = engine.process(new BuyOrder(4, "Buyer1", 10500, 75), ticker++);

        // Assert
        assertEquals(2, trades.size(), "Should execute against two different sellers");

        // First execution: Seller 1 (Price + Time priority)
        Trade firstTrade = trades.get(0);
        assertEquals(1, firstTrade.restingOrderId());
        assertEquals(10000, firstTrade.matchPrice()); // Buyer gets price improvement
        assertEquals(50, firstTrade.executedQuantity());

        // Second execution: Seller 2 (Time priority remainder)
        Trade secondTrade = trades.get(1);
        assertEquals(2, secondTrade.restingOrderId());
        assertEquals(10000, secondTrade.matchPrice()); 
        assertEquals(25, secondTrade.executedQuantity()); // Partial fill of 25

        // Check internal state of resting order
        assertEquals(25, book.getLevel(Side.SELL, 10000).getHead().getOrder().getRemainingQuantity());
    }

    @Test
    void shouldProcessPartialFillsAndEnqueueRemainder() {
        // Arrange
        engine.process(new BuyOrder(1, "Buyer1", 10000, 50), ticker++);

        // Act: Aggressive seller dumps 150 units at market price
        List<Trade> trades = engine.process(new SellOrder(2, "Seller1", 10000, 150), ticker++);

        // Assert
        assertEquals(1, trades.size());
        assertEquals(50, trades.get(0).executedQuantity());
        
        // The aggressive seller should now be resting in the book as an Ask with 100 remaining
        assertNotNull(book.getBestAsk());
        assertEquals(10000, book.getBestAsk());
        assertEquals(100, book.getLevel(Side.SELL, 10000).getHead().getOrder().getRemainingQuantity());
        assertNull(book.getBestBid(), "Bids should be depleted");
    }
}