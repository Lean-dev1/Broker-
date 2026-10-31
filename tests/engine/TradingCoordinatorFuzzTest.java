package tests.engine;

import com.jerez.fintech.domain.*;
import com.jerez.fintech.ledger.Account;
import com.jerez.fintech.ledger.InsufficientFundsException;
import com.jerez.fintech.ledger.Ledger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TradingCoordinatorFuzzTest {

    private static final String BASE_ASSET = "ALUA";
    private static final String QUOTE_ASSET = "ARS";

    private static final int NUM_PARTICIPANTS = 10;
    private static final long INITIAL_BASE_PER_USER = 10_000L;
    private static final long INITIAL_QUOTE_PER_USER = 100_000_000L; // 100M ARS
    private static final int TOTAL_OPERATIONS = 25_000;

    @Test
    void shouldMaintainZeroSumConservationAcrossThousandsOfChaoticOperations() {
        // Arrange: Initialize core infrastructure
        TradingPair pair = new TradingPair(BASE_ASSET, QUOTE_ASSET);
        Ledger ledger = new Ledger();
        OrderBook book = new OrderBook("ALUA-ARS");
        MatchingEngine engine = new MatchingEngine(book);
        TradingCoordinator coordinator = new TradingCoordinator(pair, ledger, book, engine);

        List<Account> accounts = new ArrayList<>(NUM_PARTICIPANTS);
        for (int i = 0; i < NUM_PARTICIPANTS; i++) {
            Account account = ledger.createAccount("Trader-" + i);
            account.deposit(BASE_ASSET, INITIAL_BASE_PER_USER);
            account.deposit(QUOTE_ASSET, INITIAL_QUOTE_PER_USER);
            accounts.add(account);
        }

        final long expectedSystemBase = NUM_PARTICIPANTS * INITIAL_BASE_PER_USER;
        final long expectedSystemQuote = NUM_PARTICIPANTS * INITIAL_QUOTE_PER_USER;

        // Deterministic PRNG seed: guarantees 100% reproducible execution
        Random random = new Random(20261030L);
        List<Long> potentiallyActiveOrderIds = new ArrayList<>();
        long nextOrderId = 1L;
        long ticker = 1_000_000L;

        // Act: Inject 25,000 chaotic market events
        for (int step = 0; step < TOTAL_OPERATIONS; step++) {
            int action = random.nextInt(100);

            if (action < 75 || potentiallyActiveOrderIds.isEmpty()) {
                // 75% chance: Submit a new Buy or Sell order
                String participantId = "Trader-" + random.nextInt(NUM_PARTICIPANTS);
                boolean isBuy = random.nextBoolean();

                // Price between 950 and 1,050 ARS (forces frequent crosses and price improvements)
                long price = 950L + random.nextInt(101);
                // Quantity between 1 and 250 shares (forces partial fills and multi-level sweeps)
                long quantity = 1L + random.nextInt(250);

                long orderId = nextOrderId++;
                Order order = isBuy
                    ? new BuyOrder(orderId, participantId, price, quantity)
                    : new SellOrder(orderId, participantId, price, quantity);

                try {
                    coordinator.submitOrder(order, ticker++);
                    if (!order.isFilled()) {
                        potentiallyActiveOrderIds.add(orderId);
                    }
                } catch (InsufficientFundsException ignored) {
                    // Expected in chaotic trading when a trader locks all their capital;
                    // invariant must still hold!
                }
            } else {
                // 25% chance: Cancel a random previously submitted order
                int randomIndex = random.nextInt(potentiallyActiveOrderIds.size());
                long orderIdToCancel = potentiallyActiveOrderIds.remove(randomIndex);
                coordinator.cancelOrder(orderIdToCancel);
            }

            // Periodic Invariant Check every 5,000 operations (mid-flight audit)
            if (step % 5_000 == 0) {
                assertSystemConservation(accounts, expectedSystemBase, expectedSystemQuote);
            }
        }

        // Phase 2: End-of-Day Market Sweep (Cancel all remaining orders in the book)
        for (int i = 0; i < potentiallyActiveOrderIds.size(); i++) {
            coordinator.cancelOrder(potentiallyActiveOrderIds.get(i));
        }

        // Assert Final Invariants:
        // 1. Total system assets and cash must match initial deposits down to the exact unit
        assertSystemConservation(accounts, expectedSystemBase, expectedSystemQuote);

        // 2. Zero-Held Invariant: With the OrderBook completely drained, no trader can have locked funds
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            assertEquals(0L, account.getHeldBalance(BASE_ASSET),
                "Collateral leak detected in BASE_ASSET for " + account.getParticipantId());
            assertEquals(0L, account.getHeldBalance(QUOTE_ASSET),
                "Collateral leak detected in QUOTE_ASSET for " + account.getParticipantId());
        }

        // 3. The OrderBook must be completely empty
        assertNull(book.getBestBid(), "OrderBook Bids should be empty after EOD cancellation");
        assertNull(book.getBestAsk(), "OrderBook Asks should be empty after EOD cancellation");
    }

    private void assertSystemConservation(List<Account> accounts, long expectedBase, long expectedQuote) {
        long actualTotalBase = 0L;
        long actualTotalQuote = 0L;

        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            actualTotalBase = Math.addExact(actualTotalBase, acc.getTotalBalance(BASE_ASSET));
            actualTotalQuote = Math.addExact(actualTotalQuote, acc.getTotalBalance(QUOTE_ASSET));
        }

        assertEquals(expectedBase, actualTotalBase, "System Base Asset conservation violated!");
        assertEquals(expectedQuote, actualTotalQuote, "System Quote Asset conservation violated!");
    }
}