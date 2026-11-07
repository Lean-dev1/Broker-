package main.engine;

import main.domain.Order;
import main.domain.OrderBook;
import main.domain.OrderStatus;
import main.domain.SelfTradeException;
import main.domain.TimeInForce;
import main.domain.Trade;
import main.domain.TradingPair;
import main.ledger.Account;
import main.ledger.Ledger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Single-writer in-memory transaction coordinator for simulated funds.
 * Snapshots are used to restore order book, order lifecycle and balances on an exception.
 * NOT durable, crash-safe, or safe when the underlying book/ledger is mutated externally.
 */
public final class TradingCoordinator {
    private final TradingPair tradingPair;
    private final Ledger ledger;
    private final OrderBook orderBook;
    private final MatchingEngine matchingEngine;
    private final Map<Long, Order> openOrdersIndex = new HashMap<>();
    private final Set<Long> submittedIds = new HashSet<>();

    public TradingCoordinator(TradingPair tradingPair, Ledger ledger, OrderBook orderBook,
                              MatchingEngine matchingEngine) {
        this.tradingPair = Objects.requireNonNull(tradingPair);
        this.ledger = Objects.requireNonNull(ledger);
        this.orderBook = Objects.requireNonNull(orderBook);
        this.matchingEngine = Objects.requireNonNull(matchingEngine);
    }

    /** Backward-compatible default: GTC. */
    public synchronized List<Trade> submitOrder(Order order, long timestamp) {
        return submitOrder(order, timestamp, TimeInForce.GTC);
    }

    /**
     * Accepts one order with GTC, IOC or FOK execution policy.
     * Reverts all in-memory state on a runtime failure (including failures mid-settlement).
     */
    public synchronized List<Trade> submitOrder(Order order, long timestamp, TimeInForce policy) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(policy, "time in force");
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("Only fresh orders can be submitted");
        }
        if (submittedIds.contains(order.getId()) || orderBook.containsOrder(order.getId())) {
            throw new IllegalArgumentException("Order ID already used: " + order.getId());
        }
        if (orderBook.wouldSelfMatch(order)) {
            throw new SelfTradeException("Self-trading is forbidden");
        }
        Account aggressiveAccount = ledger.getAccount(order.getParticipantId());
        Checkpoint checkpoint = checkpoint(order);
        try {
            // Hold first. If no funds, the transaction rolls back without consuming the ID.
            order.reserveFunds(aggressiveAccount, tradingPair);
            submittedIds.add(order.getId());

            // FOK can cancel without mutating the book or executing any trades.
            if (policy == TimeInForce.FOK && !order.canBeCompletelyFilledBy(orderBook)) {
                order.releaseRemainingFunds(aggressiveAccount, tradingPair);
                order.cancelRemaining();
                return List.of();
            }

            // The raw engine has GTC resting semantics; IOC is explicitly unlinked afterward.
            List<Trade> trades = matchingEngine.process(order, timestamp);
            for (Trade trade : trades) settleTrade(order, trade);

            if (!order.isFilled()) {
                if (policy == TimeInForce.GTC) {
                    if (!orderBook.containsOrder(order.getId())) {
                        throw new IllegalStateException("Resting order missing from book");
                    }
                    openOrdersIndex.put(order.getId(), order);
                } else {
                    Order unlinked = orderBook.cancelOrder(order.getId());
                    if (unlinked != order) {
                        throw new IllegalStateException("IOC/FOK remaining order not found");
                    }
                    order.releaseRemainingFunds(aggressiveAccount, tradingPair);
                    order.cancelRemaining();
                }
            }
            if (policy == TimeInForce.FOK && !order.isFilled()) {
                throw new IllegalStateException("FOK violated all-or-nothing execution");
            }
            return List.copyOf(trades);
        } catch (RuntimeException | Error failure) {
            rollback(checkpoint, failure);
            throw failure;
        }
    }

    public synchronized boolean cancelOrder(long orderId) {
        if (!openOrdersIndex.containsKey(orderId)) return false;
        Order order = openOrdersIndex.get(orderId);
        Account account = ledger.getAccount(order.getParticipantId());
        Checkpoint checkpoint = checkpoint(order);
        try {
            Order unlinked = orderBook.cancelOrder(orderId);
            if (unlinked != order) throw new IllegalStateException("Book and coordinator index differ");
            order.releaseRemainingFunds(account, tradingPair);
            order.cancelRemaining();
            openOrdersIndex.remove(orderId);
            return true;
        } catch (RuntimeException | Error failure) {
            rollback(checkpoint, failure);
            throw failure;
        }
    }

    /**
     * All executed transfers are booked as immutable, per-asset balanced postings.
     * The outer coordinator checkpoint covers any failure during multi-trade settlement.
     */
    private void settleTrade(Order aggressiveOrder, Trade trade) {
        Order restingOrder = openOrdersIndex.get(trade.restingOrderId());
        if (restingOrder == null) {
            throw new IllegalStateException(
                    "Resting order missing for settlement: " + trade.restingOrderId());
        }
        ledger.recordTrade(trade, aggressiveOrder, restingOrder, tradingPair);
        if (restingOrder.isFilled()) {
            openOrdersIndex.remove(restingOrder.getId());
        }
    }

    private record Checkpoint(OrderBook.Snapshot book, Ledger.Snapshot ledger,
                              Map<Long, Order> openOrders, Set<Long> submittedIds) {}

    private Checkpoint checkpoint(Order incoming) {
        return new Checkpoint(orderBook.snapshot(incoming), ledger.snapshot(),
                new HashMap<>(openOrdersIndex), new HashSet<>(submittedIds));
    }

    private void rollback(Checkpoint checkpoint, Throwable failure) {
        try {
            orderBook.restore(checkpoint.book());
        } catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
        try {
            ledger.restore(checkpoint.ledger());
        } catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
        openOrdersIndex.clear();
        openOrdersIndex.putAll(checkpoint.openOrders());
        submittedIds.clear();
        submittedIds.addAll(checkpoint.submittedIds());
    }
}
