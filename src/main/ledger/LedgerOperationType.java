package main.ledger;

/**
 * Balance changes other than an executed trade.
 * Deposits and withdrawals represent flows through an external boundary.
 */
public enum LedgerOperationType {
    DEPOSIT,
    WITHDRAWAL,
    HOLD,
    RELEASE
}
