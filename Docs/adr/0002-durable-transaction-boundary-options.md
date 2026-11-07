# ADR-0002 — Durable journal and replay options

- **Status:** Proposed; **no storage technology selected yet**
- **Date:** 2026-11-06
- **Related:** issue #20

## Context

The coordinator currently snapshots book state, orders, IDs and balances in
memory before processing. Synchronous exceptions can roll back. Process
termination or power failure destroys both the state and the in-memory
`LedgerTransaction` list. Direct mutations to accounts outside the
coordinator also prevent deterministic reconstruction.

## Required invariants before choosing a persistence mechanism

1. A command is acknowledged only after a **durable commit**.
2. Recovery produces either the complete pre-transaction state or the
   complete post-transaction state, never a partially applied trade.
3. Replaying a committed command must never create duplicate executions
   or move the same funds twice.
4. Every committed command has a unique persistent ID and ordered sequence.
5. Replay reproduces the book's FIFO order, trade list, order lifecycle,
   account balances and consumed identifiers.
6. Torn writes, checksum failures, incompatible schema versions and
   interrupted recovery have explicit failure behavior.
7. All state mutation (including deposits and withdrawals) passes through
   the same command boundary or is represented by a separate durable event.

## Options to evaluate

| Option | Benefits | Costs / risks |
|---|---|---|
| PostgreSQL transactional tables + event/outbox log | Mature WAL, ACID transactions, migration tools | DB round trips and coupling; need careful event order and idempotent delivery |
| Append-only local WAL + deterministic replay | Fine control over write path and state machine | fsync discipline, torn records, checksums, compaction and recovery complexity |
| Durable log with periodic verified snapshots | Potential fast replay and audit-friendly history | Versioning, snapshot/log consistency, failure-mode complexity |

## Recommended evaluation approach

Start by specifying the exact event/command schema and commit boundary,
then prototype the **simplest** strategy that passes fault-injection tests.
PostgreSQL is a pragmatic baseline to compare against a custom local WAL;
do not choose solely on presumed performance.

Run tests that kill the process immediately before and after commit, during
an append, and during replay. Confirm deterministic recovery and replay
idempotency with repeated commands.

## Exclusions

This ADR does not authorize a real broker integration, Kafka, microservices,
financial custody, or production use. The trade posting journal introduced in
ADR-0001 is **in-memory only** and is not yet a solution to issue #20.
