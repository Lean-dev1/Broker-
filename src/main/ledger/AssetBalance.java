package main.ledger;

/** Balance in integral minor units. Mutable and single-writer only. */
public class AssetBalance {
    private final String assetSymbol;
    private long available;
    private long held;

    public AssetBalance(String assetSymbol) {
        if (assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("Asset symbol required");
        }
        this.assetSymbol = assetSymbol;
    }

    public String getAssetSymbol() { return assetSymbol; }
    public long getAvailable() { return available; }
    public long getHeld() { return held; }
    public long getTotal() { return Math.addExact(available, held); }

    record State(long available, long held) {}
    State snapshotState() { return new State(available, held); }
    void restoreState(State snapshot) {
        available = snapshot.available();
        held = snapshot.held();
    }

    public void creditAvailable(long amount) {
        validatePositiveAmount(amount);
        available = Math.addExact(available, amount);
    }
    public void debitAvailable(long amount) {
        validatePositiveAmount(amount);
        if (available < amount) throw new InsufficientFundsException("Insufficient available " + assetSymbol);
        available -= amount;
    }
    public void hold(long amount) {
        validatePositiveAmount(amount);
        if (available < amount) throw new InsufficientFundsException("Insufficient available " + assetSymbol);
        long nextHeld = Math.addExact(held, amount);
        available -= amount;
        held = nextHeld;
    }
    public void release(long amount) {
        validatePositiveAmount(amount);
        if (held < amount) throw new IllegalStateException("Insufficient held " + assetSymbol);
        long nextAvailable = Math.addExact(available, amount);
        held -= amount;
        available = nextAvailable;
    }
    public void verifyCanCredit(long amount) {
        validatePositiveAmount(amount);
        Math.addExact(available, amount);
    }
    public void verifyCanSettleHeld(long amount) {
        validatePositiveAmount(amount);
        if (held < amount) throw new IllegalStateException("Insufficient held " + assetSymbol);
    }
    public void settleHeld(long amount) {
        verifyCanSettleHeld(amount);
        held -= amount;
    }

    private void validatePositiveAmount(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive: " + amount);
    }
}
