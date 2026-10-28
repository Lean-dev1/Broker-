package main.domain;

/**
 * Immutable record representing a financial execution between two orders.
 */
public record Trade(
    long aggressiveOrderId, 
    long restingOrderId, 
    long matchPrice, 
    long executedQuantity,
    long timestamp // Logical time, injected for deterministic testing
) {}
