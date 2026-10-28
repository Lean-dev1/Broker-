package domain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class OrderBook {
    private final String instrument;
    private final Map<Long, OrderNode> activeOrders;
    private final TreeMap<Long, PriceLevel> bids;
    private final TreeMap<Long, PriceLevel> asks;

    public OrderBook(String instrument) {
        this.instrument = instrument;
        this.activeOrders = new HashMap<>();
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
    }

    /**
     * Tell, Don't Ask: Le decimos a la orden que se encargue de su propio enrutamiento.
     * Cero sentencias "if", delegación total al polimorfismo.
     */
    public void enqueueOrder(Order order) {
        OrderNode node = order.routeTo(this);
        activeOrders.put(order.getId(), node);
    }

    // Métodos con visibilidad de paquete (package-private) 
    // para que solo BuyOrder y SellOrder puedan invocarlos internamente.
    OrderNode addBid(BuyOrder order) {
        PriceLevel level = bids.computeIfAbsent(order.getPrice(), PriceLevel::new);
        return level.addOrder(order);
    }

    OrderNode addAsk(SellOrder order) {
        PriceLevel level = asks.computeIfAbsent(order.getPrice(), PriceLevel::new);
        return level.addOrder(order);
    }

    public void cancelOrder(long orderId) {
        OrderNode node = activeOrders.remove(orderId);
        if (node != null) {
            // Aquí el PriceLevel debería encapsular también su propia limpieza,
            // pero mantenemos la lógica base por ahora.
            node.removeFromLevel(); 
        }
    }
}