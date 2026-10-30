package test.engine;

import src.main.domain.*;
import src.main.ledger.Account;
import src.main.ledger.InsufficientFundsException;
import src.main.ledger.Ledger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradingCoordinatorTest {

    private static final String BASE_ASSET = "ALUA";
    private static final String QUOTE_ASSET = "ARS";

    private TradingPair pair;
    private Ledger ledger;
    private OrderBook book;
    private MatchingEngine engine;
    private TradingCoordinator coordinator;
    private long ticker;

    private Account buyerAccount;
    private Account sellerAccount;

    @BeforeEach
    void setUp() {
        pair = new TradingPair(BASE_ASSET, QUOTE_ASSET);
        ledger = new Ledger();
        book = new OrderBook("ALUA-ARS");
        engine = new MatchingEngine(book);
        coordinator = new TradingCoordinator(pair, ledger, book, engine);
        ticker = 1000L;

        buyerAccount = ledger.createAccount("Buyer1");
        sellerAccount = ledger.createAccount("Seller1");
    }

    @Test
    void shouldRejectOrderAndKeepBookCleanWhenFundsAreInsufficient() {
        // Arrange: Buyer only has 50,000 ARS, tries to buy 100 shares at 1,000 ARS (needs 100,000 ARS)
        buyerAccount.deposit(QUOTE_ASSET, 50_000L);

        BuyOrder invalidOrder = new BuyOrder(1L, "Buyer1", 1_000L, 100L);

        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> 
            coordinator.submitOrder(invalidOrder, ticker++)
        );

        // Invariant: Account state and OrderBook must remain completely untouched
        assertEquals(50_000L, buyerAccount.getAvailableBalance(QUOTE_ASSET));
        assertEquals(0L, buyerAccount.getHeldBalance(QUOTE_ASSET));
        assertNull(book.getBestBid(), "Rejected order must never enter the OrderBook");
    }

    @Test
    void shouldSettleExactMatchAndRefundBuyerPriceImprovement() {
        // Arrange:
        // Seller deposits 100 ALUA and offers them at 1,000 ARS each
        sellerAccount.deposit(BASE_ASSET, 100L);
        coordinator.submitOrder(new SellOrder(1L, "Seller1", 1_000L, 100L), ticker++);

        // Verify seller's shares are held in the book
        assertEquals(0L, sellerAccount.getAvailableBalance(BASE_ASSET));
        assertEquals(100L, sellerAccount.getHeldBalance(BASE_ASSET));

        // Buyer deposits 110,000 ARS and sends an aggressive BuyOrder willing to pay up to 1,050 ARS
        // Required hold: 1,050 * 100 = 105,000 ARS
        buyerAccount.deposit(QUOTE_ASSET, 110_000L);

        // Act: Match executes at resting seller's price (1,000 ARS)
        List<Trade> trades = coordinator.submitOrder(new BuyOrder(2L, "Buyer1", 1_050L, 100L), ticker++);

        // Assert Trade
        assertEquals(1, trades.size());
        assertEquals(1_000L, trades.get(0).matchPrice());
        assertEquals(100L, trades.get(0).executedQuantity());

        // Assert Buyer Balances (Spent 100,000 ARS, refunded 5,000 ARS price improvement, 0 held)
        assertEquals(10_000L, buyerAccount.getAvailableBalance(QUOTE_ASSET));
        assertEquals(0L, buyerAccount.getHeldBalance(QUOTE_ASSET));
        assertEquals(100L, buyerAccount.getAvailableBalance(BASE_ASSET));

        // Assert Seller Balances (Consumed 100 held ALUA, received 100,000 ARS)
        assertEquals(0L, sellerAccount.getAvailableBalance(BASE_ASSET));
        assertEquals(0L, sellerAccount.getHeldBalance(BASE_ASSET));
        assertEquals(100_000L, sellerAccount.getAvailableBalance(QUOTE_ASSET));
    }

    @Test
    void shouldHandlePartialFillAndReleaseRemainingHeldFundsOnCancel() {
        // Arrange: Seller puts 40 ALUA at 1,000 ARS
        sellerAccount.deposit(BASE_ASSET, 40L);
        coordinator.submitOrder(new SellOrder(1L, "Seller1", 1_000L, 40L), ticker++);

        // Buyer has 100,000 ARS and wants 100 ALUA at 1,000 ARS
        buyerAccount.deposit(QUOTE_ASSET, 100_000L);

        // Act 1: Partial execution of 40 shares (60 shares remain resting in the book)
        List<Trade> trades = coordinator.submitOrder(new BuyOrder(2L, "Buyer1", 1_000L, 100L), ticker++);

        // Assert intermediate state after partial fill
        assertEquals(1, trades.size());
        assertEquals(40L, buyerAccount.getAvailableBalance(BASE_ASSET));
        assertEquals(0L, buyerAccount.getAvailableBalance(QUOTE_ASSET));
        // 60 remaining shares * 1,000 ARS = 60,000 ARS must remain held
        assertEquals(60_000L, buyerAccount.getHeldBalance(QUOTE_ASSET));

        // Act 2: Buyer cancels the resting order for the remaining 60 shares
        boolean canceled = coordinator.cancelOrder(2L);

        // Assert final state after cancellation
        assertTrue(canceled);
        assertEquals(60_000L, buyerAccount.getAvailableBalance(QUOTE_ASSET), "Remaining held ARS must be released");
        assertEquals(0L, buyerAccount.getHeldBalance(QUOTE_ASSET), "Held balance must drop to zero");
        assertNull(book.getBestBid(), "Book must be empty after cancellation");
    }

    @Test
    void shouldReturnFalseWhenCancelingNonExistentOrAlreadyFilledOrder() {
        assertFalse(coordinator.cancelOrder(999L));
    }
}