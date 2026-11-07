package main.ledger;

/** Immutable available/held amounts, in the ledger's current integer minor units. */
public record LedgerBalanceView(long available, long held) {
    public static final LedgerBalanceView ZERO = new LedgerBalanceView(0L, 0L);

    public LedgerBalanceView {
        if (available < 0 || held < 0) {
            throw new IllegalArgumentException("Balances cannot be negative");
        }
        Math.addExact(available, held);
    }

    public long total() {
        return Math.addExact(available, held);
    }
}
