# ADR-0005 — In-memory idempotent submission commands

- **Status:** Accepted for the in-memory simulator
- **Date:** 2026-11-07
- **Related:** issue #20
- **Depends on:** ADR-0003, ADR-0004

## Context

An API client may repeat an order submission after losing a network response.
Rejecting a duplicate order ID is not equivalent to providing idempotency:
the client still cannot determine whether the original command was accepted.
Order objects are mutable, so comparing their current status to detect an
identical request would also be incorrect.

We need stable request identity and a deterministic response without prematurely
claiming cross-restart safety.

## Decision

Add `TradingCoordinator.submitOrderIdempotent(Order, timestamp, timeInForce,
commandId)`. A client-supplied **command ID** is distinct from the **order ID**.
The command ID is a nonblank, ASCII-restricted, 1–128 character string.

At the beginning of the call, capture an immutable request fingerprint:
order ID, participant ID, buy/sell side, limit price, **original** quantity,
logical timestamp, and time-in-force policy.

- If the command ID is uncommitted, process the order normally and cache an
  immutable `SubmissionReceipt` inside the coordinator's transaction boundary.
- A repeat with the same ID and the same fingerprint returns the **original
  receipt**, without invoking matching, reserving funds or creating postings.
- A repeat with the same ID but a different fingerprint throws
  `IdempotencyConflictException` before any mutation.
- A submission that fails or is rolled back does **not** consume its command ID.
- A successfully canceled no-fill FOK *does* consume the command ID and retains
  its cancellation receipt.
- The receipt contains snapshot values at **submission acceptance**, not a
  live view of the order after later fills or cancellations.
- Existing `submitOrder` overloads remain available and preserve their
  original behavior; they are **not** retry-safe by command ID.
- Memoized IDs and receipts are included in the coordinator's in-memory
  rollback checkpoint; command processing remains serialized.

## Why

Idempotency is required before exposing a network API or persisting commands.
Defining an immutable command fingerprint now makes conflict behavior and
retry semantics testable independently of transport and storage.

Separating command ID and order ID permits different concepts to evolve:
a later cancel command will need its own ID but refer to an existing order.

## Alternatives

- **Use only the order ID:** rejects repeats but cannot return the original
  acceptance result or represent future independently identifiable commands.
- **Store a request object:** unsafe because order objects are mutable.
- **Write through directly to PostgreSQL:** persistence decisions and atomic
  commit semantics are not yet finalized (ADR-0002).
- **Cache forever in RAM:** the current development baseline, not a production
  retention strategy.

## Consequences and limitations

**No crash durability or cross-process exactly-once semantics.**
The cache and financial state both vanish on JVM termination. Command IDs
are not yet included in a durably committed journal, and a second
coordinator/process cannot deduplicate against this one.

The cache currently grows with the number of accepted commands. We need a
durable retention/compaction policy and a transactional commit that records
the command, order events, ledger postings and receipt atomically.

Receipt conflict checking assumes incoming immutable order fields are
trustworthy. Authentication and participant authorization remain out of scope.

## Acceptance tests

1. Multiple identical retries, including concurrent retries, cause one hold
   and return one immutable receipt.
2. Reusing an ID with changed order fields, time-in-force or timestamp fails
   before any journal or book modification.
3. A partially filled IOC or no-fill FOK is never processed a second time.
4. A receipt remains a historical acceptance response after later cancellation.
5. A rolled-back submission leaves the command ID available for a correct retry.
