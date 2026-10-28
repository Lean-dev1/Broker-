package main.domain;

/**
 * Represents a price level in the order book (e.g., all buy orders at $1000).
 * Enforces encapsulation by managing the doubly linked list internally.
 */
public class PriceLevel {
    private final long price;
    private OrderNode head; 
    private OrderNode tail;
    private long totalVolume; 

    public PriceLevel(long price) {
        this.price = price;
        this.totalVolume = 0;
    }

    
    public void addOrder(Order order) {
        OrderNode newNode = new OrderNode(order);
        if (head == null) {
            head = tail = newNode;
        } else {
            tail.next = newNode;
            newNode.prev = tail;
            tail = newNode;
        }
        totalVolume += order.getRemainingQuantity();
    }
    
}