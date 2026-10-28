package main.domain;

import main.engine.MatchingEngine;
import main.ledger.Account;
import java.util.List;

public class BuyOrder extends Order {

    public BuyOrder(long id, String participantId, long price, long quantity) {
        super(id, participantId, price, quantity);
    }

    @Override
    public OrderNode routeTo(OrderBook book) {
        return book.addBid(this);
    }

    @Override
    public void processBy(MatchingEngine engine, List<Trade> executions, long timestamp) {
        engine.match(this, executions, timestamp);
    }

    @Override
    public void reserveFunds(Account account, TradingPair pair) {
        // Buyer locks Quote Asset (e.g., ARS): limitPrice * initialQuantity
        long totalQuoteToLock = Math.multiplyExact(getPrice(), getInitialQuantity());
        account.hold(pair.quoteAsset(), totalQuoteToLock);
    }

    @Override
    public void releaseRemainingFunds(Account account, TradingPair pair) {
        if (getRemainingQuantity() > 0) {
            long quoteToRelease = Math.multiplyExact(getPrice(), getRemainingQuantity());
            account.release(pair.quoteAsset(), quoteToRelease);
        }
    }

    @Override
    public void settleExecution(Account account, TradingPair pair, long executionPrice, long executedQuantity) {
        if (executionPrice > getPrice()) {
            throw new IllegalStateException("Invariant violation: BuyOrder executed above limit price");
        }

        long lockedQuoteAmount = Math.multiplyExact(getPrice(), executedQuantity);
        long actualQuoteCost = Math.multiplyExact(executionPrice, executedQuantity);
        long priceImprovementRefund = lockedQuoteAmount - actualQuoteCost;

        // 1. Consume the actual cost from held quote currency and credit the purchased base asset
        account.settle(pair.quoteAsset(), actualQuoteCost, pair.baseAsset(), executedQuantity);

        // 2. If the buyer got a better price than their limit, immediately unlock the saved difference
        if (priceImprovementRefund > 0) {
            account.release(pair.quoteAsset(), priceImprovementRefund);
        }
    }
}