package main.engine;

import com.jerez.fintech.domain.*;
import com.jerez.fintech.engine.MatchingEngine;
import com.jerez.fintech.engine.TradingCoordinator;
import com.jerez.fintech.ledger.Account;
import com.jerez.fintech.ledger.Ledger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class MatchingEngineBenchmark {

    private static final String BASE_ASSET = "ALUA";
    private static final String QUOTE_ASSET = "ARS";

    // Pure Engine state (Iteration 1 baseline)
    private MatchingEngine pureEngine;
    private OrderBook pureBook;

    // Full Coordinator state (Iteration 2 pipeline)
    private TradingCoordinator coordinator;

    private long ticker;

    @Setup(Level.Trial)
    public void setUp() {
        // 1. Setup Pure Engine
        pureBook = new OrderBook("ALUA-ARS");
        pureEngine = new MatchingEngine(pureBook);

        // 2. Setup Full Coordinator Pipeline (Risk + Matching + Ledger Settlement)
        TradingPair pair = new TradingPair(BASE_ASSET, QUOTE_ASSET);
        Ledger ledger = new Ledger();
        OrderBook coordinatedBook = new OrderBook("ALUA-ARS");
        MatchingEngine coordinatedEngine = new MatchingEngine(coordinatedBook);
        coordinator = new TradingCoordinator(pair, ledger, coordinatedBook, coordinatedEngine);

        Account traderA = ledger.createAccount("TraderA");
        Account traderB = ledger.createAccount("TraderB");

        // Initial liquidity buffer for ping-pong trading
        traderA.deposit(BASE_ASSET, 1_000_000L);
        traderA.deposit(QUOTE_ASSET, 1_000_000_000L);

        traderB.deposit(BASE_ASSET, 1_000_000L);
        traderB.deposit(QUOTE_ASSET, 1_000_000_000L);

        ticker = 1L;
    }

    /**
     * Baseline: Measures raw OrderBook + MatchingEngine without accounting.
     */
    @Benchmark
    public void measurePureMatching(Blackhole blackhole) {
        List<Trade> maker = pureEngine.process(new SellOrder(ticker++, "TraderA", 1_000L, 100L), ticker);
        List<Trade> taker = pureEngine.process(new BuyOrder(ticker++, "TraderB", 1_000L, 100L), ticker);

        blackhole.consume(maker);
        blackhole.consume(taker);
    }

    /**
     * Full Pipeline: Measures Pre-Trade Risk Hold + Matching + Atomic Settlement (with Price Improvement).
     * Performs a full round-trip (2 trades = 4 orders) to keep account balances in perpetual equilibrium.
     */
    @Benchmark
    public void measureFullCoordinatorRoundTrip(Blackhole blackhole) {
        // Leg 1: TraderA sells 100 @ 1000, TraderB buys 100 @ 1050 (triggers Price Improvement refund of 5000 ARS)
        List<Trade> maker1 = coordinator.submitOrder(new SellOrder(ticker++, "TraderA", 1_000L, 100L), ticker);
        List<Trade> taker1 = coordinator.submitOrder(new BuyOrder(ticker++, "TraderB", 1_050L, 100L), ticker);

        // Leg 2: TraderB sells back 100 @ 1000, TraderA buys back 100 @ 1000 (restores exact initial balances)
        List<Trade> maker2 = coordinator.submitOrder(new SellOrder(ticker++, "TraderB", 1_000L, 100L), ticker);
        List<Trade> taker2 = coordinator.submitOrder(new BuyOrder(ticker++, "TraderA", 1_000L, 100L), ticker);

        blackhole.consume(maker1);
        blackhole.consume(taker1);
        blackhole.consume(maker2);
        blackhole.consume(taker2);
    }
}