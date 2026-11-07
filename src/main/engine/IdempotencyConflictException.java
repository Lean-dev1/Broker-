package main.engine;

/**
 * Raised when a previously committed command ID is reused with different
 * immutable submission fields. No new matching or balance mutation is applied.
 */
public final class IdempotencyConflictException extends IllegalArgumentException {
    public IdempotencyConflictException(String commandId) {
        super("Command ID already committed with a different request: " + commandId);
    }
}
