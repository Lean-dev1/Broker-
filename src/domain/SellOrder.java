package domain;

public class SellOrder extends Order {
    public SellOrder(long id, String participantId, long price, long quantity) {
        super(id, participantId, price, quantity);
    }

    @Override
    public OrderNode routeTo(OrderBook book) {
        // Despacho polimórfico: la SellOrder sabe que debe ir a los Asks
        return book.addAsk(this);

        
    }

    @Override
    public void processBy(MatchingEngineTest engine) {
        engine.match(this);
    }
}