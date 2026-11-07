package main.domain;

import main.engine.MatchingEngine;
import main.ledger.Ledger;
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
    public void reserveFunds(Ledger ledger, TradingPair pair) {
        // Buyer locks Quote Asset (e.g., ARS): limitPrice * initialQuantity
        long totalQuoteToLock = Math.multiplyExact(getPrice(), getInitialQuantity());
        ledger.hold(getParticipantId(), pair.quoteAsset(), totalQuoteToLock);
    }

    @Override
    public void releaseRemainingFunds(Ledger ledger, TradingPair pair) {
        if (getRemainingQuantity() > 0) {
            long quoteToRelease = Math.multiplyExact(getPrice(), getRemainingQuantity());
            ledger.release(getParticipantId(), pair.quoteAsset(), quoteToRelease);
        }
    }

}
