package main.domain;

/** FIFO queue of resting orders at one price. */
public final class PriceLevel {
    private final long price;
    private OrderNode head;
    private OrderNode tail;

    public PriceLevel(long price) { this.price = price; }

    public long getPrice() { return price; }
    public OrderNode getHead() { return head; }
    public boolean isEmpty() { return head == null; }

    public OrderNode addOrder(Order order) {
        OrderNode node = new OrderNode(order, this);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            node.prev = tail;
            tail = node;
        }
        return node;
    }

    public void removeOrder(OrderNode node) {
        if (node == null) return;
        if (node.prev != null) node.prev.next = node.next;
        else if (head == node) head = node.next;
        else throw new IllegalArgumentException("Node is not part of this level");
        if (node.next != null) node.next.prev = node.prev;
        else tail = node.prev;
        node.detach();
    }

    public long getAvailableVolumeUpTo(long maxNeeded) {
        if (maxNeeded <= 0) return 0L;
        long accumulated = 0L;
        for (OrderNode node = head; node != null; node = node.getNext()) {
            long left = maxNeeded - accumulated;
            accumulated += Math.min(left, node.getOrder().getRemainingQuantity());
            if (accumulated == maxNeeded) break;
        }
        return accumulated;
    }
}
