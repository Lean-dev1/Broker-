# Architecture (current implementation)

- `main.domain`: orders, trade events, price levels and the in-memory order book.
- `main.engine.MatchingEngine`: deterministic price/time matching logic.
- `main.engine.TradingCoordinator`: simulated pre-trade funds reservation and settlement.
- `main.ledger`: mutable participant balances held in memory.
- `main.performance`: a JMH benchmark.

## Important limitation

The coordinator **does not provide transactional atomicity** across the matching
engine, order book, and ledger. A failure during settlement can leave partial
mutations. The current implementation is for local experiments only, not real funds.

## Time-in-force

The primary engine path currently behaves as GTC for unfilled remainder.
IOC/FOK strategy objects exist but are not connected to the submitted-order
execution path. They are not delivered capabilities.

## Concurrency

The coordinator's public mutation methods are synchronized on the coordinator.
The standalone matching engine and order book are not thread-safe. Concurrent
or external mutations could still violate the coordinator's assumptions.
