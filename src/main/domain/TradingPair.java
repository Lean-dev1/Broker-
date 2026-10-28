package main.domain;

/**
 * Represents a financial market pair (e.g., Base: "ALUA", Quote: "ARS").
 */
public record TradingPair(String baseAsset, String quoteAsset) {
    public TradingPair {
        if (baseAsset == null || baseAsset.isBlank() || quoteAsset == null || quoteAsset.isBlank()) {
            throw new IllegalArgumentException("Base and Quote assets must be valid symbols");
        }
        if (baseAsset.equals(quoteAsset)) {
            throw new IllegalArgumentException("Base and Quote assets cannot be identical");
        }
    }
}