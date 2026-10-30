package main.engine;

import main.domain.Order;
import main.domain.OrderBook;
import main.domain.Trade;
import main.domain.TradingPair;
import main.ledger.Account;
import main.ledger.Ledger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates Pre-Trade Risk Validation, Order Matching, and Post-Trade Atomic Settlement.
 * Maintains strict single-threaded determinism on the hot path.
 */
public class TradingCoordinator {
    private final TradingPair tradingPair;
    private final Ledger ledger;
    private final OrderBook orderBook;
    private final MatchingEngine matchingEngine;

    // Tracks open orders by ID in O(1) to resolve resting orders during settlement
    private final Map<Long, Order> openOrdersIndex;

    public TradingCoordinator(TradingPair tradingPair, Ledger ledger, OrderBook orderBook, MatchingEngine matchingEngine) {
        this.tradingPair = tradingPair;
        this.ledger = ledger;
        this.orderBook = orderBook;
        this.matchingEngine = matchingEngine;
        this.openOrdersIndex = new HashMap<>();
    }

    /**
     * Submits an order through the full financial pipeline:
     * 1. Pre-Trade Risk: Locks required funds in the participant's account.
     * 2. Execution: Matches the order in the engine.
     * 3. Post-Trade Settlement: Settles both sides of every generated trade.
     */
    public List<Trade> submitOrder(Order aggressiveOrder, long timestamp) {
        Account aggressiveAccount = ledger.getAccount(aggressiveOrder.getParticipantId());

        // 1. Pre-Trade Risk Validation & Hold (Polymorphic Tell, Don't Ask)
        // If funds are insufficient, InsufficientFundsException is thrown here
        // and the order NEVER touches the MatchingEngine or OrderBook.
        aggressiveOrder.reserveFunds(aggressiveAccount, tradingPair);

        // 2. Matching Execution
        List<Trade> trades = matchingEngine.process(aggressiveOrder, timestamp);

        // 3. Post-Trade Atomic Settlement
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            settleTrade(aggressiveOrder, aggressiveAccount, trade);
        }

        // If the aggressive order was not fully filled, it is now resting in the book;
        // index it so future trades can settle against it.
        if (!aggressiveOrder.isFilled()) {
            openOrdersIndex.put(aggressiveOrder.getId(), aggressiveOrder);
        }

        return trades;
    }

    /**
     * Cancels an open order in the book and immediately unlocks its remaining held funds.
     */
    public boolean cancelOrder(long orderId) {
        Order canceledOrder = orderBook.cancelOrder(orderId);
        if (canceledOrder == null) {
            return false;
        }

        openOrdersIndex.remove(orderId);
        Account account = ledger.getAccount(canceledOrder.getParticipantId());
        
        // Polymorphic release of remaining collateral
        canceledOrder.releaseRemainingFunds(account, tradingPair);
        return true;
    }

    private void settleTrade(Order aggressiveOrder, Account aggressiveAccount, Trade trade) {
        Order restingOrder = openOrdersIndex.get(trade.restingOrderId());
        if (restingOrder == null) {
            throw new IllegalStateException("Critical invariant breach: Resting order not found for settlement: " + trade.restingOrderId());
        }

        Account restingAccount = ledger.getAccount(restingOrder.getParticipantId());

        long executionPrice = trade.matchPrice();
        long executedQty = trade.executedQuantity();

        // Polymorphic settlement: each order knows what asset it locked, what it receives,
        // and whether it deserves a price improvement refund.
        aggressiveOrder.settleExecution(aggressiveAccount, tradingPair, executionPrice, executedQty);
        restingOrder.settleExecution(restingAccount, tradingPair, executionPrice, executedQty);

        // Clean up the index if the resting order was completely exhausted
        if (restingOrder.isFilled()) {
            openOrdersIndex.remove(restingOrder.getId());
        }
    }
}