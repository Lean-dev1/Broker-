package main.ledger;

import main.domain.BuyOrder;
import main.domain.Order;
import main.domain.SellOrder;
import main.domain.Trade;
import main.domain.TradingPair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory ledger. Simulated executed trades are recorded as immutable,
 * balanced postings. Holds/deposits/withdrawals are not journaled yet.
 * Not durable, not replayable, and not thread-safe.
 */
public class Ledger {
    private final Map<String, Account> accounts = new HashMap<>();
    private final List<LedgerRecord> journal = new ArrayList<>();
    private long nextSequence = 1L;

    public synchronized Account createAccount(String participantId) {
        if (accounts.containsKey(participantId)) {
            throw new IllegalArgumentException("Account already exists for participant: " + participantId);
        }
        Account account = new Account(participantId);
        accounts.put(participantId, account);
        return account;
    }

    public synchronized Account getAccount(String participantId) {
        Account account = accounts.get(participantId);
        if (account == null) throw new IllegalArgumentException("Account not found: " + participantId);
        return account;
    }

    /**
     * Reconstruct all recorded balances independently from the journal and
     * compare them with the current in-memory account state.
     * This is O(journal records + account positions), not a hot-path check.
     */
    public synchronized LedgerAuditReport reconcile() {
        LedgerAuditReport report = LedgerAuditor.replay(List.copyOf(journal));
        Map<AccountAssetKey, LedgerBalanceView> live = new HashMap<>();
        for (Account account : accounts.values()) {
            account.balanceViews().forEach((asset, balance) ->
                    live.put(new AccountAssetKey(account.getParticipantId(), asset), balance));
        }
        java.util.Set<AccountAssetKey> keys = new java.util.HashSet<>(live.keySet());
        keys.addAll(report.balances().keySet());
        for (AccountAssetKey key : keys) {
            LedgerBalanceView observed = live.getOrDefault(key, LedgerBalanceView.ZERO);
            LedgerBalanceView expected = report.balances().getOrDefault(
                    key, LedgerBalanceView.ZERO);
            if (!observed.equals(expected)) {
                throw new IllegalStateException(
                        "Ledger reconciliation mismatch for " + key
                        + ": expected " + expected + ", actual " + observed);
            }
        }
        return report;
    }

    /** Immutable snapshot of the complete in-memory accounting journal. */
    public synchronized List<LedgerRecord> getJournal() { return List.copyOf(journal); }

    /** Compatibility query for executed trades only, in sequence order. */
    public synchronized List<LedgerTransaction> getTransactions() {
        List<LedgerTransaction> trades = new ArrayList<>();
        for (LedgerRecord record : journal) {
            if (record instanceof LedgerTransaction trade) trades.add(trade);
        }
        return List.copyOf(trades);
    }

    /** Explicit external inflow; not an internal zero-sum transfer. */
    public synchronized LedgerOperation deposit(String participantId, String asset, long amount) {
        return recordOperation(LedgerOperationType.DEPOSIT, participantId, asset, amount);
    }

    /** Explicit external outflow; not an internal zero-sum transfer. */
    public synchronized LedgerOperation withdraw(String participantId, String asset, long amount) {
        return recordOperation(LedgerOperationType.WITHDRAWAL, participantId, asset, amount);
    }

    /** Move available collateral to held, with zero net asset creation. */
    public synchronized LedgerOperation hold(String participantId, String asset, long amount) {
        return recordOperation(LedgerOperationType.HOLD, participantId, asset, amount);
    }

    /** Move held collateral back to available, with zero net asset creation. */
    public synchronized LedgerOperation release(String participantId, String asset, long amount) {
        return recordOperation(LedgerOperationType.RELEASE, participantId, asset, amount);
    }

    private LedgerOperation recordOperation(LedgerOperationType type, String participant,
                                            String asset, long amount) {
        Account account = getAccount(participant);
        if (asset == null || asset.isBlank() || amount <= 0) {
            throw new IllegalArgumentException("Valid asset and positive amount required");
        }
        long followingSequence = Math.addExact(nextSequence, 1L);
        LedgerPosting available = new LedgerPosting(participant, asset,
                BalanceBucket.AVAILABLE,
                (type == LedgerOperationType.DEPOSIT || type == LedgerOperationType.RELEASE)
                        ? amount : -amount);
        List<LedgerPosting> postings;
        if (type == LedgerOperationType.HOLD) {
            postings = List.of(available,
                    new LedgerPosting(participant, asset, BalanceBucket.HELD, amount));
        } else if (type == LedgerOperationType.RELEASE) {
            postings = List.of(
                    new LedgerPosting(participant, asset, BalanceBucket.HELD, -amount),
                    available);
        } else {
            postings = List.of(available);
        }
        LedgerOperation operation =
                new LedgerOperation(nextSequence, type, participant, asset, amount, postings);
        applyValidated(operation.postings());
        journal.add(operation);
        nextSequence = followingSequence;
        return operation;
    }

