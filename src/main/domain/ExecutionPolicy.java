package main.domain;

import com.jerez.fintech.ledger.Account;
import java.util.Map;

/**
 * Strategy interface defining Time-In-Force execution semantics (GTC, IOC, FOK)
 * using Tell, Don't Ask instead of conditional branching.
 */
public interface ExecutionPolicy {

    /**
     * Determines if the order is allowed to start matching against the OrderBook.
     */
    boolean canExecute(Order aggressiveOrder, OrderBook book);

    /**
     * Handles an unfilled or partially filled order after the matching loop finishes.
     */
    void handleUnfilledRemainder(
        Order aggressiveOrder,
        OrderBook book,
        Account account,
        TradingPair pair,
        Map<Long, Order> openOrdersIndex
    );
}