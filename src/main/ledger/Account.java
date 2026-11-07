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
    // All balance changes must go through Ledger so they are auditable.
    // Account exposes read-only balance queries to external packages.

    /** Package-private mutation entry point for balanced trade posting application. */
    void applyPosting(LedgerPosting posting) {
        if (!participantId.equals(posting.participantId())) {
            throw new IllegalArgumentException("Posting refers to another account");
        }
        getOrCreateBalance(posting.assetSymbol()).adjust(posting.bucket(), posting.delta());
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

    /** A detached, immutable balance view for reconciliation. Package-private. */
    Map<String, LedgerBalanceView> balanceViews() {
        Map<String, LedgerBalanceView> view = new HashMap<>();
        balances.forEach((asset, amount) ->
                view.put(asset, new LedgerBalanceView(amount.getAvailable(), amount.getHeld())));
        return Map.copyOf(view);
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
