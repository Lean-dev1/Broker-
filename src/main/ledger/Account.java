package main.ledger;

import java.util.HashMap;
import java.util.Map;

/** Multi-asset account backed by in-memory integer balances. */
public class Account {
    private final String participantId;
    private final Map<String, AssetBalance> balances = new HashMap<>();

    public Account(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Participant ID cannot be blank");
        }
        this.participantId = participantId;
    }

    public String getParticipantId() { return participantId; }
    public void deposit(String asset, long amount) { getOrCreateBalance(asset).creditAvailable(amount); }
    public void withdraw(String asset, long amount) { getOrCreateBalance(asset).debitAvailable(amount); }
    public void hold(String asset, long amount) { getOrCreateBalance(asset).hold(amount); }
    public void release(String asset, long amount) { getOrCreateBalance(asset).release(amount); }

    public void settle(String outgoingAsset, long outgoingHeld, String incomingAsset, long incomingAmount) {
        if (outgoingAsset.equals(incomingAsset)) {
            throw new IllegalArgumentException("Outgoing and incoming assets must differ");
        }
        AssetBalance outgoing = getOrCreateBalance(outgoingAsset);
        AssetBalance incoming = getOrCreateBalance(incomingAsset);
        outgoing.verifyCanSettleHeld(outgoingHeld);
        incoming.verifyCanCredit(incomingAmount);
        outgoing.settleHeld(outgoingHeld);
        incoming.creditAvailable(incomingAmount);
    }

    public long getAvailableBalance(String asset) {
        AssetBalance balance = balances.get(asset);
        return balance == null ? 0L : balance.getAvailable();
    }
    public long getHeldBalance(String asset) {
        AssetBalance balance = balances.get(asset);
        return balance == null ? 0L : balance.getHeld();
    }
    public long getTotalBalance(String asset) {
        AssetBalance balance = balances.get(asset);
        return balance == null ? 0L : balance.getTotal();
    }

    static final class Snapshot {
        private final Map<String, AssetBalance.State> balances;
        private Snapshot(Map<String, AssetBalance.State> balances) { this.balances = balances; }
    }

    Snapshot snapshotState() {
        Map<String, AssetBalance.State> copy = new HashMap<>();
        balances.forEach((asset, balance) -> copy.put(asset, balance.snapshotState()));
        return new Snapshot(copy);
    }

    void restoreState(Snapshot snapshot) {
        balances.clear();
        snapshot.balances.forEach((asset, balanceState) -> {
            AssetBalance balance = new AssetBalance(asset);
            balance.restoreState(balanceState);
            balances.put(asset, balance);
        });
    }

    private AssetBalance getOrCreateBalance(String asset) {
        return balances.computeIfAbsent(asset, AssetBalance::new);
    }
}
