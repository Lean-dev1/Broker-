package main.ledger;

import main.domain.Trade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable *in-memory* record of the balanced postings for one simulated trade.
 * Sequence numbers are local to one Ledger instance; they are not durable IDs.
 */
public record LedgerTransaction(long sequence, Trade trade, List<LedgerPosting> postings) {
    public LedgerTransaction {
        if (sequence <= 0) throw new IllegalArgumentException("Sequence must be positive");
        Objects.requireNonNull(trade, "trade");
        postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
        if (postings.isEmpty()) throw new IllegalArgumentException("A trade must have postings");

        Map<String, Long> assetTotals = new HashMap<>();
        for (LedgerPosting posting : postings) {
            assetTotals.merge(posting.assetSymbol(), posting.delta(), Math::addExact);
        }
        for (Map.Entry<String, Long> entry : assetTotals.entrySet()) {
            if (entry.getValue() != 0L) {
                throw new IllegalArgumentException("Unbalanced postings for asset " + entry.getKey());
            }
        }
    }
}
