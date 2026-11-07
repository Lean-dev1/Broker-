package main.ledger;

import java.util.Map;
import java.util.Objects;

/**
 * Independent reconstruction of balances from a sequence of in-memory postings.
 * A report is not evidence of storage durability or of complete matching correctness.
 */
public record LedgerAuditReport(
        long recordCount,
        long tradeCount,
        Map<AccountAssetKey, LedgerBalanceView> balances) {
    public LedgerAuditReport {
        if (recordCount < 0 || tradeCount < 0 || tradeCount > recordCount) {
            throw new IllegalArgumentException("Invalid audited counts");
        }
        balances = Map.copyOf(Objects.requireNonNull(balances, "balances"));
    }
}
