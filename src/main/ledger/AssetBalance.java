package main.ledger;

/**
 * Encapsulates the financial state of a single asset (currency or security) for a participant.
 * All values are represented in fixed-point arithmetic (long) to avoid floating-point errors and GC pressure.
 */
public class AssetBalance {
    private final String assetSymbol;
    private long available;
    private long held;

    public AssetBalance(String assetSymbol) {
        this.assetSymbol = assetSymbol;
        this.available = 0L;
        this.held = 0L;
    }

    public String getAssetSymbol() {
        return assetSymbol;
    }

    public long getAvailable() {
        return available;
    }

    public long getHeld() {
        return held;
    }

    /**
     * Invariant: Total balance is always the sum of available and held funds.
     */
    public long getTotal() {
        return Math.addExact(available, held);
    }

    /**
     * Credits new funds or assets into the available balance (e.g., deposits or trade proceeds).
     */
    public void creditAvailable(long amount) {
        validatePositiveAmount(amount);
        this.available = Math.addExact(this.available, amount);
    }

    /**
     * Debits funds directly from available balance (e.g., withdrawals).
     */
    public void debitAvailable(long amount) {
        validatePositiveAmount(amount);
        if (this.available < amount) {
            throw new InsufficientFundsException(
                "Insufficient available funds for " + assetSymbol + ": required " + amount + ", available " + available
            );
        }
        this.available -= amount;
    }

    /**
     * Moves funds from 'available' to 'held' when an order is accepted for processing.
     * Invariant: Total balance remains unchanged.
     */
    public void hold(long amount) {
        validatePositiveAmount(amount);
        if (this.available < amount) {
            throw new InsufficientFundsException(
                "Cannot hold " + amount + " of " + assetSymbol + ": only " + available + " available"
            );
        }
        this.available -= amount;
        this.held = Math.addExact(this.held, amount);
    }

    /**
     * Moves funds back from 'held' to 'available' (e.g., order cancellation or price improvement refund).
     * Invariant: Total balance remains unchanged.
     */
    public void release(long amount) {
        validatePositiveAmount(amount);
        if (this.held < amount) {
            throw new IllegalStateException(
                "Cannot release " + amount + " of " + assetSymbol + ": only " + held + " held"
            );
        }
        this.held -= amount;
        this.available = Math.addExact(this.available, amount);
    }

    /**
     * Permanently consumes held funds when a trade is executed (settlement).
     */
    public void settleHeld(long amount) {
        validatePositiveAmount(amount);
        if (this.held < amount) {
            throw new IllegalStateException(
                "Cannot settle " + amount + " of " + assetSymbol + ": only " + held + " held"
            );
        }
        this.held -= amount;
    }

    private void validatePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive: " + amount);
        }
    }
}