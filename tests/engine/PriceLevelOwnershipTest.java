package tests.engine;

import main.domain.BuyOrder;
import main.domain.OrderNode;
import main.domain.PriceLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PriceLevelOwnershipTest {
    @Test
    void cannotRemoveForeignNodeOrDetachedNode() {
        PriceLevel first = new PriceLevel(100);
        PriceLevel second = new PriceLevel(200);
        OrderNode firstNode = first.addOrder(new BuyOrder(1, "a", 100, 10));
        OrderNode secondNode = second.addOrder(new BuyOrder(2, "b", 200, 10));

        assertThrows(IllegalArgumentException.class, () -> second.removeOrder(firstNode));
        assertSame(firstNode, first.getHead());
        assertSame(secondNode, second.getHead());

        first.removeOrder(firstNode);
        assertTrue(first.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> first.removeOrder(firstNode));
        assertSame(secondNode, second.getHead());
    }
}
