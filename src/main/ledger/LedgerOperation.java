package main.ledger;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, validated ledger entry for deposit/withdraw/hold/release.
 * External operations have one posting; internal transfers are balanced.
 */
public record LedgerOperation(
        long sequence,
        LedgerOperationType type,
        String participantId,
        String assetSymbol,
        long amount,
        List<LedgerPosting> postings) implements LedgerRecord {

    public LedgerOperation {
        if (sequence <= 0) throw new IllegalArgumentException("Sequence must be positive");
        Objects.requireNonNull(type, "type");
        if (participantId == null || participantId.isBlank()
                || assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("Participant and asset must be specified");
        }
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");

        postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
        LedgerPosting availableIncrease =
                new LedgerPosting(participantId, assetSymbol, BalanceBucket.AVAILABLE, amount);
        LedgerPosting availableDecrease =
                new LedgerPosting(participantId, assetSymbol, BalanceBucket.AVAILABLE, -amount);
        LedgerPosting heldIncrease =
                new LedgerPosting(participantId, assetSymbol, BalanceBucket.HELD, amount);
        LedgerPosting heldDecrease =
                new LedgerPosting(participantId, assetSymbol, BalanceBucket.HELD, -amount);
        List<LedgerPosting> expected = switch (type) {
            case DEPOSIT -> List.of(availableIncrease);
            case WITHDRAWAL -> List.of(availableDecrease);
            case HOLD -> List.of(availableDecrease, heldIncrease);
            case RELEASE -> List.of(heldDecrease, availableIncrease);
        };
        if (!postings.equals(expected)) {
            throw new IllegalArgumentException("Postings do not match operation " + type);
        }
    }
}
