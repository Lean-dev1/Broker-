package main.domain;
/**
 * Private node of our Doubly Linked List.
   Acts as a wrapperr for the Order 

*/
public class OrderNode {
    private final Order order;
    OrderNode next; // Package-private visibility so it can be manipulated by PriceLevel
    OrderNode prev;

    public OrderNode(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
