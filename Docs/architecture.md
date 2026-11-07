# Architecture (in-memory implementation)

## Components

- `main.domain`: order state machine, price levels, book, trade events.
- `main.engine.MatchingEngine`: deterministic price-time matching (GTC-style).
- `main.engine.TradingCoordinator`: order admission, simulated risk hold,
  matching, ledger settlement, cancellation, and time-in-force semantics.
- `main.ledger`: in-memory account balances and an ordered journal of
  executed trades and explicit balance operations. Trades and holds/releases
  have balanced postings; deposits/withdrawals are external flows.
  No durable accounting journal yet.
- `main.performance`: development JMH experiments.

## Single-process transaction mechanism

The coordinator serializes its own submit/cancel operations and captures a
checkpoint immediately before a mutating attempt:

1. Book entries, FIFO order, and mutable states of resting/incoming orders.
2. Every account balance (`available` and `held` by asset), plus the
   in-memory trade-posting journal length and sequence counter.
3. Coordinator's open-order index and consumed order-ID registry.

On synchronous exceptions, it restores those snapshots and rethrows. This
also covers a second-trade settlement failure following an earlier settlement.

**Cost:** O(number of live orders + number of participant balances) per
submission. This favors correctness for the experimental iteration and is
**not** a viable HFT architecture.

**Not crash-safe:** a power loss/process death can still destroy all state.
The book/ledger/order objects are mutable and exposed to callers; bypassing
the coordinator or accessing those objects concurrently voids its safety
assumptions. An external matching engine is not transactionally synchronized.

## Time-in-force

The standalone matching engine retains GTC semantics; the coordinator supports
GTC, IOC and FOK. IOC cancels the unmatched resting remainder after executing
and settling available trades. FOK checks eligible aggregate liquidity before
matching and either fills entirely or cancels without touching resting orders.
The old unused policy strategy types have been removed in favor of the
`TimeInForce` enum as the source of truth.

## Next architectural milestone

ADRs 0001 and 0003 introduce an auditable in-memory journal for all balance
movements without selecting persistence.
ADR-0002 compares durable log approaches; storage technology is still undecided.

Replace full-state snapshots with a properly isolated order-processing boundary,
a prepared execution plan and a durable, replayable journal. Design idempotent
command IDs and an auditable double-entry ledger before use with real funds.
