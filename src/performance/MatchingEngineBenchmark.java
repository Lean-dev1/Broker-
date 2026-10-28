package performance;

import domain.*;
import engine.MatchingEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime) // Queremos medir latencia (tiempo promedio por operación)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread) // Cada hilo de prueba tiene su propio estado
@Fork(1) // Levantar una JVM limpia para evitar contaminación de memoria
@Warmup(iterations = 3, time = 1) // 3 rondas para calentar el compilador JIT
@Measurement(iterations = 5, time = 1) // 5 rondas de medición real
public class MatchingEngineBenchmark {

    private MatchingEngine engine;
    private OrderBook book;
    private long ticker;

    @Setup(Level.Trial)
    public void setUp() {
        book = new OrderBook("ALUA-AR");
        engine = new MatchingEngine(book);
        ticker = 0;
    }

    @Benchmark
    public void measureContinuousMatching(Blackhole blackhole) {
        // 1. Inyectamos una orden pasiva (Maker)
        List<Trade> makerResult = engine.process(new SellOrder(ticker++, "Maker", 10000, 100), ticker);
        
        // 2. Inyectamos una orden agresiva (Taker) que la cruza inmediatamente
        List<Trade> takerResult = engine.process(new BuyOrder(ticker++, "Taker", 10000, 100), ticker);

        // Blackhole evita que la JVM elimine el código pensando que los resultados no se usan
        blackhole.consume(makerResult);
        blackhole.consume(takerResult);
    }
}