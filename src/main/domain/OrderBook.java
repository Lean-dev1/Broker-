package main.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** In-memory price/time priority book for one instrument; single-writer only. */
public final class OrderBook {
    private final String instrument;
    private final Map<Long, OrderNode> activeOrders = new HashMap<>();
    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();

    public OrderBook(String instrument) {
        if (instrument == null || instrument.isBlank()) throw new IllegalArgumentException("Instrument required");
        this.instrument = instrument;
    }

    public String getInstrument() { return instrument; }
    public boolean hasBids() { return !bids.isEmpty(); }
    public boolean hasAsks() { return !asks.isEmpty(); }
    public Long getBestBid() { return hasBids() ? bids.firstKey() : null; }
    public Long getBestAsk() { return hasAsks() ? asks.firstKey() : null; }
    public PriceLevel getBestBidLevel() { return hasBids() ? bids.firstEntry().getValue() : null; }
    public PriceLevel getBestAskLevel() { return hasAsks() ? asks.firstEntry().getValue() : null; }
    public PriceLevel getLevel(Side side, long price) {
        return (side == Side.BUY ? bids : asks).get(price);
    }
    public boolean containsOrder(long orderId) { return activeOrders.containsKey(orderId); }

    public void enqueueOrder(Order order) {
        if (activeOrders.containsKey(order.getId())) throw new IllegalArgumentException("Duplicate active order ID");
        order.markResting();
        OrderNode node = order.routeTo(this);
        activeOrders.put(order.getId(), node);
    }

    OrderNode addBid(BuyOrder order) {
        return bids.computeIfAbsent(order.getPrice(), PriceLevel::new).addOrder(order);
    }
    OrderNode addAsk(SellOrder order) {
        return asks.computeIfAbsent(order.getPrice(), PriceLevel::new).addOrder(order);
    }

    public void removeBestBid() {
        if (!bids.isEmpty() && bids.firstEntry().getValue().isEmpty()) bids.pollFirstEntry();
    }
    public void removeBestAsk() {
        if (!asks.isEmpty() && asks.firstEntry().getValue().isEmpty()) asks.pollFirstEntry();
    }
    public void removeActiveOrder(long orderId) { activeOrders.remove(orderId); }

    /** Unlinks without changing order state: the coordinator releases holds and cancels it. */
    public Order cancelOrder(long orderId) {
        OrderNode node = activeOrders.remove(orderId);
        if (node == null) return null;
        Order order = node.getOrder();
        node.removeFromLevel();
        TreeMap<Long, PriceLevel> levels = order instanceof BuyOrder ? bids : asks;
        PriceLevel level = levels.get(order.getPrice());
        if (level != null && level.isEmpty()) levels.remove(order.getPrice());
        return order;
    }

    /** Conservative self-trade prevention: reject if any eligible opposite level contains own order. */
    public boolean wouldSelfMatch(Order incoming) {
        TreeMap<Long, PriceLevel> opposite = incoming instanceof BuyOrder ? asks : bids;
        for (PriceLevel level : opposite.values()) {
            if (incoming instanceof BuyOrder && level.getPrice() > incoming.getPrice()) break;
            if (incoming instanceof SellOrder && level.getPrice() < incoming.getPrice()) break;
            for (OrderNode n = level.getHead(); n != null; n = n.getNext()) {
                if (incoming.getParticipantId().equals(n.getOrder().getParticipantId())) return true;
            }
        }
        return false;
    }

    public boolean canFillBuyCompletely(long limitPrice, long requiredQty) {
        if (requiredQty <= 0) return false;
        long accumulated = 0L;
        for (PriceLevel level : asks.values()) {
            if (level.getPrice() > limitPrice) break;
            accumulated += level.getAvailableVolumeUpTo(requiredQty - accumulated);
            if (accumulated >= requiredQty) return true;
        }
        return false;
    }
    public boolean canFillSellCompletely(long limitPrice, long requiredQty) {
        if (requiredQty <= 0) return false;
        long accumulated = 0L;
        for (PriceLevel level : bids.values()) {
            if (level.getPrice() < limitPrice) break;
            accumulated += level.getAvailableVolumeUpTo(requiredQty - accumulated);
            if (accumulated >= requiredQty) return true;
        }
        return false;
    }

    /**
     * Snapshot includes references to resting orders and their mutable state, plus the incoming
     * order. Rebuilding preserves price and FIFO. O(open orders); this is a correctness-first
     * development transaction mechanism, NOT a high-frequency trading hot path.
     */
    public static final class Snapshot {
        private final List<Order> bidOrders;
        private final List<Order> askOrders;
        private final IdentityHashMap<Order, Order.State> states;

        private Snapshot(List<Order> bidOrders, List<Order> askOrders,
                         IdentityHashMap<Order, Order.State> states) {
            this.bidOrders = bidOrders;
            this.askOrders = askOrders;
            this.states = states;
        }
    }

    public Snapshot snapshot(Order incoming) {
        List<Order> buys = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        IdentityHashMap<Order, Order.State> states = new IdentityHashMap<>();
        states.put(incoming, incoming.snapshotState());
        for (PriceLevel level : bids.values()) {
            for (OrderNode n = level.getHead(); n != null; n = n.getNext()) {
                Order order = n.getOrder();
                buys.add(order);
                states.put(order, order.snapshotState());
            }
        }
        for (PriceLevel level : asks.values()) {
            for (OrderNode n = level.getHead(); n != null; n = n.getNext()) {
                Order order = n.getOrder();
                sells.add(order);
                states.put(order, order.snapshotState());
            }
        }
        return new Snapshot(buys, sells, states);
    }

    public void restore(Snapshot snapshot) {
        bids.clear();
        asks.clear();
        activeOrders.clear();
        snapshot.states.forEach(Order::restoreState);
        for (Order order : snapshot.bidOrders) enqueueOrder(order);
        for (Order order : snapshot.askOrders) enqueueOrder(order);
    }
}
