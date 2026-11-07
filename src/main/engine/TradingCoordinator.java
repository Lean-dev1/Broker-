package main.engine;

import main.domain.Order;
import main.domain.BuyOrder;
import main.domain.SellOrder;
import main.domain.Side;
import main.domain.OrderBook;
import main.domain.OrderStatus;
import main.domain.SelfTradeException;
import main.domain.TimeInForce;
import main.domain.Trade;
import main.domain.TradingPair;
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
    private final Map<String, CompletedSubmission> committedCommands = new HashMap<>();

    /** Immutable identity of a request, deliberately excluding mutable order status. */
    private record SubmissionIntent(long orderId, String participantId, Side side,
                                    long limitPrice, long initialQuantity, long timestamp,
                                    TimeInForce policy) {
        static SubmissionIntent capture(Order order, long timestamp, TimeInForce policy) {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(policy, "time in force");
            Side side;
            if (order instanceof BuyOrder) side = Side.BUY;
            else if (order instanceof SellOrder) side = Side.SELL;
            else throw new IllegalArgumentException("Unsupported order subtype");
            return new SubmissionIntent(order.getId(), order.getParticipantId(), side,
                    order.getPrice(), order.getInitialQuantity(), timestamp, policy);
        }
    }

    private record CompletedSubmission(SubmissionIntent intent, SubmissionReceipt receipt) {}


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
        synchronized (ledger) {
            return submitOrderUnderLedgerLock(order, timestamp, policy, null, null);
        }
    }

    /**
     * Retry-safe *within this JVM only*. Identical command IDs and requests
     * return their original receipt without touching the order book or ledger.
     * Conflicting reuse of a committed command ID is rejected before mutation.
     *
     * Command IDs must be 1..128 ASCII characters: letters, digits, dot,
     * underscore, colon or hyphen. An accepted no-fill FOK consumes its ID.
     * Rejected/rolled-back submissions do not consume their command IDs.
     */
    public synchronized SubmissionReceipt submitOrderIdempotent(
            Order order, long timestamp, TimeInForce policy, String commandId) {
        if (commandId == null || !commandId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid command ID format");
        }
        SubmissionIntent intent = SubmissionIntent.capture(order, timestamp, policy);
        synchronized (ledger) {
            CompletedSubmission prior = committedCommands.get(commandId);
            if (prior != null) {
                if (!prior.intent().equals(intent)) {
                    throw new IdempotencyConflictException(commandId);
                }
                return prior.receipt();
            }
            submitOrderUnderLedgerLock(order, timestamp, policy, commandId, intent);
            return committedCommands.get(commandId).receipt();
        }
    }

    /** Ledger and coordinator mutation cannot interleave with an external ledger command. */
    private List<Trade> submitOrderUnderLedgerLock(
            Order order, long timestamp, TimeInForce policy,
            String commandId, SubmissionIntent intent) {
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
        ledger.getAccount(order.getParticipantId());
        Checkpoint checkpoint = checkpoint(order);
        try {
            // Hold first. If no funds, the transaction rolls back without consuming the ID.
            order.reserveFunds(ledger, tradingPair);
            submittedIds.add(order.getId());

            // FOK can cancel without mutating the book or executing any trades.
            if (policy == TimeInForce.FOK && !order.canBeCompletelyFilledBy(orderBook)) {
                order.releaseRemainingFunds(ledger, tradingPair);
                order.cancelRemaining();
                return completeSubmission(order, policy, List.of(), commandId, intent);
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
                    order.releaseRemainingFunds(ledger, tradingPair);
                    order.cancelRemaining();
                }
            }
            if (policy == TimeInForce.FOK && !order.isFilled()) {
                throw new IllegalStateException("FOK violated all-or-nothing execution");
            }
            return completeSubmission(order, policy, trades, commandId, intent);
        } catch (RuntimeException | Error failure) {
            rollback(checkpoint, failure);
            throw failure;
        }
    }

    public synchronized boolean cancelOrder(long orderId) {
        synchronized (ledger) {
            return cancelUnderLedgerLock(orderId);
        }
    }

    private boolean cancelUnderLedgerLock(long orderId) {
        if (!openOrdersIndex.containsKey(orderId)) return false;
        Order order = openOrdersIndex.get(orderId);
        ledger.getAccount(order.getParticipantId());
        Checkpoint checkpoint = checkpoint(order);
        try {
            Order unlinked = orderBook.cancelOrder(orderId);
            if (unlinked != order) throw new IllegalStateException("Book and coordinator index differ");
            order.releaseRemainingFunds(ledger, tradingPair);
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

    /**
     * Finalize the request and memoize the immutable response within the same
     * rollback boundary as the financial effects.
     */
    private List<Trade> completeSubmission(Order order, TimeInForce policy,
                                            List<Trade> trades, String commandId,
                                            SubmissionIntent intent) {
        List<Trade> immutable = List.copyOf(trades);
        if (commandId != null) {
            SubmissionReceipt receipt = new SubmissionReceipt(
                    commandId, order.getId(), policy, intent.timestamp(),
                    order.getStatus(), order.getInitialQuantity(),
                    order.getExecutedQuantity(), order.getRemainingQuantity(),
                    order.getCanceledQuantity(), immutable);
            if (committedCommands.putIfAbsent(commandId,
                    new CompletedSubmission(intent, receipt)) != null) {
                throw new IllegalStateException("Command ID unexpectedly committed twice");
            }
        }
        return immutable;
    }

    private record Checkpoint(OrderBook.Snapshot book, Ledger.Snapshot ledger,
                              Map<Long, Order> openOrders, Set<Long> submittedIds,
                              Map<String, CompletedSubmission> committedCommands) {}

    private Checkpoint checkpoint(Order incoming) {
        return new Checkpoint(orderBook.snapshot(incoming), ledger.snapshot(),
                new HashMap<>(openOrdersIndex), new HashSet<>(submittedIds),
                new HashMap<>(committedCommands));
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
        committedCommands.clear();
        committedCommands.putAll(checkpoint.committedCommands());
    }
}
