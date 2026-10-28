package domain;

public class BuyOrder extends Order {
    public BuyOrder(long id, String participantId, long price, long quantity) {
        super(id, participantId, price, quantity);
    }

    @Override
    public OrderNode routeTo(OrderBook book) {
        // Despacho polimórfico: la BuyOrder sabe que debe ir a los Bids
        return book.addBid(this);

    }

    @Override
    public void processBy(MatchingEngine engine) {
        // Al estar dentro de BuyOrder, "this" es estáticamente de tipo BuyOrder.
        // Esto fuerza al compilador a llamar a engine.match(BuyOrder order).
        engine.match(this);
}