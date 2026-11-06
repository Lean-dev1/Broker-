package main.domain;

/** Node in a price level's FIFO doubly linked list. */
public final class OrderNode {
    private final Order order;
    private PriceLevel level;
    OrderNode next;
    OrderNode prev;

    OrderNode(Order order, PriceLevel level) {
        this.order = order;
        this.level = level;
    }

    public Order getOrder() { return order; }
    public OrderNode getNext() { return next; }

    public void removeFromLevel() {
        if (level != null) {
            level.removeOrder(this);
        }
    }

    boolean belongsTo(PriceLevel expected) { return level == expected; }

    void detach() {
        level = null;
        next = null;
        prev = null;
    }
}
