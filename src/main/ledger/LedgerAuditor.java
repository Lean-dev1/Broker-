package main.ledger;

import main.domain.Trade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Independently reconstructs account balance buckets from journal postings.
 *
 * This is a deterministic, read-only financial reconciliation building block.
 * It cannot recover orders, verify real external deposits, or survive process
 * termination: the source journal is still in RAM.
 */
public final class LedgerAuditor {
    private LedgerAuditor() {}

    /**
     * Replays entries from an initially empty ledger balance state.
     * Records must be contiguous, starting from sequence 1.
     *
     * @throws IllegalStateException if a record is missing, invalid, or cannot
     *                               be applied without negative/overflowing balances.
     */
    public static LedgerAuditReport replay(List<LedgerRecord> journal) {
        Objects.requireNonNull(journal, "journal");
        Map<AccountAssetKey, LedgerBalanceView> balances = new HashMap<>();
        long nextSequence = 1L;
        long trades = 0L;

        for (LedgerRecord record : journal) {
            if (record == null || record.sequence() != nextSequence) {
                throw new IllegalStateException("Journal sequence gap at expected record " + nextSequence);
            }
            try {
                validateRecord(record);
                // Atomic within each record: don't publish provisional positions
                // to the next replay step if this record proves invalid.
                Map<AccountAssetKey, LedgerBalanceView> pending = new HashMap<>();
                for (LedgerPosting posting : record.postings()) {
                    AccountAssetKey key = new AccountAssetKey(
                            posting.participantId(), posting.assetSymbol());
                    LedgerBalanceView current = pending.getOrDefault(
                            key, balances.getOrDefault(key, LedgerBalanceView.ZERO));
                    long nextAvailable = current.available();
                    long nextHeld = current.held();
                    if (posting.bucket() == BalanceBucket.AVAILABLE) {
                        nextAvailable = Math.addExact(current.available(), posting.delta());
                    } else {
                        nextHeld = Math.addExact(current.held(), posting.delta());
                    }
                    pending.put(key, new LedgerBalanceView(nextAvailable, nextHeld));
                }
                balances.putAll(pending);
                if (record instanceof LedgerTransaction) trades = Math.addExact(trades, 1L);
                nextSequence = Math.addExact(nextSequence, 1L);
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                        "Invalid journal record at sequence " + nextSequence, failure);
            }
        }
        return new LedgerAuditReport(journal.size(), trades, balances);
    }

    private static void validateRecord(LedgerRecord record) {
        if (record instanceof LedgerTransaction transaction) {
            Trade trade = transaction.trade();
            if (trade == null || trade.matchPrice() <= 0 || trade.executedQuantity() <= 0
                    || trade.aggressiveOrderId() == trade.restingOrderId()) {
                throw new IllegalArgumentException("Invalid trade fields");
            }
            Map<String, Long> totals = new HashMap<>();
            for (LedgerPosting posting : transaction.postings()) {
                totals.merge(posting.assetSymbol(), posting.delta(), Math::addExact);
            }
            for (long total : totals.values()) {
                if (total != 0L) throw new IllegalArgumentException("Trade postings are not zero-sum");
            }
        } else if (record instanceof LedgerOperation operation) {
            // Reconstruct to re-validate exactly the canonical operation postings.
            new LedgerOperation(operation.sequence(), operation.type(),
                    operation.participantId(), operation.assetSymbol(),
                    operation.amount(), operation.postings());
        } else {
            throw new IllegalArgumentException("Unknown ledger record type");
        }
    }
}
