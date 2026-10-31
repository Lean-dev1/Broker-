package main.domain;
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

   /**
     * Cancels an order in O(1) time and returns it so held funds can be released.
     */
    public Order cancelOrder(long orderId) {
        OrderNode node = activeOrders.remove(orderId);
        if (node == null) {
            return null;
        }
        node.removeFromLevel();
        return node.getOrder();
    }

    /**
     * Non-destructive check for Buy FOK orders: verifies if asks up to limitPrice can cover requiredQty.
     */
    public boolean canFillBuyCompletely(long limitPrice, long requiredQty) {
        long accumulated = 0L;
        for (PriceLevel level : asks.values()) {
            if (level.getPrice() > limitPrice) {
                break; // TreeMap is sorted ascending; no further levels can match
            }
            accumulated += level.getAvailableVolumeUpTo(requiredQty - accumulated);
            if (accumulated >= requiredQty) {
                return true;
            }
        }
        return false;
    }

    /**
     * Non-destructive check for Sell FOK orders: verifies if bids down to limitPrice can cover requiredQty.
     */
    public boolean canFillSellCompletely(long limitPrice, long requiredQty) {
        long accumulated = 0L;
        for (PriceLevel level : bids.values()) {
            if (level.getPrice() < limitPrice) {
                break; // Bids TreeMap is sorted descending; no further levels can match
            }
            accumulated += level.getAvailableVolumeUpTo(requiredQty - accumulated);
            if (accumulated >= requiredQty) {
                return true;
            }
        }
        return false;
    }
}