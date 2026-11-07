package main.domain;

import main.engine.MatchingEngine;
import main.ledger.Ledger;
import java.util.List;

public class SellOrder extends Order {

    public SellOrder(long id, String participantId, long price, long quantity) {
        super(id, participantId, price, quantity);
    }

    @Override
    public OrderNode routeTo(OrderBook book) {
        return book.addAsk(this);
    }

    @Override
    public void processBy(MatchingEngine engine, List<Trade> executions, long timestamp) {
        engine.match(this, executions, timestamp);
    }

    @Override
    public void reserveFunds(Ledger ledger, TradingPair pair) {
        // Seller locks Base Asset (e.g., ALUA shares): only the quantity matters
        ledger.hold(getParticipantId(), pair.baseAsset(), getInitialQuantity());
    }

    @Override
    public void releaseRemainingFunds(Ledger ledger, TradingPair pair) {
        if (getRemainingQuantity() > 0) {
            ledger.release(getParticipantId(), pair.baseAsset(), getRemainingQuantity());
        }
    }

}
