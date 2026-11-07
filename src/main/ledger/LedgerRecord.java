package main.ledger;

import java.util.List;

/**
 * One committed in-memory accounting record.
 * External deposits/withdrawals have an explicit boundary operation;
 * only internal transfers and trades are zero-sum within the ledger.
 */
public sealed interface LedgerRecord permits LedgerTransaction, LedgerOperation {
    long sequence();
    List<LedgerPosting> postings();
}
