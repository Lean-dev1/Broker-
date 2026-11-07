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
    private final List<LedgerTransaction> transactions = new ArrayList<>();
    private long nextSequence = 1L;

    public Account createAccount(String participantId) {
        if (accounts.containsKey(participantId)) {
            throw new IllegalArgumentException("Account already exists for participant: " + participantId);
        }
        Account account = new Account(participantId);
        accounts.put(participantId, account);
        return account;
    }

    public Account getAccount(String participantId) {
        Account account = accounts.get(participantId);
        if (account == null) throw new IllegalArgumentException("Account not found: " + participantId);
        return account;
    }

    /** Immutable snapshot of the journal; callers cannot change committed entries. */
    public List<LedgerTransaction> getTransactions() { return List.copyOf(transactions); }

    /**
     * Apply a simulated trade with per-asset zero-sum postings.
     * Must be called from the single-writer coordinator, which rolls back if a later
     * trade in the same command fails.
     */
    public LedgerTransaction recordTrade(Trade trade, Order aggressive, Order resting, TradingPair pair) {
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

        // Preflight all resulting balances before modifying any of them.
        record BalanceKey(String participant, String asset, BalanceBucket bucket) {}
        Map<BalanceKey, Long> projected = new HashMap<>();
        for (LedgerPosting posting : transaction.postings()) {
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

        // The coordinator owns the full rollback boundary for any unexpected failure.
        for (LedgerPosting posting : transaction.postings()) {
            getAccount(posting.participantId()).applyPosting(posting);
        }
        transactions.add(transaction);
        nextSequence = followingSequence;
        return transaction;
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

    public Snapshot snapshot() {
        Map<Account, Account.Snapshot> states = new HashMap<>();
        for (Account account : accounts.values()) states.put(account, account.snapshotState());
        return new Snapshot(states, transactions.size(), nextSequence);
    }

    /** Restore balances AND remove uncommitted journal entries after a failed order. */
    public void restore(Snapshot snapshot) {
        snapshot.accounts.forEach(Account::restoreState);
        if (transactions.size() < snapshot.journalSize) {
            throw new IllegalStateException("Journal shorter than checkpoint");
        }
        while (transactions.size() > snapshot.journalSize) {
            transactions.remove(transactions.size() - 1);
        }
        nextSequence = snapshot.nextSequence;
    }
}
