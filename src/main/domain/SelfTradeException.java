package main.domain;

/** Incoming order would match against the same participant. */
public final class SelfTradeException extends IllegalArgumentException {
    public SelfTradeException(String message) { super(message); }
}
