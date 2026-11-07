package main.domain;

import main.ledger.Ledger;
import main.engine.MatchingEngine;

import java.util.List;

/** Mutable order state. This class is not thread-safe and must be owned by one coordinator. */
public abstract class Order {
    private final long id;
    private final String participantId;
    private final long price;
    private final long initialQuantity;
    private long remainingQuantity;
    private long canceledQuantity;
    private OrderStatus status = OrderStatus.CREATED;

    public Order(long id, String participantId, long price, long quantity) {
        if (id < 0 || participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Valid order ID and participant ID required");
        }
        if (price <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Price and quantity must be strictly positive");
        }
        this.id = id;
        this.participantId = participantId;
        this.price = price;
        this.initialQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    /** Called by matching. Zero, negative and overfills are forbidden. */
    public void fill(long quantity) {
        if (status == OrderStatus.CANCELED || status == OrderStatus.FILLED
                || quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Invalid execution quantity or order state");
        }
        remainingQuantity -= quantity;
        status = remainingQuantity == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    /** Called when the order is inserted into the live book. */
    public void markResting() {
        if (remainingQuantity <= 0 || status == OrderStatus.CANCELED || status == OrderStatus.FILLED) {
            throw new IllegalStateException("Only an unfilled active order may rest");
        }
        status = getExecutedQuantity() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.OPEN;
    }

    /** Called by the coordinator only after releasing the remaining collateral. */
    public void cancelRemaining() {
        if (status == OrderStatus.CANCELED || status == OrderStatus.FILLED || remainingQuantity <= 0) {
            throw new IllegalStateException("Cannot cancel a terminal order");
        }
        canceledQuantity = Math.addExact(canceledQuantity, remainingQuantity);
        remainingQuantity = 0;
        status = OrderStatus.CANCELED;
    }

    public boolean canBeCompletelyFilledBy(OrderBook book) {
        if (this instanceof BuyOrder) return book.canFillBuyCompletely(price, remainingQuantity);
        if (this instanceof SellOrder) return book.canFillSellCompletely(price, remainingQuantity);
        throw new IllegalStateException("Unknown order side");
    }

    public boolean isFilled() { return status == OrderStatus.FILLED; }
    public long getId() { return id; }
    public String getParticipantId() { return participantId; }
    public long getPrice() { return price; }
    public long getInitialQuantity() { return initialQuantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public long getCanceledQuantity() { return canceledQuantity; }
    public long getExecutedQuantity() { return initialQuantity - remainingQuantity - canceledQuantity; }
    public OrderStatus getStatus() { return status; }

    /** Package-private memento: only the domain book may capture/restore mutable order state. */
    record State(long remainingQuantity, long canceledQuantity, OrderStatus status) {}
    State snapshotState() { return new State(remainingQuantity, canceledQuantity, status); }
    void restoreState(State state) {
        remainingQuantity = state.remainingQuantity();
        canceledQuantity = state.canceledQuantity();
        status = state.status();
    }

    public abstract OrderNode routeTo(OrderBook book);
    public abstract void processBy(MatchingEngine engine, List<Trade> executions, long timestamp);
    public abstract void reserveFunds(Ledger ledger, TradingPair pair);
    public abstract void releaseRemainingFunds(Ledger ledger, TradingPair pair);
}
