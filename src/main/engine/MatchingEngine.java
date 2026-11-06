package main.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import main.domain.*;

/**
 * Deterministic in-memory price/time matcher. By default, unmatched quantity rests (GTC).
 * For financial settlement use TradingCoordinator, not this standalone entry point.
 */
public class MatchingEngine {
    private final OrderBook book;

    public MatchingEngine(OrderBook book) {
        this.book = Objects.requireNonNull(book);
    }

    public List<Trade> process(Order aggressiveOrder, long logicalTimestamp) {
        Objects.requireNonNull(aggressiveOrder, "order");
        if (aggressiveOrder.getStatus() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("Only new orders may be submitted");
        }
        if (book.containsOrder(aggressiveOrder.getId())) {
            throw new IllegalArgumentException("Duplicate active order ID");
        }
        if (book.wouldSelfMatch(aggressiveOrder)) {
            throw new SelfTradeException("Self-trading is forbidden");
        }
        List<Trade> executions = new ArrayList<>();
        aggressiveOrder.processBy(this, executions, logicalTimestamp);
        return executions;
    }

    public void match(BuyOrder aggressiveBuy, List<Trade> executions, long timestamp) {
        while (!aggressiveBuy.isFilled() && book.hasAsks()) {
            PriceLevel bestAskLevel = book.getBestAskLevel();
            if (aggressiveBuy.getPrice() < bestAskLevel.getPrice()) break;
            executeLevel(aggressiveBuy, bestAskLevel, executions, timestamp);
            if (bestAskLevel.isEmpty()) book.removeBestAsk();
        }
        if (!aggressiveBuy.isFilled()) book.enqueueOrder(aggressiveBuy);
    }

    public void match(SellOrder aggressiveSell, List<Trade> executions, long timestamp) {
        while (!aggressiveSell.isFilled() && book.hasBids()) {
            PriceLevel bestBidLevel = book.getBestBidLevel();
            if (aggressiveSell.getPrice() > bestBidLevel.getPrice()) break;
            executeLevel(aggressiveSell, bestBidLevel, executions, timestamp);
            if (bestBidLevel.isEmpty()) book.removeBestBid();
        }
        if (!aggressiveSell.isFilled()) book.enqueueOrder(aggressiveSell);
    }

    private void executeLevel(Order aggressiveOrder, PriceLevel restingLevel,
                              List<Trade> executions, long timestamp) {
        OrderNode current = restingLevel.getHead();
        while (current != null && !aggressiveOrder.isFilled()) {
            Order restingOrder = current.getOrder();
            long quantity = Math.min(aggressiveOrder.getRemainingQuantity(),
                                     restingOrder.getRemainingQuantity());
            long price = restingLevel.getPrice();
            aggressiveOrder.fill(quantity);
            restingOrder.fill(quantity);
            executions.add(new Trade(aggressiveOrder.getId(), restingOrder.getId(), price, quantity, timestamp));
            OrderNode next = current.getNext();
            if (restingOrder.isFilled()) {
                restingLevel.removeOrder(current);
                book.removeActiveOrder(restingOrder.getId());
            }
            current = next;
        }
    }
}
