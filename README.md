# Broker- — In-memory financial matching simulator

Experimental Java 17 project for studying price-time matching, order lifecycle,
simulated settlement, invariants and performance. **Not a production broker.**

## Current functionality

- Price-time-priority limit order matching with FIFO price levels.
- GTC, IOC and FOK execution through `TradingCoordinator`.
- Deterministic trade timestamps injected by the caller.
- Multi-asset in-memory accounts with held collateral.
- Immutable, per-asset balanced **trade** postings recorded in memory.
- Ordered in-memory audit records for external deposits/withdrawals and
  internal holds/releases; all balance writes go through Ledger.
- Independent `LedgerAuditor.replay(journal)` and `ledger.reconcile()` to verify
  recorded postings reproduce current available/held balances.
- Explicit order lifecycle and quantity conservation.
- Single-writer in-memory transaction rollback on synchronous failures.
- JUnit regressions, seeded randomized tests and JMH microbenchmarks.

**Important:** Rollback uses a full in-memory snapshot per command. This
sacrifices throughput and memory for correctness in the current simulator.
Trade postings and balance operations share a local in-memory journal;
there is still no durable or complete production double-entry ledger. There is no crash recovery,
persistent journal, security, durable audit trail, network
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
ledger.deposit("seller", "ALUA", 10);
ledger.deposit("buyer", "ARS", 1000);
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
- [ADR 0001: Balanced trade postings](Docs/adr/0001-balanced-trade-postings.md)
- [ADR 0002: Durable transaction options](Docs/adr/0002-durable-transaction-boundary-options.md)
- [ADR 0003: Audit all balance movements](Docs/adr/0003-audit-all-balance-movements.md)
- [ADR 0004: Independent ledger reconciliation](Docs/adr/0004-independent-ledger-reconciliation.md)

## Open engineering problems

Durable and idempotent transaction processing, double-entry accounting,
account and order isolation, failure recovery, instrument validation, authentication
and external APIs. No benchmark claim should ignore snapshot costs.
