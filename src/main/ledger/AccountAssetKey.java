package main.ledger;

/**
 * Identifier of one participant's position in a specific asset.
 * Does not encode asset precision; that remains a separate domain decision.
 */
public record AccountAssetKey(String participantId, String assetSymbol) {
    public AccountAssetKey {
        if (participantId == null || participantId.isBlank()
                || assetSymbol == null || assetSymbol.isBlank()) {
            throw new IllegalArgumentException("Participant and asset are required");
        }
    }
}
