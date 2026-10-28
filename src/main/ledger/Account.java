package main.ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a participant's multi-asset account in memory.
 * Orchestrates balance transitions across different currencies and instruments in O(1) time.
 */
public class Account {
    private final String participantId;
    private final Map<String, AssetBalance> balances;

    public Account(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Participant ID cannot be null or blank");
        }
        this.participantId = participantId;
        this.balances = new HashMap<>();
    }

    public String getParticipantId() {
        return participantId;
    }

    public void deposit(String assetSymbol, long amount) {
        getOrCreateBalance(assetSymbol).creditAvailable(amount);
    }

    public void withdraw(String assetSymbol, long amount) {
        getOrCreateBalance(assetSymbol).debitAvailable(amount);
    }

    public void hold(String assetSymbol, long amount) {
        getOrCreateBalance(assetSymbol).hold(amount);
    }

    public void release(String assetSymbol, long amount) {
        getOrCreateBalance(assetSymbol).release(amount);
    }

    public void settle(String outgoingAsset, long outgoingHeldAmount, String incomingAsset, long incomingAmount) {
        getOrCreateBalance(outgoingAsset).settleHeld(outgoingHeldAmount);
        getOrCreateBalance(incomingAsset).creditAvailable(incomingAmount);
    }

    public long getAvailableBalance(String assetSymbol) {
        AssetBalance balance = balances.get(assetSymbol);
        return balance == null ? 0L : balance.getAvailable();
    }

    public long getHeldBalance(String assetSymbol) {
        AssetBalance balance = balances.get(assetSymbol);
        return balance == null ? 0L : balance.getHeld();
    }

    public long getTotalBalance(String assetSymbol) {
        AssetBalance balance = balances.get(assetSymbol);
        return balance == null ? 0L : balance.getTotal();
    }

    private AssetBalance getOrCreateBalance(String assetSymbol) {
        return balances.computeIfAbsent(assetSymbol, AssetBalance::new);
    }
}