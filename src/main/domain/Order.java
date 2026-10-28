package main.domain;

public abstract class Order {
    private final long id;
    private final String participantId;
    private final long price;
    private final long initialQuantity;
    private long remainingQuantity; // Estado encapsulado

    public Order(long id, String participantId, long price, long quantity) {
        this.id = id;
        this.participantId = participantId;
        this.price = price;
        this.initialQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    // Comportamiento encapsulado (Tell, Don't Ask)
    public void fill(long executedAmount) {
        if (executedAmount > this.remainingQuantity) {
            throw new IllegalArgumentException("Execution exceeds remaining quantity");
        }
        this.remainingQuantity -= executedAmount;
    }

    public boolean isFilled() {
        return this.remainingQuantity == 0;
    }

    // Getters mínimos estrictamente necesarios
    public long getId() { return id; }
    public long getPrice() { return price; }
    public long getRemainingQuantity() { return remainingQuantity; }

    // --- El contrato polimórfico ---
    // En lugar de preguntar "¿de qué lado eres?", le decimos a la orden: 
    // "Enrútate en este libro".
    public abstract OrderNode routeTo(OrderBook book);

    /**
     * Double Dispatch entry point.
     * The order delegates the execution to the correct overloaded method in the engine.
     */
    public abstract void processBy(MatchingEngineTest engine);
}