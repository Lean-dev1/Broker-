# Broker- — In-memory financial matching simulator

Experimental Java 17 project for studying price-time matching, order lifecycle,
simulated settlement, invariants and performance. **Not a production broker.**

## Current functionality

- Price-time-priority limit order matching with FIFO price levels.
- GTC, IOC and FOK execution through `TradingCoordinator`.
- Deterministic trade timestamps injected by the caller.
- Multi-asset in-memory accounts with held collateral.
- Explicit order lifecycle and quantity conservation.
- Single-writer in-memory transaction rollback on synchronous failures.
- JUnit regressions, seeded randomized tests and JMH microbenchmarks.

**Important:** Rollback uses a full in-memory snapshot per command. This
sacrifices throughput and memory for correctness in the current simulator.
There is no crash recovery, persistent journal, security, audit trail, network
access layer, or concurrency isolation from external mutations.

## Requirements

- JDK 17+
- Maven 3.9+

```bash
mvn clean verify
```

## Example

```java
TradingPair pair = new TradingPair("ALUA", "ARS");
Ledger ledger = new Ledger();
OrderBook book = new OrderBook("ALUA-ARS");
TradingCoordinator coordinator =
    new TradingCoordinator(pair, ledger, book, new MatchingEngine(book));

Account seller = ledger.createAccount("seller");
Account buyer = ledger.createAccount("buyer");
seller.deposit("ALUA", 10);
buyer.deposit("ARS", 1000);
coordinator.submitOrder(new SellOrder(1, "seller", 100, 10), 1L);
List<Trade> trades =
    coordinator.submitOrder(new BuyOrder(2, "buyer", 100, 10), 2L, TimeInForce.FOK);
```

`Order`, `TradingPair`, `TimeInForce`, `Trade`, `Account`,
`Ledger`, `OrderBook`, `MatchingEngine` and `TradingCoordinator`
must be imported from their respective `main.domain`, `main.ledger` and
`main.engine` packages.

The project currently uses its original `src/main` and `tests`
directories via explicit Maven configuration.

## Documents

- [Vision](Docs/Vision.md)
- [Scope](Docs/Scope.md)
- [Requirements and acceptance criteria](Docs/Requirements.md)
- [Domain model and invariants](Docs/domain-model.md)
- [Architecture](Docs/architecture.md)
- [Quality attributes](Docs/quality-attributes.md)
- [Risk register](Docs/risk-register.md)

## Open engineering problems

Durable and idempotent transaction processing, double-entry accounting,
account and order isolation, failure recovery, instrument validation, authentication
and external APIs. No benchmark claim should ignore snapshot costs.
