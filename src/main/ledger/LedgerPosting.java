package main.ledger;

import java.util.Objects;

/**
 * Immutable signed change to one participant/asset/bucket balance.
 * Positive means credit; negative means debit. Units are integer minor units.
 */
public record LedgerPosting(String participantId, String assetSymbol,
                            BalanceBucket bucket, long delta) {
    public LedgerPosting {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Participant ID required");
        }
        if (assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("Asset symbol required");
        }
        Objects.requireNonNull(bucket, "bucket");
        if (delta == 0) throw new IllegalArgumentException("Zero-value posting");
    }
}