    /**
     * Apply a simulated trade with per-asset zero-sum postings.
     * Must be called from the single-writer coordinator, which rolls back if a later
     * trade in the same command fails.
     */
    public synchronized LedgerTransaction recordTrade(Trade trade, Order aggressive, Order resting, TradingPair pair) {
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(aggressive, "aggressive order");
        Objects.requireNonNull(resting, "resting order");
        Objects.requireNonNull(pair, "pair");
        if (trade.executedQuantity() <= 0 || trade.matchPrice() <= 0) {
            throw new IllegalArgumentException("Trade must have positive price and quantity");
        }
        if (trade.aggressiveOrderId() != aggressive.getId()
                || trade.restingOrderId() != resting.getId()) {
            throw new IllegalArgumentException("Trade and order IDs do not match");
        }
        if (aggressive.getParticipantId().equals(resting.getParticipantId())) {
            throw new IllegalArgumentException("Self-trading forbidden");
        }
        Order buy = aggressive instanceof BuyOrder ? aggressive : resting;
        Order sell = aggressive instanceof SellOrder ? aggressive : resting;
        if (!(buy instanceof BuyOrder) || !(sell instanceof SellOrder)) {
            throw new IllegalArgumentException("One buy and one sell order required");
        }
        if (trade.matchPrice() > buy.getPrice() || trade.matchPrice() < sell.getPrice()) {
            throw new IllegalArgumentException("Execution violates limit price");
        }

        // Reserve at the buyer's limit, settle at the execution price,
        // then return price improvement to the buyer's available balance.
        long heldQuote = Math.multiplyExact(buy.getPrice(), trade.executedQuantity());
        long quoteCost = Math.multiplyExact(trade.matchPrice(), trade.executedQuantity());
        long refund = Math.subtractExact(heldQuote, quoteCost);
        long qty = trade.executedQuantity();

        List<LedgerPosting> postings = new ArrayList<>(5);
        postings.add(new LedgerPosting(buy.getParticipantId(), pair.quoteAsset(),
                BalanceBucket.HELD, -heldQuote));
        postings.add(new LedgerPosting(sell.getParticipantId(), pair.quoteAsset(),
                BalanceBucket.AVAILABLE, quoteCost));
        if (refund > 0) {
            postings.add(new LedgerPosting(buy.getParticipantId(), pair.quoteAsset(),
                    BalanceBucket.AVAILABLE, refund));
        }
        postings.add(new LedgerPosting(sell.getParticipantId(), pair.baseAsset(),
                BalanceBucket.HELD, -qty));
        postings.add(new LedgerPosting(buy.getParticipantId(), pair.baseAsset(),
                BalanceBucket.AVAILABLE, qty));

        LedgerTransaction transaction = new LedgerTransaction(nextSequence, trade, postings);
        long followingSequence = Math.addExact(nextSequence, 1L);

        applyValidated(transaction.postings());
        journal.add(transaction);
        nextSequence = followingSequence;
        return transaction;
    }

    /**
     * Preflight all posting effects before the first mutation. Sequences and
     * journal entries change only after the entire posting batch succeeds.
     * The coordinator owns rollback if an unexpected runtime error occurs.
     */
    private void applyValidated(List<LedgerPosting> postings) {
        record BalanceKey(String participant, String asset, BalanceBucket bucket) {}
        Map<BalanceKey, Long> projected = new HashMap<>();
        for (LedgerPosting posting : postings) {
            Account account = getAccount(posting.participantId());
            BalanceKey key = new BalanceKey(posting.participantId(),
                    posting.assetSymbol(), posting.bucket());
            long current = projected.getOrDefault(key, posting.bucket() == BalanceBucket.AVAILABLE
                    ? account.getAvailableBalance(posting.assetSymbol())
                    : account.getHeldBalance(posting.assetSymbol()));
            long after = Math.addExact(current, posting.delta());
            if (after < 0) throw new InsufficientFundsException("Posting would make " + key + " negative");
            projected.put(key, after);
        }
        // Even if both buckets individually fit in long, their sum must fit too.
        // Validate each touched account/asset before committing any posting.
        record AssetKey(String participant, String asset) {}
        java.util.Set<AssetKey> affected = new java.util.HashSet<>();
        for (LedgerPosting posting : postings) {
            affected.add(new AssetKey(posting.participantId(), posting.assetSymbol()));
        }
        for (AssetKey asset : affected) {
            Account account = getAccount(asset.participant());
            long available = projected.getOrDefault(
                    new BalanceKey(asset.participant(), asset.asset(), BalanceBucket.AVAILABLE),
                    account.getAvailableBalance(asset.asset()));
            long held = projected.getOrDefault(
                    new BalanceKey(asset.participant(), asset.asset(), BalanceBucket.HELD),
                    account.getHeldBalance(asset.asset()));
            Math.addExact(available, held);
        }
        for (LedgerPosting posting : postings) {
            getAccount(posting.participantId()).applyPosting(posting);
        }
    }

    public static final class Snapshot {
        private final Map<Account, Account.Snapshot> accounts;
        private final int journalSize;
        private final long nextSequence;

        private Snapshot(Map<Account, Account.Snapshot> accounts, int journalSize,
                         long nextSequence) {
            this.accounts = accounts;
            this.journalSize = journalSize;
            this.nextSequence = nextSequence;
        }
    }

    public synchronized Snapshot snapshot() {
        Map<Account, Account.Snapshot> states = new HashMap<>();
        for (Account account : accounts.values()) states.put(account, account.snapshotState());
        return new Snapshot(states, journal.size(), nextSequence);
    }

    /** Restore balances AND remove uncommitted journal entries after a failed order. */
    public synchronized void restore(Snapshot snapshot) {
        snapshot.accounts.forEach(Account::restoreState);
        if (journal.size() < snapshot.journalSize) {
            throw new IllegalStateException("Journal shorter than checkpoint");
        }
        while (journal.size() > snapshot.journalSize) {
            journal.remove(journal.size() - 1);
        }
        nextSequence = snapshot.nextSequence;
    }
}
