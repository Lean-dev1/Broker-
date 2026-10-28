package main.domain;

import main.ledger.Account;

public abstract class Order {
    private final long id;
    private final String participantId;
    private final long price;
    private final long initialQuantity;
    private long remainingQuantity;

    public Order(long id, String participantId, long price, long quantity) {
        if (price <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Price and quantity must be strictly positive");
        }
        this.id = id;
        this.participantId = participantId;
        this.price = price;
        this.initialQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    public void fill(long executedAmount) {
        if (executedAmount > this.remainingQuantity) {
            throw new IllegalArgumentException("Execution exceeds remaining quantity");
        }
        this.remainingQuantity -= executedAmount;
    }

    public boolean isFilled() {
        return this.remainingQuantity == 0;
    }

    public long getId() { return id; }
    public String getParticipantId() { return participantId; }
    public long getPrice() { return price; }
    public long getInitialQuantity() { return initialQuantity; }
    public long getRemainingQuantity() { return remainingQuantity; }

    // --- Contratos Polimórficos de Enrutamiento y Cruce (Iteración 1) ---
    public abstract OrderNode routeTo(OrderBook book);
    public abstract void processBy(MatchingEngine engine, java.util.List<Trade> executions, long timestamp);

    // --- Nuevos Contratos Polimórficos de Contabilidad (Iteración 2) ---

    /**
     * Tells the order to lock the required collateral in the participant's account before matching.
     */
    public abstract void reserveFunds(Account account, TradingPair pair);

    /**
     * Tells the order to unlock any remaining collateral when the order is canceled.
     */
    public abstract void releaseRemainingFunds(Account account, TradingPair pair);

    /**
     * Tells the order to settle a trade execution in the participant's account,
     * automatically handling any price improvement refunds.
     */
    public abstract void settleExecution(Account account, TradingPair pair, long executionPrice, long executedQuantity);
}