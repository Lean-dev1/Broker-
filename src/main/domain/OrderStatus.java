package main.domain;

/** Lifecycle of a limit order in the in-memory simulator. */
public enum OrderStatus {
    CREATED,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED
}
