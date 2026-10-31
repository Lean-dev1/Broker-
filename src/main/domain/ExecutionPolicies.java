package main.domain;

import com.jerez.fintech.ledger.Account;
import java.util.Map;

/**
 * Stateless singleton implementations of standard institutional Time-In-Force policies.
 */
public final class ExecutionPolicies {

    private ExecutionPolicies() {}

    /**
     * Good 'Til Canceled (GTC): Matches what it can immediately and rests the remainder in the book.
     */
    public static final ExecutionPolicy GTC = new ExecutionPolicy() {
        @Override
        public boolean canExecute(Order aggressiveOrder, OrderBook book) {
            return true;
        }

        @Override
        public void handleUnfilledRemainder(
                Order aggressiveOrder,
                OrderBook book,
                Account account,
                TradingPair pair,
                Map<Long, Order> openOrdersIndex) {
            book.enqueueOrder(aggressiveOrder);
            openOrdersIndex.put(aggressiveOrder.getId(), aggressiveOrder);
        }
    };

    /**
     * Immediate Or Cancel (IOC): Matches available liquidity immediately; any unfilled remainder
     * is canceled on the spot and its locked collateral is released back to available balance.
     */
    public static final ExecutionPolicy IOC = new ExecutionPolicy() {
        @Override
        public boolean canExecute(Order aggressiveOrder, OrderBook book) {
            return true;
        }

        @Override
        public void handleUnfilledRemainder(
                Order aggressiveOrder,
                OrderBook book,
                Account account,
                TradingPair pair,
                Map<Long, Order> openOrdersIndex) {
            // Never enters the OrderBook; immediately unlock remaining collateral
            aggressiveOrder.releaseRemainingFunds(account, pair);
        }
    };

    /**
     * Fill Or Kill (FOK): Requires 100% immediate execution or aborts completely,
     * releasing all locked funds without mutating the OrderBook.
     */
    public static final ExecutionPolicy FOK = new ExecutionPolicy() {
        @Override
        public boolean canExecute(Order aggressiveOrder, OrderBook book) {
            return aggressiveOrder.canBeCompletelyFilledBy(book);
        }

        @Override
        public void handleUnfilledRemainder(
                Order aggressiveOrder,
                OrderBook book,
                Account account,
                TradingPair pair,
                Map<Long, Order> openOrdersIndex) {
            // Reached only when canExecute returned false: release 100% of held collateral
            aggressiveOrder.releaseRemainingFunds(account, pair);
        }
    };
}